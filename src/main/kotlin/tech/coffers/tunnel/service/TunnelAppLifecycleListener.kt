package tech.coffers.tunnel.service

import com.intellij.ide.AppLifecycleListener

/**
 * 监听 IDE 退出事件，确保自动回收所有运行中的 SSH 进程
 */
class TunnelAppLifecycleListener : AppLifecycleListener {

    override fun appWillBeClosed(isRestart: Boolean) {
        try {
            TunnelProcessService.getInstance().stopAll()
        } catch (_: Exception) {
            // 忽略销毁时的静默异常，保障 IDE 正常退出
        }
    }
}
