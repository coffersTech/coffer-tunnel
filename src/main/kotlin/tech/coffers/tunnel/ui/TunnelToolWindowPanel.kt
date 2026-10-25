package tech.coffers.tunnel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import tech.coffers.tunnel.CofferTunnelBundle
import tech.coffers.tunnel.config.CofferTunnelSettingsService
import tech.coffers.tunnel.model.TunnelConfig
import tech.coffers.tunnel.model.TunnelStatus
import tech.coffers.tunnel.service.TunnelProcessService
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.ListSelectionModel

/**
 * CofferTunnel 工具窗口主面板（多语言与原生 UI 支持）
 */
class TunnelToolWindowPanel(
    private val project: Project?
) : JPanel(BorderLayout()), Disposable {

    private val settingsService = CofferTunnelSettingsService.getInstance()
    private val processService = TunnelProcessService.getInstance()

    // 卡片式列表
    private val listModel = DefaultListModel<TunnelConfig>()
    private val tunnelList = JBList(listModel)

    // 控制台组件
    private val logHeaderPanel = JPanel(BorderLayout())
    private val toggleLogBtn = JButton("", AllIcons.General.ArrowDown)
    private val logStatusLabel = JBLabel("")
    private val autoScrollCheckBox = JBCheckBox("", true)
    private val clearLogBtn = JButton(AllIcons.Actions.GC)
    private val logTextArea = JBTextArea()
    private val logScrollPane = JBScrollPane(logTextArea)
    private var isLogExpanded = true

    private var selectedTunnelId: String? = null

    // 监听器引用
    private val settingsListener: () -> Unit = { refreshList() }
    private val statusListener: (String, TunnelStatus) -> Unit = { _, _ ->
        ApplicationManager.getApplication().invokeLater {
            tunnelList.repaint()
            updateLogHeaderStatus()
        }
    }
    private val logListener: (String, String) -> Unit = { tunnelId, line ->
        if (tunnelId == selectedTunnelId) {
            ApplicationManager.getApplication().invokeLater {
                logTextArea.append(line + "\n")
                if (autoScrollCheckBox.isSelected) {
                    logTextArea.caretPosition = logTextArea.document.length
                }
            }
        }
    }

    init {
        setupUI()
        setupListeners()
        refreshList()
    }

    private fun setupUI() {
        val splitter = JBSplitter(true, 0.65f)

        // 1. 顶部操作栏
        val topPanel = JPanel(BorderLayout())
        val toolbarGroup = DefaultActionGroup().apply {
            // 新建
            add(object : AnAction(
                CofferTunnelBundle.message("action.new.tunnel"),
                CofferTunnelBundle.message("action.new.tunnel.desc"),
                AllIcons.General.Add
            ) {
                override fun actionPerformed(e: AnActionEvent) = openCreateDialog()
            })
            // 编辑
            add(object : AnAction(
                CofferTunnelBundle.message("action.edit.tunnel"),
                CofferTunnelBundle.message("action.edit.tunnel.desc"),
                AllIcons.Actions.Edit
            ) {
                override fun actionPerformed(e: AnActionEvent) = openEditSelectedDialog()
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = getSelectedTunnel() != null
                }
            })
            // 复制
            add(object : AnAction(
                CofferTunnelBundle.message("action.duplicate.tunnel"),
                CofferTunnelBundle.message("action.duplicate.tunnel.desc"),
                AllIcons.Actions.Copy
            ) {
                override fun actionPerformed(e: AnActionEvent) = duplicateSelectedTunnel()
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = getSelectedTunnel() != null
                }
            })
            // 删除
            add(object : AnAction(
                CofferTunnelBundle.message("action.delete.tunnel"),
                CofferTunnelBundle.message("action.delete.tunnel.desc"),
                AllIcons.General.Remove
            ) {
                override fun actionPerformed(e: AnActionEvent) = deleteSelectedTunnel()
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = getSelectedTunnel() != null
                }
            })
            addSeparator()
            // 启动所选
            add(object : AnAction(
                CofferTunnelBundle.message("action.start.selected"),
                CofferTunnelBundle.message("action.start.selected.desc"),
                AllIcons.Actions.Execute
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    getSelectedTunnel()?.let { processService.startTunnel(it) }
                }
                override fun update(e: AnActionEvent) {
                    val tunnel = getSelectedTunnel()
                    e.presentation.isEnabled = tunnel != null && !processService.getStatus(tunnel.id).isAlive
                }
            })
            // 停止所选
            add(object : AnAction(
                CofferTunnelBundle.message("action.stop.selected"),
                CofferTunnelBundle.message("action.stop.selected.desc"),
                AllIcons.Actions.Suspend
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    getSelectedTunnel()?.let { processService.stopTunnel(it.id) }
                }
                override fun update(e: AnActionEvent) {
                    val tunnel = getSelectedTunnel()
                    e.presentation.isEnabled = tunnel != null && processService.getStatus(tunnel.id).isAlive
                }
            })
            addSeparator()
            // 批量启停
            add(object : AnAction(
                CofferTunnelBundle.message("action.start.all"),
                CofferTunnelBundle.message("action.start.all.desc"),
                AllIcons.Actions.RunAll
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    settingsService.getTunnels().forEach { processService.startTunnel(it) }
                }
            })
            add(object : AnAction(
                CofferTunnelBundle.message("action.stop.all"),
                CofferTunnelBundle.message("action.stop.all.desc"),
                AllIcons.Actions.Cancel
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    processService.stopAll()
                }
            })
            addSeparator()
            // 跳板档案集中管理
            add(object : AnAction(
                CofferTunnelBundle.message("action.jump.profiles"),
                CofferTunnelBundle.message("action.jump.profiles.desc"),
                AllIcons.Nodes.Services
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    SshProfileManagerDialog().show()
                }
            })
            // 全局复用开关
            add(object : ToggleAction(
                CofferTunnelBundle.message("action.toggle.profile.mode"),
                CofferTunnelBundle.message("action.toggle.profile.mode.desc"),
                AllIcons.General.Settings
            ) {
                override fun isSelected(e: AnActionEvent): Boolean = settingsService.isUseProfileMode
                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    settingsService.isUseProfileMode = state
                }
            })
            addSeparator()
            // 导入导出
            add(object : AnAction(
                CofferTunnelBundle.message("action.import.config"),
                CofferTunnelBundle.message("action.import.config.desc"),
                AllIcons.Actions.Upload
            ) {
                override fun actionPerformed(e: AnActionEvent) = importConfig()
            })
            add(object : AnAction(
                CofferTunnelBundle.message("action.export.backup"),
                CofferTunnelBundle.message("action.export.backup.desc"),
                AllIcons.Actions.Download
            ) {
                override fun actionPerformed(e: AnActionEvent) = exportConfig()
            })
            addSeparator()
            // 语言手动切换菜单 (Auto / 简体中文 / English)
            val languageActionGroup = object : DefaultActionGroup("语言 / Language", true) {
                init {
                    templatePresentation.icon = AllIcons.General.Web
                    templatePresentation.description = "切换语言 / Switch Language"
                }

                override fun update(e: AnActionEvent) {
                    val current = settingsService.language
                    val langText = when (current) {
                        "ZH" -> "中"
                        "EN" -> "EN"
                        else -> "Auto"
                    }
                    e.presentation.text = "Lang: $langText"
                }
            }.apply {
                add(object : ToggleAction("跟随系统 (Auto)", "跟随 IDE 全局语言设置", null) {
                    override fun isSelected(e: AnActionEvent): Boolean = settingsService.language == "AUTO"
                    override fun setSelected(e: AnActionEvent, state: Boolean) {
                        if (state) switchLanguage("AUTO")
                    }
                })
                add(object : ToggleAction("简体中文 (Simplified Chinese)", "强制切换为简体中文", null) {
                    override fun isSelected(e: AnActionEvent): Boolean = settingsService.language == "ZH"
                    override fun setSelected(e: AnActionEvent, state: Boolean) {
                        if (state) switchLanguage("ZH")
                    }
                })
                add(object : ToggleAction("English", "Force use English interface", null) {
                    override fun isSelected(e: AnActionEvent): Boolean = settingsService.language == "EN"
                    override fun setSelected(e: AnActionEvent, state: Boolean) {
                        if (state) switchLanguage("EN")
                    }
                })
            }
            add(languageActionGroup)
        }

        val actionToolbar: ActionToolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, toolbarGroup, true)
        actionToolbar.targetComponent = this
        topPanel.add(actionToolbar.component, BorderLayout.NORTH)

        // 2. 隧道列表
        tunnelList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        tunnelList.cellRenderer = TunnelListCellRenderer()

        tunnelList.addListSelectionListener {
            val selected = getSelectedTunnel()
            switchActiveLog(selected)
        }

        tunnelList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    openEditSelectedDialog()
                }
            }

            override fun mousePressed(e: MouseEvent) = maybeShowPopup(e)
            override fun mouseReleased(e: MouseEvent) = maybeShowPopup(e)

            private fun maybeShowPopup(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    val index = tunnelList.locationToIndex(e.point)
                    if (index >= 0) {
                        tunnelList.selectedIndex = index
                        showContextMenu(e)
                    }
                }
            }
        })

        topPanel.add(JBScrollPane(tunnelList), BorderLayout.CENTER)
        splitter.firstComponent = topPanel

        // 3. 底部控制台
        val bottomPanel = JPanel(BorderLayout())
        setupLogHeader()
        bottomPanel.add(logHeaderPanel, BorderLayout.NORTH)

        logTextArea.isEditable = false
        val consoleFont = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.CONSOLE_PLAIN)
        logTextArea.font = consoleFont
        logTextArea.background = UIUtil.getTextFieldBackground()
        logTextArea.foreground = UIUtil.getTextFieldForeground()
        logTextArea.border = JBUI.Borders.empty(4, 6)

        bottomPanel.add(logScrollPane, BorderLayout.CENTER)
        splitter.secondComponent = bottomPanel

        add(splitter, BorderLayout.CENTER)
    }

    private fun setupLogHeader() {
        logHeaderPanel.border = JBUI.Borders.empty(4, 8)
        logHeaderPanel.background = UIUtil.getPanelBackground()

        toggleLogBtn.text = CofferTunnelBundle.message("action.console.log")
        autoScrollCheckBox.text = CofferTunnelBundle.message("action.auto.scroll")
        clearLogBtn.toolTipText = CofferTunnelBundle.message("action.clear.log")

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply { isOpaque = false }
        toggleLogBtn.isBorderPainted = false
        toggleLogBtn.isContentAreaFilled = false
        toggleLogBtn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toggleLogBtn.font = toggleLogBtn.font.deriveFont(12f)
        toggleLogBtn.addActionListener { toggleLogPanel() }
        leftPanel.add(toggleLogBtn)

        logStatusLabel.font = logStatusLabel.font.deriveFont(11f)
        logStatusLabel.foreground = UIUtil.getContextHelpForeground()
        leftPanel.add(logStatusLabel)

        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0)).apply { isOpaque = false }
        autoScrollCheckBox.isOpaque = false
        autoScrollCheckBox.font = autoScrollCheckBox.font.deriveFont(11f)

        clearLogBtn.isBorderPainted = false
        clearLogBtn.isContentAreaFilled = false
        clearLogBtn.preferredSize = Dimension(22, 22)
        clearLogBtn.addActionListener {
            selectedTunnelId?.let { id ->
                processService.clearLog(id)
                logTextArea.text = ""
            }
        }

        rightPanel.add(autoScrollCheckBox)
        rightPanel.add(clearLogBtn)

        logHeaderPanel.add(leftPanel, BorderLayout.WEST)
        logHeaderPanel.add(rightPanel, BorderLayout.EAST)
    }

    private fun toggleLogPanel() {
        isLogExpanded = !isLogExpanded
        logScrollPane.isVisible = isLogExpanded
        toggleLogBtn.icon = if (isLogExpanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
        revalidate()
        repaint()
    }

    private fun setupListeners() {
        settingsService.addChangeListener(settingsListener)
        processService.addStatusListener(statusListener)
        processService.addLogListener(logListener)
    }

    private fun refreshList() {
        val tunnels = settingsService.getTunnels()
        val currentSelected = getSelectedTunnel()?.id

        listModel.clear()
        tunnels.forEach { listModel.addElement(it) }

        if (tunnels.isNotEmpty()) {
            val targetIndex = tunnels.indexOfFirst { it.id == currentSelected }
            tunnelList.selectedIndex = if (targetIndex >= 0) targetIndex else 0
        } else {
            switchActiveLog(null)
        }
    }

    private fun getSelectedTunnel(): TunnelConfig? {
        val selectedIndex = tunnelList.selectedIndex
        return if (selectedIndex in 0 until listModel.size()) listModel.getElementAt(selectedIndex) else null
    }

    private fun switchActiveLog(tunnel: TunnelConfig?) {
        if (tunnel == null) {
            selectedTunnelId = null
            logStatusLabel.text = CofferTunnelBundle.message("label.no.tunnel.selected")
            logTextArea.text = ""
        } else {
            selectedTunnelId = tunnel.id
            val status = processService.getStatus(tunnel.id)
            logStatusLabel.text = "[${tunnel.name}] (${status.displayText})"
            logTextArea.text = processService.getLog(tunnel.id)
            if (autoScrollCheckBox.isSelected) {
                logTextArea.caretPosition = logTextArea.document.length
            }
        }
    }

    private fun updateLogHeaderStatus() {
        val selected = getSelectedTunnel()
        if (selected != null) {
            val status = processService.getStatus(selected.id)
            logStatusLabel.text = "[${selected.name}] (${status.displayText})"
        }
    }

    private fun switchLanguage(lang: String) {
        settingsService.language = lang
        ApplicationManager.getApplication().invokeLater {
            removeAll()
            setupUI()
            revalidate()
            repaint()
            refreshList()
        }
    }

    private fun openCreateDialog() {
        val dialog = TunnelDialog()
        if (dialog.showAndGet()) {
            val config = dialog.buildTunnelConfig()
            settingsService.saveTunnel(config)
            selectTunnelById(config.id)
        }
    }

    private fun openEditSelectedDialog() {
        val selected = getSelectedTunnel() ?: return
        val dialog = TunnelDialog(selected)
        if (dialog.showAndGet()) {
            val updated = dialog.buildTunnelConfig()
            if (processService.getStatus(selected.id).isAlive) {
                Messages.showWarningDialog(
                    this,
                    CofferTunnelBundle.message("message.running.save.warning"),
                    CofferTunnelBundle.message("message.running.save.title")
                )
            }
            settingsService.saveTunnel(updated)
            selectTunnelById(updated.id)
        }
    }

    private fun duplicateSelectedTunnel() {
        val selected = getSelectedTunnel() ?: return
        val duplicated = selected.duplicate()
        val dialog = TunnelDialog(duplicated)
        dialog.title = CofferTunnelBundle.message("dialog.tunnel.duplicate.title")
        if (dialog.showAndGet()) {
            val config = dialog.buildTunnelConfig()
            settingsService.saveTunnel(config)
            selectTunnelById(config.id)
        }
    }

    private fun deleteSelectedTunnel() {
        val selected = getSelectedTunnel() ?: return
        val confirm = Messages.showYesNoDialog(
            this,
            CofferTunnelBundle.message("message.delete.tunnel.confirm", selected.name),
            CofferTunnelBundle.message("message.delete.tunnel.title"),
            Messages.getQuestionIcon()
        )
        if (confirm == Messages.YES) {
            processService.stopTunnel(selected.id)
            settingsService.removeTunnel(selected.id)
        }
    }

    private fun selectTunnelById(id: String) {
        val tunnels = settingsService.getTunnels()
        val index = tunnels.indexOfFirst { it.id == id }
        if (index >= 0) {
            tunnelList.selectedIndex = index
        }
    }

    private fun importConfig() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
            .withTitle(CofferTunnelBundle.message("message.file.choose.import"))
        val virtualFile = FileChooser.chooseFile(descriptor, project, null) ?: return
        val file = File(virtualFile.path)

        val confirm = Messages.showYesNoCancelDialog(
            this,
            CofferTunnelBundle.message("message.import.mode.msg"),
            CofferTunnelBundle.message("message.import.mode.title"),
            CofferTunnelBundle.message("message.import.mode.merge"),
            CofferTunnelBundle.message("message.import.mode.overwrite"),
            Messages.getCancelButton(),
            Messages.getQuestionIcon()
        )
        if (confirm == Messages.CANCEL) return
        val overwrite = (confirm == Messages.NO)

        TunnelExportImportHelper.importData(file, overwrite).fold(
            onSuccess = { (profilesCount, tunnelsCount) ->
                Messages.showInfoMessage(
                    this,
                    CofferTunnelBundle.message("message.import.success", profilesCount, tunnelsCount),
                    CofferTunnelBundle.message("message.import.success.title")
                )
            },
            onFailure = { error ->
                Messages.showErrorDialog(
                    this,
                    CofferTunnelBundle.message("message.import.failed", error.message ?: ""),
                    CofferTunnelBundle.message("message.import.failed.title")
                )
            }
        )
    }

    private fun exportConfig() {
        val tunnels = settingsService.getTunnels()
        val profiles = settingsService.getProfiles()
        if (tunnels.isEmpty() && profiles.isEmpty()) {
            Messages.showWarningDialog(
                this,
                CofferTunnelBundle.message("message.export.empty"),
                CofferTunnelBundle.message("message.export.empty.title")
            )
            return
        }

        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle(CofferTunnelBundle.message("message.file.choose.export"))
        val dir = FileChooser.chooseFile(descriptor, project, null) ?: return
        val targetFile = File(dir.path, "coffer_backup.json")

        TunnelExportImportHelper.exportData(targetFile).fold(
            onSuccess = { (profilesCount, tunnelsCount) ->
                Messages.showInfoMessage(
                    this,
                    CofferTunnelBundle.message("message.export.success", profilesCount, tunnelsCount, targetFile.absolutePath),
                    CofferTunnelBundle.message("message.export.success.title")
                )
            },
            onFailure = { error ->
                Messages.showErrorDialog(
                    this,
                    CofferTunnelBundle.message("message.export.failed", error.message ?: ""),
                    CofferTunnelBundle.message("message.export.failed.title")
                )
            }
        )
    }

    private fun showContextMenu(e: MouseEvent) {
        val selected = getSelectedTunnel() ?: return
        val isAlive = processService.getStatus(selected.id).isAlive

        val menu = JPopupMenu()
        val toggleItem = JMenuItem(
            if (isAlive) CofferTunnelBundle.message("action.stop.selected") else CofferTunnelBundle.message("action.start.selected")
        ).apply {
            icon = if (isAlive) AllIcons.Actions.Suspend else AllIcons.Actions.Execute
            addActionListener {
                if (isAlive) processService.stopTunnel(selected.id) else processService.startTunnel(selected)
            }
        }
        val editItem = JMenuItem(CofferTunnelBundle.message("action.edit.tunnel")).apply {
            icon = AllIcons.Actions.Edit
            addActionListener { openEditSelectedDialog() }
        }
        val duplicateItem = JMenuItem(CofferTunnelBundle.message("action.duplicate.tunnel")).apply {
            icon = AllIcons.Actions.Copy
            addActionListener { duplicateSelectedTunnel() }
        }
        val deleteItem = JMenuItem(CofferTunnelBundle.message("action.delete.tunnel")).apply {
            icon = AllIcons.General.Remove
            addActionListener { deleteSelectedTunnel() }
        }
        val clearLogItem = JMenuItem(CofferTunnelBundle.message("action.clear.log")).apply {
            icon = AllIcons.Actions.GC
            addActionListener {
                processService.clearLog(selected.id)
                if (selectedTunnelId == selected.id) {
                    logTextArea.text = ""
                }
            }
        }
        val manageProfilesItem = JMenuItem(CofferTunnelBundle.message("action.jump.profiles")).apply {
            icon = AllIcons.Nodes.Services
            addActionListener { SshProfileManagerDialog().show() }
        }

        // 语言切换子菜单
        val langMenu = JMenu("语言 / Language").apply {
            icon = AllIcons.General.Web
            add(JMenuItem("跟随系统 (Auto)").apply { addActionListener { switchLanguage("AUTO") } })
            add(JMenuItem("简体中文 (Simplified Chinese)").apply { addActionListener { switchLanguage("ZH") } })
            add(JMenuItem("English").apply { addActionListener { switchLanguage("EN") } })
        }

        menu.add(toggleItem)
        menu.addSeparator()
        menu.add(editItem)
        menu.add(duplicateItem)
        menu.add(deleteItem)
        menu.addSeparator()
        menu.add(clearLogItem)
        menu.addSeparator()
        menu.add(manageProfilesItem)
        menu.add(langMenu)

        menu.show(e.component, e.x, e.y)
    }

    override fun dispose() {
        settingsService.removeChangeListener(settingsListener)
        processService.removeStatusListener(statusListener)
        processService.removeLogListener(logListener)
    }
}
