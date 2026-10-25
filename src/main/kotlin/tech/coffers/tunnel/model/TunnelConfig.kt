package tech.coffers.tunnel.model

import tech.coffers.tunnel.CofferTunnelBundle
import java.util.UUID

/**
 * 隧道实体配置模型
 * 支持关联 SshProfile (跳板档案模式)，同时向后兼容自包含模式
 */
data class TunnelConfig(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var targetType: String = "type.custom",
    var profileId: String = "", // 绑定的跳板档案 ID

    // 独立跳板字段（向后兼容及关闭档案复用模式时使用）
    var jumpHost: String = "",
    var sshPort: Int = 22,
    var username: String = "",
    var privateKeyPath: String = "",
    var extraArgs: String = "",

    // 目标与监听端口
    var localPort: Int = 3306,
    var remoteHost: String = "127.0.0.1",
    var remotePort: Int = 3306
) {
    /**
     * 校验配置完整度与合法性
     * @param hasProfile 是否已成功关联跳板档案
     */
    fun validate(hasProfile: Boolean = false): String? {
        if (name.isBlank()) return CofferTunnelBundle.message("validation.tunnel.name.required")
        if (localPort !in 1..65535) return CofferTunnelBundle.message("validation.local.port.invalid")
        if (remoteHost.isBlank()) return CofferTunnelBundle.message("validation.remote.host.required")
        if (remotePort !in 1..65535) return CofferTunnelBundle.message("validation.remote.port.invalid")

        if (!hasProfile && profileId.isBlank()) {
            if (jumpHost.isBlank()) return CofferTunnelBundle.message("validation.jump.host.required")
            if (sshPort !in 1..65535) return CofferTunnelBundle.message("validation.ssh.port.invalid")
            if (username.isBlank()) return CofferTunnelBundle.message("validation.username.required")
        }
        return null
    }

    /**
     * 获取深拷贝副本
     */
    fun copyConfig(): TunnelConfig {
        return this.copy()
    }

    /**
     * 复制为新的独立隧道副本（重新生成 ID 并修改名称）
     */
    fun duplicate(): TunnelConfig {
        val copySuffix = CofferTunnelBundle.message("label.copy.suffix")
        return this.copy(
            id = UUID.randomUUID().toString(),
            name = "$name $copySuffix"
        )
    }
}
