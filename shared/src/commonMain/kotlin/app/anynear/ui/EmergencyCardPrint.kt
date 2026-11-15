package app.anynear.ui

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The printed emergency card: one A4 page with title, the code large in monospace, the QR code, the date and what the
 * card is for. Everything inline (QR as `data:` URL), no scripts, nothing loaded from outside.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun emergencyCardHtml(code: String, qrPng: ByteArray?, title: String, text: String, date: String): String {
    val qr = qrPng?.let { """<img class="qr" alt="" src="data:image/png;base64,${Base64.encode(it)}">""" }.orEmpty()
    return """<!doctype html>
<html><head><meta charset="utf-8"><title>${escapeHtml(title)}</title>
<style>
@page { size: A4; margin: 20mm; }
body { font-family: -apple-system, Roboto, "Helvetica Neue", Arial, sans-serif; color: #000; background: #fff; margin: 0; }
.card { border: 2px solid #000; border-radius: 6mm; padding: 10mm; max-width: 150mm; margin: 0 auto; text-align: center; }
h1 { font-size: 20pt; margin: 0 0 6mm; }
.code { font-family: "Courier New", Menlo, monospace; font-size: 17pt; font-weight: bold; white-space: nowrap; margin: 0 0 6mm; }
.qr { width: 55mm; height: 55mm; image-rendering: pixelated; margin: 0 0 6mm; }
p { font-size: 11pt; line-height: 1.4; margin: 0 0 3mm; text-align: left; }
.date { color: #444; font-size: 10pt; text-align: center; }
</style></head>
<body><div class="card">
<h1>${escapeHtml(title)}</h1>
<div class="code">${escapeHtml(code)}</div>
$qr
<p>${escapeHtml(text)}</p>
<p class="date">${escapeHtml(date)}</p>
</div></body></html>"""
}

private fun escapeHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
