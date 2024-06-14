package ru.yourok.torrserve.ui.activities.main

import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
//import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.Utils
import ru.yourok.torrserve.ext.clearStackFragment
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.ServerFile
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.fragments.add.AddFragment
import ru.yourok.torrserve.ui.fragments.donate.DonateFragment
import ru.yourok.torrserve.ui.fragments.donate.DonateMessage
import ru.yourok.torrserve.ui.fragments.main.servfinder.ServerFinderFragment
import ru.yourok.torrserve.ui.fragments.main.servsets.ServerSettingsFragment
import ru.yourok.torrserve.ui.fragments.main.settings.SettingsFragment
import ru.yourok.torrserve.ui.fragments.main.torrents.TorrentsFragment
//import ru.yourok.torrserve.ui.fragments.main.update.apk.ApkUpdateFragment
//import ru.yourok.torrserve.ui.fragments.main.update.apk.UpdaterApk
import ru.yourok.torrserve.ui.fragments.main.update.server.ServerUpdateFragment
import ru.yourok.torrserve.ui.fragments.main.update.server.UpdaterServer
import ru.yourok.torrserve.utils.CImageSpan
import ru.yourok.torrserve.utils.Format.dp2px
import ru.yourok.torrserve.utils.Net
import ru.yourok.torrserve.utils.Permission
import ru.yourok.torrserve.utils.SpanFormat
import ru.yourok.torrserve.utils.ThemeUtil
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: StatusViewModel
    private val themeUtil = ThemeUtil()
//    private var firebaseAnalytics: FirebaseAnalytics? = null

    private val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (BuildConfig.DEBUG) Log.d("MainActivity", "handleOnBackPressed()")
            if (isMenuVisible)
                closeMenu()
            else if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else finish()
        }
    }

    private val isInTorrents: Boolean
        get() {
            val f = supportFragmentManager.findFragmentById(R.id.container)
            return f is TorrentsFragment
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        themeUtil.onCreate(this)

        setContentView(R.layout.main_activity)

        Permission.requestPermissionWithRationale(this)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        viewModel = ViewModelProvider(this)[StatusViewModel::class.java]

        setupNavigator()

        lifecycleScope.launch(Dispatchers.IO) {
            TorrService.start()
            if (TorrService.wait(10)) {
                if (TorrService.isLocal()) {
                    val ver = Api.echo()
                    if (ver.startsWith("1.1.")) {
                        withContext(Dispatchers.Main) {
                            ServerUpdateFragment().show(this@MainActivity, R.id.container, true)
                            App.toast(R.string.need_update_server, true)
                        }
                    }
                } else {
                    val ver = Api.echo()
                    if (ver.startsWith("1.1.")) {
                        withContext(Dispatchers.Main) {
                            App.toast(R.string.not_support_old_server, true)
                        }
                    }
                }
            } else { // no server response
                withContext(Dispatchers.Main) {
                    if (App.inForeground)
                        if (TorrService.isLocal()) {
                            if (!ServerFile().exists()) {
                                ServerUpdateFragment().show(this@MainActivity, R.id.container, true)
                                App.toast(R.string.need_install_server, true)
                            } else // local torrserver exists but not started, show restart hint
                                App.toast(R.string.not_loaded_exit_hint)
                        } else {
                            ServerFinderFragment().show(this@MainActivity, R.id.container, true)
                            App.toast(R.string.not_loaded_select_hint)
                        }
                }
            }
        }

        if (savedInstanceState == null) {
            clearStackFragment()
            TorrentsFragment().apply {
                show(this@MainActivity, R.id.container)
            }
            DonateMessage.showDonate(this)
            checkUpdate()
        }
    }

    override fun onResume() {
        super.onResume()
        themeUtil.onResume(this)
        //TorrService.start()
        updateStatus()
        if (Settings.showFab) setupFab()
        if (Settings.showSortFab) setupSortFab()
        lifecycleScope.launch(Dispatchers.IO) {
            if (TorrService.wait(10) && isShowCat()) {
                withContext(Dispatchers.Main) {
                    setupCatFab()
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        themeUtil.onConfigurationChanged(this, newConfig)
    }

    private fun updateStatus() {
        val host = viewModel.getHost()
        val hostView = findViewById<TextView>(R.id.tvCurrentHost)
        val statusView = findViewById<TextView>(R.id.tvStatus)
        val hostColor = ThemeUtil.getColorFromAttr(this, R.attr.colorHost)
        val inactiveColor = ThemeUtil.getColorFromAttr(this, R.attr.colorOnSurface)
        val labelTextColor = ThemeUtil.getColorFromAttr(this@MainActivity, R.attr.colorSurface)
        val versionColorList = ColorStateList.valueOf(ThemeUtil.getColorFromAttr(this@MainActivity, R.attr.colorPrimary))
        val radius = dp2px(2.0f).toFloat()
        val shapeAppearanceModel = ShapeAppearanceModel()
            .toBuilder()
            .setAllCorners(CornerFamily.ROUNDED, radius)
            .build()

        host.observe(this) {
            hostView?.text = if (it.startsWith("https", true)) {
                val sIcon = SpannableString(" ")
                AppCompatResources.getDrawable(this, R.drawable.ssl)?.let { icon ->
                    icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
                    sIcon.setSpan(CImageSpan(icon), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                SpanFormat.format("%s ${it.removePrefix("https://")}", sIcon)
            } else it.removePrefix("http://")
        }
        val data = viewModel.get()
        data.observe(this) {
            statusView?.text = it
            if (it.equals(getString(R.string.server_not_responding))) {
                statusView?.apply {
                    background = null
                    setTextColor(inactiveColor)
                }
                hostView?.apply {
                    setTextColor(inactiveColor)
                    alpha = 0.75f
                }
            } else {
                statusView?.apply {
                    val shapeDrawable = MaterialShapeDrawable(shapeAppearanceModel)
                    shapeDrawable.fillColor = versionColorList.withAlpha(160) // 160
                    shapeDrawable.setStroke(2.0f, versionColorList.withAlpha(100)) // 100
                    background = shapeDrawable
                    setTextColor(labelTextColor)
                }
                hostView?.apply {
                    setTextColor(hostColor)
                    alpha = 1.0f
                }
            }
        }
    }

    private val isMenuVisible: Boolean
        get() {
            return findViewById<DrawerLayout>(R.id.drawerLayout)?.isDrawerOpen(GravityCompat.START) == true
        }

    private val drawerListener = object : DrawerLayout.SimpleDrawerListener() {
        override fun onDrawerOpened(drawerView: View) {
            super.onDrawerOpened(drawerView)

            if (Settings.showFab) showFab(false)
            if (Settings.showSortFab) showSortFab(false)
            lifecycleScope.launch(Dispatchers.IO) {
                if (isShowCat()) withContext(Dispatchers.Main) { showCatFab(false) }
            }
        }

        override fun onDrawerClosed(drawerView: View) {
            super.onDrawerClosed(drawerView)
            if (Settings.showFab) showFab()
            if (Settings.showSortFab && isInTorrents) showSortFab()
            lifecycleScope.launch(Dispatchers.IO) {
                if (isShowCat() && isInTorrents)
                    withContext(Dispatchers.Main) { showCatFab() }
                else
                    withContext(Dispatchers.Main) { showCatFab(false) }
            }
        }
    }

    private fun closeMenu() {
        findViewById<DrawerLayout>(R.id.drawerLayout)?.apply {
            addDrawerListener(drawerListener)
            closeDrawers()
        }
    }

    private fun openMenu() {
        findViewById<DrawerLayout>(R.id.drawerLayout)?.apply {
            addDrawerListener(drawerListener)
            openDrawer(GravityCompat.START)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        supportFragmentManager.fragments.forEach {
            when (it) {
                is AddFragment ->
                    if (it.onKeyUp(keyCode))
                        return true

                is TorrentsFragment ->
                    if (it.onKeyUp(keyCode))
                        return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        supportFragmentManager.fragments.forEach {
            when (it) {
                is AddFragment -> {
                    if (it.onKeyDown(keyCode))
                        return true
                }

                is TorrentsFragment ->
                    if (it.onKeyDown(keyCode))
                        return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (isMenuVisible)
                closeMenu()
            else
                openMenu()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun checkUpdate() {
//        lifecycleScope.launch(Dispatchers.IO) {
//            if (UpdaterApk.check())
//                withContext(Dispatchers.Main) {
//                    App.toast(R.string.found_new_app_update, true)
//                }
//        }
        lifecycleScope.launch(Dispatchers.IO) {
            UpdaterServer.check()
        }
    }

    private fun setupFab() { // Fab
        if (Utils.isTvBox()) return
        val fab: FloatingActionButton? = findViewById(R.id.fab)
        fab?.apply {
            setImageDrawable(AppCompatResources.getDrawable(this.context, R.mipmap.ic_launcher))
            customSize = dp2px(32f)
            setMaxImageSize(dp2px(30f))
            setOnClickListener {
                if (isMenuVisible) {
                    closeMenu()
                    showFab()
                } else {
                    openMenu()
                    showFab(false)
                }
            }
        }
        if (!isMenuVisible) {
            showFab()
        }
    }

    private fun showFab(show: Boolean = true) {
        val fab: FloatingActionButton? = findViewById(R.id.fab)
        if (show)
            fab?.show()
        else
            fab?.hide()
    }

    private fun setupSortFab() {
        if (Utils.isTvBox()) return

        val accentColor = ThemeUtil.getColorFromAttr(this, R.attr.colorAccent)
        val actionsColor = ThemeUtil.getColorFromAttr(this, R.attr.colorMainMenu)

        val fab: FloatingActionButton? = findViewById(R.id.sort_fab)
        fab?.apply {
            if (Settings.sortTorrByTitle)
                setImageDrawable(AppCompatResources.getDrawable(this.context, R.drawable.round_filter_list_24))
            else
                setImageDrawable(AppCompatResources.getDrawable(this.context, R.drawable.round_sort_by_alpha_24))
            customSize = dp2px(32f)
            setMaxImageSize(dp2px(24f))
            backgroundTintList = ColorStateList.valueOf(actionsColor)
            setColorFilter(accentColor)
            setRippleColor(ColorStateList.valueOf(accentColor))
            setOnClickListener {
                if (isInTorrents) {
                    val f = supportFragmentManager.findFragmentById(R.id.container)
                    (f as TorrentsFragment?)?.sort()
                }
                if (Settings.sortTorrByTitle)
                    setImageDrawable(AppCompatResources.getDrawable(this.context, R.drawable.round_filter_list_24))
                else
                    setImageDrawable(AppCompatResources.getDrawable(this.context, R.drawable.round_sort_by_alpha_24))
            }
        }
        // visibility change
        if (isInTorrents)
            showSortFab()
        else
            showSortFab(false)
    }

    private fun showSortFab(show: Boolean = true) {
        val fab: FloatingActionButton? = findViewById(R.id.sort_fab)
        if (show)
            fab?.show()
        else
            fab?.hide()
    }

    private var isCatsOpen = false
    private var movFab: FloatingActionButton? = null
    private var movText: TextView? = null
    private var tvFab: FloatingActionButton? = null
    private var tvText: TextView? = null
    private var musFab: FloatingActionButton? = null
    private var musText: TextView? = null
    private var othFab: FloatingActionButton? = null
    private var othText: TextView? = null
    private var allFab: FloatingActionButton? = null
    private var allText: TextView? = null


    suspend fun isShowCat(): Boolean {
        return try {
            val vi = Api.getMatrixVersionInt()
            vi > 131 && Settings.get("show_cat_fab", false) // MatriX.132 add Categories
        } catch (e: Exception) {
            false
        }
    }

    private fun setupCatFab() { // categories options menu
        if (Utils.isTvBox()) return

        val accentColor = ThemeUtil.getColorFromAttr(this, R.attr.colorAccent)
        val actionsColor = ThemeUtil.getColorFromAttr(this, R.attr.colorMainMenu)
        //val textColor = ThemeUtil.getColorFromAttr(this, R.attr.colorBright)

        val catFab: FloatingActionButton? = findViewById(R.id.cat_fab)
        movFab = findViewById(R.id.mov_fab)
        movText = findViewById<TextView?>(R.id.mov_fab_text)?.apply { setTextColor(accentColor) }
        tvFab = findViewById(R.id.tv_fab)
        tvText = findViewById<TextView?>(R.id.tv_fab_text)?.apply { setTextColor(accentColor) }
        musFab = findViewById(R.id.mus_fab)
        musText = findViewById<TextView?>(R.id.mus_fab_text)?.apply { setTextColor(accentColor) }
        othFab = findViewById(R.id.oth_fab)
        othText = findViewById<TextView?>(R.id.oth_fab_text)?.apply { setTextColor(accentColor) }
        allFab = findViewById(R.id.all_fab)
        allText = findViewById<TextView?>(R.id.all_fab_text)?.apply { setTextColor(accentColor) }
        catFab?.apply {
            setImageDrawable(AppCompatResources.getDrawable(this.context, R.drawable.round_view_list_24))
            customSize = dp2px(32f)
            setMaxImageSize(dp2px(24f))
            backgroundTintList = ColorStateList.valueOf(actionsColor)
            setColorFilter(accentColor)
            setRippleColor(ColorStateList.valueOf(accentColor))
            setOnClickListener {
                if (!isCatsOpen) {
                    showActions()
                } else {
                    hideActions()
                }
                isCatsOpen = !isCatsOpen
            }
        }
        movFab?.apply {
            backgroundTintList = ColorStateList.valueOf(actionsColor)
            setColorFilter(accentColor)
            setRippleColor(ColorStateList.valueOf(accentColor))
            setOnClickListener {
                filterTorrents("movie")
                hideActions()
                isCatsOpen = false
            }
        }
        tvFab?.apply {
            backgroundTintList = ColorStateList.valueOf(actionsColor)
            setColorFilter(accentColor)
            setRippleColor(ColorStateList.valueOf(accentColor))
            setOnClickListener {
                filterTorrents("tv")
                hideActions()
                isCatsOpen = false
            }
        }
        musFab?.apply {
            backgroundTintList = ColorStateList.valueOf(actionsColor)
            setColorFilter(accentColor)
            setRippleColor(ColorStateList.valueOf(accentColor))
            setOnClickListener {
                filterTorrents("music")
                hideActions()
                isCatsOpen = false
            }
        }
        othFab?.apply {
            backgroundTintList = ColorStateList.valueOf(actionsColor)
            setColorFilter(accentColor)
            setRippleColor(ColorStateList.valueOf(accentColor))
            setOnClickListener {
                filterTorrents("other")
                hideActions()
                isCatsOpen = false
            }
        }
        allFab?.apply {
            backgroundTintList = ColorStateList.valueOf(actionsColor)
            setColorFilter(accentColor)
            setRippleColor(ColorStateList.valueOf(accentColor))
            setOnClickListener {
                filterTorrents()
                hideActions()
                isCatsOpen = false
            }
        }
        // visibility change
        if (isInTorrents) {
            showCatFab()
        } else {
            showCatFab(false)
        }
    }

    private fun showCatFab(show: Boolean = true) {
        val catFab: FloatingActionButton? = findViewById(R.id.cat_fab)
        if (show) {
            catFab?.show()
        } else {
            hideActions()
            isCatsOpen = false
            catFab?.hide()
        }
    }

    private fun showActions() {
        movFab?.show()
        tvFab?.show()
        musFab?.show()
        othFab?.show()
        allFab?.show()
        movText?.visibility = View.VISIBLE
        tvText?.visibility = View.VISIBLE
        musText?.visibility = View.VISIBLE
        othText?.visibility = View.VISIBLE
        allText?.visibility = View.VISIBLE
    }

    private fun hideActions() {
        movFab?.hide()
        tvFab?.hide()
        musFab?.hide()
        othFab?.hide()
        allFab?.hide()
        movText?.visibility = View.GONE
        tvText?.visibility = View.GONE
        musText?.visibility = View.GONE
        othText?.visibility = View.GONE
        allText?.visibility = View.GONE
    }

    private fun filterTorrents(category: String = "") {
        if (isInTorrents) {
            val f = supportFragmentManager.findFragmentById(R.id.container)
            lifecycleScope.launch { (f as TorrentsFragment?)?.filter(category) }
        }
    }

    private fun setupNavigator() {
        // Logo
        findViewById<FrameLayout>(R.id.header)?.setOnClickListener {
            val currFragment = supportFragmentManager.findFragmentById(R.id.container)
            if (currFragment is TorrentsFragment)
                ServerFinderFragment().show(this, R.id.container, true)
            else {
                clearStackFragment()
                TorrentsFragment().show(this, R.id.container)
            }
            closeMenu()
        }
        findViewById<FrameLayout>(R.id.header)?.setOnLongClickListener {
            ServerSettingsFragment().show(this, R.id.container, true)
            closeMenu()
            true
        }

        findViewById<FrameLayout>(R.id.btnAdd)?.setOnClickListener {
            AddFragment().show(this@MainActivity, R.id.container, true)
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnRemoveAll)?.setOnClickListener { _ ->
            val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.remove_all_warn)
                .setPositiveButton(R.string.yes) { dialog, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val list = Api.listTorrent()
                            list.forEach {
                                Api.remTorrent(it.hash)
                            }
                            withContext(Dispatchers.Main) { dialog.dismiss() }
                        } catch (e: Exception) {
                            e.message?.let {
                                App.toast(it)
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
            dialog.show()
            dialog.getButton(BUTTON_POSITIVE)?.requestFocus()
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnPlaylist)?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    if (Api.listTorrent().isNotEmpty()) {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(Uri.parse(Net.getHostUrl("/playlistall/all.m3u")), "video/*")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        App.context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    e.message?.let {
                        App.toast(it)
                    }
                }
            }
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnDonate)?.setOnClickListener {
            DonateFragment().show(this, R.id.container, true)
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnUpdate)?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
//                if (UpdaterApk.check())
//                    withContext(Dispatchers.Main) {
//                        ApkUpdateFragment().show(this@MainActivity, R.id.container, true)
//                    }
//                else
//                    withContext(Dispatchers.Main) {
//                        ServerUpdateFragment().show(this@MainActivity, R.id.container, true)
//                    }
                withContext(Dispatchers.Main) {
                    ServerUpdateFragment().show(this@MainActivity, R.id.container, true)
                }
            }
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnSettings)?.setOnClickListener {
            SettingsFragment().show(this@MainActivity, R.id.container)
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnExit)?.setOnClickListener {
            val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.exit_title)
                .setMessage(getString(R.string.exit_text))
                .setPositiveButton(R.string.exit) { _, _ ->
                    TorrService.stop()
                    finishAffinity()
                    exitProcess(0)
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
            dialog.show()
            dialog.getButton(BUTTON_POSITIVE)?.requestFocus()
            closeMenu()
        }
    }
}