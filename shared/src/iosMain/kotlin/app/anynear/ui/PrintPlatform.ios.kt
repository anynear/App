package app.anynear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOutputType
import platform.UIKit.UIPrintInteractionController
import platform.UIKit.viewPrintFormatter
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

@Composable
internal actual fun rememberHtmlPrint(): (jobName: String, html: String) -> Unit = remember {
    { jobName, html -> HtmlPrintJob(jobName).start(html) }
}

/** Loads the page in an off-screen web view (images as data URLs need it), then hands its print formatter to UIKit. */
@OptIn(ExperimentalForeignApi::class)
private class HtmlPrintJob(private val jobName: String) : NSObject(), WKNavigationDelegateProtocol {
    private val view = WKWebView(CGRectMake(0.0, 0.0, 595.0, 842.0), WKWebViewConfiguration())

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        val info = UIPrintInfo.printInfo().apply {
            setJobName(jobName)
            setOutputType(UIPrintInfoOutputType.UIPrintInfoOutputGeneral)
        }
        val controller = UIPrintInteractionController.sharedPrintController
        controller.setPrintInfo(info)
        controller.setPrintFormatter(webView.viewPrintFormatter())
        controller.presentAnimated(true) { _, _, _ -> runningPrintJobs.remove(this) }
    }

    fun start(html: String) {
        runningPrintJobs += this
        view.navigationDelegate = this
        view.loadHTMLString(html, baseURL = null)
    }
}

// WKWebView holds its navigation delegate only weakly.
private val runningPrintJobs = mutableSetOf<HtmlPrintJob>()
