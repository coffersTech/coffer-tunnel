package tech.coffers.tunnel

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import tech.coffers.tunnel.config.CofferTunnelSettingsService
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.MessageFormat
import java.util.Locale
import java.util.Properties

private const val BUNDLE = "messages.CofferTunnelBundle"

/**
 * CofferTunnel 国际化 (i18n) 资源管理类
 * 彻底规避 Java ResourceBundle 在中文宿主下强行 Fallback 到 zh_CN 的问题
 */
object CofferTunnelBundle : DynamicBundle(BUNDLE) {

    private val enProperties by lazy {
        loadProperties("messages/CofferTunnelBundle_en.properties")
    }

    private val zhProperties by lazy {
        loadProperties("messages/CofferTunnelBundle_zh_CN.properties")
    }

    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        val targetLang = getCurrentLanguageMode()

        val pattern = when (targetLang) {
            "ZH" -> zhProperties.getProperty(key) ?: getMessage(key, *params)
            "EN" -> enProperties.getProperty(key) ?: getMessage(key, *params)
            else -> {
                // AUTO: 检查当前 IDE/系统语言是否为中文
                val isZh = Locale.getDefault().language.equals("zh", ignoreCase = true)
                if (isZh) {
                    zhProperties.getProperty(key) ?: getMessage(key, *params)
                } else {
                    enProperties.getProperty(key) ?: getMessage(key, *params)
                }
            }
        }

        return if (params.isNotEmpty()) {
            try {
                MessageFormat.format(pattern, *params)
            } catch (_: Exception) {
                pattern
            }
        } else {
            pattern
        }
    }

    private fun getCurrentLanguageMode(): String {
        return try {
            CofferTunnelSettingsService.getInstance().language.uppercase()
        } catch (_: Exception) {
            "AUTO"
        }
    }

    private fun loadProperties(resourcePath: String): Properties {
        val props = Properties()
        try {
            val stream = CofferTunnelBundle::class.java.classLoader.getResourceAsStream(resourcePath)
                ?: CofferTunnelBundle::class.java.classLoader.getResourceAsStream("messages/CofferTunnelBundle.properties")
            if (stream != null) {
                InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                    props.load(reader)
                }
            }
        } catch (_: Exception) {}
        return props
    }
}
