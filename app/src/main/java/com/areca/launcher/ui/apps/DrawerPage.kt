package com.areca.launcher.ui.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextOverflow
import com.areca.launcher.data.AppInfo
import com.areca.launcher.data.LauncherSettings
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.text.style.TextAlign
import com.areca.launcher.data.DrawerStyle
import androidx.compose.foundation.layout.Arrangement
import java.text.Normalizer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areca.launcher.R
import com.areca.launcher.ui.IconImage
import com.areca.launcher.data.zoomFor
import com.areca.launcher.data.isFavorite
import com.areca.launcher.ui.AppContextMenu
import com.areca.launcher.ui.HomeViewModel
import com.areca.launcher.ui.Screen
import com.areca.launcher.ui.theme.toShape

/** Listado de todas las apps instaladas (excepto las ocultas). */
@Composable
fun DrawerPage(vm: HomeViewModel, active: Boolean) {
    val apps by vm.visibleApps.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(active) { if (!active) focusManager.clearFocus() }
    val folders by vm.drawerFolders.collectAsStateWithLifecycle()
    // Las apps de una carpeta salen del listado general; al buscar se encuentran todas, también las de carpetas.
    val inFolders = remember(settings.drawerFolders) { settings.drawerFolders.flatMapTo(HashSet()) { it.apps } }
    val filtered = remember(apps, query, inFolders) {
        if (query.isBlank()) apps.filter { it.key !in inFolders } else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    val shownFolders = if (query.isBlank()) folders else emptyList()

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        // En pantallas anchas (horizontal, tablet) las apps se reparten en varias columnas para aprovechar el ancho.
        val wide = maxWidth >= WIDE_DRAWER_DP.dp
        val compact = wide && maxHeight < COMPACT_HEIGHT_DP.dp // horizontal en teléfono: poco alto
        Column(
            Modifier.fillMaxHeight().then(if (wide) Modifier.fillMaxWidth() else Modifier.widthIn(max = 640.dp))
                .statusBarsPadding().navigationBarsPadding().displayCutoutPadding().imePadding(),
        ) {
            if (settings.drawerStyle == DrawerStyle.GRID) {
                LetteredGrid(
                    apps = filtered,
                    settings = settings,
                    vm = vm,
                    showRecents = !compact && recents.isNotEmpty() && query.isBlank(),
                    showIndex = settings.drawerLetterIndex && query.isBlank(),
                    grouped = settings.drawerGroupByLetter,
                    folders = shownFolders,
                    recentsContent = { RecentsRow(recents, settings, vm) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else if (wide) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive((if (compact) COMPACT_COLUMN_DP else DRAWER_COLUMN_DP).dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    if (!compact && recents.isNotEmpty() && query.isBlank()) {
                        item(key = "recents", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                                Box(Modifier.widthIn(max = 640.dp)) { RecentsRow(recents, settings, vm) }
                            }
                        }
                    }
                    items(shownFolders, key = { it.key }) { folder -> AppListItem(folder, settings, vm, horizontalPadding = 12.dp, verticalPadding = if (compact) 2.dp else 8.dp) }
                    items(filtered, key = { it.key }) { app -> AppListItem(app, settings, vm, horizontalPadding = 12.dp, verticalPadding = if (compact) 2.dp else 8.dp) }
                }
            } else {
                LetteredList(
                    apps = filtered,
                    folders = shownFolders,
                    showRecents = recents.isNotEmpty() && query.isBlank(),
                    showIndex = query.isBlank(),
                    recentsContent = { RecentsRow(recents, settings, vm) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { app -> AppListItem(app, settings, vm) }
            }
            var overflow by remember { mutableStateOf(false) }
            // En horizontal bajo, la barra inferior se parte en dos mitades: recientes y buscador.
            val showRecentsInBar = compact && recents.isNotEmpty() && query.isBlank()
            @Composable
            fun SearchAndMenu(modifier: Modifier) {
                Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                    ArecaSearchField(
                        query, { query = it }, stringResource(R.string.drawer_search),
                        Modifier.weight(1f).focusProperties { canFocus = active },
                    )
                    Box {
                        IconButton(onClick = { overflow = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_menu_pick_apps)) },
                                onClick = { overflow = false; vm.open(Screen.APPS) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_menu_settings)) },
                                onClick = { overflow = false; vm.open(Screen.SETTINGS) },
                            )
                        }
                    }
                }
            }
            if (showRecentsInBar) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RecentsRow(recents, settings, vm, compact = true, modifier = Modifier.weight(1f))
                    SearchAndMenu(Modifier.weight(1f))
                }
            } else {
                SearchAndMenu(
                    Modifier.then(if (wide) Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally) else Modifier)
                        .fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
                )
            }
        }
    }
}

/** Letra del índice de una app: la inicial sin acento; lo que no empieza con letra va en "#". */
private fun letterOf(label: String): String {
    val first = Normalizer.normalize(label.trim(), Normalizer.Form.NFD).firstOrNull { !it.isWhitespace() }?.uppercaseChar()
    return if (first != null && first in 'A'..'Z') first.toString() else "#"
}

private fun sortKey(label: String): String = Normalizer.normalize(label.trim(), Normalizer.Form.NFD).lowercase()

/**
 * Listado vertical agrupado por letra: encabezados que se quedan fijos arriba y, a la derecha, una barra con
 * las letras para saltar rápido. Tocar o arrastrar por la barra lleva a esa letra.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LetteredList(
    apps: List<AppInfo>,
    folders: List<AppInfo>,
    showRecents: Boolean,
    showIndex: Boolean,
    recentsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    item: @Composable (AppInfo) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // Ordenadas sin tener en cuenta acentos, para que "Álbum" quede con la A y no al final.
    val grouped = remember(apps) { apps.sortedBy { sortKey(it.label) }.groupBy { letterOf(it.label) } }
    val letters = remember(grouped) { grouped.keys.toList() }
    // Posición de cada encabezado en el listado (los recientes, si están, ocupan la primera).
    val headerIndex = remember(grouped, showRecents, folders.size) {
        var i = (if (showRecents) 1 else 0) + folders.size
        letters.associateWith { letter -> i.also { i += 1 + grouped.getValue(letter).size } }
    }
    val current by remember(headerIndex) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            letters.lastOrNull { headerIndex.getValue(it) <= first } ?: letters.firstOrNull()
        }
    }
    var touched by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    // La barra de letras empieza justo debajo de los recientes; al desplazarse y salir ellos de la vista, sube al borde.
    val indexTop by remember(showRecents) {
        derivedStateOf {
            if (!showRecents) 0
            else listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "recents" }
                ?.let { (it.offset + it.size).coerceAtLeast(0) } ?: 0
        }
    }

    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(end = if (showIndex) INDEX_WIDTH_DP.dp else 0.dp)) {
            if (showRecents) item(key = "recents") { recentsContent() }
            items(folders, key = { it.key }) { folder -> item(folder) }
            letters.forEach { letter ->
                stickyHeader(key = "header-$letter") {
                    Text(
                        letter,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }
                items(grouped.getValue(letter), key = { it.key }) { app -> item(app) }
            }
        }
        if (showIndex && letters.size > 1) {
            LetterIndex(
                letters = letters,
                current = current,
                onSelect = { letter ->
                    if (letter != touched) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    touched = letter
                    scope.launch { listState.scrollToItem(headerIndex.getValue(letter)) }
                },
                onRelease = { touched = null },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = with(density) { indexTop.toDp() }),
            )
            touched?.let { letter ->
                Box(
                    Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

/** Barra vertical de letras; la letra visible va resaltada. */
@Composable
private fun LetterIndex(
    letters: List<String>,
    current: String?,
    onSelect: (String) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLetters by rememberUpdatedState(letters)
    val select by rememberUpdatedState(onSelect)
    val release by rememberUpdatedState(onRelease)
    Column(
        modifier.width(INDEX_WIDTH_DP.dp).fillMaxHeight().padding(vertical = 8.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun pick(y: Float) {
                        val list = currentLetters
                        val i = (y / size.height * list.size).toInt().coerceIn(0, list.lastIndex)
                        select(list[i])
                    }
                    pick(down.position.y)
                    down.consume()
                    do {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        pick(change.position.y)
                        change.consume()
                    } while (change.pressed)
                    release()
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Text(
                letter,
                fontSize = 11.sp,
                fontWeight = if (letter == current) FontWeight.Bold else FontWeight.Normal,
                color = if (letter == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val INDEX_WIDTH_DP = 28

/** Ancho desde el que el listado pasa a varias columnas, y ancho mínimo de cada columna. */
private const val WIDE_DRAWER_DP = 600
private const val DRAWER_COLUMN_DP = 260
private const val COMPACT_HEIGHT_DP = 480
private const val COMPACT_COLUMN_DP = 220

/**
 * Cuadrícula de iconos agrupada por letra: los encabezados ocupan una fila completa y, a la derecha, la misma barra
 * de letras del listado. El icono se encoge si no cabe en la columna.
 */
@Composable
private fun LetteredGrid(
    apps: List<AppInfo>,
    settings: LauncherSettings,
    vm: HomeViewModel,
    showRecents: Boolean,
    showIndex: Boolean,
    grouped: Boolean,
    folders: List<AppInfo>,
    recentsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val byLetter = remember(apps) { apps.sortedBy { sortKey(it.label) }.groupBy { letterOf(it.label) } }
    val letters = remember(byLetter) { byLetter.keys.toList() }
    // Posición de la letra en la cuadrícula: con encabezados cada uno ocupa un elemento; sin ellos, la letra
    // apunta a su primera app. Los recientes, si están, ocupan el primero.
    val headerIndex = remember(byLetter, showRecents, grouped, folders.size) {
        // Las carpetas van fijas arriba y, si hay, un separador que cierra su fila.
        var i = (if (showRecents) 1 else 0) + folders.size + (if (folders.isNotEmpty()) 1 else 0)
        letters.associateWith { letter -> i.also { i += (if (grouped) 1 else 0) + byLetter.getValue(letter).size } }
    }
    val current by remember(headerIndex) {
        derivedStateOf {
            val first = gridState.firstVisibleItemIndex
            letters.lastOrNull { headerIndex.getValue(it) <= first } ?: letters.firstOrNull()
        }
    }
    var touched by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val indexTop by remember(showRecents) {
        derivedStateOf {
            if (!showRecents) 0
            else gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "recents" }
                ?.let { (it.offset.y + it.size.height).coerceAtLeast(0) } ?: 0
        }
    }
    val shape = remember(settings.iconShape) { settings.iconShape.toShape() }

    BoxWithConstraints(modifier) {
        val indexWidth = if (showIndex) INDEX_WIDTH_DP.dp else 0.dp
        val cellWidth = (maxWidth - indexWidth - GRID_SIDE_PADDING_DP.dp * 2) / settings.drawerColumns
        val iconSize = minOf(settings.drawerIconSize.dp, cellWidth - 8.dp).coerceAtLeast(32.dp)
        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.drawerColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = GRID_SIDE_PADDING_DP.dp, end = GRID_SIDE_PADDING_DP.dp + indexWidth),
        ) {
            if (showRecents) item(key = "recents", span = { GridItemSpan(maxLineSpan) }) { recentsContent() }
            items(folders, key = { it.key }) { folder -> AppGridItem(folder, settings, vm, shape, iconSize) }
            if (folders.isNotEmpty()) item(key = "folders-end", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(8.dp)) }
            letters.forEach { letter ->
                if (grouped) {
                    item(key = "header-$letter", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            letter,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                items(byLetter.getValue(letter), key = { it.key }) { app -> AppGridItem(app, settings, vm, shape, iconSize) }
            }
        }
        if (showIndex && letters.size > 1) {
            LetterIndex(
                letters = letters,
                current = current,
                onSelect = { letter ->
                    if (letter != touched) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    touched = letter
                    scope.launch { gridState.scrollToItem(headerIndex.getValue(letter)) }
                },
                onRelease = { touched = null },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = with(density) { indexTop.toDp() }),
            )
            touched?.let { letter ->
                Box(
                    Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

/** Una app de la cuadrícula: icono y, si se quiere, su nombre debajo; mantener pulsado abre su menú. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(app: AppInfo, settings: LauncherSettings, vm: HomeViewModel, shape: androidx.compose.ui.graphics.Shape, iconSize: androidx.compose.ui.unit.Dp) {
    var menu by remember { mutableStateOf(false) }
    val favorite = settings.isFavorite(app.key)
    Box {
        Column(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = { vm.launch(app) }, onLongClick = { menu = true })
                .padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(app, settings.iconPack, settings.iconOverrides[app.key], shape, settings.iconPaddingPercent, vm, settings.zoomFor(app.key), size = iconSize)
            if (settings.drawerShowLabels) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        AppContextMenu(menu, { menu = false }, app, vm.drawerMenuActions, isFavorite = favorite, onToggleFavorite = { vm.toggleFavorite(app) })
    }
}

private const val GRID_SIDE_PADDING_DP = 8

/** Una app del listado: icono y nombre; toque abre, mantener pulsado abre su menú. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListItem(app: AppInfo, settings: LauncherSettings, vm: HomeViewModel, horizontalPadding: androidx.compose.ui.unit.Dp = 24.dp, verticalPadding: androidx.compose.ui.unit.Dp = 8.dp) {
    var menu by remember { mutableStateOf(false) }
    val favorite = settings.isFavorite(app.key)
    Box {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = { vm.launch(app) }, onLongClick = { menu = true })
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app, settings.iconPack, settings.iconOverrides[app.key], remember(settings.iconShape) { settings.iconShape.toShape() }, settings.iconPaddingPercent, vm, settings.zoomFor(app.key))
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        AppContextMenu(menu, { menu = false }, app, vm.drawerMenuActions, isFavorite = favorite, onToggleFavorite = { vm.toggleFavorite(app) })
    }
}

/** Las últimas apps abiertas desde Areca, a un toque, antes del listado completo. */
@Composable
private fun RecentsRow(apps: List<AppInfo>, settings: LauncherSettings, vm: HomeViewModel, compact: Boolean = false, modifier: Modifier = Modifier) {
    val shape = remember(settings.iconShape) { settings.iconShape.toShape() }
    if (compact) {
        // Mitad de la barra inferior en horizontal: una línea de iconos, sin etiquetas.
        Row(
            modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            apps.forEach { app ->
                Box(Modifier.clickable { vm.launch(app) }.padding(4.dp)) {
                    AppIcon(app, settings.iconPack, settings.iconOverrides[app.key], shape, settings.iconPaddingPercent, vm, settings.zoomFor(app.key), contentDescription = app.label)
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            apps.forEach { app ->
                Column(
                    Modifier.weight(1f).clickable { vm.launch(app) }.padding(vertical = 6.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(app, settings.iconPack, settings.iconOverrides[app.key], shape, settings.iconPaddingPercent, vm, settings.zoomFor(app.key))
                    Text(
                        app.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            // Mantiene el ancho de cada celda cuando hay menos de cinco.
            repeat(HomeViewModel.RECENTS_COUNT - apps.size) { Spacer(Modifier.weight(1f)) }
        }
        // Separa los recientes del listado completo.
        HorizontalDivider(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
