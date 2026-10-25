package tech.coffers.tunnel.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import tech.coffers.tunnel.model.TunnelConfig
import tech.coffers.tunnel.model.TunnelStatus
import tech.coffers.tunnel.service.TunnelProcessService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.GridLayout
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * 遵循 IntelliJ 原生风格的卡片式隧道列表项渲染器
 * 结构：
 * [ 状态图标 ]  [ 隧道名称 (主文本加粗)                        ]
 *             [ 127.0.0.1:localPort -> jumpHost -> remoteHost:port (次要浅灰文本) ]
 */
class TunnelListCellRenderer : ListCellRenderer<TunnelConfig> {

    private val rootPanel = JPanel(BorderLayout(JBUI.scale(10), 0))
    private val iconLabel = JBLabel()
    private val textPanel = JPanel(GridLayout(2, 1, 0, JBUI.scale(3)))
    private val nameLabel = JBLabel()
    private val detailLabel = JBLabel()

    init {
        rootPanel.border = JBUI.Borders.empty(6, 12)
        rootPanel.isOpaque = true
        textPanel.isOpaque = false

        nameLabel.font = nameLabel.font.deriveFont(Font.BOLD, nameLabel.font.size2D + 1f)
        detailLabel.font = detailLabel.font.deriveFont(Font.PLAIN, detailLabel.font.size2D - 1f)

        textPanel.add(nameLabel)
        textPanel.add(detailLabel)

        rootPanel.add(iconLabel, BorderLayout.WEST)
        rootPanel.add(textPanel, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out TunnelConfig>,
        value: TunnelConfig?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        if (value == null) {
            nameLabel.text = ""
            detailLabel.text = ""
            iconLabel.icon = null
            return rootPanel
        }

        val status = TunnelProcessService.getInstance().getStatus(value.id)

        // 仅使用 JetBrains 内置 AllIcons 图标
        iconLabel.icon = when (status) {
            TunnelStatus.RUNNING -> AllIcons.RunConfigurations.TestPassed
            TunnelStatus.STARTING -> AllIcons.General.Warning
            TunnelStatus.ERROR -> AllIcons.General.Error
            TunnelStatus.STOPPED -> AllIcons.RunConfigurations.TestIgnored
        }

        val profile = if (value.profileId.isNotBlank()) {
            tech.coffers.tunnel.config.CofferTunnelSettingsService.getInstance().findProfileById(value.profileId)
        } else null

        val jumpDesc = if (profile != null) {
            "${profile.name} (${profile.username}@${profile.jumpHost})"
        } else if (value.jumpHost.isNotBlank()) {
            "${value.username}@${value.jumpHost}:${value.sshPort}"
        } else {
            tech.coffers.tunnel.CofferTunnelBundle.message("label.unassociated.profile")
        }

        nameLabel.text = value.name
        detailLabel.text = "127.0.0.1:${value.localPort} ➔ $jumpDesc ➔ ${value.remoteHost}:${value.remotePort}"

        // 主题自适应色彩机制，严禁硬编码 RGB
        if (isSelected) {
            rootPanel.background = UIUtil.getListSelectionBackground(true)
            nameLabel.foreground = UIUtil.getListSelectionForeground(true)
            detailLabel.foreground = UIUtil.getListSelectionForeground(true)
        } else {
            rootPanel.background = UIUtil.getListBackground()
            nameLabel.foreground = UIUtil.getListForeground()
            detailLabel.foreground = UIUtil.getContextHelpForeground()
        }

        return rootPanel
    }
}
