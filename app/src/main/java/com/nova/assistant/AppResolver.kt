package com.nova.assistant

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import java.util.Locale

data class LaunchResult(
    val launched: Boolean,
    val message: String,
)

object AppResolver {
    private val knownPackages = mapOf(
        "youtube" to listOf("com.google.android.youtube"),
        "chrome" to listOf("com.android.chrome", "com.chrome.beta"),
        "freefire" to listOf("com.dts.freefireth", "com.dts.freefiremax"),
        "camera" to emptyList(),
    )

    fun launch(context: Context, spokenTarget: String): LaunchResult = try {
        launchInternal(context, spokenTarget)
    } catch (_: ActivityNotFoundException) {
        LaunchResult(false, "Android could not open ${spokenTarget.trim()}")
    } catch (_: SecurityException) {
        LaunchResult(false, "Android blocked opening ${spokenTarget.trim()}")
    }

    private fun launchInternal(context: Context, spokenTarget: String): LaunchResult {
        val target = normalize(spokenTarget)

        if (target == "camera" || target.contains("camera")) {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return if (cameraIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(cameraIntent)
                LaunchResult(true, "Opening Camera")
            } else {
                LaunchResult(false, "No camera app is installed")
            }
        }

        val packageCandidates = knownPackages[target].orEmpty()
        packageCandidates.forEach { packageName ->
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return LaunchResult(true, "Opening ${spokenTarget.trim()}")
            }
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = context.packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.MATCH_ALL,
        )
        val match = activities.firstOrNull { info ->
            val label = info.loadLabel(context.packageManager).toString()
            normalize(label) == target ||
                normalize(label).contains(target) ||
                target.contains(normalize(label))
        }

        return if (match != null) {
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(match.activityInfo.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)
            LaunchResult(true, "Opening ${match.loadLabel(context.packageManager)}")
        } else {
            LaunchResult(false, "I couldn't find an installed app called ${spokenTarget.trim()}")
        }
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9]"), "")
}