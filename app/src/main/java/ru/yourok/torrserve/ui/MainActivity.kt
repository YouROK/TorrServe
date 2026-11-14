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
import androidx.fragment.app.Fragment
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.GithubReleases
import ru.yourok.torrserve.server.api.SiloApi
import ru.yourok.torrserve.server.local.ServerFile
import ru.yourok.torrserve.settings.Prefs
import android.widget.Toast
import java.io.File

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

    /** Панель в компактном режиме (телефон): подписи и состояние скрыты. */
    private var compactSidebar = false

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
        val available = hasServer()
        val loginRequired = needsLogin()
        val ready = available && !loginRequired

        listOf(R.id.nav_torrents, R.id.nav_add, R.id.nav_delete_all, R.id.nav_category).forEach {
            findViewById<View>(it).visibility = if (ready) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.nav_login).visibility = if (loginRequired) View.VISIBLE else View.GONE
        findViewById<View>(R.id.sidebar_plugins).visibility = if (ready) View.VISIBLE else View.GONE
        // Заголовок секции плагинов прячем, пока нечего показывать (иначе висит пустым)
        if (!ready) findViewById<View>(R.id.sidebar_plugins_header).visibility = View.GONE
        val showStatus = ready && !compactSidebar
        sidebarUser.visibility = if (showStatus) View.VISIBLE else View.GONE
        sidebarServer.visibility = if (showStatus) View.VISIBLE else View.GONE
        findViewById<View>(R.id.header_sort).visibility = if (ready) View.VISIBLE else View.GONE
        findViewById<View>(R.id.header_refresh).visibility = if (ready) View.VISIBLE else View.GONE
        if (!ready) headerSearch.visibility = View.GONE
    }

    /** Панель: на широком экране с подписями, на телефоне - только иконки. */
    private fun applyResponsiveSidebar() {
        val compact = resources.configuration.screenWidthDp < WIDE_DP
        compactSidebar = compact
        val sidebar = findViewById<LinearLayout>(R.id.sidebar)
        sidebar.layoutParams.width = if (compact) dp(84) else dp(260)
        if (compact) {
            findViewById<View>(R.id.brand_name).visibility = View.GONE
            listOf(
                R.id.nav_torrents_label, R.id.nav_add_label,
                R.id.nav_login_label, R.id.nav_settings_label,
            ).forEach { findViewById<View>(it).visibility = View.GONE }
            sidebarUser.visibility = View.GONE
            sidebarServer.visibility = View.GONE
        }
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.nav_torrents).setOnClickListener { showScreen(Screen.TORRENTS) }
        findViewById<View>(R.id.nav_add).setOnClickListener {
            AddTorrentDialog.show(this) {
                (currentFragment() as? TorrentsFragment)?.onTorrentAdded()
            }
        }
        findViewById<View>(R.id.nav_donate).setOnClickListener { showDonate() }
        findViewById<View>(R.id.nav_exit).setOnClickListener { confirmExit() }
        findViewById<View>(R.id.nav_delete_all).setOnClickListener { confirmDeleteAll() }
        findViewById<View>(R.id.nav_category).setOnClickListener { chooseCategory() }
        findViewById<View>(R.id.nav_settings).setOnClickListener { showScreen(Screen.SETTINGS) }
        findViewById<View>(R.id.nav_login).setOnClickListener {
            loginLauncher.launch(LoginActivity.intent(this))
        }
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
        if (inDetail) {
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
        checkUpdates()
    }

    /** Переключение активного пользователя без выхода из приложения. */
    fun switchUser(id: String) {
        prefs.activeUserId = id
        updateSidebarState()
        // Меню плагинов зависит от прав пользователя - перечитываем
        loadPluginMenu()
        showScreen(current)
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
        val wide = resources.configuration.screenWidthDp >= WIDE_DP
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
            if (!wide) {
                // На телефоне панель сжата: подписи скрываем, остаются иконки
                item.findViewById<TextView>(R.id.plugin_item_title).visibility = View.GONE
            }
            container.addView(item)
        }
    }

    fun updateSidebarState() {
        val session = prefs.activeSession()
        sidebarUser.text = session?.username ?: when {
            // Открытый режим локального сервера - это работа под owner без пароля
            hasServer() && prefs.isLocalServer && prefs.ownerPassword.isEmpty() -> LocalAuth.OWNER_ID
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
     * Проверка обновлений сервера и приложения на GitHub (не чаще раза в 5 минут).
     * Один запрос к API отдаёт и серверные релизы Silo.*, и клиентские Silo.*.Client.
     */
    private fun checkUpdates() {
        if (updateChecked) return
        updateChecked = true
        val now = System.currentTimeMillis()
        if (now - prefs.updateCheckedAt < UPDATE_INTERVAL_MS) return
        prefs.updateCheckedAt = now

        val url = prefs.serverUrl
        val localServer = hasServer() && prefs.isLocalServer
        val currentApp = appVersionName()

        Thread {
            val releases = try {
                GithubReleases.all()
            } catch (e: Exception) {
                emptyList()
            }

            if (localServer) {
                val installed = SiloApi(url).version()
                val server = latestTag(releases) { !it.isClient }
                if (installed != null && server != null && GithubReleases.isNewer(server.tag, installed)) {
                    runOnUiThread {
                        if (!isFinishing) {
                            Toast.makeText(
                                this,
                                getString(R.string.server_update_available, server.tag),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            }

            val app = latestTag(releases) { it.isClient }
            if (app != null && AppUpdater.isNewer(app.tag, currentApp)) {
                runOnUiThread { if (!isFinishing) showAppUpdateDialog(app) }
            }
        }.start()
    }

    /** Самый новый релиз из ленты по фильтру (сервер или клиент). */
    private fun latestTag(
        releases: List<GithubReleases.Release>,
        filter: (GithubReleases.Release) -> Boolean,
    ): GithubReleases.Release? =
        releases.filter { it.tag.startsWith(GithubReleases.TAG_PREFIX) && filter(it) }
            .maxWithOrNull { a, b ->
                GithubReleases.compare(
                    GithubReleases.versionOf(a.tag) ?: emptyList(),
                    GithubReleases.versionOf(b.tag) ?: emptyList(),
                )
            }

    /** Версия установленного приложения ("Silo.0.Client"). */
    private fun appVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    /** Диалог о новой версии приложения со ссылкой на релиз. */
    private fun showAppUpdateDialog(release: GithubReleases.Release) {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.app_update_title)
            .setMessage(getString(R.string.app_update_message, release.tag, appVersionName()))
            .setPositiveButton(R.string.app_update_download) { _, _ ->
                runCatching {
                    startActivity(android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(release.htmlUrl),
                    ))
                }
            }
            .setNegativeButton(R.string.app_update_later, null)
            .show()
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
