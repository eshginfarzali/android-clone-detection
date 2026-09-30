package dev.clonedetection.guard.expo

import android.content.Context
import expo.modules.core.interfaces.Package
import expo.modules.core.interfaces.ReactActivityLifecycleListener

/** Discovered by Expo autolinking; hooks detection into the main activity's onCreate. */
class CloneGuardPackage : Package {
    override fun createReactActivityLifecycleListeners(activityContext: Context): List<ReactActivityLifecycleListener> =
        listOf(CloneGuardActivityListener())
}
