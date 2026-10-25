package tech.coffers.tunnel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import tech.coffers.tunnel.CofferTunnelBundle
import tech.coffers.tunnel.model.SshProfile
import tech.coffers.tunnel.service.TunnelConnectivityTester
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 单个 SSH 跳板档案新建与编辑弹窗（多语言支持）
 */
class SshProfileDialog(
    private val existingProfile: SshProfile? = null
) : DialogWrapper(true) {

    private val nameField = JBTextField().apply {
        emptyText.text = CofferTunnelBundle.message("dialog.profile.field.name.placeholder")
    }
    private val jumpHostField = JBTextField()
    private val sshPortField = JBTextField("22")
    private val testConnectionBtn = JButton(CofferTunnelBundle.message("action.test.connection"), AllIcons.Actions.Lightning)
    private val connectionStatusLabel = JBLabel("")

    private val usernameField = JBTextField()
    private val privateKeyField = TextFieldWithBrowseButton()
    private val extraArgsField = JBTextField().apply {
        emptyText.text = CofferTunnelBundle.message("dialog.tunnel.extra.args.placeholder")
    }

    init {
        title = if (existingProfile == null) {
            CofferTunnelBundle.message("dialog.profile.new.title")
        } else {
            CofferTunnelBundle.message("dialog.profile.edit.title")
        }

        val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
            .withTitle(CofferTunnelBundle.message("message.file.choose.private.key"))
            .withDescription(CofferTunnelBundle.message("message.file.choose.private.key.desc"))
        privateKeyField.addBrowseFolderListener(CofferTunnelBundle.message("message.file.choose.private.key"), null, null, descriptor)

        testConnectionBtn.addActionListener {
            performConnectivityTest()
        }

        existingProfile?.let {
            nameField.text = it.name
            jumpHostField.text = it.jumpHost
            sshPortField.text = it.sshPort.toString()
            usernameField.text = it.username
            privateKeyField.text = it.privateKeyPath
            extraArgsField.text = it.extraArgs
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        val rootPanel = JPanel(BorderLayout(0, JBUI.scale(10)))

        val jumpHostPanel = JPanel(BorderLayout(JBUI.scale(6), 0))
        jumpHostPanel.add(jumpHostField, BorderLayout.CENTER)

        val portAndTestPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
        sshPortField.columns = 6
        portAndTestPanel.add(JBLabel(CofferTunnelBundle.message("dialog.tunnel.field.port")))
        portAndTestPanel.add(sshPortField)
        portAndTestPanel.add(testConnectionBtn)
        jumpHostPanel.add(portAndTestPanel, BorderLayout.EAST)

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(CofferTunnelBundle.message("dialog.profile.field.name"), nameField)
            .addLabeledComponent(CofferTunnelBundle.message("dialog.tunnel.field.jump.host"), jumpHostPanel)
            .addComponent(connectionStatusLabel)
            .addLabeledComponent(CofferTunnelBundle.message("dialog.tunnel.field.username"), usernameField)
            .addLabeledComponent(CofferTunnelBundle.message("dialog.tunnel.field.private.key"), privateKeyField)
            .addLabeledComponent(CofferTunnelBundle.message("dialog.tunnel.field.extra.args"), extraArgsField)
            .panel

        form.border = IdeBorderFactory.createTitledBorder(CofferTunnelBundle.message("dialog.profile.border"), false)

        rootPanel.add(form, BorderLayout.CENTER)
        rootPanel.preferredSize = Dimension(520, 260)
        return rootPanel
    }

    private fun performConnectivityTest() {
        val host = jumpHostField.text.trim()
        val port = sshPortField.text.trim().toIntOrNull() ?: 22

        if (host.isEmpty()) {
            connectionStatusLabel.icon = AllIcons.General.Error
            connectionStatusLabel.text = CofferTunnelBundle.message("validation.jump.host.required")
            connectionStatusLabel.foreground = UIUtil.getErrorForeground()
            return
        }

        testConnectionBtn.isEnabled = false
        connectionStatusLabel.icon = AllIcons.Process.Step_1
        connectionStatusLabel.text = CofferTunnelBundle.message("validation.test.connecting", host, port)
        connectionStatusLabel.foreground = UIUtil.getContextHelpForeground()

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = TunnelConnectivityTester.testConnection(host, port)
            ApplicationManager.getApplication().invokeLater {
                testConnectionBtn.isEnabled = true
                if (result.success) {
                    connectionStatusLabel.icon = AllIcons.General.InspectionsOK
                    connectionStatusLabel.text = CofferTunnelBundle.message("validation.test.success", result.elapsedMs)
                    connectionStatusLabel.foreground = UIUtil.getLabelForeground()
                } else {
                    connectionStatusLabel.icon = AllIcons.General.Error
                    connectionStatusLabel.text = CofferTunnelBundle.message("validation.test.failed", result.message, result.elapsedMs)
                    connectionStatusLabel.foreground = UIUtil.getErrorForeground()
                }
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.trim().isEmpty()) {
            return ValidationInfo(CofferTunnelBundle.message("validation.profile.name.required"), nameField)
        }
        if (jumpHostField.text.trim().isEmpty()) {
            return ValidationInfo(CofferTunnelBundle.message("validation.jump.host.required"), jumpHostField)
        }
        val sshPort = sshPortField.text.trim().toIntOrNull()
        if (sshPort == null || sshPort !in 1..65535) {
            return ValidationInfo(CofferTunnelBundle.message("validation.ssh.port.invalid"), sshPortField)
        }
        if (usernameField.text.trim().isEmpty()) {
            return ValidationInfo(CofferTunnelBundle.message("validation.username.required"), usernameField)
        }
        return null
    }

    fun buildProfile(): SshProfile {
        return SshProfile(
            id = existingProfile?.id ?: java.util.UUID.randomUUID().toString(),
            name = nameField.text.trim(),
            jumpHost = jumpHostField.text.trim(),
            sshPort = sshPortField.text.trim().toIntOrNull() ?: 22,
            username = usernameField.text.trim(),
            privateKeyPath = privateKeyField.text.trim(),
            extraArgs = extraArgsField.text.trim()
        )
    }
}
