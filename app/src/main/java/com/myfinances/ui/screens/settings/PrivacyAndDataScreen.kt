package com.jcadenas.xpendz.ui.screens.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.jcadenas.xpendz.R
import com.jcadenas.xpendz.diagnostics.AppIdentityLogger
import com.jcadenas.xpendz.domain.usecase.AuthProvider
import com.jcadenas.xpendz.ui.components.CompactHeader
import com.jcadenas.xpendz.ui.viewmodel.DeleteAccountEvent
import com.jcadenas.xpendz.ui.viewmodel.DeleteAccountState
import com.jcadenas.xpendz.ui.viewmodel.PrivacyAndDataViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyAndDataScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onAccountDeleted: () -> Unit,
    viewModel: PrivacyAndDataViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val deleteAccountState by viewModel.deleteAccountState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it) }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReauthEmailDialog by remember { mutableStateOf(false) }
    var reauthEmail by remember { mutableStateOf("") }
    var reauthPassword by remember { mutableStateOf("") }
    var availableProviders by remember { mutableStateOf<List<AuthProvider>>(emptyList()) }
    var reauthDialogError by remember { mutableStateOf<String?>(null) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // TEMP DIAGNOSTIC
        AppIdentityLogger.logIntentDiagnostics("PrivacyAndDataScreen.googleSignInLauncher", result.data)
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                // TEMP DIAGNOSTIC
                AppIdentityLogger.logIntentDiagnostics("PrivacyAndDataScreen.googleSignInLauncher.RESULT_OK.beforeGetSignedInAccountFromIntent", result.data)
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                account?.idToken?.let { token ->
                    viewModel.reauthenticateWithGoogleAndRetry(token)
                } ?: viewModel.onReauthCancelled()
            } catch (e: ApiException) {
                // TEMP DIAGNOSTIC
                AppIdentityLogger.logApiException("PrivacyAndDataScreen.googleSignInLauncher.RESULT_OK", e)
                viewModel.onReauthCancelled()
            } catch (e: Exception) {
                // TEMP DIAGNOSTIC
                AppIdentityLogger.logThrowable("PrivacyAndDataScreen.googleSignInLauncher.RESULT_OK", e)
                viewModel.onReauthCancelled()
            }
        } else {
            if (AuthProvider.EMAIL in availableProviders) {
                showReauthEmailDialog = true
            } else {
                viewModel.onReauthCancelled()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.deleteAccountEvents.collect { event ->
            when (event) {
                is DeleteAccountEvent.RequiresReauthentication -> {
                    availableProviders = event.providers
                    when {
                        AuthProvider.GOOGLE in event.providers -> {
                            // TEMP DIAGNOSTIC
                            AppIdentityLogger.logGoogleSignInBuilder(
                                source = "PrivacyAndDataScreen.reauthGoogle",
                                defaultWebClientId = context.getString(R.string.default_web_client_id),
                                requestEmail = true,
                                requestIdToken = true,
                                extraConfig = listOf(
                                    "GoogleSignInOptions.DEFAULT_SIGN_IN",
                                    "requestEmail()",
                                    "requestIdToken(default_web_client_id)",
                                    "signOut().addOnCompleteListener { signInIntent.launch }"
                                )
                            )
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(context.getString(R.string.default_web_client_id))
                                .requestEmail()
                                .build()
                            val client = GoogleSignIn.getClient(context, gso)
                            client.signOut().addOnCompleteListener {
                                googleSignInLauncher.launch(client.signInIntent)
                            }
                        }
                        AuthProvider.EMAIL in event.providers -> showReauthEmailDialog = true
                    }
                }
                is DeleteAccountEvent.Success -> {
                    showDeleteConfirm = false
                    showReauthEmailDialog = false
                    reauthEmail = ""
                    reauthPassword = ""
                    reauthDialogError = null
                    onAccountDeleted()
                }
                is DeleteAccountEvent.Error -> {
                    if (showReauthEmailDialog) {
                        reauthDialogError = event.message
                    } else {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Text(
                        text = "Privacidad y datos",
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
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Privacy Policy Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Policy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Política de privacidad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Consulta cómo recopilamos, usamos y protegemos tu información personal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedButton(
                        onClick = onNavigateToPrivacyPolicy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Ver política de privacidad")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Delete Data Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Eliminar datos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                    }

                    Text(
                        text = "Elimina permanentemente todos tus datos de la aplicación y del servidor. Esta acción no se puede deshacer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Eliminar todos mis datos")
                    }
                }
            }
        }
    }

    val isDeleting = deleteAccountState is DeleteAccountState.Loading

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirm = false },
            containerColor = Color.White,
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFDC2626)
                )
            },
            title = { Text("¿Eliminar todos tus datos?") },
            text = {
                Column {
                    Text("Esta acción eliminará permanentemente:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Todas las transacciones")
                    Text("• Todas las transferencias")
                    Text("• Todas las cuentas")
                    Text("• Todas las categorías")
                    Text("• Todas las metas")
                    Text("• Todos los presupuestos")
                    Text("• Todos los préstamos")
                    Text("• Configuración personal")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Esta acción no se puede deshacer.",
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAccount() },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626)
                    )
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Sí, eliminar todo")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    enabled = !isDeleting
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Reauthentication dialog for email/password
    if (showReauthEmailDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showReauthEmailDialog = false
                    reauthDialogError = null
                    reauthEmail = ""
                    reauthPassword = ""
                    viewModel.onReauthCancelled()
                }
            },
            containerColor = Color.White,
            title = { Text("Confirma tu identidad") },
            text = {
                Column {
                    Text(
                        "Para eliminar tu cuenta necesitamos verificar tu identidad.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    if (reauthDialogError != null) {
                        Text(
                            text = reauthDialogError!!,
                            color = Color(0xFFDC2626),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    OutlinedTextField(
                        value = reauthEmail,
                        onValueChange = { reauthEmail = it; reauthDialogError = null },
                        label = { Text("Correo electrónico") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        enabled = !isDeleting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = reauthPassword,
                        onValueChange = { reauthPassword = it; reauthDialogError = null },
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !isDeleting,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reauthenticateWithEmailAndRetry(reauthEmail, reauthPassword)
                    },
                    enabled = !isDeleting && reauthEmail.isNotBlank() && reauthPassword.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626)
                    )
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Confirmar y eliminar")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showReauthEmailDialog = false
                        reauthDialogError = null
                        reauthEmail = ""
                        reauthPassword = ""
                        viewModel.onReauthCancelled()
                    },
                    enabled = !isDeleting
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

