package tech.coffers.tunnel.model

import tech.coffers.tunnel.CofferTunnelBundle

/**
 * 隧道运行状态枚举（支持中英文多语言切换）
 */
enum class TunnelStatus(private val messageKey: String) {
    STOPPED("status.stopped"),
    STARTING("status.starting"),
    RUNNING("status.running"),
    ERROR("status.error");

    val displayText: String
        get() = CofferTunnelBundle.message(messageKey)

    val isAlive: Boolean
        get() = this == RUNNING || this == STARTING
}
