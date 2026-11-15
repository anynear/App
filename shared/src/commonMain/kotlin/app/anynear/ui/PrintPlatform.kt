package app.anynear.ui

import androidx.compose.runtime.Composable

/**
 * Opens the print dialog of the system for one page of HTML (images as `data:` URLs). Nothing is stored or shared;
 * where the page goes is chosen in the system dialog. Used for the emergency card (FUT 14.11.2026).
 */
@Composable
internal expect fun rememberHtmlPrint(): (jobName: String, html: String) -> Unit
