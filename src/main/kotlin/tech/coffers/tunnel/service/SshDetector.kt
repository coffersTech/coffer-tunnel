package tech.coffers.tunnel.service

import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 跨平台 OpenSSH 客户端探测器
 */
object SshDetector {

    /**
     * 自动探测本机可用的 ssh 可执行文件路径
     */
    fun findSshExecutable(): String? {
        val candidates = mutableListOf<String>()

        if (SystemInfo.isWindows) {
            // Windows 常见自带 OpenSSH 路径
            candidates.add("C:\\Windows\\System32\\OpenSSH\\ssh.exe")
            candidates.add("C:\\Program Files\\Git\\usr\\bin\\ssh.exe")
            candidates.add("ssh.exe")
            candidates.add("ssh")
        } else {
            // macOS 与 Linux 常见路径
            candidates.add("/usr/bin/ssh")
            candidates.add("/usr/local/bin/ssh")
            candidates.add("/opt/homebrew/bin/ssh")
            candidates.add("ssh")
        }

        for (candidate in candidates) {
            if (candidate.contains(File.separator) || candidate.contains("/")) {
                val file = File(candidate)
                if (file.exists() && file.canExecute()) {
                    return file.absolutePath
                }
            } else {
                // 探测系统 PATH 环境变量中的命令
                if (testExecutable(candidate)) {
                    return candidate
                }
            }
        }

        // 回退默认命令
        return if (testExecutable("ssh")) "ssh" else null
    }

    /**
     * 测试命令是否在当前系统环境下有效可用
     */
    fun testExecutable(command: String): Boolean {
        return try {
            val process = ProcessBuilder(command, "-V").start()
            val finished = process.waitFor(2, TimeUnit.SECONDS)
            if (finished) {
                // ssh -V 无论 exitCode 是 0 还是非 0，只要能拉起并输出版本信息即为有效
                true
            } else {
                process.destroyForcibly()
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
