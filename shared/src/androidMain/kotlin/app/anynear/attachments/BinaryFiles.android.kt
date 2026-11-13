package app.anynear.attachments

import app.anynear.AndroidPlatform
import java.io.File
import java.io.FileOutputStream

private class AndroidBinaryFiles(private val dir: File) : BinaryFiles {
    override fun read(name: String): ByteArray? = File(dir, name).takeIf { it.exists() }?.readBytes()

    override fun write(name: String, content: ByteArray) {
        dir.mkdirs()
        val tmp = File(dir, "$name.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(content)
            out.fd.sync()
        }
        check(tmp.renameTo(File(dir, name))) { "Anhang konnte nicht gespeichert werden." }
    }

    override fun delete(name: String) {
        File(dir, name).delete()
    }

    override fun names(): List<String> = dir.list()?.filter { !it.endsWith(".tmp") }.orEmpty()
}

actual fun platformBinaryFiles(dirName: String): BinaryFiles =
    AndroidBinaryFiles(File(AndroidPlatform.requireContext().filesDir, dirName))
