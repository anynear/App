package app.anynear

import app.anynear.app.AboLimit
import app.anynear.app.AboPlan
import app.anynear.app.AboPreview
import app.anynear.attachments.AttachmentLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Subscription preview (ADR 0076): as the website delivers it under /v1/abo.json (example values). */
class AboPreviewTest {
    private val sample = """
        {"version":1,"preview":true,"currency":"EUR","trialMonths":3,"future":"egal",
         "interest":{"de":"https://example.org/de/abo/","en":"https://example.org/en/abo/"},
         "plans":[
          {"id":"free","name":{"de":"Kostenlos","en":"Free"},"monthlyCents":0,"yearlyCents":0,"features":{"de":["1 GB"],"en":["1 GB"]}},
          {"id":"plus","name":{"de":"Plus","en":"Plus"},"monthlyCents":500,"yearlyCents":5000,"recommended":true,
           "features":{"de":["100 GB Speicher","Neu: etwas anderes"],"en":["100 GB storage","New: something else"]},"storageGb":100},
          {"id":"gruppe","monthlyCents":900,"yearlyCents":9000,"features":{"en":["Only English"]}}
         ]}
    """.trimIndent()

    @Test
    fun takesNamesAndFeaturesAsTheyCome() {
        val p = AboPreview.parse(sample)!!
        assertEquals(3, p.trialMonths)
        assertEquals("Kostenlos", p.free?.name(german = true))
        assertEquals(listOf("plus", "gruppe"), p.paid.map { it.id })
        val plus = p.paid.first()
        assertEquals(listOf("100 GB Speicher", "Neu: etwas anderes"), plus.features(german = true))
        assertEquals(listOf("100 GB storage", "New: something else"), plus.features(german = false))
        // Unknown plan without a name and without German text blocks: ID as name, English text blocks.
        val other = p.paid.last()
        assertEquals("Gruppe", other.name(german = true))
        assertEquals(listOf("Only English"), other.features(german = true))
        assertEquals("https://example.org/de/abo/?paket=plus&takt=jahr", p.interestUrl(plus, yearly = true, german = true))
        assertEquals("https://example.org/en/abo/?paket=gruppe&takt=monat", p.interestUrl(other, yearly = false, german = false))
    }

    @Test
    fun yearlyShowsSavingAndPricePerMonth() {
        assertEquals(25, AboPlan("single", monthlyCents = 299, yearlyCents = 2690).yearlySaving())
        assertEquals(25, AboPlan("plus", monthlyCents = 599, yearlyCents = 5390).yearlySaving())
        assertEquals(25, AboPlan("family", monthlyCents = 999, yearlyCents = 8990).yearlySaving())
        assertEquals(19, AboPlan("alt", monthlyCents = 299, yearlyCents = 2900).yearlySaving())
        assertEquals(0, AboPlan("free").yearlySaving())
        assertEquals(0, AboPlan("teurer", monthlyCents = 100, yearlyCents = 1500).yearlySaving())
        assertEquals(224, AboPlan("single", monthlyCents = 299, yearlyCents = 2690).yearlyPerMonth())
        assertEquals(749, AboPlan("family", monthlyCents = 999, yearlyCents = 8990).yearlyPerMonth())
    }

    @Test
    fun refusesUnusableAnswers() {
        assertNull(AboPreview.parse("<html>"))
        assertNull(AboPreview.parse("""{"plans":[]}"""))
        val noHttps = AboPreview.parse(sample.replace("https://example.org/de/abo/", "http://example.org/de/abo/"))!!
        assertNull(noHttps.interestUrl(noHttps.paid.first(), yearly = true, german = true))
    }

    @Test
    fun pointsToTheCheapestPlanThatAllowsMore() {
        val p = AboPreview.parse(
            """
            {"plans":[
             {"id":"free","monthlyCents":0,"limits":{"textChars":1000,"attachmentMb":10,"attachments":5}},
             {"id":"family","monthlyCents":999,"limits":{"textChars":5000,"attachmentMb":50,"attachments":10}},
             {"id":"plus","monthlyCents":599,"limits":{"textChars":10000,"attachmentMb":100,"attachments":20}},
             {"id":"single","monthlyCents":299,"limits":{"textChars":5000,"attachmentMb":50,"attachments":10}}
            ]}
            """.trimIndent(),
        )!!
        assertEquals("single" to 5000, p.upgradeFor(AboLimit.TEXT)?.let { it.first.id to it.second })
        assertEquals("single" to 50, p.upgradeFor(AboLimit.ATTACHMENT_SIZE)?.let { it.first.id to it.second })
        // Already at the limit with Single: then Plus.
        assertEquals("plus" to 20, p.upgradeFor(AboLimit.ATTACHMENT_COUNT, current = 10)?.let { it.first.id to it.second })
        assertNull(p.upgradeFor(AboLimit.TEXT, current = 10000))
        // Older preview without limits: no hint.
        assertNull(AboPreview.parse(sample)!!.upgradeFor(AboLimit.TEXT))
    }

    @Test
    fun comparisonTableInTheLanguageOtherwiseEnglish() {
        val p = AboPreview.parse(
            """
            {"trialMonths":3,"plans":[{"id":"free","monthlyCents":0},{"id":"plus","monthlyCents":500,"yearlyCents":5000}],
             "compare":{"en":{"rows":[{"label":"Quota","values":{"free":"250 MB","plus":"20 GB"}},{"label":"Bin","values":{"free":"7 days"}}],
                              "note":"All plans include messages."}}}
            """.trimIndent(),
        )!!
        val c = p.compare(german = true)!!
        assertEquals(listOf("Quota", "Bin"), c.rows.map { it.label })
        assertEquals("20 GB", c.rows[0].value(p.paid.first()))
        assertEquals("–", c.rows[1].value(p.paid.first()))
        assertEquals("All plans include messages.", c.note)
        // Older previews without a table: the screen falls back to the text blocks.
        assertNull(AboPreview.parse(sample)!!.compare(german = true))
    }

    @Test
    fun voiceLengthFollowsTheOwnPlan() {
        val p = AboPreview.parse(
            """
            {"plans":[
             {"id":"free","monthlyCents":0,"limits":{"textChars":1000,"voiceMinutes":5}},
             {"id":"single","monthlyCents":299,"limits":{"textChars":5000,"voiceMinutes":15}},
             {"id":"plus","monthlyCents":599,"limits":{"textChars":10000,"voiceMinutes":30}}
            ]}
            """.trimIndent(),
        )!!
        // ADR 0099: hint names the cheapest plan with longer voice messages.
        assertEquals("single" to 15, p.upgradeFor(AboLimit.VOICE_LENGTH)?.let { it.first.id to it.second })
        assertEquals("plus" to 30, p.upgradeFor(AboLimit.VOICE_LENGTH, current = 15)?.let { it.first.id to it.second })
        // Recording until the limit of „Kostenlos“ (the own plan until the entitlement service).
        assertEquals(5L * 60_000, AttachmentLimits.audioMaxMillis(p.free?.limits?.voiceMinutes))
        assertEquals(15L * 60_000, AttachmentLimits.audioMaxMillis(15))
        // Without preview or with an older one: the previous 5 minutes.
        assertEquals(AttachmentLimits.AUDIO_MAX_MILLIS, AttachmentLimits.audioMaxMillis(null))
        assertEquals(AttachmentLimits.AUDIO_MAX_MILLIS, AttachmentLimits.audioMaxMillis(AboPreview.parse(sample)!!.free?.limits?.voiceMinutes))
        assertEquals(AttachmentLimits.AUDIO_MAX_MILLIS, AttachmentLimits.audioMaxMillis(0))
        // Never more than the fixed upper bound, whatever the website says.
        assertEquals(AttachmentLimits.AUDIO_HARD_MAX_MILLIS, AttachmentLimits.audioMaxMillis(600))
        // Older preview without voiceMinutes: no hint.
        assertNull(AboPreview.parse(sample)!!.upgradeFor(AboLimit.VOICE_LENGTH))
    }
}
