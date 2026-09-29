package dev.clonedetection.guard

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.io.File
import java.security.MessageDigest

/**
 * Single source of truth for clone and emulator signals.
 *
 * Call [evaluate] once from `Activity.onCreate`. Block on [Result.cloneReason]
 * locally, and ship the whole [Result] to your backend for audit — blocking is a
 * client decision, evidence belongs on the server.
 *
 * Check order is based on measurements (SM-A536E, three environments):
 * a virtual container spoofs the data path, process name, `Process.myUid()` and
 * `applicationInfo.uid`, so the cheap Java checks pass and the kernel checks catch it.
 */
object CloneDetector {

    data class Result(
        /** Reason code (K1…K9) if a clone was detected, otherwise null. */
        val cloneReason: String?,
        /** Emulator hints. Never block on these; report them only. */
        val emulatorSignals: List<String>,
        val uid: Int,
        /** True when [uid] came from the kernel, false when from `Process.myUid()`. */
        val nativeUid: Boolean,
        /** Data directory owner via libc `stat()`; -1 = unreadable. */
        val dataDirOwnerUid: Int,
        /** Same value via a raw syscall — what K9 relies on. */
        val dataDirOwnerUidRaw: Int,
        val signatureSha256: String?
    )

    @Volatile
    var lastResult: Result? = null
        private set

    /**
     * @param frames class names from the calling Activity's `Throwable().stackTrace`,
     *        needed by K1 — so this must be called from `onCreate`.
     * @param acceptedSignatures SHA-256 of every signing certificate your users can
     *        legitimately have (upload key AND Play App Signing key). Empty disables K8.
     */
    fun evaluate(
        context: Context,
        frames: List<String>,
        acceptedSignatures: Set<String> = emptySet()
    ): Result {
        val uid = if (NativeProbe.isAvailable) NativeProbe.getUid() else Process.myUid()
        val dataDir = dataDirPath(context, uid)
        val signature = signatureSha256(context)
        val result = Result(
            cloneReason = cloneReason(context, frames, uid, signature, acceptedSignatures),
            emulatorSignals = emulatorSignals(),
            uid = uid,
            nativeUid = NativeProbe.isAvailable,
            dataDirOwnerUid = if (NativeProbe.isAvailable) NativeProbe.getPathOwnerUid(dataDir) else -1,
            dataDirOwnerUidRaw = if (NativeProbe.isAvailable) NativeProbe.getPathOwnerUidRaw(dataDir) else -1,
            signatureSha256 = signature
        )
        lastResult = result
        return result
    }

    private fun cloneReason(
        context: Context,
        frames: List<String>,
        uid: Int,
        signature: String?,
        acceptedSignatures: Set<String>
    ): String? {
        val packageName = context.packageName
        val applicationInfo = context.applicationInfo

        // K1: Virtual engines launch the activity through their own Instrumentation,
        // so a foreign class sits between Activity.performCreate and ActivityThread.
        val activityFrame = frames.indexOfLast { it == "android.app.Activity" }
        val launcher = frames.drop(activityFrame + 1).firstOrNull { it != "android.app.Instrumentation" }
        if (activityFrame < 0 || launcher?.startsWith("android.app.ActivityThread") != true) return "K1"

        // K9: Owner of our data directory, read through libc and through a raw syscall.
        //
        //   genuine    → uid=10763  libc=10763  raw=10763
        //   MultiBox   → uid=10764  libc=-1     raw=-1
        //   Clone App  → uid=10765  libc=10765  raw=-1
        //
        // In a clone, libc either points at the container's directory or stays silent,
        // while the syscall reaches the REAL directory and SELinux denies it because
        // the cloned process does not own it. A genuine install reads it both ways.
        //
        // The `/system` probe is an ABI guard: if raw syscalls do not work on this
        // platform at all, K9 is skipped rather than blocking everyone.
        if (NativeProbe.isAvailable && NativeProbe.getPathOwnerUidRaw("/system") >= 0) {
            val dataDir = dataDirPath(context, uid)
            val rawOwner = NativeProbe.getPathOwnerUidRaw(dataDir)
            val libcOwner = NativeProbe.getPathOwnerUid(dataDir)

            // Someone else owns it → we are not the process we claim to be.
            if (rawOwner >= 0 && rawOwner != uid) return "K9"
            // libc answers, the kernel refuses → libc is being redirected.
            if (rawOwner < 0 && libcOwner >= 0) return "K9"
            // Neither can read it. A genuine app always owns its directory; the one
            // exception is an app moved to adoptable storage, told apart by sourceDir.
            if (rawOwner < 0 && libcOwner < 0 &&
                !applicationInfo.sourceDir.startsWith("/mnt/expand")
            ) {
                return "K9"
            }
        }

        // K7: Kernel UID differs from what Java reports → Process.myUid() is hooked.
        if (NativeProbe.isAvailable && uid != Process.myUid()) return "K7"

        // K2: Not the primary user (Dual App, Second Space, work profile).
        if (uid / 100000 != 0) return "K2"
        // K3/K4: Inside a container the process runs under the host app's UID.
        if (applicationInfo.uid != uid) return "K3"
        if (context.packageManager.getPackagesForUid(uid)?.singleOrNull() != packageName) return "K4"
        // K5: Data directory lives inside the host app (e.g. /data/data/<host>/virtual/...).
        val dataDirPattern = Regex("^/(data/data|data/user/0|mnt/expand/[^/]+/user/0)/${Regex.escape(packageName)}/?$")
        if (!dataDirPattern.matches(applicationInfo.dataDir)) return "K5"
        // K6: A genuine install maps our own base.apk from /data/app; a clone maps it
        // from the host's data directory.
        val ownApkMapped = try {
            File("/proc/self/maps").useLines { lines ->
                lines.any { it.endsWith("/base.apk") && it.contains("/data/app/") && it.contains("/$packageName-") }
            }
        } catch (error: Exception) {
            true
        }
        if (!ownApkMapped) return "K6"

        // K8: Repackaged — re-signed with the cloner's key.
        if (acceptedSignatures.isNotEmpty() && signature != null &&
            signature.lowercase() !in acceptedSignatures
        ) {
            return "K8"
        }

        return null
    }

    private fun dataDirPath(context: Context, uid: Int): String =
        "/data/user/${uid / 100000}/${context.packageName}"

    /**
     * Emulator hints. These are userspace values a rooted device can fake, so they
     * never block — the point is to see casual use on the server, not to prove it.
     */
    private fun emulatorSignals(): List<String> {
        val signals = mutableListOf<String>()

        // Measured: real SM-A536E → "s5e8825", emulator → "ranchu"
        if (Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")) {
            signals.add("hardware:${Build.HARDWARE}")
        }
        if (Build.TAGS?.contains("keys") == true && !Build.TAGS.contains("release-keys")) {
            signals.add("tags:${Build.TAGS}")
        }
        if (Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("sdk_gphone") ||
            Build.FINGERPRINT.contains("/sdk")
        ) {
            signals.add("fingerprint")
        }
        if (Build.PRODUCT.contains("sdk") || Build.PRODUCT.contains("emulator")) {
            signals.add("product:${Build.PRODUCT}")
        }
        if (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) {
            signals.add("brand")
        }

        return signals
    }

    private fun signatureSha256(context: Context): String? = try {
        val pm = context.packageManager
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        }
        bytes?.let { raw ->
            MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
        }
    } catch (error: Throwable) {
        null
    }
}
