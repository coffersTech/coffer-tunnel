package tech.coffers.tunnel.config

import tech.coffers.tunnel.model.SshProfile
import tech.coffers.tunnel.model.TunnelConfig

/**
 * 持久化状态载体
 */
class CofferTunnelSettingsState {
    var language: String = "AUTO" // "AUTO" (跟随系统), "ZH" (中文), "EN" (English)
    var useProfileMode: Boolean = true
    var profiles: MutableList<SshProfile> = mutableListOf()
    var tunnels: MutableList<TunnelConfig> = mutableListOf()
}
