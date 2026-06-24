package com.jcadenas.xpendz.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.jcadenas.xpendz.ui.components.CompactHeader
import com.jcadenas.xpendz.ui.components.HamburgerMenu
import com.jcadenas.xpendz.ui.components.HamburgerMenuButton
import com.jcadenas.xpendz.ui.components.SettingsSection
import com.jcadenas.xpendz.ui.components.SettingsItem
import com.jcadenas.xpendz.ui.components.UserAccountHeader
import com.jcadenas.xpendz.ui.components.SyncStatus
import com.jcadenas.xpendz.ui.util.CountryCurrency
import com.jcadenas.xpendz.ui.viewmodel.SettingsViewModel
import com.jcadenas.xpendz.ui.viewmodel.SyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPrivacyAndData: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToBackupSettings: (userUid: String) -> Unit,
    onNavigateToCharts: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToReports: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    syncViewModel: SyncViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showHamburgerMenu by remember { mutableStateOf(false) }
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        packageInfo.longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode
    }

    val state by viewModel.state.collectAsState()
    val isSyncing by syncViewModel.isSyncing.collectAsState()
    val syncError by syncViewModel.error.collectAsState()
    val snackbarHostState = androidx.compose.material3.SnackbarHostState()

    val syncStatus = if (isSyncing) SyncStatus.SYNCING else SyncStatus.SYNCED

    val isLoggedIn = state.userEmail.isNotBlank()
    val syncStatusText = when {
        !isLoggedIn -> "Sin sesión"
        syncError != null -> "Error de sincronización"
        isSyncing -> "Sincronizando"
        else -> "Sincronizado"
    }
    val syncStatusSubtitle = when {
        !isLoggedIn -> "Inicia sesión para activar respaldo en la nube"
        syncError != null -> "Tus datos siguen seguros en este dispositivo"
        isSyncing -> "Estamos actualizando tu información"
        else -> "Tus datos están respaldados y al día"
    }

    var showCountryDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Text(
                        text = "Configuración",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    Box {
                        HamburgerMenuButton(onClick = { showHamburgerMenu = true })
                        HamburgerMenu(
                            expanded = showHamburgerMenu,
                            onDismissRequest = { showHamburgerMenu = false },
                            onNavigateToCharts = onNavigateToCharts,
                            onNavigateToBudget = onNavigateToBudget,
                            onNavigateToReports = onNavigateToReports,
                            onNavigateToSettings = { },
                            onLogout = onLogout,
                            currentScreen = "settings"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // User Account Header
            UserAccountHeader(
                displayName = state.userDisplayName.ifBlank { "Usuario" },
                email = state.userEmail,
                photoUrl = state.userPhotoUrl,
                syncStatus = syncStatus,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // General Section
            SettingsSection(
                title = "General",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Default.LocationOn,
                        title = "País",
                        subtitle = CountryCurrency.displayNameWithCode(state.countryCode),
                        onClick = { showCountryDialog = true }
                    ),
                    SettingsItem(
                        icon = Icons.Default.MonetizationOn,
                        title = "Moneda base",
                        subtitle = CountryCurrency.currencyDisplayNameWithCode(state.baseCurrency),
                        onClick = { showCurrencyDialog = true }
                    )
                ),
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Data & Privacy Section
            SettingsSection(
                title = "Datos y privacidad",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Default.Lock,
                        title = "Privacidad y datos",
                        subtitle = "Gestiona tu información personal",
                        onClick = onNavigateToPrivacyAndData
                    ),
                    SettingsItem(
                        icon = Icons.Default.CloudUpload,
                        title = "Respaldo y restauración",
                        subtitle = "Exporta o importa tus datos",
                        onClick = {
                            val userUid = viewModel.uid
                            if (userUid != null) {
                                onNavigateToBackupSettings(userUid)
                            }
                        }
                    ),
                    SettingsItem(
                        icon = Icons.Default.Description,
                        title = "Política de privacidad",
                        subtitle = "Consulta cómo protegemos tus datos",
                        onClick = onNavigateToPrivacyPolicy
                    )
                ),
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Backup and Sync Section
            val syncIcon = when {
                !isLoggedIn -> Icons.Default.Person
                syncError != null -> Icons.Default.ErrorOutline
                isSyncing -> Icons.Default.Cloud
                else -> Icons.Default.CloudDone
            }

            SettingsSection(
                title = "Respaldo y sincronización",
                items = listOf(
                    SettingsItem(
                        icon = syncIcon,
                        title = "Estado de sincronización",
                        subtitle = syncStatusSubtitle,
                        onClick = { },
                        showChevron = false
                    ),
                    SettingsItem(
                        icon = Icons.Default.Person,
                        title = "Cuenta conectada",
                        subtitle = if (isLoggedIn) "Conectada" else "No iniciada",
                        onClick = { },
                        showChevron = false
                    )
                ),
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // About Section
            SettingsSection(
                title = "Acerca de",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "Versión",
                        subtitle = "$versionName ($versionCode)",
                        onClick = { },
                        showChevron = false
                    ),
                    SettingsItem(
                        icon = Icons.Default.Email,
                        title = "Contacto",
                        subtitle = "servicios@jcadenas.com",
                        onClick = {
                            val success = openEmailClient(
                                context = context,
                                to = "servicios@jcadenas.com",
                                subject = "Consulta sobre Xpendz"
                            )
                            if (!success) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "No hay aplicación de correo instalada"
                                    )
                                }
                            }
                        },
                        showChevron = true
                    ),
                    SettingsItem(
                        icon = Icons.Default.BugReport,
                        title = "Reportar problema",
                        subtitle = "Envíanos feedback o reporta un error",
                        onClick = {
                            val body = """
                                |Describe el problema:
                                |
                                |
                                |Pasos para reproducir:
                                |
                                |
                                |Resultado esperado:
                                |
                                |
                                |Resultado obtenido:
                                |
                                |
                                |${getDeviceInfo()}
                                |""".trimMargin()
                            val success = openEmailClient(
                                context = context,
                                to = "servicios@jcadenas.com",
                                subject = "Reporte de problema - Xpendz",
                                body = body
                            )
                            if (!success) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "No hay aplicación de correo instalada"
                                    )
                                }
                            }
                        },
                        showChevron = true
                    )
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }

    // Country Selection Dialog
    if (showCountryDialog) {
        CountrySelectionDialog(
            currentCountryCode = state.countryCode,
            onDismiss = { showCountryDialog = false },
            onCountrySelected = { countryCode ->
                viewModel.saveCountry(countryCode)
                showCountryDialog = false
            }
        )
    }

    // Currency Selection Dialog
    if (showCurrencyDialog) {
        CurrencySelectionDialog(
            currentCurrency = state.baseCurrency,
            onDismiss = { showCurrencyDialog = false },
            onCurrencySelected = { currency ->
                viewModel.saveBaseCurrency(currency)
                showCurrencyDialog = false
            }
        )
    }
}

private fun openEmailClient(
    context: Context,
    to: String,
    subject: String,
    body: String = ""
): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotEmpty()) {
                putExtra(Intent.EXTRA_TEXT, body)
            }
        }
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

private fun getDeviceInfo(): String {
    return """
        |
        |Versión de la app: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})
        |Modelo del dispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
        |Android: ${android.os.Build.VERSION.RELEASE}
        |""".trimMargin()
}

@Composable
fun CountrySelectionDialog(
    currentCountryCode: String,
    onDismiss: () -> Unit,
    onCountrySelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Seleccionar país")
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(CountryCurrency.options) { country ->
                    val isSelected = country.code == currentCountryCode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCountrySelected(country.code)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onCountrySelected(country.code) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = country.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun CurrencySelectionDialog(
    currentCurrency: String,
    onDismiss: () -> Unit,
    onCurrencySelected: (String) -> Unit
) {
    val availableCurrencies = listOf(
        "COP", "MXN", "ARS", "CLP", "PEN", "BOB", "PYG", "UYU", "VES",
        "BRL", "PAB", "CRC", "GTQ", "HNL", "NIO", "DOP", "CUP", "USD", "EUR"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Seleccionar moneda base")
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(availableCurrencies) { currency ->
                    val isSelected = currency == currentCurrency
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCurrencySelected(currency)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onCurrencySelected(currency) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = currency,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = CountryCurrency.currencyDisplayName(currency),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
