package tech.coffers.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.coffers.tunnel.config.CofferTunnelSettingsService
import tech.coffers.tunnel.model.SshProfile
import tech.coffers.tunnel.model.TunnelConfig
import tech.coffers.tunnel.model.TunnelStatus
import tech.coffers.tunnel.service.TunnelConnectivityTester
import tech.coffers.tunnel.service.TunnelPortValidator
import tech.coffers.tunnel.ui.TunnelDialog

class TunnelModelAndValidationTest {

    @Test
    fun testLanguageSwitch() {
        try {
            // 测试强制英文
            CofferTunnelBundle.overrideLanguageForTest = "EN"
            assertEquals("Running", CofferTunnelBundle.message("status.running"))
            assertEquals("New Jump Profile", CofferTunnelBundle.message("dialog.profile.new.title"))
            assertEquals("Jump Host Connection Parameters", CofferTunnelBundle.message("dialog.profile.border"))
            assertEquals("Test Connection", CofferTunnelBundle.message("action.test.connection"))

            // 测试强制中文
            CofferTunnelBundle.overrideLanguageForTest = "ZH"
            assertEquals("运行中", CofferTunnelBundle.message("status.running"))
            assertEquals("新建跳板档案", CofferTunnelBundle.message("dialog.profile.new.title"))
            assertEquals("跳板连接参数", CofferTunnelBundle.message("dialog.profile.border"))
            assertEquals("测试连通性", CofferTunnelBundle.message("action.test.connection"))
        } finally {
            CofferTunnelBundle.overrideLanguageForTest = null
        }
    }

    @Test
    fun testPortNumberFormattingWithoutCommas() {
        try {
            CofferTunnelBundle.overrideLanguageForTest = "ZH"
            val logZh = CofferTunnelBundle.message("log.established", 15432, "root@jump", "172.16.32.22", 5432)
            assertTrue(logZh.contains("127.0.0.1:15432"))
            assertTrue(logZh.contains("172.16.32.22:5432"))
            assertFalse(logZh.contains("15,432"))
            assertFalse(logZh.contains("5,432"))

            CofferTunnelBundle.overrideLanguageForTest = "EN"
            val logEn = CofferTunnelBundle.message("log.established", 15432, "root@jump", "172.16.32.22", 5432)
            assertTrue(logEn.contains("127.0.0.1:15432"))
            assertTrue(logEn.contains("172.16.32.22:5432"))
            assertFalse(logEn.contains("15,432"))
            assertFalse(logEn.contains("5,432"))
        } finally {
            CofferTunnelBundle.overrideLanguageForTest = null
        }
    }

    @Test
    fun testSshProfile() {
        val profile = SshProfile(
            name = "生产跳板",
            jumpHost = "jump.prod.com",
            sshPort = 22,
            username = "root"
        )
        assertNull(profile.validate())

        val invalid = profile.copy(jumpHost = "")
        assertNotNull(invalid.validate())
    }

    @Test
    fun testTunnelValidationWithProfile() {
        val configWithProfile = TunnelConfig(
            name = "订单库隧道",
            profileId = "profile-uuid-1234",
            localPort = 15432,
            remoteHost = "10.0.0.5",
            remotePort = 5432
        )
        assertNull(configWithProfile.validate(hasProfile = true))

        val legacyWithoutHost = TunnelConfig(
            name = "订单库隧道",
            profileId = "",
            jumpHost = "",
            localPort = 15432,
            remoteHost = "10.0.0.5",
            remotePort = 5432
        )
        assertNotNull(legacyWithoutHost.validate(hasProfile = false))
    }

    @Test
    fun testTargetTypeDefaults() {
        val mysql = TunnelDialog.TARGET_TYPES.find { it.key == "type.mysql" }
        val pg = TunnelDialog.TARGET_TYPES.find { it.key == "type.pg" }
        val redis = TunnelDialog.TARGET_TYPES.find { it.key == "type.redis" }
        val mongo = TunnelDialog.TARGET_TYPES.find { it.key == "type.mongo" }
        val custom = TunnelDialog.TARGET_TYPES.find { it.key == "type.custom" }

        assertEquals(3306, mysql?.defaultPort)
        assertEquals(5432, pg?.defaultPort)
        assertEquals(6379, redis?.defaultPort)
        assertEquals(27017, mongo?.defaultPort)
        assertNull(custom?.defaultPort)
    }

    @Test
    fun testDuplicateConfig() {
        val original = TunnelConfig(
            name = "生产数据库",
            targetType = "type.pg",
            profileId = "profile-uuid-1",
            localPort = 3306,
            remoteHost = "10.0.1.10",
            remotePort = 5432
        )
        val copy = original.duplicate()
        assertTrue(copy.name.contains("生产数据库"))
        assertEquals("type.pg", copy.targetType)
        assertEquals("profile-uuid-1", copy.profileId)
        assertTrue(copy.id != original.id)
    }

    @Test
    fun testTunnelStatus() {
        assertTrue(TunnelStatus.RUNNING.isAlive)
        assertTrue(TunnelStatus.STARTING.isAlive)
        assertEquals(false, TunnelStatus.STOPPED.isAlive)
        assertEquals(false, TunnelStatus.ERROR.isAlive)
    }

    @Test
    fun testPortValidator() {
        assertEquals(false, TunnelPortValidator.isPortAvailable(0))
        assertEquals(false, TunnelPortValidator.isPortAvailable(70000))
    }

    @Test
    fun testConnectivityTesterValidation() {
        val blankHostResult = TunnelConnectivityTester.testConnection("", 22)
        assertFalse(blankHostResult.success)

        val invalidPortResult = TunnelConnectivityTester.testConnection("127.0.0.1", -1)
        assertFalse(invalidPortResult.success)
    }
}
