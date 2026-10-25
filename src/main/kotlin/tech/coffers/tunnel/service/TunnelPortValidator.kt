package tech.coffers.tunnel.service

import java.net.InetAddress
import java.net.ServerSocket

/**
 * 本地端口可用性校验器
 */
object TunnelPortValidator {

    /**
     * 检测本地端口是否处于可用空闲状态
     * @param port 待检测的本地端口 (1..65535)
     * @return true 表示端口空闲可用；false 表示已被占用
     */
    fun isPortAvailable(port: Int): Boolean {
        if (port !in 1..65535) return false

        // 尝试绑定 127.0.0.1
        var loopbackSocket: ServerSocket? = null
        var anyLocalSocket: ServerSocket? = null
        return try {
            loopbackSocket = ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))
            loopbackSocket.reuseAddress = true

            anyLocalSocket = ServerSocket(port, 1, InetAddress.getByName("0.0.0.0"))
            anyLocalSocket.reuseAddress = true

            true
        } catch (_: Exception) {
            false
        } finally {
            try {
                loopbackSocket?.close()
            } catch (_: Exception) {}
            try {
                anyLocalSocket?.close()
            } catch (_: Exception) {}
        }
    }
}
