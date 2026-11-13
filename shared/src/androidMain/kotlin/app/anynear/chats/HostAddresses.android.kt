package app.anynear.chats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

internal actual suspend fun resolveHost(host: String): List<ByteArray> = withContext(Dispatchers.IO) {
    runCatching { InetAddress.getAllByName(host).map { it.address } }.getOrDefault(emptyList())
}
