package com.example.typuscontrolmini

import android.app.Dialog
import android.content.Intent
import android.net.Uri
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
                "TypusControl Mini requiere activar el Servicio de Accesibilidad para funcionar como una herramienta de control remoto.:\n\n" +
                        "• Inyectar gestos: Permitir que los comandos recibidos desde tu PC \n (clics, deslizamientos) se ejecuten en la pantalla de este dispositivo.\n" +
                        "• Leer la interfaz de la pantalla\n" +
                        "• Monitoreo: Detectar cambios en la \n interfaz para mantener la sincronización con el cliente de escritorio\n\n" +
                        "\uD83D\uDEE1\uFE0F Tu Privacidad: \n Este servicio NO se utiliza para recopilar datos personales, contraseñas, \n ni información bancaria. Todos los datos se procesan localmente \n para la transmisión en tiempo real y no se almacenan externamente."
            )
            .setPositiveButton("Abrir Configuración") { _, _ ->
                AccessibilityUtils.openAccessibilitySettings(requireContext())
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                // Opcional: cerrar la app o deshabilitar funciones
            }
            .setNeutralButton("GitHub Repo") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AmaiDonatsu/TypusControlMini.git"))
                startActivity(intent)
            }
            .setCancelable(false)
            .create()
    }
}