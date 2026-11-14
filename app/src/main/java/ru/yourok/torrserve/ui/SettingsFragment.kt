package ru.yourok.torrserve.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.ServerFinder
import ru.yourok.torrserve.server.api.SiloApi
import ru.yourok.torrserve.server.local.ServerDownloader
import ru.yourok.torrserve.server.local.ServerFile
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.settings.Prefs
import java.io.File
import kotlin.concurrent.thread

/**
 * Настройки: адрес сервера, скачивание/запуск локального сервера, текущий аккаунт.
 */
class SettingsFragment : Fragment() {

    private lateinit var prefs: Prefs
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var address: EditText
    private lateinit var serverState: TextView
    private lateinit var account: TextView

    /** Разрешение на чтение папки Download (запрашивается при нажатии «Из папки»). */
    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) showServerFiles()
            else toast(getString(R.string.settings_folder_no_access))
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View =
        inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = Prefs(requireContext())
        address = view.findViewById(R.id.settings_address)
        serverState = view.findViewById(R.id.settings_server_state)
        account = view.findViewById(R.id.settings_account)
        address.setText(prefs.serverUrl)

        view.findViewById<View>(R.id.settings_save).setOnClickListener {
            val value = address.text.toString().trim()
            if (value.isEmpty()) return@setOnClickListener
            prefs.serverUrl = if (value.startsWith("http")) value else "http://$value"
            (activity as? MainActivity)?.updateSidebarState()
            toast(getString(R.string.settings_server_saved))
            updateState()
        }

        view.findViewById<View>(R.id.settings_local).setOnClickListener {
            prefs.serverUrl = Prefs.LOCAL_URL
            address.setText(prefs.serverUrl)
            (activity as? MainActivity)?.updateSidebarState()
            updateState()
        }

        view.findViewById<View>(R.id.settings_search).setOnClickListener { searchServers() }
        view.findViewById<View>(R.id.settings_player_choose).setOnClickListener { choosePlayer() }
        view.findViewById<View>(R.id.settings_accounts)?.setOnClickListener {
            (activity as? MainActivity)?.showScreen(MainActivity.Screen.USERS)
        }
        view.findViewById<View>(R.id.settings_add_reset)?.setOnClickListener {
            prefs.magnetAction = 0
            prefs.magnetRemember = false
            toast(getString(R.string.settings_add_reset_done))
            updateState()
        }
        view.findViewById<View>(R.id.settings_player_options).setOnClickListener { playerOptions() }
        view.findViewById<View>(R.id.settings_download).setOnClickListener { downloadServer() }
        view.findViewById<View>(R.id.settings_from_folder).setOnClickListener { installFromFolder() }
        view.findViewById<View>(R.id.settings_delete).setOnClickListener { confirmDeleteServer() }
        view.findViewById<View>(R.id.settings_start).setOnClickListener {
            TorrService.start(requireContext().applicationContext)
            toast(getString(R.string.settings_starting))
            // После старта сразу входим в локальный сервер, если задан пароль владельца
            thread {
                repeat(20) {
                    Thread.sleep(400)
                    if (SiloApi(prefs.serverUrl).ping()) {
                        LocalAuth.ensureSession(requireContext(), prefs)
                        ui.post { updateState() }
                        return@thread
                    }
                }
            }
            ui.postDelayed({ updateState() }, 2000)
        }
        view.findViewById<View>(R.id.settings_stop).setOnClickListener {
            TorrService.stop(requireContext().applicationContext)
            toast(getString(R.string.settings_stopping))
            ui.postDelayed({ updateState() }, 1000)
        }
        view.findViewById<View>(R.id.settings_owner_password_apply)?.setOnClickListener {
            val input = view.findViewById<EditText>(R.id.settings_owner_password_input)
            prefs.ownerPassword = input.text.toString().trim()
            // Пароль изменился - старые токены локального сервера больше не подходят
            prefs.getSessions().forEach { prefs.removeSession(it.id) }
            prefs.activeUserId = ""
            toast(getString(R.string.settings_owner_password_saved))
            LocalAuth.restartService(requireContext().applicationContext)
            // Сессии сброшены: если пароль задан, меню должно перейти в состояние «Вход»
            (activity as? MainActivity)?.updateSidebarState()
            ui.postDelayed({ updateState() }, 2500)
        }
        view.findViewById<View>(R.id.settings_logout).setOnClickListener {
            prefs.getSessions().forEach { prefs.removeSession(it.id) }
            prefs.ownerToken = ""
            (activity as? MainActivity)?.showScreen(MainActivity.Screen.USERS)
        }

        updateState()
    }

    override fun onResume() {
        super.onResume()
        updateState()
        (activity as? MainActivity)?.updateSidebarState()
    }

    private fun updateState() {
        val binary = File(requireContext().filesDir, ServerFile.BIN_NAME)
        val installed = binary.exists()
        serverState.text = if (installed) {
            getString(R.string.settings_server_installed, ServerDownloader.remoteName())
        } else {
            getString(R.string.settings_server_missing)
        }
        bindOwnerPasswordField()
        updateAccountChips()
        updateAddAction()

        val playerView = view?.findViewById<TextView>(R.id.settings_player)
        if (playerView != null) {
            val pkg = prefs.playerPkg
            playerView.text = if (pkg.isBlank()) {
                getString(R.string.players_default, getString(R.string.player_ask))
            } else {
                getString(R.string.players_default, Players.label(requireContext(), pkg))
            }
        }

        val session = prefs.activeSession()
        account.text = if (session != null) {
            getString(R.string.settings_account_current, session.username)
        } else {
            getString(R.string.sidebar_no_user)
        }
    }

    /** Поле пароля владельца локального сервера. */
    private fun bindOwnerPasswordField() {
        val input = view?.findViewById<EditText>(R.id.settings_owner_password_input) ?: return
        if (!input.hasFocus()) input.setText(prefs.ownerPassword)
    }

    /** Плашки уже авторизованных пользователей: нажатие переключает активного. */
    private fun updateAccountChips() {
        val container = view?.findViewById<LinearLayout>(R.id.settings_account_chips) ?: return
        container.removeAllViews()
        val sessions = prefs.getSessions()
        if (sessions.isEmpty()) return
        val active = prefs.activeUserId
        sessions.forEach { session ->
            val chip = TextView(requireContext()).apply {
                text = session.username
                setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (session.id == active) R.color.silo_amber else R.color.silo_text,
                    )
                )
                textSize = 14f
                setPadding(dp(16), dp(8), dp(16), dp(8))
                background = ContextCompat.getDrawable(
                    requireContext(),
                    if (session.id == active) R.drawable.bg_tab else R.drawable.card_selector,
                )
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    if (session.id != active) {
                        toast(getString(R.string.accounts_switched, session.username))
                        (activity as? MainActivity)?.switchUser(session.id)
                    }
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(8) }
            container.addView(chip, params)
        }
    }

    /** Текст «действие по умолчанию» для окна добавления. */
    private fun updateAddAction() {
        val view = view?.findViewById<TextView>(R.id.settings_add_action) ?: return
        val action = when (prefs.magnetAction) {
            1 -> getString(R.string.magnet_action_add_short)
            2 -> getString(R.string.magnet_action_play_short)
            3 -> getString(R.string.magnet_action_add_play_short)
            else -> getString(R.string.player_ask)
        }
        view.text = getString(R.string.settings_add_action, action)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    /** Выбор плеера по умолчанию (список установленных приложений). */
    private fun choosePlayer() {
        PlayersDialog.show(
            requireContext(),
            "http://127.0.0.1:8090/probe.mkv",
            "video/x-matroska",
            "",
            "",
            launchSelected = false,
            onChosen = { updateState() },
        )
    }

    /** Настройки выбранного плеера: что ему передавать вместе с потоком. */
    private fun playerOptions() {
        val pkg = prefs.playerPkg
        if (pkg.isBlank()) {
            toast(getString(R.string.player_none_selected))
            return
        }
        val view = layoutInflater.inflate(R.layout.dialog_player_settings, null)
        val title = view.findViewById<android.widget.CheckBox>(R.id.opt_title)
        val poster = view.findViewById<android.widget.CheckBox>(R.id.opt_poster)
        title.isChecked = prefs.playerSendsTitle(pkg)
        poster.isChecked = prefs.playerSendsPoster(pkg)

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(Players.label(requireContext(), pkg))
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.setPlayerSendsTitle(pkg, title.isChecked)
                prefs.setPlayerSendsPoster(pkg, poster.isChecked)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Поиск серверов Silo в локальной сети: выбор адреса из найденных. */
    private fun searchServers() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_server_search, null)
        val status = dialogView.findViewById<TextView>(R.id.search_status)
        val progress = dialogView.findViewById<android.widget.ProgressBar>(R.id.search_progress)
        val listView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.search_list)
        val found = mutableListOf<ServerFinder.Found>()
        val adapter = ServersAdapter(found) { server ->
            prefs.serverUrl = server.url
            address.setText(server.url)
            (activity as? MainActivity)?.updateSidebarState()
            toast(getString(R.string.settings_server_saved))
            updateState()
        }
        listView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        listView.adapter = adapter

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.server_search_title)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        val scanning = java.util.concurrent.atomic.AtomicBoolean(true)
        dialog.setOnDismissListener { scanning.set(false) }
        dialog.show()

        thread {
            ServerFinder.scan(
                isActive = { scanning.get() && isAdded },
                onProgress = { pct ->
                    ui.post {
                        if (!scanning.get()) return@post
                        progress.progress = pct
                        status.text = getString(R.string.server_search_progress, pct)
                    }
                },
                onFound = { server ->
                    ui.post {
                        if (!scanning.get()) return@post
                        found += server
                        adapter.notifyDataSetChanged()
                        status.text = getString(R.string.server_search_found, found.size)
                    }
                },
            )
            ui.post {
                if (!scanning.get()) return@post
                scanning.set(false)
                progress.visibility = View.GONE
                status.text = if (found.isEmpty()) getString(R.string.server_search_none)
                               else getString(R.string.server_search_found, found.size)
            }
        }
    }

    /** Список найденных серверов в диалоге поиска. */
    private inner class ServersAdapter(
        private val data: List<ServerFinder.Found>,
        private val onClick: (ServerFinder.Found) -> Unit,
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<ServersAdapter.VH>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH =
            VH(layoutInflater.inflate(R.layout.item_server, parent, false))

        override fun getItemCount() = data.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val server = data[position]
            holder.address.text = server.url
            holder.version.text = getString(R.string.server_search_version, server.version)
            holder.itemView.setOnClickListener { onClick(server) }
        }

        inner class VH(v: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {
            val address: TextView = v.findViewById(R.id.server_address)
            val version: TextView = v.findViewById(R.id.server_version)
        }
    }

    private fun downloadServer() {
        serverState.text = getString(R.string.settings_downloading)
        thread {
            try {
                ServerDownloader.download(requireContext().applicationContext) { p ->
                    ui.post { if (isAdded) serverState.text = "${getString(R.string.settings_downloading)} $p%" }
                }
                prefs.serverUrl = Prefs.LOCAL_URL
                TorrService.start(requireContext().applicationContext)
                ui.post {
                    if (!isAdded) return@post
                    address.setText(prefs.serverUrl)
                    (activity as? MainActivity)?.updateSidebarState()
                    serverState.text = getString(R.string.settings_download_done)
                }
            } catch (e: Exception) {
                ui.post {
                    if (isAdded) serverState.text = getString(R.string.settings_download_error, e.message ?: "")
                }
            }
        }
    }

    private fun toast(text: String) = Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()

    /** Кнопка «Из папки»: сканируем Download и ставим выбранный файл сервера. */
    private fun installFromFolder() {
        if (hasStorageAccess()) showServerFiles()
        else storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasStorageAccess(): Boolean = ContextCompat.checkSelfPermission(
        requireContext(),
        Manifest.permission.READ_EXTERNAL_STORAGE,
    ) == PackageManager.PERMISSION_GRANTED

    /** Файлы в папке Download, в имени которых есть «torrserver» (без учёта регистра). */
    @Suppress("DEPRECATION")
    private fun serverFiles(): List<File> {
        val dirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory(), "Download"),
        )
        return dirs.asSequence()
            .filter { it.isDirectory }
            .flatMap { dir -> (dir.listFiles() ?: emptyArray()).asSequence() }
            .filter { it.isFile && it.name.contains(FILE_MASK, ignoreCase = true) }
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }
            .toList()
    }

    /** Окно выбора файла сервера из Download. */
    private fun showServerFiles() {
        val files = serverFiles()
        if (files.isEmpty()) {
            toast(getString(R.string.settings_folder_empty))
            return
        }
        val labels = files.map { "${it.name}  (${humanSize(it.length())})" }.toTypedArray()
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_folder_title)
            .setItems(labels) { _, which -> installServer(files[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun installServer(source: File) {
        serverState.text = getString(R.string.settings_installing)
        val context = requireContext().applicationContext
        thread {
            try {
                ServerFile(context).installFrom(source)
                prefs.serverUrl = Prefs.LOCAL_URL
                TorrService.start(context)
                ui.post {
                    if (!isAdded) return@post
                    address.setText(prefs.serverUrl)
                    (activity as? MainActivity)?.updateSidebarState()
                    serverState.text = getString(R.string.settings_install_done)
                    toast(getString(R.string.settings_install_done))
                    updateState()
                }
            } catch (e: Exception) {
                ui.post {
                    if (!isAdded) return@post
                    serverState.text = getString(R.string.settings_install_error, e.message ?: "")
                    updateState()
                }
            }
        }
    }

    /** Кнопка «Удалить»: подтверждение перед удалением сервера с устройства. */
    private fun confirmDeleteServer() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_delete_title)
            .setMessage(R.string.settings_delete_message)
            .setPositiveButton(R.string.settings_delete_server) { _, _ -> deleteServer() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Останавливает сервер и удаляет его файлы (бинарник, конфиг, вывод). */
    private fun deleteServer() {
        val context = requireContext().applicationContext
        TorrService.stop(context)
        thread {
            ServerFile(context).uninstall()
            ui.post {
                if (!isAdded) return@post
                toast(getString(R.string.settings_server_deleted))
                (activity as? MainActivity)?.updateSidebarState()
                updateState()
            }
        }
    }

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    companion object {
        /** Часть имени файла сервера в Download (сравнение без учёта регистра). */
        private const val FILE_MASK = "torrserver"
    }
}
