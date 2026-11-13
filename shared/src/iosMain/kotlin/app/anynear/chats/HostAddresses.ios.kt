package app.anynear.chats

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.AF_UNSPEC
import platform.posix.SOCK_STREAM
import platform.posix.addrinfo
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.sockaddr_in
import platform.posix.sockaddr_in6

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun resolveHost(host: String): List<ByteArray> = withContext(Dispatchers.IO) {
    memScoped {
        val hints = alloc<addrinfo>().apply {
            ai_family = AF_UNSPEC
            ai_socktype = SOCK_STREAM
        }
        val result = allocPointerTo<addrinfo>()
        if (getaddrinfo(host, null, hints.ptr, result.ptr) != 0) return@memScoped emptyList()
        val out = mutableListOf<ByteArray>()
        var node = result.value
        while (node != null) {
            val info = node.pointed
            when (info.ai_family) {
                AF_INET -> info.ai_addr?.reinterpret<sockaddr_in>()?.pointed?.sin_addr?.ptr?.readBytes(4)?.let(out::add)
                AF_INET6 -> info.ai_addr?.reinterpret<sockaddr_in6>()?.pointed?.sin6_addr?.ptr?.readBytes(16)?.let(out::add)
            }
            node = info.ai_next
        }
        freeaddrinfo(result.value)
        out
    }
}
