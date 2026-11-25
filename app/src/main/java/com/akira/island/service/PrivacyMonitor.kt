package com.akira.island.service

import android.app.AppOpsManager
import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.akira.island.core.IslandBus
import com.akira.island.core.PrivacySensor
import com.akira.island.core.PrivacyUsage
import com.akira.island.data.SettingsRepo

/**
 * Observes camera, microphone and location usage so the Dynamic Island can show
 * privacy indicators similar to iPhone and Android 12+ status-bar dots.
 *
 * Camera: CameraManager.AvailabilityCallback (package inferred from foreground app).
 * Microphone: AudioManager.AudioRecordingCallback (package via getClientPackageName on API 29+).
 * Location: AppOpsManager.OnOpActiveChangedListener when available; otherwise onLocationHint().
 */
class PrivacyMonitor(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val pm = context.packageManager

    private var active = false
    private val cameraUsers = mutableSetOf<String>()
    private val micUsers = mutableSetOf<String>()
    private val locationUsers = mutableSetOf<String>()

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            main.post {
                cameraUsers.remove(cameraId)
                publish()
            }
        }

        override fun onCameraUnavailable(cameraId: String) {
            main.post {
                cameraUsers.add(cameraId)
                publish()
            }
        }
    }

    private val audioCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            main.post {
                micUsers.clear()
                for (cfg in configs) {
                    val pkg = clientPackageOf(cfg)
                    if (!pkg.isNullOrBlank()) micUsers.add(pkg)
                    else micUsers.add("unknown")
                }
                publish()
            }
        }
    }

    private val locationOpListener = AppOpsManager.OnOpActiveChangedListener { op, _, packageName, activeOp ->
        if (packageName.isNullOrBlank()) return@OnOpActiveChangedListener
        if (op != AppOpsManager.OPSTR_FINE_LOCATION && op != AppOpsManager.OPSTR_COARSE_LOCATION) {
            return@OnOpActiveChangedListener
        }
        main.post {
            if (activeOp) locationUsers.add(packageName)
            else locationUsers.remove(packageName)
            publish()
        }
    }

    fun start() {
        if (active) return
        active = true
        runCatching { cameraManager?.registerAvailabilityCallback(cameraCallback, main) }
        runCatching { audioManager?.registerAudioRecordingCallback(audioCallback, main) }
        runCatching {
            micUsers.clear()
            for (cfg in audioManager?.activeRecordingConfigurations.orEmpty()) {
                val pkg = clientPackageOf(cfg)
                if (!pkg.isNullOrBlank()) micUsers.add(pkg) else micUsers.add("unknown")
            }
        }
        // startWatchingActive / stopWatchingActive require API 30 (lint NewApi vs minSdk 29).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                appOps?.startWatchingActive(
                    arrayOf(AppOpsManager.OPSTR_FINE_LOCATION, AppOpsManager.OPSTR_COARSE_LOCATION),
                    { runnable -> main.post(runnable) },
                    locationOpListener,
                )
            }
        }
        publish()
    }

    fun stop() {
        if (!active) return
        active = false
        runCatching { cameraManager?.unregisterAvailabilityCallback(cameraCallback) }
        runCatching { audioManager?.unregisterAudioRecordingCallback(audioCallback) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { appOps?.stopWatchingActive(locationOpListener) }
        }
        cameraUsers.clear()
        micUsers.clear()
        locationUsers.clear()
        IslandBus.privacy.value = emptyList()
    }

    /** Hint from accessibility / other sources when AppOps is unavailable. */
    fun onLocationHint(packageName: String?, activeHint: Boolean) {
        if (!this.active || packageName.isNullOrBlank()) return
        if (activeHint) locationUsers.add(packageName) else locationUsers.remove(packageName)
        publish()
    }

    private fun publish() {
        val s = SettingsRepo.current
        val list = mutableListOf<PrivacyUsage>()
        val now = SystemClock.elapsedRealtime()

        if (s.privacyCamera && cameraUsers.isNotEmpty()) {
            val pkg = IslandBus.foregroundPackage.value
            list += PrivacyUsage(
                sensor = PrivacySensor.CAMERA,
                packageName = pkg,
                appLabel = labelOf(pkg),
                startedAt = now,
            )
        }
        if (s.privacyMic) {
            for (pkg in micUsers) {
                val realPkg = pkg.takeIf { it != "unknown" }
                list += PrivacyUsage(
                    sensor = PrivacySensor.MICROPHONE,
                    packageName = realPkg,
                    appLabel = labelOf(realPkg),
                    startedAt = now,
                )
            }
        }
        if (s.privacyLocation) {
            for (pkg in locationUsers) {
                list += PrivacyUsage(
                    sensor = PrivacySensor.LOCATION,
                    packageName = pkg,
                    appLabel = labelOf(pkg),
                    startedAt = now,
                )
            }
        }

        IslandBus.privacy.value = list
    }

    private fun labelOf(pkg: String?): String? {
        if (pkg.isNullOrBlank()) return null
        return runCatching {
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        }.getOrNull()
    }

    /** Package of the app that owns this recording config (API 29+). */
    private fun clientPackageOf(cfg: AudioRecordingConfiguration): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val method = AudioRecordingConfiguration::class.java.getMethod("getClientPackageName")
            method.invoke(cfg) as? String
        }.getOrNull()
    }
}
