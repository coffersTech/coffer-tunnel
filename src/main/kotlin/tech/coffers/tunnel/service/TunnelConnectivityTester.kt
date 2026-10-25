package tech.coffers.tunnel.service

import java.net.InetSocketAddress
import java.net.Socket

/**
 * 跳板机网络连通性测试服务
 */
object TunnelConnectivityTester {

    data class TestResult(val success: Boolean, val message: String, val elapsedMs: Long)

    /**
     * 测试目标跳板机的主机与 SSH 端口是否可达
     * @param host 主机域名或 IP
     * @param port SSH 端口 (默认 22)
     * @param timeoutMs 超时时间毫秒 (默认 3000ms)
     */
    fun testConnection(host: String, port: Int, timeoutMs: Int = 3000): TestResult {
        if (host.isBlank()) {
            return TestResult(false, "跳板机主机 (Host) 不能为空", 0)
        }
        if (port !in 1..65535) {
            return TestResult(false, "SSH 端口必须在 1-65535 范围内", 0)
        }

        val startTime = System.currentTimeMillis()
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(host.trim(), port), timeoutMs)
            val elapsed = System.currentTimeMillis() - startTime
            TestResult(true, "连接成功！目标跳板机端口响应正常 (耗时: ${elapsed}ms)", elapsed)
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            TestResult(false, "连接失败: ${e.message ?: e.javaClass.simpleName} (耗时: ${elapsed}ms)", elapsed)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }
}
