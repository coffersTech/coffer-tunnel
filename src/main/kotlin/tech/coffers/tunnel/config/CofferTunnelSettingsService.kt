package tech.coffers.tunnel.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import tech.coffers.tunnel.model.SshProfile
import tech.coffers.tunnel.model.TunnelConfig
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 隧道与跳板档案配置持久化服务（全局应用级）
 */
@State(
    name = "tech.coffers.tunnel.config.CofferTunnelSettingsService",
    storages = [Storage("coffer_tunnel.xml")]
)
class CofferTunnelSettingsService : PersistentStateComponent<CofferTunnelSettingsState> {

    private var myState = CofferTunnelSettingsState()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    override fun getState(): CofferTunnelSettingsState {
        return myState
    }

    override fun loadState(state: CofferTunnelSettingsState) {
        XmlSerializerUtil.copyBean(state, myState)
        notifyChanged()
    }

    // --- 全局开关 ---

    var language: String
        get() = myState.language
        set(value) {
            if (myState.language != value) {
                myState.language = value
                notifyChanged()
            }
        }

    var isUseProfileMode: Boolean
        get() = myState.useProfileMode
        set(value) {
            if (myState.useProfileMode != value) {
                myState.useProfileMode = value
                notifyChanged()
            }
        }

    // --- 跳板档案 (SshProfile) 管理 ---

    fun getProfiles(): List<SshProfile> {
        return myState.profiles.toList()
    }

    fun findProfileById(id: String): SshProfile? {
        return myState.profiles.find { it.id == id }
    }

    fun saveProfile(profile: SshProfile) {
        val existingIndex = myState.profiles.indexOfFirst { it.id == profile.id }
        if (existingIndex >= 0) {
            myState.profiles[existingIndex] = profile.copyProfile()
        } else {
            myState.profiles.add(profile.copyProfile())
        }
        notifyChanged()
    }

    /**
     * 查找引用该跳板档案的全部隧道
     */
    fun getReferencingTunnels(profileId: String): List<TunnelConfig> {
        return myState.tunnels.filter { it.profileId == profileId }
    }

    /**
     * 删除跳板档案（严格进行引用约束校验）
     */
    fun deleteProfile(profileId: String): Result<Unit> {
        val references = getReferencingTunnels(profileId)
        if (references.isNotEmpty()) {
            val names = references.take(3).joinToString("、") { "【${it.name}】" } +
                    if (references.size > 3) " 等 ${references.size} 条隧道" else ""
            return Result.failure(IllegalStateException("该跳板档案正被 $names 引用，请先修改或删除关联隧道后再删除！"))
        }

        val removed = myState.profiles.removeIf { it.id == profileId }
        if (removed) {
            notifyChanged()
        }
        return Result.success(Unit)
    }

    // --- 隧道 (TunnelConfig) 管理 ---

    fun getTunnels(): List<TunnelConfig> {
        return myState.tunnels.toList()
    }

    fun findTunnelById(id: String): TunnelConfig? {
        return myState.tunnels.find { it.id == id }
    }

    fun saveTunnel(tunnel: TunnelConfig) {
        val existingIndex = myState.tunnels.indexOfFirst { it.id == tunnel.id }
        if (existingIndex >= 0) {
            myState.tunnels[existingIndex] = tunnel.copyConfig()
        } else {
            myState.tunnels.add(tunnel.copyConfig())
        }
        notifyChanged()
    }

    fun removeTunnel(id: String) {
        val removed = myState.tunnels.removeIf { it.id == id }
        if (removed) {
            notifyChanged()
        }
    }

    /**
     * 全量更新数据（用于导入覆盖）
     */
    fun replaceAll(profiles: List<SshProfile>, tunnels: List<TunnelConfig>) {
        myState.profiles.clear()
        myState.profiles.addAll(profiles.map { it.copyProfile() })
        myState.tunnels.clear()
        myState.tunnels.addAll(tunnels.map { it.copyConfig() })
        notifyChanged()
    }

    /**
     * 合并追加数据（用于增量导入）
     */
    fun mergeData(incomingProfiles: List<SshProfile>, incomingTunnels: List<TunnelConfig>) {
        incomingProfiles.forEach { p ->
            val index = myState.profiles.indexOfFirst { it.id == p.id }
            if (index >= 0) {
                myState.profiles[index] = p.copyProfile()
            } else {
                myState.profiles.add(p.copyProfile())
            }
        }
        incomingTunnels.forEach { t ->
            val index = myState.tunnels.indexOfFirst { it.id == t.id }
            if (index >= 0) {
                myState.tunnels[index] = t.copyConfig()
            } else {
                myState.tunnels.add(t.copyConfig())
            }
        }
        notifyChanged()
    }

    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.forEach { it.invoke() }
    }

    companion object {
        fun getInstance(): CofferTunnelSettingsService {
            return ApplicationManager.getApplication().getService(CofferTunnelSettingsService::class.java)
        }
    }
}
