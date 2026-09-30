import { requireOptionalNativeModule } from "expo-modules-core";

/**
 * K1 launch stack trace · K2 secondary user · K3/K4 UID ≠ package
 * K5 data directory · K6 APK path · K7 hooked UID · K8 signature
 * K9 data directory owner, libc vs raw syscall
 */
export type CloneReason = "K1" | "K2" | "K3" | "K4" | "K5" | "K6" | "K7" | "K8" | "K9";

export interface CloneStatus {
  /** null when no clone was detected */
  cloneReason: CloneReason | null;
  isCloned: boolean;
  /** Emulator hints. Report them; never block on them. */
  emulatorSignals: string[];
  isEmulator: boolean;
  uid: number;
  /** true when uid was read from the kernel */
  nativeUid: boolean;
  /** Data directory owner via libc stat(); -1 = unreadable */
  dataDirOwnerUid: number;
  /** Same value via a raw syscall */
  dataDirOwnerUidRaw: number;
  signatureSha256: string | null;
}

interface CloneGuardNativeModule {
  getStatus(): CloneStatus | null;
}

// null on iOS, web, Expo Go, or a build without the native module.
const nativeModule = requireOptionalNativeModule<CloneGuardNativeModule>("CloneGuard");

/**
 * The result computed when the main activity was created, before JavaScript loaded.
 * Returns null where the check does not apply (iOS, web, Expo Go).
 */
export function getCloneStatus(): CloneStatus | null {
  try {
    return nativeModule?.getStatus() ?? null;
  } catch {
    return null;
  }
}
