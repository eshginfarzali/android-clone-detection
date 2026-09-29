package dev.clonedetection.guard

/**
 * Values read from the kernel. If the native library fails to load the app must
 * keep working: callers check [isAvailable] and fall back to Java equivalents.
 */
internal object NativeProbe {
    val isAvailable: Boolean = try {
        System.loadLibrary("cloneguard")
        true
    } catch (error: Throwable) {
        false
    }

    external fun getUid(): Int

    /** Owner UID of [path] via libc `stat()`; -1 if unreadable. */
    external fun getPathOwnerUid(path: String): Int

    /** Same value via a raw syscall, below any libc hook; -1 if unreadable. */
    external fun getPathOwnerUidRaw(path: String): Int
}
