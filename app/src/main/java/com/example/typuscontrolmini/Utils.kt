package com.example.typuscontrolmini

import android.content.Context
import android.content.Intent
import android.provider.Settings

object AccessibilityUtilsDeprecated {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = "${context.packageName}/${DeviceControlService::class.java.canonicalName}"

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        return enabledServices?.contains(service) == true
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}