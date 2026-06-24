package com.jcadenas.xpendz.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jcadenas.xpendz.ui.components.CompactHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Text(
                        text = "Política de privacidad",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Política de Privacidad de Xpendz",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Última actualización: 15 de abril de 2026",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            PolicySection(
                title = "1. Información que recopilamos",
                content = """
Recopilamos la siguiente información cuando usas Xpendz:

• Información de registro: correo electrónico y UID de autenticación proporcionados por Firebase Authentication.
• Datos financieros: transacciones, cuentas, categorías, presupuestos, metas de ahorro y préstamos que ingresas voluntariamente.
• Configuración de la app: moneda base, país, tasas de cambio personalizadas.
• Datos técnicos: identificador de dispositivo para sincronización, marcas de tiempo de actualización.

No recopilamos información de ubicación GPS, contactos ni archivos multimedia sin tu consentimiento explícito.
                """.trimIndent()
            )

            PolicySection(
                title = "2. Cómo usamos tu información",
                content = """
Utilizamos tus datos para:

• Proporcionar la funcionalidad principal de la app (registro de transacciones, seguimiento de presupuestos, etc.).
• Sincronizar tus datos entre dispositivos mediante Firebase Firestore.
• Generar reportes y estadísticas de tus finanzas personales.
• Respaldar tu información en la nube para recuperación ante pérdida del dispositivo.

No utilizamos tus datos financieros para publicidad ni los vendemos a terceros.
                """.trimIndent()
            )

            PolicySection(
                title = "3. Almacenamiento y seguridad",
                content = """
• Los datos se almacenan localmente en tu dispositivo usando SQLite (Room).
• Los datos se sincronizan con Firebase Firestore para respaldo en la nube.
• La autenticación está gestionada por Firebase Authentication con encriptación segura.
• Las comunicaciones entre el dispositivo y los servidores de Firebase están encriptadas con HTTPS/TLS.

Tus datos financieros son tuyos. No accedemos a ellos para ningún propósito comercial.
                """.trimIndent()
            )

            PolicySection(
                title = "4. Tus derechos",
                content = """
Como usuario de Xpendz tienes los siguientes derechos:

• Acceso: puedes consultar todos tus datos en cualquier momento desde la app.
• Portabilidad: puedes exportar tus transacciones a CSV desde la sección de Privacidad y datos.
• Eliminación: puedes eliminar permanentemente todos tus datos desde Configuración > Privacidad y datos > Eliminar datos.
• Corrección: puedes editar cualquier transacción, cuenta o categoría que hayas creado.

Para ejercer estos derechos, usa las funciones disponibles en la app o contacta al soporte.
                """.trimIndent()
            )

            PolicySection(
                title = "5. Eliminación de datos",
                content = """
Cuando solicitas la eliminación de datos:

• Todos tus datos locales en el dispositivo se eliminan inmediatamente.
• Todos tus datos en Firebase Firestore se eliminan permanentemente.
• Los datos se eliminan de todos los dispositivos sincronizados.
• El proceso es irreversible; una vez eliminados, los datos no pueden recuperarse.

Nota: Las copias de seguridad del sistema pueden retener datos residualmente por hasta 30 días según las políticas de Firebase.
                """.trimIndent()
            )

            PolicySection(
                title = "6. Servicios de terceros",
                content = """
Xpendz utiliza los siguientes servicios de terceros:

• Firebase (Google): autenticación, base de datos en la nube (Firestore), almacenamiento.
• Google Play Services: actualizaciones y estadísticas de uso anónimas.

Estos servicios tienen sus propias políticas de privacidad:
• Firebase: https://firebase.google.com/support/privacy
• Google: https://policies.google.com/privacy
                """.trimIndent()
            )

            PolicySection(
                title = "7. Cambios en esta política",
                content = """
Podemos actualizar esta política de privacidad ocasionalmente. Te notificaremos sobre cambios significativos mediante:

• Un aviso en la app al iniciar sesión.
• Un correo electrónico a la dirección registrada en tu cuenta.

Te recomendamos revisar esta política periódicamente.
                """.trimIndent()
            )

            PolicySection(
                title = "8. Contacto",
                content = """
Si tienes preguntas o inquietudes sobre esta política de privacidad o sobre el manejo de tus datos:

• Desde la app: Configuración > Privacidad y datos
• Correo electrónico: servicios@jcadenas.com

Responderemos a tus solicitudes dentro de los 30 días calendario.
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "© 2026 Xpendz. Todos los derechos reservados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PolicySection(
    title: String,
    content: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
