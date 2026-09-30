package dev.clonedetection.guard.expo

import dev.clonedetection.guard.CloneDetector
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/**
 * Exposes the result computed at launch to JavaScript. Detection is NOT repeated
 * here — the single source is [CloneDetector], evaluated by [CloneGuardActivityListener].
 */
class CloneGuardModule : Module() {
    override fun definition() = ModuleDefinition {
        Name("CloneGuard")

        // Synchronous: the value was already computed in onCreate.
        Function("getStatus") {
            val result = CloneDetector.lastResult ?: return@Function null

            mapOf(
                "cloneReason" to result.cloneReason,
                "isCloned" to (result.cloneReason != null),
                "emulatorSignals" to result.emulatorSignals,
                "isEmulator" to result.emulatorSignals.isNotEmpty(),
                "uid" to result.uid,
                "nativeUid" to result.nativeUid,
                "dataDirOwnerUid" to result.dataDirOwnerUid,
                "dataDirOwnerUidRaw" to result.dataDirOwnerUidRaw,
                "signatureSha256" to result.signatureSha256
            )
        }
    }
}
