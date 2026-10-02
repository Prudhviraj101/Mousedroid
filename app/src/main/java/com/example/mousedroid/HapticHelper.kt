package com.example.mousedroid

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

object HapticHelper {

    enum class HapticLevel {
        OFF, SOFT, CRISP, HEAVY
    }

    private var vibrator: Vibrator? = null
    var enabled: Boolean = true
    var currentLevel: HapticLevel = HapticLevel.CRISP

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Subtle tick for scroll ticks and slider changes */
    fun tick(view: View? = null) {
        if (!enabled || currentLevel == HapticLevel.OFF) return
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                ?: run {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(8, 40))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(8)
                    }
                }
        } catch (_: Exception) { }
    }

    /** Crisp click for buttons and tap-to-click */
    fun click(view: View? = null) {
        if (!enabled || currentLevel == HapticLevel.OFF) return
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                ?: run {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val amplitude = if (currentLevel == HapticLevel.SOFT) 80 else 160
                        vibrator?.vibrate(VibrationEffect.createOneShot(16, amplitude))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(16)
                    }
                }
        } catch (_: Exception) { }
    }

    /** Heavy / double click for special actions, long press, or right click */
    fun heavyClick(view: View? = null) {
        if (!enabled || currentLevel == HapticLevel.OFF) return
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                ?: run {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val amplitude = if (currentLevel == HapticLevel.SOFT) 120 else 240
                        vibrator?.vibrate(VibrationEffect.createOneShot(32, amplitude))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(32)
                    }
                }
        } catch (_: Exception) { }
    }
}
