// Kernel-facing probes for clone detection.
//
// App cloners (virtual containers such as MultiBox or Clone App) run the target
// APK inside their own process and intercept what it asks the OS. Measured on a
// Samsung SM-A536E, the Java layer is fully spoofed (PackageManager, dataDir,
// process name, Process.myUid()) and even libc `stat()` is hooked to point at the
// container's own directory.
//
// A hook lives in userspace. The kernel does not. So every value that matters is
// read twice: once through libc, which can be hooked, and once through a raw
// syscall(), which goes straight to the kernel. Disagreement between the two is
// the signal.

#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/syscall.h>

#if defined(__aarch64__) || defined(__x86_64__)
#define STAT_SYSCALL __NR_newfstatat
#else
#define STAT_SYSCALL __NR_fstatat64
#endif

JNIEXPORT jint JNICALL
Java_dev_clonedetection_guard_NativeProbe_getUid(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    return (jint) getuid();
}

// Owner UID of `path` via libc stat(). Hookable; kept as the comparison baseline.
JNIEXPORT jint JNICALL
Java_dev_clonedetection_guard_NativeProbe_getPathOwnerUid(JNIEnv *env, jobject thiz, jstring path) {
    (void) thiz;
    const char *native_path = (*env)->GetStringUTFChars(env, path, NULL);
    if (native_path == NULL) {
        return -1;
    }

    struct stat info;
    const int failed = stat(native_path, &info);
    (*env)->ReleaseStringUTFChars(env, path, native_path);

    return failed ? -1 : (jint) info.st_uid;
}

// Same value through a raw syscall, bypassing the libc `stat` symbol entirely.
JNIEXPORT jint JNICALL
Java_dev_clonedetection_guard_NativeProbe_getPathOwnerUidRaw(JNIEnv *env, jobject thiz, jstring path) {
    (void) thiz;
    const char *native_path = (*env)->GetStringUTFChars(env, path, NULL);
    if (native_path == NULL) {
        return -1;
    }

    struct stat info;
    const long result = syscall(STAT_SYSCALL, AT_FDCWD, native_path, &info, 0);
    (*env)->ReleaseStringUTFChars(env, path, native_path);

    return (result != 0) ? -1 : (jint) info.st_uid;
}
