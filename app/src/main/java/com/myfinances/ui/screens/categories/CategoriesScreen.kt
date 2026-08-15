package com.jcadenas.xpendz.ui.screens.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jcadenas.xpendz.data.local.entity.CategoryEntity
import com.jcadenas.xpendz.ui.components.CompactHeader
import com.jcadenas.xpendz.ui.components.HamburgerMenu
import com.jcadenas.xpendz.ui.components.HamburgerMenuButton
import com.jcadenas.xpendz.ui.components.SyncSwipeRefresh
import com.jcadenas.xpendz.ui.theme.Expense
import com.jcadenas.xpendz.ui.theme.Income
import com.jcadenas.xpendz.ui.theme.XpendzThemeTokens
import com.jcadenas.xpendz.ui.viewmodel.CategoryMonthlyInsight
import com.jcadenas.xpendz.ui.viewmodel.CategoriesViewModel
import com.jcadenas.xpendz.ui.viewmodel.SyncViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCharts: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val typography = XpendzThemeTokens.typography

    val state by viewModel.state.collectAsState()
    val currencyFormat = remember(state.baseCurrency) {
        NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
            currency = java.util.Currency.getInstance(state.baseCurrency)
        }
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showHamburgerMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val syncViewModel: SyncViewModel = hiltViewModel()
    val syncVersion by syncViewModel.syncVersion.collectAsState()

    val filteredRoots = remember(state.rootCategories, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            state.rootCategories
        } else {
            state.rootCategories.filter { it.name.contains(query, ignoreCase = true) }
        }
    }
    val incomeRoots = remember(filteredRoots) {
        filteredRoots.filter { resolveCategoryKind(it) == CategoryKind.Income }
    }
    val expenseRoots = remember(filteredRoots) {
        filteredRoots.filter { resolveCategoryKind(it) == CategoryKind.Expense }
    }
    val otherRoots = remember(filteredRoots) {
        filteredRoots.filter { resolveCategoryKind(it) == CategoryKind.Other }
    }
    val hasResults = incomeRoots.isNotEmpty() || expenseRoots.isNotEmpty() || otherRoots.isNotEmpty()

    LaunchedEffect(syncVersion) {
        viewModel.loadCategories()
    }

    Scaffold(
        topBar = {
            CompactHeader(
                title = {
                    Text(
                        text = "Categorías",
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) {
                            searchQuery = ""
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                    Box {
                        HamburgerMenuButton(onClick = { showHamburgerMenu = true })
                        HamburgerMenu(
                            expanded = showHamburgerMenu,
                            onDismissRequest = { showHamburgerMenu = false },
                            onNavigateToCharts = onNavigateToCharts,
                            onNavigateToBudget = onNavigateToBudget,
                            onNavigateToReports = onNavigateToReports,
                            onNavigateToSettings = onNavigateToSettings,
                            onLogout = onLogout,
                            currentScreen = "categories"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog(null) }
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Nueva categoría"
                )
            }
        }
    ) { paddingValues ->
        SyncSwipeRefresh(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (state.rootCategories.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Category,
                                contentDescription = null,
                                modifier = Modifier.size(spacing.xxxl + spacing.xl),
                                tint = colors.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(spacing.m))
                            Text(
                                "No hay categorías",
                                style = typography.bodyLarge,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                "Crea tu primera categoría",
                                style = typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(spacing.m),
                        verticalArrangement = Arrangement.spacedBy(spacing.s + spacing.xxs)
                    ) {
                        if (showSearch) {
                            item {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(shapes.extraLarge),
                                    placeholder = { Text("Buscar categorías o subcategorías") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotBlank()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "Limpiar")
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        if (!hasResults) {
                            item {
                                EmptySearchState(searchQuery = searchQuery)
                            }
                        } else {
                            if (incomeRoots.isNotEmpty()) {
                                item {
                                    CategorySectionHeader(
                                        title = "INGRESOS",
                                        subtitle = "${incomeRoots.size} categorías",
                                        accentColor = Income,
                                        icon = Icons.Default.TrendingUp
                                    )
                                }
                                items(incomeRoots, key = { it.id }) { category ->
                                    CategoryItem(
                                        category = category,
                                        children = state.childrenMap[category.id] ?: emptyList(),
                                        isExpanded = state.expandedCategories.contains(category.id),
                                        onToggleExpand = { viewModel.toggleExpanded(category.id) },
                                        onAddSubcategory = { viewModel.showAddDialog(category.id) },
                                        onRename = { newName, iconKey, kind -> viewModel.renameCategory(category.id, newName, iconKey, kind) },
                                        onDelete = { viewModel.deleteCategory(category.id) },
                                        onRenameChild = { childId, newName, iconKey -> viewModel.renameCategory(childId, newName, iconKey) },
                                        onDeleteChild = { childId -> viewModel.deleteCategory(childId) }
                                    )
                                }
                            }

                            if (expenseRoots.isNotEmpty()) {
                                item {
                                    CategorySectionHeader(
                                        title = "GASTOS",
                                        subtitle = "${expenseRoots.size} categorías",
                                        accentColor = Expense,
                                        icon = Icons.Default.TrendingDown
                                    )
                                }
                                items(expenseRoots, key = { it.id }) { category ->
                                    CategoryItem(
                                        category = category,
                                        children = state.childrenMap[category.id] ?: emptyList(),
                                        isExpanded = state.expandedCategories.contains(category.id),
                                        onToggleExpand = { viewModel.toggleExpanded(category.id) },
                                        onAddSubcategory = { viewModel.showAddDialog(category.id) },
                                        onRename = { newName, iconKey, kind -> viewModel.renameCategory(category.id, newName, iconKey, kind) },
                                        onDelete = { viewModel.deleteCategory(category.id) },
                                        onRenameChild = { childId, newName, iconKey -> viewModel.renameCategory(childId, newName, iconKey) },
                                        onDeleteChild = { childId -> viewModel.deleteCategory(childId) }
                                    )
                                }
                            }

                            if (otherRoots.isNotEmpty()) {
                                item {
                                    CategorySectionHeader(
                                        title = "OTRAS",
                                        subtitle = "${otherRoots.size} categorías",
                                        accentColor = colors.secondary,
                                        icon = Icons.Default.Category
                                    )
                                }
                                items(otherRoots, key = { it.id }) { category ->
                                    CategoryItem(
                                        category = category,
                                        children = state.childrenMap[category.id] ?: emptyList(),
                                        isExpanded = state.expandedCategories.contains(category.id),
                                        onToggleExpand = { viewModel.toggleExpanded(category.id) },
                                        onAddSubcategory = { viewModel.showAddDialog(category.id) },
                                        onRename = { newName, iconKey, kind -> viewModel.renameCategory(category.id, newName, iconKey, kind) },
                                        onDelete = { viewModel.deleteCategory(category.id) },
                                        onRenameChild = { childId, newName, iconKey -> viewModel.renameCategory(childId, newName, iconKey) },
                                        onDeleteChild = { childId -> viewModel.deleteCategory(childId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Category Dialog
        if (state.showAddDialog) {
            AddCategoryDialog(
                isSubcategory = state.addDialogParentId != null,
                onDismiss = { viewModel.hideAddDialog() },
                onConfirm = { name, iconKey, kind -> viewModel.createCategory(name, iconKey, kind) }
            )
        }

        state.error?.let { error ->
            LaunchedEffect(error) {
                snackbarHostState.showSnackbar(message = error)
                viewModel.clearError()
            }
        }
    }
}

@Composable
private fun MonthlyInsightCard(
    insight: CategoryMonthlyInsight,
    formattedAmount: String
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(shapes.extraLarge),
        tonalElevation = elevation.level1,
        shadowElevation = elevation.level0
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.s + spacing.xxs / 2, vertical = spacing.s),
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.92f),
                shape = RoundedCornerShape(shapes.large),
                modifier = Modifier.size(spacing.xl + spacing.xxs)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = null,
                        tint = Expense,
                        modifier = Modifier.size(spacing.s + spacing.xxs)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Insight del mes",
                    style = typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Expense
                )
                Spacer(modifier = Modifier.height(spacing.xxs))
                Text(
                    text = "Este mes has gastado más en ${insight.categoryName}.",
                    style = typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Acumulas $formattedAmount en esta categoría.",
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private enum class CategoryKind {
    Income,
    Expense,
    Other
}

@Composable
private fun CategorySectionHeader(
    title: String,
    subtitle: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val typography = XpendzThemeTokens.typography

    Surface(
        color = accentColor.copy(alpha = 0.10f),
        shape = RoundedCornerShape(shapes.extraLarge),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.s + spacing.xxs / 2, vertical = spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.9f),
                shape = RoundedCornerShape(shapes.large),
                modifier = Modifier.size(spacing.xl + spacing.xxs)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(spacing.s))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = subtitle,
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreviewChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val typography = XpendzThemeTokens.typography

    Surface(
        modifier = modifier
            .widthIn(min = spacing.xxxl + spacing.xxs, max = spacing.xxxl * 3 + spacing.xxs),
        color = accentColor.copy(alpha = 0.08f),
        shape = RoundedCornerShape(shapes.large)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.s, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(spacing.xs))
            Text(
                text = label,
                style = typography.labelMedium,
                color = colors.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}
@Composable
private fun CategoryFabAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(shapes.extraLarge),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation.level2),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.s + spacing.xxs / 2, vertical = spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Icon(icon, contentDescription = null, tint = colors.brand)
            Text(
                text = label,
                style = typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmptySearchState(searchQuery: String) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(shapes.extraLarge),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.level1)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.xl + spacing.xxs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(spacing.xxxl - spacing.xs)
            )
            Text(
                text = "No encontramos categorías para \"$searchQuery\"",
                style = typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Prueba con otro nombre o crea una nueva categoría desde el botón +.",
                style = typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun resolveCategoryKind(category: CategoryEntity): CategoryKind {
    val k = category.kind.trim().uppercase()
    if (k == "INCOME") return CategoryKind.Income
    if (k == "EXPENSE") return CategoryKind.Expense

    // Fallback for legacy/older records that might still be BOTH or invalid.
    val n = category.name.uppercase()
    return when {
        n.contains("INGRES") || n.contains("SALAR") || n.contains("SUELD") || n.contains("VENTA") || n.contains("INVERS") || n.contains("PRESTAM") -> CategoryKind.Income
        n.contains("EGRES") || n.contains("GAST") || n.contains("MERCAD") || n.contains("TRANSP") || n.contains("SERVIC") || n.contains("FIJ") || n.contains("HOGAR") || n.contains("SALUD") -> CategoryKind.Expense
        else -> CategoryKind.Other
    }
}

@Composable
private fun categoryAccentColor(kind: CategoryKind): Color {
    val colors = XpendzThemeTokens.colors
    return when (kind) {
        CategoryKind.Income -> Income
        CategoryKind.Expense -> Expense
        CategoryKind.Other -> colors.brand
    }
}

private fun resolveCategoryIcon(
    iconKey: String?,
    categoryName: String,
    kind: CategoryKind
): androidx.compose.ui.graphics.vector.ImageVector {
    categoryIconOptions.firstOrNull { it.key == iconKey }?.let { return it.icon }
    val n = categoryName.uppercase()
    return when {
        n.contains("MERCAD") || n.contains("SUPER") || n.contains("ALIMENT") -> Icons.Default.ShoppingCart
        n.contains("TRANSP") || n.contains("GASOL") || n.contains("VEH") -> Icons.Default.DirectionsCar
        n.contains("SERVIC") || n.contains("LUZ") || n.contains("AGUA") || n.contains("INTERNET") -> Icons.Default.Lightbulb
        n.contains("HOGAR") || n.contains("ARRIEND") || n.contains("CASA") -> Icons.Default.Home
        n.contains("SALUD") || n.contains("MEDIC") -> Icons.Default.LocalHospital
        n.contains("OCIO") || n.contains("ENTRET") -> Icons.Default.LocalActivity
        n.contains("EDUC") || n.contains("CURS") -> Icons.Default.School
        n.contains("AHORR") || n.contains("INVERS") -> Icons.Default.Savings
        n.contains("PRESTAM") -> Icons.Default.AccountBalance
        n.contains("PLOMER") || n.contains("TUBER") -> Icons.Default.Plumbing
        n.contains("ELECTR") || n.contains("CABLE") -> Icons.Default.ElectricBolt
        n.contains("TECNO") || n.contains("SOFT") || n.contains("SISTEM") || n.contains("COMPUT") -> Icons.Default.Computer
        n.contains("REPAR") || n.contains("MANTEN") || n.contains("ARREG") -> Icons.Default.Handyman
        n.contains("LLAVE") || n.contains("CERRAJ") -> Icons.Default.Key
        n.contains("CONSTR") || n.contains("OBRA") -> Icons.Default.Construction
        n.contains("INGEN") -> Icons.Default.Engineering
        n.contains("NEGOC") || n.contains("EMPRES") -> Icons.Default.BusinessCenter
        n.contains("SALAR") || n.contains("SUELD") || n.contains("INGRES") -> Icons.Default.Payments
        kind == CategoryKind.Income -> Icons.Default.TrendingUp
        kind == CategoryKind.Expense -> Icons.Default.ReceiptLong
        else -> Icons.Default.Folder
    }
}

@Composable
private fun CategoryItem(
    category: CategoryEntity,
    children: List<CategoryEntity>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddSubcategory: () -> Unit,
    onRename: (String, String?, String?) -> Unit,
    onDelete: () -> Unit,
    onRenameChild: (String, String, String?) -> Unit,
    onDeleteChild: (String) -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val kind = remember(category.name) { resolveCategoryKind(category) }
    val accentColor = categoryAccentColor(kind)
    val kindLabel = when (kind) {
        CategoryKind.Income -> "Ingreso"
        CategoryKind.Expense -> "Gasto"
        CategoryKind.Other -> "Categoría"
    }
    val categoryIcon = resolveCategoryIcon(category.iconKey, category.name, kind)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val maxPreviewChildren = remember(screenWidthDp) {
        if (screenWidthDp < 360) 1 else 2
    }
    val previewChildren = remember(children, maxPreviewChildren) { children.take(maxPreviewChildren) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(
            elevation.level1,
            accentColor.copy(alpha = 0.10f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.level2),
        shape = RoundedCornerShape(shapes.extraLarge)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(spacing.xxs)
                    .fillMaxHeight()
                    .background(accentColor.copy(alpha = 0.85f))
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = children.isNotEmpty(), onClick = onToggleExpand)
                        .padding(horizontal = spacing.m, vertical = spacing.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(shapes.large),
                        color = accentColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(spacing.xl + spacing.xs)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                categoryIcon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(spacing.s + spacing.xxs)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(spacing.s))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            category.name,
                            style = typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(spacing.xxs))
                        Text(
                            if (children.isNotEmpty()) "$kindLabel • ${children.size} subcategorías" else kindLabel,
                            style = typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (previewChildren.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(spacing.xs))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                previewChildren.forEach { child ->
                                    PreviewChip(
                                        label = child.name,
                                        icon = resolveCategoryIcon(child.iconKey, child.name, kind),
                                        accentColor = accentColor
                                    )
                                }
                                if (children.size > previewChildren.size) {
                                    PreviewChip(
                                        label = "+${children.size - previewChildren.size}",
                                        icon = Icons.Default.MoreHoriz,
                                        accentColor = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(spacing.xs))

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        if (children.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(shapes.extraLarge),
                                color = accentColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${children.size}",
                                    modifier = Modifier.padding(horizontal = spacing.s, vertical = spacing.xxs + spacing.xxs / 2),
                                    style = typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (children.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(shapes.large),
                                    color = accentColor.copy(alpha = 0.10f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = spacing.s, vertical = spacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(spacing.xxs)
                                    ) {
                                        Text(
                                            text = if (isExpanded) "Ocultar" else "Ver",
                                            style = typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = accentColor
                                        )
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                                            tint = accentColor,
                                            modifier = Modifier.size(spacing.s)
                                        )
                                    }
                                }
                            }

                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    containerColor = Color.White,
                                    tonalElevation = elevation.level0
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Agregar subcategoría") },
                                        onClick = {
                                            showMenu = false
                                            onAddSubcategory()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Renombrar") },
                                        onClick = {
                                            showMenu = false
                                            showRenameDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Eliminar") },
                                        onClick = {
                                            showMenu = false
                                            onDelete()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (isExpanded && children.isNotEmpty()) {
                    HorizontalDivider(
                        color = accentColor.copy(alpha = 0.12f)
                    )
                    Column(
                        modifier = Modifier.padding(horizontal = spacing.s, vertical = spacing.s),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        children.forEach { child ->
                            SubcategoryItem(
                                category = child,
                                kind = kind,
                                onRename = { newName, iconKey -> onRenameChild(child.id, newName, iconKey) },
                                onDelete = { onDeleteChild(child.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        CategoryEditorDialog(
            title = "Renombrar categoría",
            initialName = category.name,
            initialIconKey = category.iconKey,
            showKindSelector = true,
            initialKind = category.kind,
            confirmLabel = "Guardar",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName, iconKey, kind ->
                onRename(newName, iconKey, kind)
                showRenameDialog = false
            }
        )
    }
}

@Composable
private fun SubcategoryItem(
    category: CategoryEntity,
    kind: CategoryKind,
    onRename: (String, String?) -> Unit,
    onDelete: () -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val accentColor = categoryAccentColor(kind)
    val itemIcon = resolveCategoryIcon(category.iconKey, category.name, kind)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(shapes.large)),
        color = accentColor.copy(alpha = 0.06f),
        shape = RoundedCornerShape(shapes.large)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.s, end = spacing.xs, top = spacing.s, bottom = spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(shapes.medium),
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(spacing.xl + spacing.xxs)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        itemIcon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(spacing.s)
                    )
                }
            }

            Spacer(modifier = Modifier.width(spacing.s))

            Text(
                category.name,
                style = typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(spacing.xl)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        modifier = Modifier.size(spacing.s)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = Color.White,
                    tonalElevation = elevation.level0
                ) {
                    DropdownMenuItem(
                        text = { Text("Renombrar") },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        CategoryEditorDialog(
            title = "Renombrar subcategoría",
            initialName = category.name,
            initialIconKey = category.iconKey,
            showKindSelector = false,
            initialKind = null,
            confirmLabel = "Guardar",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName, iconKey, _ ->
                onRename(newName, iconKey)
                showRenameDialog = false
            }
        )
    }
}

@Composable
private fun AddCategoryDialog(
    isSubcategory: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String?) -> Unit
) {
    CategoryEditorDialog(
        title = if (isSubcategory) "Nueva subcategoría" else "Nueva categoría",
        initialName = "",
        initialIconKey = null,
        showKindSelector = !isSubcategory,
        initialKind = "EXPENSE",
        confirmLabel = "Crear",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

private data class CategoryIconOption(
    val key: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val categoryIconOptions = listOf(
    CategoryIconOption("folder", "General", Icons.Default.Folder),
    CategoryIconOption("shopping_cart", "Compras", Icons.Default.ShoppingCart),
    CategoryIconOption("directions_car", "Transporte", Icons.Default.DirectionsCar),
    CategoryIconOption("lightbulb", "Servicios", Icons.Default.Lightbulb),
    CategoryIconOption("home", "Hogar", Icons.Default.Home),
    CategoryIconOption("local_hospital", "Salud", Icons.Default.LocalHospital),
    CategoryIconOption("local_activity", "Ocio", Icons.Default.LocalActivity),
    CategoryIconOption("school", "Educación", Icons.Default.School),
    CategoryIconOption("savings", "Ahorro", Icons.Default.Savings),
    CategoryIconOption("account_balance", "Banca", Icons.Default.AccountBalance),
    CategoryIconOption("payments", "Ingresos", Icons.Default.Payments),
    CategoryIconOption("receipt_long", "Gastos", Icons.Default.ReceiptLong),
    CategoryIconOption("trending_up", "Tendencia +", Icons.Default.TrendingUp),
    CategoryIconOption("trending_down", "Tendencia -", Icons.Default.TrendingDown),
    CategoryIconOption("restaurant", "Comida", Icons.Default.Restaurant),
    CategoryIconOption("work", "Trabajo", Icons.Default.Work),
    CategoryIconOption("computer", "Tecnología", Icons.Default.Computer),
    CategoryIconOption("handyman", "Reparaciones", Icons.Default.Handyman),
    CategoryIconOption("plumbing", "Plomería", Icons.Default.Plumbing),
    CategoryIconOption("electric_bolt", "Eléctrico", Icons.Default.ElectricBolt),
    CategoryIconOption("key", "Llaves", Icons.Default.Key),
    CategoryIconOption("construction", "Construcción", Icons.Default.Construction),
    CategoryIconOption("engineering", "Ingeniería", Icons.Default.Engineering),
    CategoryIconOption("build", "Herramientas", Icons.Default.Build),
    CategoryIconOption("business_center", "Negocio", Icons.Default.BusinessCenter),
    CategoryIconOption("pets", "Mascotas", Icons.Default.Pets),
    CategoryIconOption("flight", "Viajes", Icons.Default.Flight)
)

@Composable
private fun CategoryEditorDialog(
    title: String,
    initialName: String,
    initialIconKey: String?,
    showKindSelector: Boolean,
    initialKind: String?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String?) -> Unit
) {
    val colors = XpendzThemeTokens.colors
    val spacing = XpendzThemeTokens.spacing
    val shapes = XpendzThemeTokens.shapes
    val elevation = XpendzThemeTokens.elevation
    val typography = XpendzThemeTokens.typography

    var name by remember(initialName) { mutableStateOf(initialName) }
    var selectedIconKey by remember(initialIconKey) { mutableStateOf(initialIconKey) }
    var kind by remember(initialKind) {
        mutableStateOf(initialKind?.trim()?.uppercase()?.takeIf { it == "INCOME" || it == "EXPENSE" })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showKindSelector) {
                    Text(
                        text = "Tipo",
                        style = typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = kind == "INCOME",
                            onClick = { kind = "INCOME" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Ingreso")
                        }
                        SegmentedButton(
                            selected = kind == "EXPENSE",
                            onClick = { kind = "EXPENSE" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Egreso")
                        }
                    }
                }

                Text(
                    text = "Icono",
                    style = typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s)
                ) {
                    items(categoryIconOptions) { option ->
                        val selected = selectedIconKey == option.key
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(spacing.xs),
                            modifier = Modifier
                                .widthIn(min = spacing.xxxl)
                                .clickable {
                                    selectedIconKey = if (selected) null else option.key
                                }
                        ) {
                            Surface(
                                modifier = Modifier.size(spacing.xl + spacing.xs),
                                shape = CircleShape,
                                color = if (selected) {
                                    colors.brand.copy(alpha = 0.14f)
                                } else {
                                    colors.surfaceVariant.copy(alpha = 0.55f)
                                },
                                border = BorderStroke(
                                    width = if (selected) spacing.xxs else spacing.xxs / 2,
                                    color = if (selected) colors.brand else colors.onSurface.copy(alpha = 0.12f)
                                ),
                                tonalElevation = elevation.level0,
                                shadowElevation = elevation.level0
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(spacing.s),
                                        tint = if (selected) colors.brand else colors.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = option.label,
                                style = typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (selected) colors.brand else colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), selectedIconKey, kind)
                    }
                },
                enabled = name.isNotBlank() && (!showKindSelector || kind != null)
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
