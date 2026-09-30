package dev.clonedetection.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloneRulesTest {

    // --- K9: values measured on a Samsung SM-A536E -------------------------------

    @Test
    fun `K9 genuine install - libc and syscall agree on our uid`() {
        assertFalse(CloneRules.dataDirOwnerMismatch(uid = 10763, rawOwner = 10763, libcOwner = 10763, sourceDir = APP_DIR))
    }

    @Test
    fun `K9 MultiBox - neither path can read our directory`() {
        assertTrue(CloneRules.dataDirOwnerMismatch(uid = 10764, rawOwner = -1, libcOwner = -1, sourceDir = APP_DIR))
    }

    @Test
    fun `K9 Clone App - libc answers but the kernel refuses`() {
        assertTrue(CloneRules.dataDirOwnerMismatch(uid = 10765, rawOwner = -1, libcOwner = 10765, sourceDir = APP_DIR))
    }

    @Test
    fun `K9 kernel reports a different owner`() {
        assertTrue(CloneRules.dataDirOwnerMismatch(uid = 10764, rawOwner = 10763, libcOwner = 10764, sourceDir = APP_DIR))
    }

    @Test
    fun `K9 app on adoptable storage is not flagged when unreadable`() {
        assertFalse(
            CloneRules.dataDirOwnerMismatch(uid = 10763, rawOwner = -1, libcOwner = -1, sourceDir = "/mnt/expand/1234-abcd/app/x/base.apk")
        )
    }

    // --- K1 -----------------------------------------------------------------------

    @Test
    fun `K1 system launch path is clean`() {
        val frames = listOf(
            "com.example.MainActivity",
            "android.app.Activity",
            "android.app.Instrumentation",
            "android.app.ActivityThread",
            "android.app.ActivityThread\$H"
        )
        assertFalse(CloneRules.foreignLauncher(frames))
    }

    @Test
    fun `K1 path through an Expo lifecycle listener is clean`() {
        val frames = listOf(
            "dev.clonedetection.guard.expo.CloneGuardActivityListener",
            "expo.modules.ReactActivityDelegateWrapper",
            "com.facebook.react.ReactActivity",
            "com.example.MainActivity",
            "android.app.Activity",
            "android.app.Instrumentation",
            "android.app.ActivityThread"
        )
        assertFalse(CloneRules.foreignLauncher(frames))
    }

    @Test
    fun `K1 foreign Instrumentation between Activity and ActivityThread is flagged`() {
        val frames = listOf(
            "com.example.MainActivity",
            "android.app.Activity",
            "com.lody.virtual.client.hook.delegate.InstrumentationDelegate",
            "android.app.ActivityThread"
        )
        assertTrue(CloneRules.foreignLauncher(frames))
    }

    @Test
    fun `K1 missing Activity frame is flagged`() {
        assertTrue(CloneRules.foreignLauncher(listOf("com.example.MainActivity", "android.app.ActivityThread")))
    }

    // --- K2 -----------------------------------------------------------------------

    @Test
    fun `K2 primary user is clean, secondary users are flagged`() {
        assertFalse(CloneRules.secondaryUser(10763))
        assertTrue(CloneRules.secondaryUser(95_10763)) // Samsung Dual Messenger, user 95
        assertTrue(CloneRules.secondaryUser(10_10763)) // work profile, user 10
    }

    // --- K5 -----------------------------------------------------------------------

    @Test
    fun `K5 own data directories are clean`() {
        assertFalse(CloneRules.foreignDataDir("/data/user/0/$PKG", PKG))
        assertFalse(CloneRules.foreignDataDir("/data/data/$PKG", PKG))
        assertFalse(CloneRules.foreignDataDir("/mnt/expand/1234-abcd/user/0/$PKG", PKG))
    }

    @Test
    fun `K5 data directory nested in a host app is flagged`() {
        assertTrue(CloneRules.foreignDataDir("/data/data/com.cloner.host/virtual/data/user/0/$PKG", PKG))
    }

    @Test
    fun `K5 package name is matched literally, not as a regex`() {
        assertTrue(CloneRules.foreignDataDir("/data/data/comXexampleXapp", PKG))
    }

    // --- K6 -----------------------------------------------------------------------

    @Test
    fun `K6 own base apk mapped from data app`() {
        val maps = sequenceOf(
            "7f00-7f10 r--p 00000000 fd:05 123 /system/lib64/libc.so",
            "7f20-7f30 r--p 00000000 fd:05 456 /data/app/~~abc==/$PKG-xyz==/base.apk"
        )
        assertTrue(CloneRules.ownApkMapped(maps, PKG))
    }

    @Test
    fun `K6 base apk mapped from a host directory is not ours`() {
        val maps = sequenceOf("7f20-7f30 r--p 00000000 fd:05 456 /data/data/com.cloner.host/virtual/$PKG/base.apk")
        assertFalse(CloneRules.ownApkMapped(maps, PKG))
    }

    // --- K8 -----------------------------------------------------------------------

    @Test
    fun `K8 disabled when no signatures are configured`() {
        assertFalse(CloneRules.unexpectedSignature("deadbeef", emptySet()))
    }

    @Test
    fun `K8 accepts listed signatures case-insensitively and rejects others`() {
        val accepted = setOf("abc123")
        assertFalse(CloneRules.unexpectedSignature("ABC123", accepted))
        assertTrue(CloneRules.unexpectedSignature("fff000", accepted))
    }

    private companion object {
        const val PKG = "com.example.app"
        const val APP_DIR = "/data/app/~~abc==/com.example.app-xyz==/base.apk"
    }
}
