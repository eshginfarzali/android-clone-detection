package dev.clonedetection.guard

/**
 * Pure decision rules behind the reason codes. No Android APIs here, so every
 * rule — including the measured device results — is covered by plain JUnit tests.
 */
internal object CloneRules {

    /** K1: the launch path must be Activity → (Instrumentation) → ActivityThread. */
    fun foreignLauncher(frames: List<String>): Boolean {
        val activityFrame = frames.indexOfLast { it == "android.app.Activity" }
        if (activityFrame < 0) return true
        val launcher = frames.drop(activityFrame + 1).firstOrNull { it != "android.app.Instrumentation" }
        return launcher?.startsWith("android.app.ActivityThread") != true
    }

    /**
     * K9: owner of our data directory, via the kernel ([rawOwner]) and via libc
     * ([libcOwner]); -1 means unreadable.
     *
     *   genuine    → uid=10763  libc=10763  raw=10763   clean
     *   MultiBox   → uid=10764  libc=-1     raw=-1      K9
     *   Clone App  → uid=10765  libc=10765  raw=-1      K9
     */
    fun dataDirOwnerMismatch(uid: Int, rawOwner: Int, libcOwner: Int, sourceDir: String): Boolean {
        // Someone else owns it → we are not the process we claim to be.
        if (rawOwner >= 0 && rawOwner != uid) return true
        // libc answers, the kernel refuses → libc is being redirected.
        if (rawOwner < 0 && libcOwner >= 0) return true
        // Neither can read it. A genuine app always owns its directory, except when
        // moved to adoptable storage.
        return rawOwner < 0 && libcOwner < 0 && !sourceDir.startsWith("/mnt/expand")
    }

    /** K2: Dual App, Second Space and work profiles run as a secondary Android user. */
    fun secondaryUser(uid: Int): Boolean = uid / 100000 != 0

    /** K5: the data directory must be our own, not nested inside a host app. */
    fun foreignDataDir(dataDir: String, packageName: String): Boolean {
        val pattern = Regex("^/(data/data|data/user/0|mnt/expand/[^/]+/user/0)/${Regex.escape(packageName)}/?$")
        return !pattern.matches(dataDir)
    }

    /** K6: a genuine install maps its own base.apk from /data/app/…/<pkg>-…/. */
    fun ownApkMapped(mapsLines: Sequence<String>, packageName: String): Boolean =
        mapsLines.any { it.endsWith("/base.apk") && it.contains("/data/app/") && it.contains("/$packageName-") }

    /** K8: opt-in; an empty accepted set disables the check. */
    fun unexpectedSignature(signature: String?, accepted: Set<String>): Boolean =
        accepted.isNotEmpty() && signature != null && signature.lowercase() !in accepted
}
