package app.anynear.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberHtmlPrint(): (jobName: String, html: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { jobName, html ->
            val activity = context.activity()
            if (activity != null) {
                val view = WebView(activity)
                // Kept until the next print job: without a reference the WebView may be collected before printing.
                printing = view
                view.settings.javaScriptEnabled = false
                view.settings.allowFileAccess = false
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView, url: String?) {
                        val manager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
                        val attributes = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
                        runCatching { manager.print(jobName, v.createPrintDocumentAdapter(jobName), attributes) }
                    }
                }
                view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }
    }
}

private var printing: WebView? = null

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
