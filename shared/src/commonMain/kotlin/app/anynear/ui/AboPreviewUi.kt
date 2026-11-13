package app.anynear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.anynear.app.AboCompare
import app.anynear.app.AboPlan
import app.anynear.app.AboPreview
import app.anynear.app.AboPreviews
import app.anynear.i18n.Language
import app.anynear.i18n.currentLanguage
import app.anynear.i18n.texts

/**
 * „Mehr Speicher“ (ADR 0076): the subscription plans with expected prices from the website and per plan
 * „Interesse bekunden“, which opens the page on anynear.app with the plan preselected. No purchase yet.
 */
@Composable
internal fun AboPreviewScreen(title: String = texts.files.moreStorage, onBack: () -> Unit) {
    SystemBackHandler(enabled = true, onBack = onBack)
    var attempt by remember { mutableIntStateOf(0) }
    // null: still loading; Result with null: cannot be loaded.
    val result by produceState<Result<AboPreview?>?>(null, attempt) {
        value = null
        value = Result.success(AboPreviews.load())
    }
    // Monthly as the default and large; yearly small below it.
    var yearly by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        AppHeader(title, leading = { HeaderBackButton(onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val p = result?.getOrNull()
            if (p == null) {
                if (result != null) {
                    Text(texts.files.aboOffline, style = MaterialTheme.typography.bodyLarge)
                    AppButton(texts.common.retry, { attempt++ }, kind = ButtonKind.Secondary)
                } else {
                    Text(texts.files.aboLoading, style = MaterialTheme.typography.bodyLarge)
                }
                return@Column
            }
            Text(texts.files.aboIntro(p.trialMonths), style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !yearly, onClick = { yearly = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text(texts.files.aboMonthly)
                }
                SegmentedButton(selected = yearly, onClick = { yearly = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text(texts.files.aboYearly)
                }
            }
            val compare = p.compare(german())
            if (compare != null) {
                // One table: the free plan the person has now on the left, the paid plans next to it. Tapping a paid
                // plan selects it; below it stand its price details and the one button.
                var selectedId by remember(p) { mutableStateOf((p.paid.firstOrNull { it.recommended } ?: p.paid.firstOrNull())?.id) }
                val selected = p.paid.firstOrNull { it.id == selectedId }
                Text(texts.files.aboChooseHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompareTable(p, compare, yearly, selectedId) { selectedId = it.id }
                compare.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                selected?.let { PlanCard(p, it, yearly, showFeatures = false) }
            } else {
                // Older previews without a table: the free plan first, so that every plan can be read against it.
                p.free?.let { FreePlanCard(it) }
                p.paid.forEach { PlanCard(p, it, yearly) }
            }
            Text(texts.files.aboNetwork, style = MaterialTheme.typography.bodyMedium)
            Text(
                texts.files.aboPreviewNote + " " + texts.files.aboInterestNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Comparison of all plans in four columns (free plan first). Values that differ from the free plan are bold, so that
 * the gain over today shows at a glance. The selected column is framed; the columns lie behind the content so that the
 * row labels can run across the whole width.
 */
@Composable
private fun CompareTable(preview: AboPreview, compare: AboCompare, yearly: Boolean, selectedId: String?, onSelect: (AboPlan) -> Unit) {
    val free = preview.free
    val plans = listOfNotNull(free) + preview.paid
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            plans.forEach { plan ->
                val isSelected = plan.id == selectedId
                val paid = plan.monthlyCents > 0
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(shape)
                        .background(
                            when {
                                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                                paid -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surface
                            },
                        )
                        .border(
                            if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape,
                        )
                        .then(if (paid) Modifier.selectable(isSelected, role = Role.RadioButton) { onSelect(plan) } else Modifier),
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CompareRow(plans) { plan ->
                Text(plan.name(german()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                val tag = when {
                    plan.monthlyCents == 0L -> texts.files.aboYourPlan
                    plan.recommended -> texts.files.aboRecommendedShort
                    else -> null
                }
                Text(tag ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, maxLines = 1)
            }
            CompareRow(plans) { plan ->
                Text(texts.files.euro(if (yearly) plan.yearlyCents else plan.monthlyCents), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(if (yearly) texts.files.aboYearShort else texts.files.aboMonthShort, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            compare.rows.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    val base = free?.let(row::value)
                    CompareRow(plans) { plan ->
                        val v = row.value(plan)
                        Text(
                            v,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (plan != free && v != base) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** One line of the table: a cell per plan, as wide as the column behind it. */
@Composable
private fun CompareRow(plans: List<AboPlan>, cell: @Composable (AboPlan) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        plans.forEach { plan ->
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) { cell(plan) }
        }
    }
}

@Composable
private fun PlanCard(preview: AboPreview, plan: AboPlan, yearly: Boolean, showFeatures: Boolean = true) {
    val uriHandler = LocalUriHandler.current
    val url = preview.interestUrl(plan, yearly, german = german())
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if (plan.recommended) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.name(german()), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (plan.recommended) Text(texts.files.aboRecommended, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                if (yearly) texts.files.aboPerYear(texts.files.euro(plan.yearlyCents)) else texts.files.aboPerMonth(texts.files.euro(plan.monthlyCents)),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                if (yearly) texts.files.aboYearlyPerMonth(texts.files.euro(plan.yearlyPerMonth()), texts.files.euro(plan.monthlyCents)) else texts.files.aboOrYearly(texts.files.euro(plan.yearlyCents)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Whoever commits for a year gets something out of it: the saving is shown alongside.
            plan.yearlySaving().takeIf { it > 0 }?.let { n ->
                Text(texts.files.aboSaving(n), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (showFeatures) PlanFeatures(plan)
            if (url != null) {
                AppButton(
                    texts.files.aboInterest(preview.trialMonths),
                    { runCatching { uriHandler.openUri(url) } },
                    Modifier.fillMaxWidth(),
                    kind = if (plan.recommended) ButtonKind.Main else ButtonKind.Secondary,
                    icon = AppIcons.Link,
                )
            }
        }
    }
}

@Composable
private fun FreePlanCard(plan: AboPlan) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.name(german()), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(texts.files.aboFreeNow, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PlanFeatures(plan)
        }
    }
}

@Composable
private fun PlanFeatures(plan: AboPlan) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        plan.features(german()).forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

internal fun german(): Boolean = currentLanguage() == Language.GERMAN
