package com.example.app

import android.app.AlertDialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.clonedetection.guard.CloneDetector

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Capture the stack here: K1 inspects who launched this Activity.
        val frames = Throwable().stackTrace.map { it.className }
        val result = CloneDetector.evaluate(
            context = this,
            frames = frames,
            // Both keys, or Play installs get blocked. See "Signature check" in the README.
            acceptedSignatures = emptySet()
        )

        super.onCreate(savedInstanceState)

        val reason = result.cloneReason
        if (reason != null) {
            AlertDialog.Builder(this)
                .setTitle("Unsupported environment")
                .setMessage("This app cannot run inside a cloned or virtual environment. ($reason)")
                .setCancelable(false)
                .setPositiveButton("Close") { _, _ -> finishAffinity() }
                .show()
            return
        }

        // Later, attach CloneDetector.lastResult to sensitive requests for server-side audit.
    }
}
