package com.areca.launcher.ui

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.net.Uri
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.areca.launcher.ArecaApp
import com.areca.launcher.data.FOLDER_PACKAGE
import com.areca.launcher.data.FOLDER_PREFIX
import com.areca.launcher.data.Folder
import java.util.UUID
import com.areca.launcher.data.AppInfo
import com.areca.launcher.data.GridLayout
import com.areca.launcher.data.IconPackInfo
import com.areca.launcher.data.IconShape
import com.areca.launcher.data.LauncherSettings
import com.areca.launcher.data.SystemGestures
import com.areca.launcher.data.AccentColor
import com.areca.launcher.data.ThemeMode
import com.areca.launcher.data.WallpaperMode
import com.areca.launcher.data.Backup
import com.areca.launcher.data.BackupManager
import com.areca.launcher.data.NotificationBadges
import com.areca.launcher.data.LanguageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

enum class Screen { HOME, APPS, SETTINGS, ABOUT }

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ArecaApp
    private val settingsRepo = app.settingsRepository
    private val appRepo = app.appRepository

    val iconLoader = app.iconLoader.also { loader -> loader.overrideOf = { key -> settings.value.iconOverrides[key] } }

    val settings: StateFlow<LauncherSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    val allApps: StateFlow<List<AppInfo>> = appRepo.apps

    /** Favoritas en el orden elegido, omitiendo apps desinstaladas u ocultas. */
    val favorites: StateFlow<List<AppInfo>> = combine(allApps, settings) { apps, s ->
        val byKey = apps.associateBy { it.key }
        val foldersByKey = s.folders.associateBy { it.key }
        s.favorites.mapNotNull { key ->
            if (key.startsWith(FOLDER_PREFIX)) {
                // Una carpeta es una burbuja más; sin apps visibles no se muestra.
                foldersByKey[key]?.let { f ->
                    val members = f.apps.mapNotNull { byKey[it] }.filter { it.key !in s.hidden }
                    if (members.isEmpty()) null
                    else AppInfo(key = f.key, label = f.name, packageName = FOLDER_PACKAGE, className = f.id, folderMembers = members)
                }
            } else byKey[key]?.takeIf { it.key !in s.hidden }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Carpetas ---
    private val _openFolderKey = MutableStateFlow<String?>(null)
    /** La carpeta abierta (su panel con las apps), o null. */
    val openFolder: StateFlow<AppInfo?> = combine(favorites, _openFolderKey) { favs, key ->
        key?.let { k -> favs.firstOrNull { it.key == k } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun closeFolder() { _openFolderKey.value = null }

    private val _folderChooserApp = MutableStateFlow<AppInfo?>(null)
    /** App que se va a meter en una carpeta (el cuadro para elegir o crear una). */
    val folderChooserApp: StateFlow<AppInfo?> = _folderChooserApp.asStateFlow()
    fun chooseFolderFor(app: AppInfo) { _folderChooserApp.value = app }
    fun dismissFolderChooser() { _folderChooserApp.value = null }

    private val _renameFolderKey = MutableStateFlow<String?>(null)
    val renameFolderKey: StateFlow<String?> = _renameFolderKey.asStateFlow()
    fun startRenameFolder(folderKey: String) { _renameFolderKey.value = folderKey }
    fun dismissRenameFolder() { _renameFolderKey.value = null }

    /** Guarda favoritas y carpetas juntas, quitando carpetas vacías y claves de carpetas que ya no existen. */
    private suspend fun saveFavorites(favorites: List<String>, folders: List<Folder>) {
        val alive = folders.filter { it.apps.isNotEmpty() }
        val keys = alive.map { it.key }.toSet()
        settingsRepo.setFavoritesAndFolders(
            favorites.filter { !it.startsWith(FOLDER_PREFIX) || it in keys }.distinct(),
            alive,
        )
    }

    fun createFolder(name: String, first: AppInfo) = update {
        val s = settings.value
        val folder = Folder(UUID.randomUUID().toString(), name.trim().ifEmpty { DEFAULT_FOLDER_NAME }, listOf(first.key))
        val others = s.folders.map { it.copy(apps = it.apps - first.key) }
        // La carpeta ocupa el lugar de la app si ya estaba en el escritorio.
        val favs = if (first.key in s.favorites) s.favorites.map { if (it == first.key) folder.key else it } else s.favorites + folder.key
        saveFavorites(favs, others + folder)
    }

    fun addToFolder(folderKey: String, app: AppInfo) = update {
        val s = settings.value
        val folders = s.folders.map { f ->
            if (f.key == folderKey) f.copy(apps = (f.apps - app.key) + app.key) else f.copy(apps = f.apps - app.key)
        }
        saveFavorites(s.favorites - app.key, folders)
    }

    /** Saca una app de su carpeta; con [keepOnDesktop] queda en el escritorio, justo donde estaba la carpeta. */
    fun removeFromFolder(folderKey: String, appKey: String, keepOnDesktop: Boolean = true) = update {
        val s = settings.value
        val folders = s.folders.map { if (it.key == folderKey) it.copy(apps = it.apps - appKey) else it }
        val favs = if (keepOnDesktop) s.favorites.flatMap { if (it == folderKey) listOf(it, appKey) else listOf(it) } else s.favorites
        saveFavorites(favs, folders)
    }

    fun renameFolder(folderKey: String, name: String) = update {
        val s = settings.value
        settingsRepo.setFavoritesAndFolders(
            s.favorites,
            s.folders.map { if (it.key == folderKey) it.copy(name = name.trim().ifEmpty { it.name }) else it },
        )
    }

    /** Deshace la carpeta: sus apps pasan al escritorio en su lugar. */
    fun dissolveFolder(folderKey: String) = update {
        val s = settings.value
        val members = s.folders.firstOrNull { it.key == folderKey }?.apps.orEmpty()
        val favs = s.favorites.flatMap { if (it == folderKey) members.filter { m -> m !in s.favorites } else listOf(it) }
        saveFavorites(favs, s.folders.filter { it.key != folderKey })
        if (_openFolderKey.value == folderKey) closeFolder()
    }

    /** Apps no ocultas, para el listado de apps instaladas. */
    val visibleApps: StateFlow<List<AppInfo>> = combine(allApps, settings) { apps, s ->
        apps.filter { it.key !in s.hidden }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Precarga de iconos de las favoritas, que son las que se ven al abrir. El listado completo no se
    // precarga: cargar todos los iconos del sistema a la vez llega a triplicar la memoria nativa.
    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(favorites, settings) { favs, s -> Triple(favs, s.iconPack, s.iconOverrides) }
                .collectLatest { (apps, pack, overrides) ->
                    for (app in apps) {
                        iconLoader.load(app, pack, overrides[app.key])
                        yield()
                    }
                }
        }
    }

    private val _homeTick = MutableStateFlow(0)
    /** Se incrementa con cada "ir a inicio" para volver a la primera página. */
    val homeTick: StateFlow<Int> = _homeTick.asStateFlow()

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    // --- Menú del escritorio (mantener pulsado un espacio vacío) ---
    private val _homeMenuOpen = MutableStateFlow(false)
    val homeMenuOpen: StateFlow<Boolean> = _homeMenuOpen.asStateFlow()
    fun openHomeMenu() { _homeMenuOpen.value = true }
    fun closeHomeMenu() { _homeMenuOpen.value = false }

    // --- Widgets ---
    val widgets = app.widgetController
    /** La activity registra aquí cómo enlazar/configurar un widget (necesita startActivityForResult). */
    var widgetStarter: ((AppWidgetProviderInfo) -> Unit)? = null
    private val _widgetPickerOpen = MutableStateFlow(false)
    val widgetPickerOpen: StateFlow<Boolean> = _widgetPickerOpen.asStateFlow()
    fun openWidgetPicker() { _widgetPickerOpen.value = true }
    fun closeWidgetPicker() { _widgetPickerOpen.value = false }
    fun pickWidget(provider: AppWidgetProviderInfo) {
        closeWidgetPicker()
        widgetStarter?.invoke(provider)
    }
    /** Alto con el que se muestra un widget en el escritorio (el mínimo que declara su proveedor). */
    fun widgetHeightDp(id: Int): Dp {
        settings.value.widgetHeights[id]?.let { return it.dp }
        return defaultWidgetHeight(id).dp
    }

    private fun defaultWidgetHeight(id: Int): Int {
        val info = widgets.info(id) ?: return 80
        return (info.minHeight / app.resources.displayMetrics.density).toInt().coerceIn(MIN_WIDGET_DP, MAX_WIDGET_DP)
    }

    // Menú de cada widget (mover, alto, configurar, quitar): lo abre la pulsación larga sobre el widget.
    private val _widgetMenuId = MutableStateFlow<Int?>(null)
    val widgetMenuId: StateFlow<Int?> = _widgetMenuId.asStateFlow()
    fun openWidgetMenu(id: Int) { _widgetMenuId.value = id }
    fun closeWidgetMenu() { _widgetMenuId.value = null }

    /** Ancho elegido para el widget; null = el máximo que cabe. */
    fun widgetWidthDp(id: Int): Dp? = settings.value.widgetWidths[id]?.dp

    fun setWidgetHeight(id: Int, dp: Int) = update {
        settingsRepo.setWidgetHeights(settings.value.widgetHeights + (id to dp.coerceIn(MIN_WIDGET_DP, MAX_WIDGET_DP)))
    }

    fun setWidgetWidth(id: Int, dp: Int) = update {
        settingsRepo.setWidgetWidths(settings.value.widgetWidths + (id to dp.coerceAtLeast(MIN_WIDGET_WIDTH_DP)))
    }

    /** Orden tras arrastrar; solo cambia el orden, no el lado (arriba/abajo) de cada widget. */
    fun setWidgetOrder(ids: List<Int>) = update {
        if (ids.toSet() == settings.value.widgetIds.toSet()) settingsRepo.setWidgetIds(ids)
    }

    fun moveWidget(id: Int, delta: Int) = update {
        val list = settings.value.widgetIds.toMutableList()
        val from = list.indexOf(id)
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return@update
        list.add(to, list.removeAt(from))
        settingsRepo.setWidgetIds(list)
    }

    fun toggleWidgetBottom(id: Int) = update {
        val bottom = settings.value.widgetBottomIds
        settingsRepo.setWidgetBottomIds(if (id in bottom) bottom - id else bottom + id)
    }

    fun resizeWidget(id: Int, deltaDp: Int) = update {
        val current = settings.value.widgetHeights[id] ?: defaultWidgetHeight(id)
        val next = (current + deltaDp).coerceIn(MIN_WIDGET_DP, MAX_WIDGET_DP)
        settingsRepo.setWidgetHeights(settings.value.widgetHeights + (id to next))
    }

    fun canConfigureWidget(id: Int): Boolean = widgets.info(id)?.configure != null

    /** La activity registra aquí cómo reabrir la pantalla de configuración de un widget. */
    var widgetConfigurer: ((Int) -> Unit)? = null
    fun configureWidget(id: Int) { widgetConfigurer?.invoke(id) }

    fun addWidgetId(id: Int) = update { settingsRepo.setWidgetIds(settings.value.widgetIds + id) }
    fun removeWidget(id: Int) = update {
        widgets.release(id)
        settingsRepo.setWidgetIds(settings.value.widgetIds - id)
        settingsRepo.setWidgetHeights(settings.value.widgetHeights - id)
        settingsRepo.setWidgetWidths(settings.value.widgetWidths - id)
        settingsRepo.setWidgetBottomIds(settings.value.widgetBottomIds - id)
    }

    // --- Fondo de pantalla del sistema ---
    private val _wallpaperDialogOpen = MutableStateFlow(false)
    val wallpaperDialogOpen: StateFlow<Boolean> = _wallpaperDialogOpen.asStateFlow()
    fun openWallpaperDialog() { _wallpaperDialogOpen.value = true }
    fun closeWallpaperDialog() { _wallpaperDialogOpen.value = false }

    // --- Gestos (bloquear, notificaciones) ---
    private val _accessibilityPrompt = MutableStateFlow(false)
    val accessibilityPrompt: StateFlow<Boolean> = _accessibilityPrompt.asStateFlow()
    fun dismissAccessibilityPrompt() { _accessibilityPrompt.value = false }
    private var promptedForRecents = false

    /** Apps abiertas; si falta el servicio de accesibilidad se avisa una sola vez por sesión (el gesto es frecuente). */
    fun openRecentApps() {
        if (SystemGestures.openRecents()) return
        if (!promptedForRecents) {
            promptedForRecents = true
            _accessibilityPrompt.value = true
        }
    }
    fun setBottomSwipeRecents(v: Boolean) = update { settingsRepo.setBottomSwipeRecents(v) }

    fun lockScreen() { if (!SystemGestures.lockScreen()) _accessibilityPrompt.value = true }
    fun openNotifications(context: Context) {
        if (!SystemGestures.openNotifications(context)) _accessibilityPrompt.value = true
    }
    fun setDoubleTapLock(v: Boolean) = update { settingsRepo.setDoubleTapLock(v) }
    fun setSwipeDownNotifications(v: Boolean) = update { settingsRepo.setSwipeDownNotifications(v) }

    /** Quita todos los iconos cambiados uno por uno (el paquete de iconos elegido no cambia). */
    /** Guarda un respaldo de la configuración en [uri]; [onDone] recibe si salió bien (en el hilo principal). */
    fun exportBackup(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = BackupManager.toJson(app, settings.value, LanguageStore.get(app))
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                }.isSuccess
            }
            onDone(ok)
        }
    }

    /** Aplica el respaldo de [uri]; [onDone] recibe el respaldo (null si el archivo no es válido). */
    fun importBackup(uri: Uri, onDone: (Backup?) -> Unit) {
        viewModelScope.launch {
            val backup = withContext(Dispatchers.IO) {
                runCatching {
                    val text = app.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    BackupManager.fromJson(app, text)
                }.getOrNull()
            }
            if (backup != null) {
                settingsRepo.restore(backup.settings)
                LanguageStore.set(app, backup.language)
            }
            onDone(backup)
        }
    }

    fun setIconZoom(percent: Int) = update { settingsRepo.setIconZoom(percent) }
    fun setAppIconZoom(app: AppInfo, percent: Int?) = update { settingsRepo.setAppIconZoom(app.key, percent) }

    fun resetIcons() {
        viewModelScope.launch(Dispatchers.IO) {
            this@HomeViewModel.app.imageStore.clearIcons()
            settingsRepo.clearIconOverrides()
            settingsRepo.clearIconZooms()
        }
    }

    // --- Iconos individuales ---
    private val _iconEditTarget = MutableStateFlow<AppInfo?>(null)
    val iconEditTarget: StateFlow<AppInfo?> = _iconEditTarget.asStateFlow()
    fun editIcon(target: AppInfo) { _iconEditTarget.value = target }
    fun closeIconEditor() { _iconEditTarget.value = null }

    /** [value] null restablece el icono original. */
    fun setIconOverride(target: AppInfo, value: String?) = update { settingsRepo.setIconOverride(target.key, value) }

    fun setIconFromImage(target: AppInfo, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            this@HomeViewModel.app.imageStore.saveIcon(uri)?.let { settingsRepo.setIconOverride(target.key, "file:$it") }
        }
    }

    fun packIcons(pack: String): List<String> = this.app.iconPackManager.allIcons(pack)

    private val _homePage = MutableStateFlow(HOME_PAGE)
    fun setHomePage(page: Int) { _homePage.value = page }

    /** El escritorio se ve sobre un fondo propio (oscuro): las barras del sistema llevan iconos claros. */
    val darkHomeBackdrop: StateFlow<Boolean> = combine(settings, _screen, _homePage) { s, screen, page ->
        s.wallpaper != WallpaperMode.SYSTEM && screen == Screen.HOME && page == HOME_PAGE
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** La barra de estado solo se oculta en el escritorio de burbujas (no en menús ni en el listado de apps). */
    val statusBarHidden: StateFlow<Boolean> = combine(settings, _screen, _homePage) { s, screen, page ->
        s.hideStatusBar && screen == Screen.HOME && page == HOME_PAGE
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun open(screen: Screen) { _screen.value = screen }
    fun goHome() {
        _screen.value = Screen.HOME
        _homeTick.value++
    }

    /** Apps abiertas hace poco desde Areca, para la fila de recientes del listado. */
    val recents: StateFlow<List<AppInfo>> = combine(app.usageStore.recent, allApps, settings) { keys, apps, s ->
        if (!s.showRecents) emptyList()
        else {
            val byKey = apps.associateBy { it.key }
            keys.mapNotNull { byKey[it] }.filter { it.key !in s.hidden }.take(RECENTS_COUNT)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun launch(app: AppInfo) {
        if (app.isFolder) { _openFolderKey.value = app.key; return }
        // Areca misma no cuenta como reciente.
        if (app.packageName != this.app.packageName) this.app.usageStore.record(app.key)
        appRepo.launch(app)
    }
    fun uninstall(app: AppInfo) = appRepo.uninstall(app)

    /** Acciones del menú de cada app, compartidas por el escritorio y el listado. */
    val menuActions = AppMenuActions(
        info = ::openAppInfo,
        hide = ::hide,
        editIcon = ::editIcon,
        uninstall = ::uninstall,
        loadShortcuts = { target -> withContext(Dispatchers.IO) { appRepo.shortcuts(target) } },
        startShortcut = appRepo::startShortcut,
        addToFolder = ::chooseFolderFor,
        renameFolder = { startRenameFolder(it.key) },
        dissolveFolder = { dissolveFolder(it.key) },
    )
    fun openAppInfo(app: AppInfo) = appRepo.openAppInfo(app)
    fun iconPacks(): List<IconPackInfo> = this.app.iconPackManager.installedPacks()

    private fun update(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    fun setColumns(v: Int) = update { settingsRepo.setColumns(v) }
    fun setIconSize(v: Int) = update { settingsRepo.setIconSize(v) }
    fun setSpacing(v: Int) = update { settingsRepo.setSpacing(v) }
    fun setIconPadding(v: Int) = update { settingsRepo.setIconPadding(v) }
    fun setIconShape(v: IconShape) = update { settingsRepo.setIconShape(v) }
    fun setLayout(v: GridLayout) = update { settingsRepo.setLayout(v) }
    fun setThemeMode(v: ThemeMode) = update { settingsRepo.setThemeMode(v) }
    fun setWallpaper(v: WallpaperMode) = update { settingsRepo.setWallpaper(v) }
    /** Usa una imagen de la galería como fondo del escritorio (reducida al tamaño de la pantalla). */
    fun setCustomWallpaper(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            this@HomeViewModel.app.imageStore.saveWallpaper(uri)?.let {
                settingsRepo.setCustomWallpaper(it)
                settingsRepo.setWallpaper(WallpaperMode.CUSTOM)
            }
        }
    }
    fun wallpaperFile(name: String) = app.imageStore.wallpaperFile(name)
    fun setWallpaperDim(v: Int) = update { settingsRepo.setWallpaperDim(v) }
    fun setAccent(v: AccentColor) = update { settingsRepo.setAccent(v) }
    fun setThemedIcons(v: Boolean) = update { settingsRepo.setThemedIcons(v) }
    fun setPackFallback(v: Boolean) = update { settingsRepo.setPackFallback(v) }
    fun setIconPack(v: String) = update { settingsRepo.setIconPack(v) }
    fun setClockY(v: Float) = update { settingsRepo.setClockY(v) }
    fun setHideStatusBar(v: Boolean) = update { settingsRepo.setHideStatusBar(v) }
    fun setShowClock(v: Boolean) = update { settingsRepo.setShowClock(v) }
    /** Cuentas de notificaciones por paquete; vacío si las insignias están apagadas. */
    val badgeCounts: StateFlow<Map<String, Int>> = combine(NotificationBadges.counts, settings) { counts, s ->
        if (s.showBadges) counts else emptyMap()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun setShowBadges(v: Boolean) = update { settingsRepo.setShowBadges(v) }

    fun setShowRecents(v: Boolean) {
        if (!v) app.usageStore.clear() // apagarlo también borra lo registrado
        update { settingsRepo.setShowRecents(v) }
    }

    fun toggleFavorite(app: AppInfo) = update {
        // Si está dentro de una carpeta, quitarla de favoritas es sacarla de la carpeta sin dejarla en el escritorio.
        settings.value.folders.firstOrNull { app.key in it.apps }?.let { folder ->
            val folders = settings.value.folders.map { if (it.key == folder.key) it.copy(apps = it.apps - app.key) else it }
            saveFavorites(settings.value.favorites, folders)
            return@update
        }
        val current = settings.value.favorites
        settingsRepo.setFavorites(if (app.key in current) current - app.key else current + app.key)
    }

    fun removeFavorite(app: AppInfo) = update {
        settingsRepo.setFavorites(settings.value.favorites - app.key)
    }

    fun toggleHidden(app: AppInfo) = update {
        val current = settings.value.hidden
        settingsRepo.setHidden(if (app.key in current) current - app.key else current + app.key)
    }

    fun hide(app: AppInfo) = update { settingsRepo.setHidden(settings.value.hidden + app.key) }

    /** Guarda el nuevo orden de las favoritas visibles; las no visibles (ocultas/desinstaladas) quedan al final. */
    fun setFavoriteOrder(visibleKeys: List<String>) = update {
        val rest = settings.value.favorites.filterNot { it in visibleKeys }
        settingsRepo.setFavorites(visibleKeys + rest)
    }

    companion object {
        /** Páginas del paginador: 0 escritorio (burbujas y widgets), 1 listado de apps. */
        const val HOME_PAGE = 0
        const val RECENTS_COUNT = 5
        const val DEFAULT_FOLDER_NAME = "Carpeta"
        const val MIN_WIDGET_WIDTH_DP = 160
        const val MIN_WIDGET_DP = 80
        const val MAX_WIDGET_DP = 420
    }
}
