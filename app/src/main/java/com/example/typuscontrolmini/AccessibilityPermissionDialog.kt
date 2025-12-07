// AccessibilityPermissionDialog.kt (nuevo archivo)
package com.example.typuscontrolmini

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AccessibilityPermissionDialog : DialogFragment() {

    companion object {
        const val TAG = "AccessibilityPermissionDialog"

        fun newInstance(): AccessibilityPermissionDialog {
            return AccessibilityPermissionDialog()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("🔐 Permiso Requerido")
            .setMessage(
                "TypusControl necesita el servicio de accesibilidad para:\n\n" +
                        "• Ejecutar taps y gestos\n" +
                        "• Leer la interfaz de la pantalla\n" +
                        "• Controlar el dispositivo remotamente\n\n" +
                        "Por favor, activa 'DeviceControlService' en la siguiente pantalla."
            )
            .setPositiveButton("Abrir Configuración") { _, _ ->
                AccessibilityUtils.openAccessibilitySettings(requireContext())
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                // Opcional: cerrar la app o deshabilitar funciones
            }
            .setCancelable(false)
            .create()
    }
}