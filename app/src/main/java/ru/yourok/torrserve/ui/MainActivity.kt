package ru.yourok.torrserve.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.GithubReleases
import ru.yourok.torrserve.server.api.SiloApi
import ru.yourok.torrserve.server.local.ServerFile
import ru.yourok.torrserve.settings.Prefs
import android.widget.Toast
import java.io.File
import kotlin.concurrent.thread

/**
 * Единая Activity: постоянная боковая панель + область контента.
 * Экраны - фрагменты: торренты, пользователи, настройки; детали торрента открываются поверх.
 * На ТВ/планшете (>= 600dp) панель с подписями, на телефоне - компактная с иконками.
 */
class MainActivity : AppCompatActivity() {

    enum class Screen { TORRENTS, USERS, SETTINGS }

    private lateinit var prefs: Prefs
    private lateinit var content: android.widget.FrameLayout
    private lateinit var headerTitle: TextView
    private lateinit var headerBack: ImageButton
    private lateinit var headerSearch: ImageButton
    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var sidebarUser: TextView
    private lateinit var sidebarServer: TextView

    private var current: Screen = Screen.TORRENTS
    private var inDetail = false

    /** На телефоне боковая панель - выдвижной ящик; на ТВ/планшете её нет. */
    private var drawer: DrawerLayout? = null

    /** null - ещё не проверяли, true/false - сервер отвечает/не отвечает. */
    private var serverOnline: Boolean? = null
    private var serverCheck = 0
    private var headerMenu: ImageButton? = null

    /** Проверка обновления сервера выполняется один раз за запуск приложения. */
    private var updateChecked = false

    /** Возврат с экрана входа: успешный вход сразу открывает список торрентов. */
    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) showScreen(Screen.TORRENTS) else updateSidebarState()
    }

    /** Последняя открытая карточка: к ней возвращаем фокус при выходе из деталей. */
    private var lastOpenedHash: String? = null
    private var onSearch: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        content = findViewById(R.id.content)
        headerTitle = findViewById(R.id.header_title)
        headerBack = findViewById(R.id.header_back)
        headerSearch = findViewById(R.id.header_search)
        searchBar = findViewById(R.id.search_bar)
        searchInput = findViewById(R.id.search_input)
        sidebarUser = findViewById(R.id.sidebar_user)
        sidebarServer = findViewById(R.id.sidebar_server)

        setupDrawer()
        applyResponsiveSidebar()
        setupNavigation()

        headerBack.setOnClickListener { goBack() }
        headerSearch.setOnClickListener {
            if (searchBar.visibility == View.VISIBLE) {
                searchBar.visibility = View.GONE
                searchInput.setText("")
                onSearch?.invoke("")
            } else {
                searchBar.visibility = View.VISIBLE
                searchInput.requestFocus()
            }
        }
        findViewById<ImageButton>(R.id.header_sort).setOnClickListener { chooseSort() }
        findViewById<ImageButton>(R.id.header_refresh).setOnClickListener {
            (currentFragment() as? Refreshable)?.onRefresh()
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                onSearch?.invoke(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })

        if (savedInstanceState == null) {
            showScreen(if (hasServer() && !needsLogin()) Screen.TORRENTS else Screen.SETTINGS)
        }
    }

    /** Есть рабочий сервер: назначен внешний или установлен локальный бинарник. */
    private fun hasServer(): Boolean {
        if (prefs.serverUrl.isBlank()) return false
        return if (prefs.isLocalServer) File(filesDir, ServerFile.BIN_NAME).exists() else true
    }

    /**
     * Сервер есть, сессии нет и сервер требует пароль.
     * Локальный сервер без пароля пускает как owner - вход для него не нужен.
     */
    private fun needsLogin(): Boolean {
        if (!hasServer() || prefs.activeSession() != null) return false
        if (prefs.isLocalServer && prefs.ownerPassword.isEmpty()) return false
        return true
    }

    /**
     * Видимость пунктов меню в трёх состояниях:
     * сервера нет и не назначен внешний - только настройки/донат/закрыть;
     * сервер требует пароль, входа нет - добавляется «Вход»;
     * есть рабочий сервер и сессия - полное меню.
     */
    private fun applyNavAvailability() {
        // Сервер не отвечает - меню как «сервера нет»
        val available = hasServer() && serverOnline != false
        val loginRequired = available && needsLogin()
        val ready = available && !loginRequired

        listOf(R.id.nav_torrents, R.id.nav_add, R.id.nav_delete_all, R.id.nav_category).forEach {
            findViewById<View>(it).visibility = if (ready) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.nav_login).visibility = if (loginRequired) View.VISIBLE else View.GONE
        findViewById<View>(R.id.sidebar_plugins).visibility = if (ready) View.VISIBLE else View.GONE
        // Заголовок секции плагинов прячем, пока нечего показывать (иначе висит пустым)
        if (!ready) findViewById<View>(R.id.sidebar_plugins_header).visibility = View.GONE
        val showStatus = ready
        sidebarUser.visibility = if (showStatus) View.VISIBLE else View.GONE
        sidebarServer.visibility = if (showStatus) View.VISIBLE else View.GONE
        findViewById<View>(R.id.header_sort).visibility = if (ready) View.VISIBLE else View.GONE
        findViewById<View>(R.id.header_refresh).visibility = if (ready) View.VISIBLE else View.GONE
        if (!ready) headerSearch.visibility = View.GONE
    }

    /**
     * Телефон (activity_main с DrawerLayout): панель - выдвижной ящик по гамбургеру,
     * контент занимает всю ширину. ТВ/планшет (layout-sw600dp): панель закреплена слева.
     */
    private fun setupDrawer() {
        drawer = findViewById(R.id.main_drawer)
        headerMenu = findViewById(R.id.header_menu)
        val d = drawer
        if (d != null) {
            val sidebar = findViewById<View>(R.id.sidebar)
            val width = minOf(dp(320), (resources.displayMetrics.widthPixels * 0.86f).toInt())
            sidebar.layoutParams = DrawerLayout.LayoutParams(
                width.coerceAtLeast(dp(240)),
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply { gravity = GravityCompat.START }
            d.setScrimColor(0x99000000.toInt())
            headerMenu?.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    if (d.isDrawerOpen(GravityCompat.START)) {
                        d.closeDrawer(GravityCompat.START)
                    } else {
                        d.openDrawer(GravityCompat.START)
                    }
                }
            }
        } else {
            headerMenu?.visibility = View.GONE
        }
    }

    /** На широком экране панель фиксированной ширины; на телефоне шириной управляет ящик. */
    private fun applyResponsiveSidebar() {
        val sidebar = findViewById<LinearLayout>(R.id.sidebar)
        if (drawer == null) sidebar.layoutParams.width = dp(260)
    }

    /** Действие пункта меню: на телефоне сначала закрываем ящик. */
    private fun nav(id: Int, action: () -> Unit) {
        findViewById<View>(id).setOnClickListener {
            drawer?.closeDrawers()
            action()
        }
    }

    private fun setupNavigation() {
        nav(R.id.nav_torrents) { showScreen(Screen.TORRENTS) }
        nav(R.id.nav_add) {
            AddTorrentDialog.show(this) {
                (currentFragment() as? TorrentsFragment)?.onTorrentAdded()
            }
        }
        nav(R.id.nav_donate) { showDonate() }
        nav(R.id.nav_exit) { confirmExit() }
        nav(R.id.nav_delete_all) { confirmDeleteAll() }
        nav(R.id.nav_category) { chooseCategory() }
        nav(R.id.nav_settings) { showScreen(Screen.SETTINGS) }
        nav(R.id.nav_login) { loginLauncher.launch(LoginActivity.intent(this)) }
    }

    fun showScreen(screen: Screen) {
        current = screen
        inDetail = false
        headerBack.visibility = View.GONE
        searchBar.visibility = View.GONE
        onSearch = null

        val fragment: Fragment = when (screen) {
            Screen.TORRENTS -> TorrentsFragment().also { it.restoreFocusHash = lastOpenedHash }
            Screen.USERS -> UsersFragment()
            Screen.SETTINGS -> SettingsFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .commitAllowingStateLoss()

        headerTitle.text = when (screen) {
            Screen.TORRENTS -> getString(R.string.nav_torrents)
            Screen.USERS -> getString(R.string.nav_users)
            Screen.SETTINGS -> getString(R.string.nav_settings)
        }
        headerSearch.visibility = if (screen == Screen.TORRENTS) View.VISIBLE else View.GONE
        highlightNav(screen)
        updateSidebarState()
    }

    /** Открыть карточку торрента. */
    fun openTorrent(hash: String) {
        inDetail = true
        lastOpenedHash = hash
        headerBack.visibility = View.VISIBLE
        searchBar.visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, TorrentDetailFragment.newInstance(hash))
            .commitAllowingStateLoss()
    }

    private fun goBack() {
        if (inDetail) showScreen(Screen.TORRENTS) else showScreen(current)
    }

    override fun onBackPressed() {
        if (drawer?.isDrawerOpen(GravityCompat.START) == true) {
            drawer?.closeDrawers()
        } else if (overlay != null) {
            preloader?.cancel()
            preloader = null
            pendingPlay = null
            hideOverlay()
        } else if (inDetail) {
            showScreen(Screen.TORRENTS)
        } else if (searchBar.visibility == View.VISIBLE) {
            searchBar.visibility = View.GONE
        } else if (current != Screen.TORRENTS) {
            showScreen(Screen.TORRENTS)
        } else {
            super.onBackPressed()
        }
    }

    fun setSearchListener(listener: (String) -> Unit) {
        onSearch = listener
    }

    /** Заголовок экрана (например, название торрента в карточке). */
    fun setHeaderTitle(text: String) {
        headerTitle.text = text
    }

    fun refreshCurrent() {
        (currentFragment() as? Refreshable)?.onRefresh()
    }

    override fun onResume() {
        super.onResume()
        updateSidebarState()
        loadPluginMenu()
        refreshServerState()
        checkUpdates()
    }

    /** Переключение активного пользователя без выхода из приложения. */
    fun switchUser(id: String) {
        if (prefs.isOpenLocalMode) {
            // Открытый сервер всегда owner: переключение ни на что не влияет
            Toast.makeText(this, R.string.open_mode_switch_denied, Toast.LENGTH_LONG).show()
            return
        }
        prefs.activeUserId = id
        updateSidebarState()
        // Меню плагинов зависит от прав пользователя - перечитываем
        loadPluginMenu()
        showScreen(current)
    }

    /**
     * Проверка живости сервера: если не отвечает - меню сворачивается до состояния
     * «сервера нет» (только настройки/донат/закрыть). Проверка в фоне, UI не блокируем.
     */
    fun refreshServerState() {
        if (!hasServer()) {
            setServerOnline(false)
            return
        }
        val gen = ++serverCheck
        val url = prefs.serverUrl
        Thread {
            val ok = SiloApi(url).ping()
            runOnUiThread {
                if (isFinishing || gen != serverCheck) return@runOnUiThread
                setServerOnline(ok)
            }
        }.start()
    }

    /** Список загрузился - сервер точно жив. */
    fun onServerReachable() = setServerOnline(true)

    /** Сеть/соединение упало - показываем меню как без сервера. */
    fun onServerUnreachable() = setServerOnline(false)

    private fun setServerOnline(ok: Boolean) {
        if (serverOnline == ok) return
        serverOnline = ok
        applyNavAvailability()
        if (ok) loadPluginMenu()
    }

    /** Донат: ссылки как в веб-версии. */
    private fun showDonate() {
        val links = arrayOf(
            "Boosty" to "https://boosty.to/yourok",
            "YooMoney" to "https://yoomoney.ru/to/410013733697114/200",
            "T-Bank" to "https://www.tbank.ru/cf/742qEMhKhKn",
        )
        val view = layoutInflater.inflate(R.layout.dialog_donate, null)
        val buttons = listOf(
            view.findViewById<View>(R.id.donate_boosty),
            view.findViewById<View>(R.id.donate_yoomoney),
            view.findViewById<View>(R.id.donate_tbank),
        )
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.donate_title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        // На телефоне три кнопки в строку не влезают - столбиком
        if (resources.configuration.screenWidthDp < WIDE_DP) {
            val row = view.findViewById<LinearLayout>(R.id.donate_row)
            row.orientation = LinearLayout.VERTICAL
            buttons.forEachIndexed { index, button ->
                button.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { if (index > 0) topMargin = dp(8) }
            }
        }
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(links[index].second),
                )
                runCatching { startActivity(intent) }
            }
        }
        dialog.show()
    }

    /** Выход: останавливаем локальный сервер и закрываем приложение. */
    private fun confirmExit() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.exit_title)
            .setMessage(R.string.exit_message)
            .setPositiveButton(R.string.nav_exit) { _, _ ->
                if (prefs.isLocalServer) {
                    ru.yourok.torrserve.server.local.ServerFile(this).stop()
                    ru.yourok.torrserve.server.local.TorrService.stop(this)
                }
                finishAffinity()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Удаление всех торрентов с подтверждением. */
    private fun confirmDeleteAll() {
        val fragment = currentFragment() as? TorrentsFragment
        if (fragment == null) {
            showScreen(Screen.TORRENTS)
            Toast.makeText(this, R.string.torrents_nothing_found, Toast.LENGTH_SHORT).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setMessage(R.string.delete_all_confirm)
            .setPositiveButton(R.string.torrent_delete) { _, _ ->
                fragment.deleteAll { count ->
                    Toast.makeText(
                        this,
                        getString(R.string.delete_all_done, count),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Фильтр по категории: список категорий текущих торрентов. */
    private fun chooseCategory() {
        val fragment = currentFragment() as? TorrentsFragment ?: return
        val cats = mutableListOf(getString(R.string.category_all))
        cats += fragment.categories()
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.nav_category)
            .setItems(cats.toTypedArray()) { _, which ->
                fragment.setCategory(if (which == 0) "" else cats[which])
            }
            .show()
    }

    /** Сортировка списка. */
    private fun chooseSort() {
        val modes = arrayOf(
            getString(R.string.sort_by_date),
            getString(R.string.sort_by_title),
            getString(R.string.sort_by_size),
            getString(R.string.sort_by_speed),
        )
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.sort_title)
            .setSingleChoiceItems(modes, prefs.sortMode) { dialog, which ->
                prefs.sortMode = which
                (currentFragment() as? TorrentsFragment)?.onSortChanged()
                dialog.dismiss()
            }
            .show()
    }

    /** Меню плагинов: сервер отдаёт только то, что разрешено текущему пользователю. */
    fun loadPluginMenu() {
        // Без сервера или без входа меню плагинов нечего показывать
        if (!hasServer() || needsLogin()) {
            populatePluginMenu(emptyList())
            return
        }
        val token = prefs.activeToken
        val url = prefs.serverUrl
        Thread {
            val menu = try {
                SiloApi(url).uiMenu(token)
            } catch (e: Exception) {
                emptyList()
            }
            runOnUiThread { if (!isFinishing) populatePluginMenu(menu) }
        }.start()
    }

    private fun populatePluginMenu(menu: List<SiloApi.MenuItem>) {
        val container = findViewById<LinearLayout>(R.id.sidebar_plugins)
        val header = findViewById<View>(R.id.sidebar_plugins_header)
        container.removeAllViews()
        header.visibility = if (menu.isEmpty()) View.GONE else View.VISIBLE
        menu.forEach { entry ->
            val item = layoutInflater.inflate(R.layout.item_nav_plugin, container, false)
            val title = entry.title.ifBlank { entry.pluginName }
            item.findViewById<TextView>(R.id.plugin_item_title).text = title
            val icon = item.findViewById<android.widget.ImageView>(R.id.plugin_item_icon)
            // Иконки плагинов обычно SVG - BitmapFactory их не умеет, оставляем стандартную
            val iconUrl = prefs.serverUrl + entry.icon
            if (entry.icon.isNotBlank() && !entry.icon.endsWith(".svg", ignoreCase = true)) {
                PosterLoader.load(this, iconUrl, icon)
            }
            item.setOnClickListener {
                startActivity(PluginActivity.intent(this, entry.href, title))
            }
            container.addView(item)
        }
    }

    fun updateSidebarState() {
        val session = prefs.activeSession()
        sidebarUser.text = when {
            // Открытый режим: сервер выполняет всё как owner, токены пользователей не действуют
            prefs.isOpenLocalMode -> LocalAuth.OWNER_ID
            session != null -> session.username
            prefs.isLocalServer -> getString(R.string.sidebar_local_no_login)
            else -> getString(R.string.sidebar_no_user)
        }
        sidebarServer.text = prefs.serverUrl
        applyNavAvailability()
        // Работать не с чем (сервера нет или нужен вход) - показываем настройки
        if ((!hasServer() || needsLogin()) && current == Screen.TORRENTS) showScreen(Screen.SETTINGS)
    }

    private fun highlightNav(screen: Screen) {
        val map = mapOf(
            Screen.TORRENTS to (R.id.nav_torrents to R.id.nav_torrents_label),
            Screen.SETTINGS to (R.id.nav_settings to R.id.nav_settings_label),
        )
        map.forEach { (key, ids) ->
            val item = findViewById<View>(ids.first)
            val label = findViewById<TextView>(ids.second)
            val active = key == screen
            item.isSelected = active
            label.setTextColor(
                getColor(if (active) R.color.silo_amber else R.color.silo_steel)
            )
        }
    }

    private fun currentFragment(): Fragment? =
        supportFragmentManager.findFragmentById(R.id.content)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /**
     * Проверка обновлений сервера и приложения на GitHub.
     *
     * Всё берётся из Atom-лент релизов: клиент - YouROK/TorrServe, сервер - YouROK/TorrServer.
     * Клиент проверяется один раз за запуск, результат кешируется на 6 часов
     * (`Prefs.updateCheckedAt`), чтобы не дёргать GitHub на каждом открытии окна.
     */
    private fun checkUpdates() {
        if (updateChecked) return
        updateChecked = true

        val localServer = hasServer() && prefs.isLocalServer
        Thread {
            if (localServer) checkServerUpdate()
            when (val result = AppUpdater.check(this)) {
                is AppUpdater.Result.Available -> runOnUiThread {
                    if (!isFinishing && !isDestroyed) UpdateDialog.show(this, result.update)
                }
                is AppUpdater.Result.Failed ->
                    android.util.Log.w("SiloUpd", "app update check: " + result.error)
                else -> Unit
            }
        }.start()
    }

    /** Тост, если на GitHub лежит сервер новее запущенного локального. */
    private fun checkServerUpdate() {
        try {
            val installed = SiloApi(prefs.serverUrl).version() ?: return
            val latest = GithubReleases.latestServer() ?: return
            if (GithubReleases.isNewer(latest.tag, installed)) {
                runOnUiThread {
                    if (!isFinishing) {
                        Toast.makeText(
                            this,
                            getString(R.string.server_update_available, latest.tag),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SiloUpd", "server update check failed", e)
        }
    }

    // ------------------------------------------------------------ предзагрузка

    /** Данные запуска: что играем, когда предзагрузка закончится. */
    private data class PendingPlay(
        val api: SiloApi,
        val token: String,
        val hash: String,
        val title: String,
        val poster: String,
        val file: SiloApi.TorrentFile,
        val playable: List<SiloApi.TorrentFile>,
        val forceAsk: Boolean,
    )

    private var overlay: View? = null
    private var preloader: Preloader? = null
    private var pendingPlay: PendingPlay? = null

    /**
     * Запуск файла из карточки: предзагрузка идёт поверх этого же окна
     * (затемнение + панель), без отдельного окна-диалога.
     */
    fun startPlayback(
        api: SiloApi,
        token: String,
        hash: String,
        details: SiloApi.TorrentDetails,
        file: SiloApi.TorrentFile,
        playable: List<SiloApi.TorrentFile>,
        forceAsk: Boolean,
    ) {
        // Повторное нажатие не запускает вторую предзагрузку поверх первой
        if (overlay != null) return
        pendingPlay = PendingPlay(
            api = api,
            token = token,
            hash = hash,
            title = details.title.ifBlank { details.hash },
            poster = details.poster,
            file = file,
            playable = playable,
            forceAsk = forceAsk,
        )

        val panel = PreloadPanel(
            context = this,
            onCancel = {
                preloader?.cancel()
                preloader = null
                pendingPlay = null
                hideOverlay()
            },
            onOpenNow = { preloader?.openNow() },
        )
        panel.bind(file.name)
        showOverlay(panel.view)

        val loader = Preloader(this, api, token, hash, file, panel)
        preloader = loader
        loader.start {
            if (isFinishing || isDestroyed) return@start
            panel.done()
            preloader = null
            afterPreload()
        }
    }

    /** После предзагрузки: сразу плеер или выбор плеера поверх того же окна. */
    private fun afterPreload() {
        val pending = pendingPlay ?: return hideOverlay()
        if (pending.forceAsk || prefs.playerPkg.isBlank()) {
            showPlayersOverlay(pending)
        } else {
            launchPlayer(pending, prefs.playerPkg)
        }
    }

    private fun showPlayersOverlay(pending: PendingPlay) {
        val content = layoutInflater.inflate(R.layout.view_players, null)
        val list = content.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.players_list)
        val remember = content.findViewById<android.widget.CheckBox>(R.id.players_remember)
        remember.isChecked = true
        PlayerPicker.bind(
            context = this,
            list = list,
            remember = remember,
            url = pending.api.streamUrl(pending.token, pending.hash, pending.file.id),
            mime = Players.mimeOf(pending.file.name),
        ) { pkg ->
            // «Спрашивать каждый раз» - просто остаёмся на списке
            if (pkg.isNotBlank()) launchPlayer(pending, pkg)
        }
        showOverlay(content, initialFocus = list)
    }

    private fun launchPlayer(pending: PendingPlay, pkg: String) {
        val playable = pending.playable
        val startIndex = playable.indexOfFirst { it.id == pending.file.id }.coerceAtLeast(0)
        val playlist = playable.map {
            pending.api.streamUrl(pending.token, pending.hash, it.id) to it.name
        }
        thread {
            runCatching {
                pending.api.setFileViewed(pending.token, pending.hash, pending.file.id, true)
            }
        }
        Players.launch(
            context = this,
            url = pending.api.streamUrl(pending.token, pending.hash, pending.file.id),
            mime = Players.mimeOf(pending.file.name),
            title = pending.title,
            poster = pending.poster,
            pkg = pkg,
            playlist = playlist,
            startIndex = startIndex,
        )
        pendingPlay = null
        hideOverlay()
    }

    /** Оверлей внутри главного окна: та же Activity, отдельного окна не появляется. */
    private fun showOverlay(content: View, initialFocus: View? = null) {
        hideOverlay()
        val root = layoutInflater.inflate(R.layout.overlay_panel, null)
        val container = root.findViewById<android.widget.LinearLayout>(R.id.overlay_panel_container)
        container.addView(content)
        addContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val width = minOf(dp(600), resources.displayMetrics.widthPixels - dp(48))
        container.layoutParams = (container.layoutParams as android.widget.FrameLayout.LayoutParams)
            .apply { this.width = width.coerceAtLeast(dp(260)) }
        overlay = root
        // Фокус остаётся в оверлее: кнопки под затемнением не должны срабатывать
        val target = initialFocus ?: root
        target.post { target.requestFocus() }
    }

    fun hideOverlay() {
        overlay?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        overlay = null
    }

    override fun onStop() {
        super.onStop()
        // Ушли из приложения во время предзагрузки - не запускаем плеер «из фона»
        if (overlay != null && preloader != null && !isChangingConfigurations) {
            preloader?.cancel()
            preloader = null
            pendingPlay = null
            hideOverlay()
        }
    }

    override fun onDestroy() {
        preloader?.cancel()
        preloader = null
        super.onDestroy()
    }

    companion object {
        /** Порог ширины, после которого панель показывается с подписями. */
        const val WIDE_DP = 600

        /** Как часто проверять обновления на GitHub (5 минут, один запрос API). */
        private const val UPDATE_INTERVAL_MS = 5 * 60 * 1000L
    }
}

/** Экран умеет обновлять свои данные по кнопке в заголовке. */
interface Refreshable {
    fun onRefresh()
}
