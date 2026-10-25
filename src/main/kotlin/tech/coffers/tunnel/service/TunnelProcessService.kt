package tech.coffers.tunnel.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import tech.coffers.tunnel.CofferTunnelBundle
import tech.coffers.tunnel.config.CofferTunnelSettingsService
import tech.coffers.tunnel.model.TunnelConfig
import tech.coffers.tunnel.model.TunnelStatus
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 隧道进程核心管理服务（全局应用级单例，多语言日志支持）
 */
class TunnelProcessService : Disposable {

    private val log = Logger.getInstance(TunnelProcessService::class.java)

    // 运行中的进程句柄集合 <tunnelId, Process>
    private val activeProcesses = ConcurrentHashMap<String, Process>()

    // 各隧道的运行状态 <tunnelId, TunnelStatus>
    private val tunnelStatuses = ConcurrentHashMap<String, TunnelStatus>()

    // 各隧道的独立日志缓冲区 <tunnelId, StringBuilder>
    private val logBuffers = ConcurrentHashMap<String, StringBuilder>()

    // 状态变动观察者
    private val statusListeners = CopyOnWriteArrayList<(tunnelId: String, status: TunnelStatus) -> Unit>()

    // 日志输出观察者
    private val logListeners = CopyOnWriteArrayList<(tunnelId: String, line: String) -> Unit>()

    private val executor = Executors.newCachedThreadPool { runnable ->
        val thread = Thread(runnable, "CofferTunnel-Worker")
        thread.isDaemon = true
        thread
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss")

    fun getStatus(tunnelId: String): TunnelStatus {
        return tunnelStatuses.getOrDefault(tunnelId, TunnelStatus.STOPPED)
    }

    fun getLog(tunnelId: String): String {
        return logBuffers[tunnelId]?.toString() ?: ""
    }

    fun clearLog(tunnelId: String) {
        logBuffers[tunnelId]?.setLength(0)
    }

    /**
     * 启动隧道
     */
    @Synchronized
    fun startTunnel(tunnel: TunnelConfig) {
        val currentStatus = getStatus(tunnel.id)
        if (currentStatus.isAlive) {
            appendLog(tunnel.id, CofferTunnelBundle.message("log.already.alive"))
            return
        }

        // 1. 动态读取关联的跳板档案 (SshProfile)
        val settingsService = CofferTunnelSettingsService.getInstance()
        val profile = if (tunnel.profileId.isNotBlank()) {
            settingsService.findProfileById(tunnel.profileId)
        } else null

        if (tunnel.profileId.isNotBlank() && profile == null) {
            updateStatus(tunnel.id, TunnelStatus.ERROR)
            appendLog(tunnel.id, CofferTunnelBundle.message("log.profile.missing", tunnel.profileId))
            return
        }

        // 2. 基础配置校验
        val validationError = tunnel.validate(hasProfile = profile != null)
        if (validationError != null) {
            updateStatus(tunnel.id, TunnelStatus.ERROR)
            appendLog(tunnel.id, CofferTunnelBundle.message("log.config.invalid", validationError))
            return
        }

        val effectiveJumpHost = profile?.jumpHost ?: tunnel.jumpHost
        val effectiveSshPort = profile?.sshPort ?: tunnel.sshPort
        val effectiveUsername = profile?.username ?: tunnel.username
        val effectivePrivateKey = profile?.privateKeyPath ?: tunnel.privateKeyPath
        val effectiveExtraArgs = listOfNotNull(
            profile?.extraArgs?.takeIf { it.isNotBlank() },
            tunnel.extraArgs.takeIf { it.isNotBlank() }
        ).joinToString(" ")

        if (effectiveJumpHost.isBlank() || effectiveUsername.isBlank()) {
            updateStatus(tunnel.id, TunnelStatus.ERROR)
            appendLog(tunnel.id, CofferTunnelBundle.message("log.jump.info.missing"))
            return
        }

        // 3. 检测系统 OpenSSH
        val sshExecutable = SshDetector.findSshExecutable()
        if (sshExecutable == null) {
            updateStatus(tunnel.id, TunnelStatus.ERROR)
            appendLog(tunnel.id, CofferTunnelBundle.message("log.ssh.not.found"))
            return
        }

        // 4. 校验本地端口是否被占用
        if (!TunnelPortValidator.isPortAvailable(tunnel.localPort)) {
            updateStatus(tunnel.id, TunnelStatus.ERROR)
            appendLog(tunnel.id, CofferTunnelBundle.message("log.port.occupied", tunnel.localPort))
            return
        }

        updateStatus(tunnel.id, TunnelStatus.STARTING)
        val profileDesc = if (profile != null) " [Profile: ${profile.name}]" else ""
        appendLog(tunnel.id, CofferTunnelBundle.message("log.starting", profileDesc))

        // 5. 构建命令
        val command = mutableListOf<String>().apply {
            add(sshExecutable)
            add("-N")
            add("-L")
            add("${tunnel.localPort}:${tunnel.remoteHost}:${tunnel.remotePort}")
            add("-p")
            add(effectiveSshPort.toString())
            add("-o")
            add("ExitOnForwardFailure=yes")
            add("-o")
            add("ServerAliveInterval=30")
            add("-o")
            add("ServerAliveCountMax=3")

            if (effectivePrivateKey.isNotBlank()) {
                val keyFile = File(effectivePrivateKey.trim())
                if (!keyFile.exists()) {
                    appendLog(tunnel.id, CofferTunnelBundle.message("log.key.not.found", keyFile.absolutePath))
                } else {
                    add("-i")
                    add(keyFile.absolutePath)
                }
            }

            if (effectiveExtraArgs.isNotBlank()) {
                val extras = effectiveExtraArgs.trim().split("\\s+".toRegex())
                addAll(extras)
            }

            val target = "${effectiveUsername.trim()}@${effectiveJumpHost.trim()}"
            add(target)
        }

        appendLog(tunnel.id, CofferTunnelBundle.message("log.command", command.joinToString(" ")))

        try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)

            val userHome = System.getProperty("user.home")
            if (userHome != null) {
                processBuilder.directory(File(userHome))
            }

            val process = processBuilder.start()
            activeProcesses[tunnel.id] = process

            // 异步读取日志流
            executor.submit {
                val charset = determineCharset()
                try {
                    BufferedReader(InputStreamReader(process.inputStream, charset)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            appendLog(tunnel.id, line!!)
                        }
                    }
                } catch (e: Exception) {
                    if (process.isAlive) {
                        appendLog(tunnel.id, CofferTunnelBundle.message("log.read.error", e.message ?: ""))
                    }
                }
            }

            // 异步监控退出
            executor.submit {
                try {
                    val exitedEarly = process.waitFor(1200, TimeUnit.MILLISECONDS)
                    if (exitedEarly) {
                        val exitCode = process.exitValue()
                        activeProcesses.remove(tunnel.id)
                        updateStatus(tunnel.id, TunnelStatus.ERROR)
                        appendLog(tunnel.id, CofferTunnelBundle.message("log.exit.abnormal", exitCode))
                    } else {
                        updateStatus(tunnel.id, TunnelStatus.RUNNING)
                        appendLog(
                            tunnel.id,
                            CofferTunnelBundle.message(
                                "log.established",
                                tunnel.localPort,
                                "${effectiveUsername}@${effectiveJumpHost}",
                                tunnel.remoteHost,
                                tunnel.remotePort
                            )
                        )

                        val exitVal = process.waitFor()
                        activeProcesses.remove(tunnel.id)

                        val finalStatus = if (getStatus(tunnel.id) == TunnelStatus.STOPPED) {
                            TunnelStatus.STOPPED
                        } else if (exitVal == 0) {
                            TunnelStatus.STOPPED
                        } else {
                            TunnelStatus.ERROR
                        }
                        updateStatus(tunnel.id, finalStatus)
                        appendLog(tunnel.id, CofferTunnelBundle.message("log.stopped", exitVal))
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (e: Exception) {
                    activeProcesses.remove(tunnel.id)
                    updateStatus(tunnel.id, TunnelStatus.ERROR)
                    appendLog(tunnel.id, "[Error] ${e.message}")
                }
            }

        } catch (e: Exception) {
            log.error("Failed to start SSH process", e)
            updateStatus(tunnel.id, TunnelStatus.ERROR)
            appendLog(tunnel.id, "[Error] ${e.message}")
        }
    }

    @Synchronized
    fun stopTunnel(tunnelId: String) {
        val process = activeProcesses.remove(tunnelId)
        updateStatus(tunnelId, TunnelStatus.STOPPED)

        if (process != null && process.isAlive) {
            appendLog(tunnelId, CofferTunnelBundle.message("log.stopping"))
            try {
                process.destroy()
                val terminated = process.waitFor(2, TimeUnit.SECONDS)
                if (!terminated) {
                    process.destroyForcibly()
                }
                appendLog(tunnelId, CofferTunnelBundle.message("log.stop.success"))
            } catch (e: Exception) {
                appendLog(tunnelId, "[Error] ${e.message}")
            }
        } else {
            appendLog(tunnelId, CofferTunnelBundle.message("log.already.stopped"))
        }
    }

    @Synchronized
    fun stopAll() {
        if (activeProcesses.isEmpty()) return
        log.info("CofferTunnel: Safely cleaning up running SSH child processes...")

        val currentRunning = HashMap(activeProcesses)
        activeProcesses.clear()

        currentRunning.forEach { (tunnelId, process) ->
            updateStatus(tunnelId, TunnelStatus.STOPPED)
            appendLog(tunnelId, CofferTunnelBundle.message("log.lifecycle.cleanup"))
            try {
                if (process.isAlive) {
                    process.destroy()
                    if (!process.waitFor(1, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to destroy process: $tunnelId", e)
            }
        }
    }

    private fun appendLog(tunnelId: String, line: String) {
        val timestamp = timeFormat.format(Date())
        val formattedLine = "[$timestamp] $line"

        val buffer = logBuffers.computeIfAbsent(tunnelId) { StringBuilder() }
        synchronized(buffer) {
            if (buffer.length > 200_000) {
                buffer.delete(0, 50_000)
            }
            buffer.append(formattedLine).append("\n")
        }

        logListeners.forEach { it.invoke(tunnelId, formattedLine) }
    }

    private fun updateStatus(tunnelId: String, status: TunnelStatus) {
        tunnelStatuses[tunnelId] = status
        statusListeners.forEach { it.invoke(tunnelId, status) }
    }

    fun addStatusListener(listener: (tunnelId: String, status: TunnelStatus) -> Unit) {
        statusListeners.add(listener)
    }

    fun removeStatusListener(listener: (tunnelId: String, status: TunnelStatus) -> Unit) {
        statusListeners.remove(listener)
    }

    fun addLogListener(listener: (tunnelId: String, line: String) -> Unit) {
        logListeners.add(listener)
    }

    fun removeLogListener(listener: (tunnelId: String, line: String) -> Unit) {
        logListeners.remove(listener)
    }

    private fun determineCharset(): Charset {
        val osName = System.getProperty("os.name", "").lowercase()
        return if (osName.contains("win")) {
            try {
                Charset.forName(System.getProperty("sun.jnu.encoding", "GBK"))
            } catch (_: Exception) {
                StandardCharsets.UTF_8
            }
        } else {
            StandardCharsets.UTF_8
        }
    }

    override fun dispose() {
        stopAll()
        executor.shutdownNow()
    }

    companion object {
        fun getInstance(): TunnelProcessService {
            return ApplicationManager.getApplication().getService(TunnelProcessService::class.java)
        }
    }
}
