package com.example.device

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

enum class HapticEvent {
    DICE_ROLL,
    CRIT_SUCCESS,
    DAMAGE_OR_WARN,
    METER_CHANGE,
    TAG_INSPECT
}

object HapticFeedbackEngine {
    fun trigger(view: View?, enabled: Boolean, event: HapticEvent) {
        if (!enabled || view == null) return
        runCatching {
            val constant = when (event) {
                HapticEvent.CRIT_SUCCESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
                HapticEvent.DAMAGE_OR_WARN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
                HapticEvent.DICE_ROLL -> HapticFeedbackConstants.LONG_PRESS
                HapticEvent.METER_CHANGE -> HapticFeedbackConstants.CONTEXT_CLICK
                HapticEvent.TAG_INSPECT -> HapticFeedbackConstants.KEYBOARD_TAP
            }
            view.performHapticFeedback(constant)
        }
    }
}
