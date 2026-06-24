package com.jcadenas.xpendz.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jcadenas.xpendz.ui.backup.BackupUiState
import com.jcadenas.xpendz.ui.backup.BackupViewModel
import com.jcadenas.xpendz.ui.components.CompactHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    onNavigateBack: () -> Unit,
    userUid: String,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var exportPassword by remember { mutableStateOf("") }
    var exportConfirmPassword by remember { mutableStateOf("") }
    var exportPasswordVisible by remember { mutableStateOf(false) }
    var exportConfirmPasswordVisible by remember { mutableStateOf(false) }

    var importPassword by remember { mutableStateOf("") }
    var importPasswordVisible by remember { mutableStateOf(false) }
    var importFileUri by remember { mutableStateOf<Uri?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSize by remember { mutableStateOf<String?>(null) }
    // Tracks which operation last completed, to show contextual feedback
    var lastOperation by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            lastOperation = "export"
            viewModel.exportBackup(userUid, exportPassword.toCharArray(), uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importFileUri = uri
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    selectedFileName = cursor.getString(nameIndex)
                    val size = cursor.getLong(sizeIndex)
                    selectedFileSize = if (size > 0) {
                        when {
                            size < 1024 -> "$size B"
                            size < 1024 * 1024 -> "${size / 1024} KB"
                            else -> "${size / (1024 * 1024)} MB"
                        }
                    } else null
                }
            }
            showRestoreConfirmDialog = true
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is BackupUiState.Success) {
            selectedFileName = null
            selectedFileSize = null
            importPassword = ""
        }
    }

    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Text(
                        text = "Respaldo y restauración",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Volver a configuración"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── Export Section ───────────────────────────────────────────────
            BackupSection(
                icon = Icons.Default.CloudUpload,
                title = "Crear respaldo",
                description = "Cifra y exporta todos tus datos a un archivo seguro",
                content = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(tween(200, easing = EaseOutCubic))
                    ) {
                        PasswordField(
                            label = "Contraseña",
                            value = exportPassword,
                            onValueChange = { exportPassword = it },
                            visible = exportPasswordVisible,
                            onVisibilityChange = { exportPasswordVisible = it },
                            supportingText = when {
                                exportPassword.isEmpty() -> "Mínimo 8 caracteres"
                                exportPassword.length < 8 -> "${8 - exportPassword.length} caracteres más requeridos"
                                else -> null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PasswordField(
                            label = "Confirmar contraseña",
                            value = exportConfirmPassword,
                            onValueChange = { exportConfirmPassword = it },
                            visible = exportConfirmPasswordVisible,
                            onVisibilityChange = { exportConfirmPasswordVisible = it },
                            isError = exportConfirmPassword.isNotEmpty() && exportPassword != exportConfirmPassword,
                            supportingText = when {
                                exportConfirmPassword.isNotEmpty() && exportPassword != exportConfirmPassword ->
                                    "Las contraseñas no coinciden"
                                exportConfirmPassword.isNotEmpty() && exportPassword == exportConfirmPassword ->
                                    "Las contraseñas coinciden ✓"
                                else -> null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        val exportEnabled = exportPassword.length >= 8 &&
                            exportPassword == exportConfirmPassword &&
                            uiState !is BackupUiState.Exporting

                        Button(
                            onClick = {
                                exportLauncher.launch("xpendz_backup_${System.currentTimeMillis()}.xpb")
                            },
                            enabled = exportEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Crossfade(
                                targetState = uiState is BackupUiState.Exporting,
                                animationSpec = tween(200),
                                label = "export_button"
                            ) { isExporting ->
                                if (isExporting) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Preparando respaldo seguro...",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Crear respaldo",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }

                        // Export feedback (inline, within the section)
                        AnimatedVisibility(
                            visible = lastOperation == "export" && (uiState is BackupUiState.Success || uiState is BackupUiState.Error),
                            enter = expandVertically(tween(250, easing = EaseOutCubic)) + fadeIn(tween(250, easing = EaseOutCubic)),
                            exit = shrinkVertically(tween(200, easing = EaseInCubic)) + fadeOut(tween(200, easing = EaseInCubic))
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                if (uiState is BackupUiState.Success) {
                                    FeedbackCard(
                                        isSuccess = true,
                                        title = "Respaldo creado",
                                        subtitle = "Tu información está protegida con cifrado AES-256."
                                    )
                                } else if (uiState is BackupUiState.Error) {
                                    FeedbackCard(
                                        isSuccess = false,
                                        title = "No se pudo crear el respaldo",
                                        subtitle = (uiState as BackupUiState.Error).message
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        SecurityRecommendationCard()
                    }
                }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ── Import Section ───────────────────────────────────────────────
            BackupSection(
                icon = Icons.Default.CloudDownload,
                title = "Restaurar respaldo",
                description = "Importa un archivo cifrado para recuperar tus datos",
                content = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(tween(200, easing = EaseOutCubic))
                    ) {
                        PasswordField(
                            label = "Contraseña del respaldo",
                            value = importPassword,
                            onValueChange = { importPassword = it },
                            visible = importPasswordVisible,
                            onVisibilityChange = { importPasswordVisible = it },
                            supportingText = if (importPassword.isNotEmpty() && importPassword.length < 8)
                                "${8 - importPassword.length} caracteres más requeridos" else null,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // File picker area: empty state OR selected file card
                        Crossfade(
                            targetState = selectedFileName,
                            animationSpec = tween(200, easing = EaseOutCubic),
                            label = "file_state"
                        ) { fileName ->
                            if (fileName == null) {
                                // Empty state
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .semantics { contentDescription = "Ningún archivo de respaldo seleccionado" },
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.FolderOpen,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Ningún archivo seleccionado",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                // File info card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.CloudDownload,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = fileName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 1
                                            )
                                            selectedFileSize?.let { size ->
                                                Text(
                                                    text = size,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                selectedFileName = null
                                                selectedFileSize = null
                                                importFileUri = null
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Quitar archivo seleccionado",
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        FilledTonalButton(
                            onClick = {
                                lastOperation = "import"
                                importLauncher.launch(arrayOf("application/octet-stream"))
                            },
                            enabled = importPassword.length >= 8 && uiState !is BackupUiState.Importing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Crossfade(
                                targetState = uiState is BackupUiState.Importing,
                                animationSpec = tween(200),
                                label = "import_button"
                            ) { isImporting ->
                                if (isImporting) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Restaurando datos...",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (selectedFileName != null) "Cambiar archivo" else "Seleccionar respaldo",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }

                        // Import feedback (inline, within the section)
                        AnimatedVisibility(
                            visible = lastOperation == "import" && (uiState is BackupUiState.Success || uiState is BackupUiState.Error),
                            enter = expandVertically(tween(250, easing = EaseOutCubic)) + fadeIn(tween(250, easing = EaseOutCubic)),
                            exit = shrinkVertically(tween(200, easing = EaseInCubic)) + fadeOut(tween(200, easing = EaseInCubic))
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                if (uiState is BackupUiState.Success) {
                                    FeedbackCard(
                                        isSuccess = true,
                                        title = "Restauración completada",
                                        subtitle = "Los datos fueron recuperados correctamente."
                                    )
                                } else if (uiState is BackupUiState.Error) {
                                    FeedbackCard(
                                        isSuccess = false,
                                        title = "No se pudo restaurar",
                                        subtitle = (uiState as BackupUiState.Error).message
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        WarningCard()
                    }
                }
            )

            // Bottom padding for gesture navigation
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── Restore Confirmation Dialog ──────────────────────────────────────────
    if (showRestoreConfirmDialog && importFileUri != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Advertencia",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Confirmar restauración",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        text = "Esta acción reemplazará todos los datos actuales.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "La operación no se puede deshacer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    selectedFileName?.let { fileName ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importBackup(userUid, importPassword.toCharArray(), importFileUri!!)
                        showRestoreConfirmDialog = false
                    },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Restaurar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreConfirmDialog = false },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun BackupSection(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            content()
        }
    }
}

@Composable
fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(
                onClick = { onVisibilityChange(!visible) },
                modifier = Modifier.size(48.dp)
            ) {
                Crossfade(
                    targetState = visible,
                    animationSpec = tween(150),
                    label = "visibility_icon"
                ) { isVisible ->
                    Icon(
                        imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (isVisible) "Ocultar contraseña" else "Mostrar contraseña",
                        tint = when {
                            isError -> MaterialTheme.colorScheme.error
                            isFocused -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        },
        supportingText = supportingText?.let { text ->
            {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = modifier.onFocusChanged { focusState -> isFocused = focusState.isFocused },
        shape = MaterialTheme.shapes.large
    )
}

@Composable
fun SecurityRecommendationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Recomendaciones de seguridad",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Conserva tu contraseña en un lugar seguro y almacena el respaldo en una ubicación protegida.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }
        }
    }
}

@Composable
fun WarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Advertencia importante",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "La restauración reemplazará todos los datos actuales. Asegúrate de tener un respaldo antes de continuar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }
        }
    }
}

@Composable
fun FeedbackCard(
    isSuccess: Boolean,
    title: String,
    subtitle: String
) {
    val containerColor = if (isSuccess)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    else
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)

    val borderColor = if (isSuccess)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    else
        MaterialTheme.colorScheme.error.copy(alpha = 0.3f)

    val iconTint = if (isSuccess)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.error

    val iconBg = if (isSuccess)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else
        MaterialTheme.colorScheme.error.copy(alpha = 0.15f)

    val titleColor = if (isSuccess)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onErrorContainer

    val subtitleColor = titleColor.copy(alpha = 0.75f)

    val icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline
    val cardDesc = if (isSuccess) "Operación exitosa: $title" else "Error: $title"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDesc },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = iconBg,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor
                )
            }
        }
    }
}
