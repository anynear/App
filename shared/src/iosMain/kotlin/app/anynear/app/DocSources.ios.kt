package app.anynear.app

import app.anynear.ui.openDocSource

internal actual fun platformDocSources(): DocSourceOpener = DocSourceOpener(::openDocSource)
