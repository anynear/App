package app.anynear.mesh

/** iOS: no USB access to radios. */
internal actual fun platformUsbRescue(log: (String) -> Unit): UsbRescue? = null
