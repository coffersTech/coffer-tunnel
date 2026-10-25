package tech.coffers.tunnel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBox
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
import tech.coffers.tunnel.config.CofferTunnelSettingsService
import tech.coffers.tunnel.model.SshProfile
import tech.coffers.tunnel.model.TunnelConfig
import tech.coffers.tunnel.service.TunnelConnectivityTester
import tech.coffers.tunnel.service.TunnelPortValidator
import tech.coffers.tunnel.service.TunnelProcessService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 目标类型封装（支持中英文多语言切换与端口预设）
 */
data class TargetTypeItem(val key: String, val defaultPort: Int?) {
    override fun toString(): String = CofferTunnelBundle.message(key)
}

/**
 * 隧道新建与编辑弹窗（多语言支持）
 */
class TunnelDialog(
    private val existingConfig: TunnelConfig? = null
) : DialogWrapper(true) {

    private val settingsService = CofferTunnelSettingsService.getInstance()
    private val useProfileMode = settingsService.isUseProfileMode

    // 核心公共字段
    private val nameField = JBTextField()
    private val targetTypeComboBoxModel = DefaultComboBoxModel<TargetTypeItem>()
    private val targetTypeComboBox = ComboBox(targetTypeComboBoxModel)

    // 跳板档案复用模式组件
    private val profileComboBoxModel = DefaultComboBoxModel<SshProfile>()
    private val profileComboBox = ComboBox(profileComboBoxModel)
    private val addProfileBtn = JButton(CofferTunnelBundle.message("action.new.profile"), AllIcons.General.Add)
    private val profileDetailLabel = JBLabel("")

    // 传统独立跳板字段（关闭复用模式时展示）
    private val legacyJumpHostField = JBTextField()
    private val legacySshPortField = JBTextField("22")
    private val legacyTestConnectionBtn = JButton(CofferTunnelBundle.message("action.test.connection"), AllIcons.Actions.Lightning)
    private val legacyConnectionStatusLabel = JBLabel("")
    private val legacyUsernameField = JBTextField()
    private val legacyPrivateKeyField = TextFieldWithBrowseButton()
    private val legacyExtraArgsField = JBTextField().apply {
        emptyText.text = CofferTunnelBundle.message("dialog.tunnel.extra.args.placeholder")
    }

    // 本地端口与远端目标
    private val localPortField = JBTextField("3306")
    private val checkPortBtn = JButton(CofferTunnelBundle.message("action.check.port"), AllIcons.Actions.Find)
    private val portStatusLabel = JBLabel("")
    private val remoteHostField = JBTextField("127.0.0.1")
    private val remotePortField = JBTextField().apply {
        emptyText.text = CofferTunnelBundle.message("dialog.tunnel.remote.port.placeholder")
    }

    private var isInitializing = true

    init {
        title = if (existingConfig == null) {
            CofferTunnelBundle.message("dialog.tunnel.new.title")
        } else {
            CofferTunnelBundle.message("dialog.tunnel.edit.title")
        }

        // 初始化目标类型下拉项
        TARGET_TYPES.forEach { targetTypeComboBoxModel.addElement(it) }

        if (useProfileMode) {
            setupProfileComponents()
        } else {
            setupLegacyComponents()
        }

        // 本地端口检测
        checkPortBtn.addActionListener { performPortCheck() }
        localPortField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent?) = performPortCheck()
        })

        // 回显已有配置
        existingConfig?.let {
            nameField.text = it.name
            val matchedType = TARGET_TYPES.find { item -> item.key == it.targetType || it.targetType.contains(item.key.removePrefix("type.")) }
                ?: TARGET_TYPES[0]
            targetTypeComboBox.selectedItem = matchedType

            localPortField.text = it.localPort.toString()
            remoteHostField.text = it.remoteHost
            remotePortField.text = it.remotePort.toString()

            if (useProfileMode) {
                val profile = settingsService.findProfileById(it.profileId)
                if (profile != null) {
                    profileComboBox.selectedItem = profile
                }
            } else {
                legacyJumpHostField.text = it.jumpHost
                legacySshPortField.text = it.sshPort.toString()
                legacyUsernameField.text = it.username
                legacyPrivateKeyField.text = it.privateKeyPath
                legacyExtraArgsField.text = it.extraArgs
            }
        }

        // 类型联动逻辑
        targetTypeComboBox.addActionListener {
            if (isInitializing) return@addActionListener
            val selected = targetTypeComboBox.selectedItem as? TargetTypeItem ?: return@addActionListener
            if (selected.defaultPort != null) {
                remotePortField.text = selected.defaultPort.toString()
            } else {
                remotePortField.text = ""
            }
        }

        isInitializing = false
        updateProfileDetail()
        init()
    }

    private fun setupProfileComponents() {
        refreshProfiles()

        addProfileBtn.addActionListener {
            val dialog = SshProfileDialog()
            if (dialog.showAndGet()) {
                val newProfile = dialog.buildProfile()
                settingsService.saveProfile(newProfile)
                refreshProfiles()
                profileComboBox.selectedItem = newProfile
                updateProfileDetail()
            }
        }

        profileComboBox.addActionListener {
            updateProfileDetail()
        }
    }

    private fun refreshProfiles() {
        profileComboBoxModel.removeAllElements()
        settingsService.getProfiles().forEach { profileComboBoxModel.addElement(it) }
    }

    private fun updateProfileDetail() {
        val selected = profileComboBox.selectedItem as? SshProfile
        if (selected != null) {
            val keyInfo = if (selected.privateKeyPath.isNotBlank()) "YES" else "NO"
            profileDetailLabel.text = CofferTunnelBundle.message(
                "dialog.tunnel.jump.connection",
                "${selected.username}@${selected.jumpHost}:${selected.sshPort}",
                keyInfo
            )
            profileDetailLabel.foreground = UIUtil.getContextHelpForeground()
        } else {
            profileDetailLabel.text = CofferTunnelBundle.message("dialog.tunnel.select.profile.prompt")
            profileDetailLabel.foreground = UIUtil.getErrorForeground()
        }
    }

    private fun setupLegacyComponents() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
            .withTitle(CofferTunnelBundle.message("message.file.choose.private.key"))
        legacyPrivateKeyField.addBrowseFolderListener(CofferTunnelBundle.message("message.file.choose.private.key"), null, null, descriptor)
        legacyTestConnectionBtn.addActionListener {
            performLegacyConnectivityTest()
        }
    }

    override fun createCenterPanel(): JComponent {
        val rootPanel = JPanel(BorderLayout(0, JBUI.scale(10)))

        val localPortPanel = JPanel(BorderLayout(JBUI.scale(6), 0))
        localPortPanel.add(localPortField, BorderLayout.CENTER)
        localPortPanel.add(checkPortBtn, BorderLayout.EAST)

        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent(CofferTunnelBundle.message("dialog.tunnel.field.name"), nameField)
            .addLabeledComponent(CofferTunnelBundle.message("dialog.tunnel.field.type"), targetTypeComboBox)

        if (useProfileMode) {
            val profilePanel = JPanel(BorderLayout(JBUI.scale(6), 0))
            profilePanel.add(profileComboBox, BorderLayout.CENTER)
            profilePanel.add(addProfileBtn, BorderLayout.EAST)

            formBuilder
                .addSeparator()
                .addLabeledComponent(CofferTunnelBundle.message("dialog.tunnel.field.profile"), profilePanel)
                .addComponent(profileDetailLabel)
                .addSeparator()
        } else {
            val jumpHostPanel = JPanel(BorderLayout(JBUI.scale(6), 0))
            jumpHostPanel.add(legacyJumpHostField, BorderLayout.CENTER)

            val portAndTestPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
            legacySshPortField.columns = 6
            portAndTestPanel.add(JBLabel(CofferTunnelBundle.message("dialog.tunnel.field.port")))
            portAndTestPanel.add(legacySshPortField)
            portAndTestPanel.add(legacyTestConnectionBtn)
            jumpHostPanel.add(portAndTestPanel, BorderLayout.EAST)

            formBuilder
                .addSeparator()
                .addLabeledComponent(CofferTunnelBundle.message("dialog.tunnel.field.jump.host"), jumpHostPanel)
                .addComponent(legacyConnectionStatusLabel)
                .addLabeledComponent(CofferTunnelBundle.message("dialog.tunnel.field.username"), legacyUsernameField)
                .addLabeledComponent(CofferTunnelBundle.message("dialog.tunnel.field.private.key"), legacyPrivateKeyField)
                .addLabeledComponent(CofferTunnelBundle.message("dialog.tunnel.field.extra.args"), legacyExtraArgsField)
                .addSeparator()
        }

        formBuilder
            .addLabeledComponent(CofferTunnelBundle.message("dialog.tunnel.field.local.port"), localPortPanel)
            .addComponent(portStatusLabel)
            .addLabeledComponent(CofferTunnelBundle.message("dialog.tunnel.field.remote.host"), remoteHostField)
            .addLabeledComponent(CofferTunnelBundle.message("dialog.tunnel.field.remote.port"), remotePortField)

        val form = formBuilder.panel
        form.border = IdeBorderFactory.createTitledBorder(CofferTunnelBundle.message("dialog.tunnel.border.settings"), false)

        rootPanel.add(form, BorderLayout.CENTER)
        rootPanel.preferredSize = Dimension(540, if (useProfileMode) 350 else 450)
        return rootPanel
    }

    private fun performLegacyConnectivityTest() {
        val host = legacyJumpHostField.text.trim()
        val port = legacySshPortField.text.trim().toIntOrNull() ?: 22

        if (host.isEmpty()) {
            legacyConnectionStatusLabel.icon = AllIcons.General.Error
            legacyConnectionStatusLabel.text = CofferTunnelBundle.message("validation.jump.host.required")
            legacyConnectionStatusLabel.foreground = UIUtil.getErrorForeground()
            return
        }

        legacyTestConnectionBtn.isEnabled = false
        legacyConnectionStatusLabel.icon = AllIcons.Process.Step_1
        legacyConnectionStatusLabel.text = CofferTunnelBundle.message("validation.test.connecting", host, port)

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = TunnelConnectivityTester.testConnection(host, port)
            ApplicationManager.getApplication().invokeLater {
                legacyTestConnectionBtn.isEnabled = true
                if (result.success) {
                    legacyConnectionStatusLabel.icon = AllIcons.General.InspectionsOK
                    legacyConnectionStatusLabel.text = CofferTunnelBundle.message("validation.test.success", result.elapsedMs)
                    legacyConnectionStatusLabel.foreground = UIUtil.getLabelForeground()
                } else {
                    legacyConnectionStatusLabel.icon = AllIcons.General.Error
                    legacyConnectionStatusLabel.text = CofferTunnelBundle.message("validation.test.failed", result.message, result.elapsedMs)
                    legacyConnectionStatusLabel.foreground = UIUtil.getErrorForeground()
                }
            }
        }
    }

    private fun performPortCheck() {
        val port = localPortField.text.trim().toIntOrNull()
        if (port == null || port !in 1..65535) {
            portStatusLabel.icon = AllIcons.General.Error
            portStatusLabel.text = CofferTunnelBundle.message("validation.local.port.invalid")
            portStatusLabel.foreground = UIUtil.getErrorForeground()
            return
        }

        if (existingConfig != null &&
            TunnelProcessService.getInstance().getStatus(existingConfig.id).isAlive &&
            existingConfig.localPort == port
        ) {
            portStatusLabel.icon = AllIcons.RunConfigurations.TestPassed
            portStatusLabel.text = CofferTunnelBundle.message("validation.port.self.running", port)
            portStatusLabel.foreground = UIUtil.getLabelForeground()
            return
        }

        val available = TunnelPortValidator.isPortAvailable(port)
        if (available) {
            portStatusLabel.icon = AllIcons.General.InspectionsOK
            portStatusLabel.text = CofferTunnelBundle.message("validation.port.available", port)
            portStatusLabel.foreground = UIUtil.getLabelForeground()
        } else {
            portStatusLabel.icon = AllIcons.General.Error
            portStatusLabel.text = CofferTunnelBundle.message("validation.port.occupied", port)
            portStatusLabel.foreground = UIUtil.getErrorForeground()
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.trim().isEmpty()) {
            return ValidationInfo(CofferTunnelBundle.message("validation.tunnel.name.required"), nameField)
        }

        if (useProfileMode) {
            val selectedProfile = profileComboBox.selectedItem as? SshProfile
            if (selectedProfile == null) {
                return ValidationInfo(CofferTunnelBundle.message("validation.profile.required"), profileComboBox)
            }
        } else {
            if (legacyJumpHostField.text.trim().isEmpty()) {
                return ValidationInfo(CofferTunnelBundle.message("validation.jump.host.required"), legacyJumpHostField)
            }
            val sshPort = legacySshPortField.text.trim().toIntOrNull()
            if (sshPort == null || sshPort !in 1..65535) {
                return ValidationInfo(CofferTunnelBundle.message("validation.ssh.port.invalid"), legacySshPortField)
            }
            if (legacyUsernameField.text.trim().isEmpty()) {
                return ValidationInfo(CofferTunnelBundle.message("validation.username.required"), legacyUsernameField)
            }
        }

        val localPort = localPortField.text.trim().toIntOrNull()
        if (localPort == null || localPort !in 1..65535) {
            return ValidationInfo(CofferTunnelBundle.message("validation.local.port.invalid"), localPortField)
        }
        if (remoteHostField.text.trim().isEmpty()) {
            return ValidationInfo(CofferTunnelBundle.message("validation.remote.host.required"), remoteHostField)
        }
        val remotePort = remotePortField.text.trim().toIntOrNull()
        if (remotePort == null || remotePort !in 1..65535) {
            return ValidationInfo(CofferTunnelBundle.message("validation.remote.port.invalid"), remotePortField)
        }

        // 端口占用警告
        val isSelfRunning = existingConfig != null &&
            TunnelProcessService.getInstance().getStatus(existingConfig.id).isAlive &&
            existingConfig.localPort == localPort
        if (!isSelfRunning && !TunnelPortValidator.isPortAvailable(localPort)) {
            return ValidationInfo(CofferTunnelBundle.message("validation.port.occupied.warning", localPort), localPortField).asWarning()
        }

        return null
    }

    fun buildTunnelConfig(): TunnelConfig {
        val selectedProfile = profileComboBox.selectedItem as? SshProfile
        val selectedType = targetTypeComboBox.selectedItem as? TargetTypeItem ?: TARGET_TYPES[0]
        return TunnelConfig(
            id = existingConfig?.id ?: java.util.UUID.randomUUID().toString(),
            name = nameField.text.trim(),
            targetType = selectedType.key,
            profileId = if (useProfileMode) selectedProfile?.id ?: "" else "",

            jumpHost = if (!useProfileMode) legacyJumpHostField.text.trim() else "",
            sshPort = if (!useProfileMode) legacySshPortField.text.trim().toIntOrNull() ?: 22 else 22,
            username = if (!useProfileMode) legacyUsernameField.text.trim() else "",
            privateKeyPath = if (!useProfileMode) legacyPrivateKeyField.text.trim() else "",
            extraArgs = if (!useProfileMode) legacyExtraArgsField.text.trim() else "",

            localPort = localPortField.text.trim().toIntOrNull() ?: 3306,
            remoteHost = remoteHostField.text.trim(),
            remotePort = remotePortField.text.trim().toIntOrNull() ?: 3306
        )
    }

    companion object {
        val TARGET_TYPES = listOf(
            TargetTypeItem("type.custom", null),
            TargetTypeItem("type.mysql", 3306),
            TargetTypeItem("type.pg", 5432),
            TargetTypeItem("type.redis", 6379),
            TargetTypeItem("type.mongo", 27017)
        )
    }
}
