package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserSettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("user_settings_prefs", Context.MODE_PRIVATE)

    private val _userAge = MutableStateFlow<Int?>(getSavedAge())
    val userAge: StateFlow<Int?> = _userAge.asStateFlow()

    private val _targetWeightKg = MutableStateFlow<Float?>(getSavedTargetWeight())
    val targetWeightKg: StateFlow<Float?> = _targetWeightKg.asStateFlow()

    private val _weeklyReminderEnabled = MutableStateFlow(getSavedReminder())
    val weeklyReminderEnabled: StateFlow<Boolean> = _weeklyReminderEnabled.asStateFlow()

    private fun getSavedAge(): Int? {
        val age = prefs.getInt(KEY_USER_AGE, -1)
        return if (age in 14..120) age else null
    }

    private fun getSavedTargetWeight(): Float? {
        val weight = prefs.getFloat(KEY_TARGET_WEIGHT, -1f)
        return if (weight in 20f..350f) weight else null
    }

    private fun getSavedReminder(): Boolean {
        return prefs.getBoolean(KEY_WEEKLY_REMINDER, false)
    }

    fun setAge(age: Int?) {
        if (age != null && age in 14..120) {
            prefs.edit().putInt(KEY_USER_AGE, age).apply()
            _userAge.value = age
        } else if (age == null) {
            prefs.edit().remove(KEY_USER_AGE).apply()
            _userAge.value = null
        }
    }

    fun setTargetWeight(targetWeight: Float?) {
        if (targetWeight != null && targetWeight in 20f..350f) {
            prefs.edit().putFloat(KEY_TARGET_WEIGHT, targetWeight).apply()
            _targetWeightKg.value = targetWeight
        } else if (targetWeight == null) {
            prefs.edit().remove(KEY_TARGET_WEIGHT).apply()
            _targetWeightKg.value = null
        }
    }

    fun setWeeklyReminder(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEEKLY_REMINDER, enabled).apply()
        _weeklyReminderEnabled.value = enabled
    }

    companion object {
        private const val KEY_USER_AGE = "key_user_age"
        private const val KEY_TARGET_WEIGHT = "key_target_weight"
        private const val KEY_WEEKLY_REMINDER = "key_weekly_reminder"
    }
}
