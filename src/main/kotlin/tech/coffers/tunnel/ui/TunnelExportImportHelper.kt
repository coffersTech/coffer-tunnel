package tech.coffers.tunnel.ui

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import tech.coffers.tunnel.config.CofferTunnelSettingsService
import tech.coffers.tunnel.model.SshProfile
import tech.coffers.tunnel.model.TunnelConfig
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 备份数据结构封装
 */
data class CofferBackupData(
    val version: String = "2.0",
    val useProfileMode: Boolean = true,
    val profiles: List<SshProfile> = emptyList(),
    val tunnels: List<TunnelConfig> = emptyList()
)

/**
 * 隧道与跳板档案导入导出助手
 */
object TunnelExportImportHelper {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 导出配置（同时包含跳板档案与隧道列表）
     */
    fun exportData(targetFile: File): Result<Pair<Int, Int>> {
        return runCatching {
            val service = CofferTunnelSettingsService.getInstance()
            val backupData = CofferBackupData(
                version = "2.0",
                useProfileMode = service.isUseProfileMode,
                profiles = service.getProfiles(),
                tunnels = service.getTunnels()
            )
            val json = gson.toJson(backupData)
            targetFile.writeText(json, StandardCharsets.UTF_8)
            Pair(backupData.profiles.size, backupData.tunnels.size)
        }
    }

    /**
     * 导入配置（智能兼容 v2.0 完整备份与 v1.0 旧版纯隧道列表备份）
     */
    fun importData(sourceFile: File, overwrite: Boolean = false): Result<Pair<Int, Int>> {
        return runCatching {
            val json = sourceFile.readText(StandardCharsets.UTF_8)
            val jsonElement = JsonParser.parseString(json)

            val service = CofferTunnelSettingsService.getInstance()

            if (jsonElement.isJsonObject) {
                // v2.0 格式 (包含 profiles 与 tunnels)
                val backupData: CofferBackupData = gson.fromJson(json, CofferBackupData::class.java)
                val validProfiles = backupData.profiles.filter { it.name.isNotBlank() && it.jumpHost.isNotBlank() }
                val validTunnels = backupData.tunnels.filter { it.name.isNotBlank() }

                if (overwrite) {
                    service.replaceAll(validProfiles, validTunnels)
                } else {
                    service.mergeData(validProfiles, validTunnels)
                }
                Pair(validProfiles.size, validTunnels.size)
            } else if (jsonElement.isJsonArray) {
                // v1.0 旧版纯隧道列表
                val type = object : TypeToken<List<TunnelConfig>>() {}.type
                val legacyTunnels: List<TunnelConfig> = gson.fromJson(json, type) ?: emptyList()
                val validTunnels = legacyTunnels.filter { it.name.isNotBlank() }

                if (overwrite) {
                    service.replaceAll(emptyList(), validTunnels)
                } else {
                    service.mergeData(emptyList(), validTunnels)
                }
                Pair(0, validTunnels.size)
            } else {
                throw IllegalArgumentException("非法的备份文件格式")
            }
        }
    }
}
