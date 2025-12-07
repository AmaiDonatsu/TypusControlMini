package com.example.typuscontrolmini

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log

object AccessibilityUtils {
    private const val TAG = "AccessibilityUtils"

    /**
     * Verifica si un servicio de accesibilidad específico está habilitado.
     * @param context Contexto de la aplicación.
     * @param serviceClass Clase del servicio de accesibilidad a verificar. Por defecto DeviceControlService.
     * @return true si el servicio está habilitado, false en caso contrario.
     */
    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*> = DeviceControlService::class.java): Boolean {
        val expectedComponentName = ComponentName(context, serviceClass)
        
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)

            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                Log.d(TAG, "Accessibility Service verificado y activo: $componentNameString")
                return true
            }
        }
        
        Log.w(TAG, "Accessibility Service no encontrado activo. Servicios activos: $enabledServicesSetting")
        return false
    }

    /**
     * Abre la pantalla de configuración de accesibilidad del sistema.
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir configuración de accesibilidad", e)
        }
    }
}
