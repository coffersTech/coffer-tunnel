package tech.coffers.tunnel.model

import tech.coffers.tunnel.CofferTunnelBundle
import java.util.UUID

/**
 * SSH 跳板机档案实体
 * 封装跳板机的主机、端口、认证凭据等核心连接信息，供多条隧道复用
 */
data class SshProfile(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var jumpHost: String = "",
    var sshPort: Int = 22,
    var username: String = "",
    var privateKeyPath: String = "",
    var extraArgs: String = ""
) {
    /**
     * 校验跳板档案合法性
     */
    fun validate(): String? {
        if (name.isBlank()) return CofferTunnelBundle.message("validation.profile.name.required")
        if (jumpHost.isBlank()) return CofferTunnelBundle.message("validation.jump.host.required")
        if (sshPort !in 1..65535) return CofferTunnelBundle.message("validation.ssh.port.invalid")
        if (username.isBlank()) return CofferTunnelBundle.message("validation.username.required")
        return null
    }

    fun copyProfile(): SshProfile {
        return this.copy()
    }

    override fun toString(): String {
        return if (name.isNotBlank()) "$name ($username@$jumpHost:$sshPort)" else "$username@$jumpHost:$sshPort"
    }
}
