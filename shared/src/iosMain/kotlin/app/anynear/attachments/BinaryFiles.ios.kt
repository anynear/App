package app.anynear.attachments

import app.anynear.data.platformDataDir
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = if (isEmpty()) null else pinned.addressOf(0), length = size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

@OptIn(ExperimentalForeignApi::class)
internal class IosBinaryFiles(private val dir: String) : BinaryFiles {
    private val fm = NSFileManager.defaultManager

    override fun read(name: String): ByteArray? = NSData.dataWithContentsOfFile("$dir/$name")?.toByteArray()

    override fun write(name: String, content: ByteArray) {
        fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        check(content.toNSData().writeToFile("$dir/$name", atomically = true)) { "Anhang konnte nicht gespeichert werden." }
    }

    override fun delete(name: String) {
        fm.removeItemAtPath("$dir/$name", error = null)
    }

    override fun names(): List<String> =
        fm.contentsOfDirectoryAtPath(dir, error = null)?.filterIsInstance<String>()?.filter { !it.startsWith(".") }.orEmpty()
}

actual fun platformBinaryFiles(dirName: String): BinaryFiles = IosBinaryFiles("${platformDataDir()}/$dirName")
