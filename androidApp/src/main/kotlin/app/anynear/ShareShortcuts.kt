package app.anynear

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.anynear.app.AppController
import app.anynear.mesh.Conversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Direct share (Android „Sharing Shortcuts“): the latest conversations are at the very top of the system's share menu,
 * as in common messengers. A tap stores the shared item as a draft in this conversation and opens it, no „An wen?“.
 * The same shortcuts appear on a long press on the app icon and open the conversation.
 * The category is also in `res/xml/shortcuts.xml` (`share-target`).
 */
internal object ShareShortcuts {
    const val CATEGORY = "app.anynear.category.SHARE_TARGET"

    /** As many conversations as the share menu sensibly shows. */
    private const val COUNT = 5

    @OptIn(FlowPreview::class)
    fun start(context: Context, controller: AppController) {
        val app = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // New as soon as the conversations change or an image is added on the phone.
        combine(controller.chatList, controller.avatarVersion) { list, _ ->
            list.filter { !it.removed }.take(COUNT).map {
                Target(it.key, it.title, it.conversation, controller.conversationImageId(it.conversation))
            }
        }
            .distinctUntilChanged()
            // Android limits how often an app changes its shortcuts in the background.
            .debounce(2_000)
            .onEach { targets ->
                runCatching {
                    val images = targets.associate { t -> t.key to t.imageId?.let { controller.conversationImageBytes(t.conversation) } }
                    publish(app, targets, images)
                }.onFailure { controller.debugLog.add("Teilen", "Kurzbefehle: ${it.message}") }
            }
            .launchIn(scope)
    }

    private data class Target(val key: String, val title: String, val conversation: Conversation, val imageId: String?) {
        val person: Boolean get() = conversation is Conversation.Direct
    }

    /** [images]: uploaded image per conversation; without an image the initial letter. */
    private fun publish(context: Context, targets: List<Target>, images: Map<String, ByteArray?>) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(1)
        val shortcuts = targets.take(max).mapIndexed { rank, t ->
            val label = t.title.ifBlank { "…" }
            val icon = images[t.key]?.let(::adaptivePhoto)?.let(IconCompat::createWithAdaptiveBitmap)
                ?: IconCompat.createWithAdaptiveBitmap(initialsIcon(context, label))
            ShortcutInfoCompat.Builder(context, t.key)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(icon)
                .setIntent(
                    Intent(context, MainActivity::class.java)
                        .setAction(MessageNotifier.ACTION_OPEN_CONVERSATION)
                        .putExtra(MessageNotifier.EXTRA_CONVERSATION, t.key),
                )
                .setCategories(setOf(CATEGORY))
                .setLongLived(true)
                .setRank(rank)
                .apply { if (t.person) setPerson(Person.Builder().setName(label).setKey(t.key).setIcon(icon).build()) }
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    /**
     * Photo as adaptive icon: the system only shows the inner 72 of 108 dp, so the photo fills the whole
     * area so that it does not appear as a small cutout with a border.
     */
    private fun adaptivePhoto(bytes: ByteArray): Bitmap? = AvatarBitmaps.square(bytes, ADAPTIVE_SIZE)

    private const val ADAPTIVE_SIZE = 288

    /** Initial letter on night blue, as adaptive icon (108 dp, content in the inner 72 dp). */
    private fun initialsIcon(context: Context, title: String): Bitmap {
        val size = (108 * context.resources.displayMetrics.density).toInt().coerceAtLeast(108)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ContextCompat.getColor(context, R.color.brand_nachtblau))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.brand_alpinweiss)
            textSize = size * 0.36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val initial = title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(initial, size / 2f, y, paint)
        return bitmap
    }
}
