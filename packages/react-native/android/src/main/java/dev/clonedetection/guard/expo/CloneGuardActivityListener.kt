package dev.clonedetection.guard.expo

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import dev.clonedetection.guard.CloneDetector
import expo.modules.core.interfaces.ReactActivityLifecycleListener

/**
 * Runs detection inside the main activity's onCreate, before any JavaScript loads.
 *
 * The listener is invoked synchronously from `Activity.performCreate`, so the stack
 * trace captured here still contains the launch path K1 inspects.
 *
 * Configuration is read from AndroidManifest `<meta-data>`, written by the config
 * plugin (app.plugin.js) or by hand in a bare React Native project:
 *   dev.clonedetection.BLOCK       "true" to close the app on detection (default: report only)
 *   dev.clonedetection.SIGNATURES  comma-separated SHA-256 list; enables K8
 *   dev.clonedetection.MESSAGE     text of the blocking dialog
 */
internal class CloneGuardActivityListener : ReactActivityLifecycleListener {

    override fun onCreate(activity: Activity, savedInstanceState: Bundle?) {
        val config = readConfig(activity)
        val result = CloneDetector.evaluate(
            activity,
            Throwable().stackTrace.map { it.className },
            config.signatures
        )

        val reason = result.cloneReason
        if (config.block && reason != null) {
            AlertDialog.Builder(activity)
                .setMessage("${config.message} ($reason)")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ -> activity.finishAffinity() }
                .show()
        }
    }

    private data class Config(val block: Boolean, val signatures: Set<String>, val message: String)

    private fun readConfig(activity: Activity): Config {
        val metaData = try {
            activity.packageManager
                .getApplicationInfo(activity.packageName, PackageManager.GET_META_DATA)
                .metaData
        } catch (error: Exception) {
            null
        }

        return Config(
            block = metaData?.get("dev.clonedetection.BLOCK")?.toString() == "true",
            signatures = metaData?.getString("dev.clonedetection.SIGNATURES")
                ?.split(",")
                ?.map { it.trim().lowercase() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet(),
            message = metaData?.getString("dev.clonedetection.MESSAGE")
                ?: "This app cannot run inside a cloned or virtual environment."
        )
    }
}
