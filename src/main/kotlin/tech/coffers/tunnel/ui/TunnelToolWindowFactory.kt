package tech.coffers.tunnel.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * CofferTunnel 侧边栏工具窗口工厂
 * 实现 DumbAware，确保在项目索引构建中也能正常打开和使用隧道
 */
class TunnelToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TunnelToolWindowPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "", false)

        toolWindow.contentManager.addContent(content)

        // 绑定生命周期，释放资源
        Disposer.register(toolWindow.disposable, panel)
    }
}
