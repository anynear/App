package app.anynear.app

import app.anynear.AndroidPlatform
import app.anynear.ui.openDocSource

internal actual fun platformDocSources(): DocSourceOpener = DocSourceOpener { ref -> openDocSource(AndroidPlatform.requireContext(), ref) }
