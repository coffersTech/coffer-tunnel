package tech.coffers.tunnel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import tech.coffers.tunnel.CofferTunnelBundle
import tech.coffers.tunnel.config.CofferTunnelSettingsService
import tech.coffers.tunnel.model.SshProfile
import tech.coffers.tunnel.service.TunnelConnectivityTester
import tech.coffers.tunnel.service.TunnelProcessService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * 跳板机档案集中管理弹窗（多语言支持）
 */
class SshProfileManagerDialog : DialogWrapper(true) {

    private val settingsService = CofferTunnelSettingsService.getInstance()
    private val listModel = DefaultListModel<SshProfile>()
    private val profileList = JBList(listModel)

    init {
        title = CofferTunnelBundle.message("dialog.profile.manager.title")
        setupList()
        refreshList()
        init()
    }

    private fun setupList() {
        profileList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        profileList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    editSelected()
                }
            }
        })
    }

    override fun createCenterPanel(): JComponent {
        val rootPanel = JPanel(BorderLayout(0, JBUI.scale(8)))

        val actionGroup = DefaultActionGroup().apply {
            add(object : AnAction(
                CofferTunnelBundle.message("action.new.profile"),
                CofferTunnelBundle.message("action.new.profile"),
                AllIcons.General.Add
            ) {
                override fun actionPerformed(e: AnActionEvent) = createProfile()
            })
            add(object : AnAction(
                CofferTunnelBundle.message("action.edit.tunnel"),
                CofferTunnelBundle.message("action.edit.tunnel.desc"),
                AllIcons.Actions.Edit
            ) {
                override fun actionPerformed(e: AnActionEvent) = editSelected()
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = profileList.selectedValue != null
                }
            })
            add(object : AnAction(
                CofferTunnelBundle.message("action.delete.tunnel"),
                CofferTunnelBundle.message("action.delete.tunnel.desc"),
                AllIcons.General.Remove
            ) {
                override fun actionPerformed(e: AnActionEvent) = deleteSelected()
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = profileList.selectedValue != null
                }
            })
            addSeparator()
            add(object : AnAction(
                CofferTunnelBundle.message("action.test.connection"),
                CofferTunnelBundle.message("action.test.connection"),
                AllIcons.Actions.Lightning
            ) {
                override fun actionPerformed(e: AnActionEvent) = testSelectedConnectivity()
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = profileList.selectedValue != null
                }
            })
        }

        val toolbar: ActionToolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, true)
        toolbar.targetComponent = rootPanel
        rootPanel.add(toolbar.component, BorderLayout.NORTH)

        rootPanel.add(JBScrollPane(profileList), BorderLayout.CENTER)
        rootPanel.preferredSize = Dimension(480, 320)
        return rootPanel
    }

    private fun refreshList() {
        val currentSelectedId = profileList.selectedValue?.id
        listModel.clear()
        val profiles = settingsService.getProfiles()
        profiles.forEach { listModel.addElement(it) }

        if (profiles.isNotEmpty()) {
            val targetIndex = profiles.indexOfFirst { it.id == currentSelectedId }
            profileList.selectedIndex = if (targetIndex >= 0) targetIndex else 0
        }
    }

    private fun createProfile() {
        val dialog = SshProfileDialog()
        if (dialog.showAndGet()) {
            val newProfile = dialog.buildProfile()
            settingsService.saveProfile(newProfile)
            refreshList()
            profileList.setSelectedValue(newProfile, true)
        }
    }

    private fun editSelected() {
        val selected = profileList.selectedValue ?: return
        val dialog = SshProfileDialog(selected)
        if (dialog.showAndGet()) {
            val updated = dialog.buildProfile()
            settingsService.saveProfile(updated)

            val runningReferences = settingsService.getReferencingTunnels(updated.id)
                .filter { TunnelProcessService.getInstance().getStatus(it.id).isAlive }

            if (runningReferences.isNotEmpty()) {
                val names = runningReferences.joinToString("、") { "【${it.name}】" }
                Messages.showWarningDialog(
                    contentPanel,
                    CofferTunnelBundle.message("dialog.profile.running.warning", names),
                    CofferTunnelBundle.message("dialog.profile.running.warning.title")
                )
            }

            refreshList()
        }
    }

    private fun deleteSelected() {
        val selected = profileList.selectedValue ?: return

        val references = settingsService.getReferencingTunnels(selected.id)
        if (references.isNotEmpty()) {
            val names = references.take(3).joinToString("、") { "【${it.name}】" } +
                    if (references.size > 3) " 等 ${references.size} 条" else ""
            Messages.showErrorDialog(
                contentPanel,
                CofferTunnelBundle.message("dialog.profile.delete.restricted", names),
                CofferTunnelBundle.message("dialog.profile.delete.restricted.title")
            )
            return
        }

        val confirm = Messages.showYesNoDialog(
            contentPanel,
            CofferTunnelBundle.message("dialog.profile.delete.confirm", selected.name),
            CofferTunnelBundle.message("message.delete.tunnel.title"),
            Messages.getQuestionIcon()
        )
        if (confirm == Messages.YES) {
            settingsService.deleteProfile(selected.id)
            refreshList()
        }
    }

    private fun testSelectedConnectivity() {
        val selected = profileList.selectedValue ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = TunnelConnectivityTester.testConnection(selected.jumpHost, selected.sshPort)
            ApplicationManager.getApplication().invokeLater {
                if (result.success) {
                    Messages.showInfoMessage(contentPanel, result.message, CofferTunnelBundle.message("validation.test.success", result.elapsedMs))
                } else {
                    Messages.showErrorDialog(contentPanel, result.message, CofferTunnelBundle.message("validation.test.failed", result.message, result.elapsedMs))
                }
            }
        }
    }
}
