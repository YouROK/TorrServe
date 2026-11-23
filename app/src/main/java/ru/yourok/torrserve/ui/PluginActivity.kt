package ru.yourok.torrserve.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.InflateException
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import ru.yourok.torrserve.R
import ru.yourok.torrserve.settings.Prefs

/**
 * Экран плагина: веб-страница плагина Silo в WebView.
 * Токен активного пользователя кладём в cookie silo_token - плагин ходит к API от его имени.
 */
class PluginActivity : AppCompatActivity() {

    private var web: WebView? = null
    private lateinit var titleView: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Без рабочего WebView раздувание layout падает с MissingWebViewPackageException
        // (например, на AOSP-эмуляторе), поэтому проверяем провайдера до setContentView.
        if (!hasWebView()) {
            showWebViewMissing()
            return
        }

        try {
            setContentView(R.layout.activity_plugin)
        } catch (e: InflateException) {
            showWebViewMissing()
            return
        }

        val prefs = Prefs(this)
        val href = intent.getStringExtra(EXTRA_HREF).orEmpty()
        val name = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val url = prefs.serverUrl.trimEnd('/') + href

        titleView = findViewById(R.id.plugin_title)
        titleView.text = name
        findViewById<android.view.View>(R.id.plugin_back).setOnClickListener { goBack() }

        val webView = findViewById<WebView>(R.id.plugin_web)
        web = webView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
        }

        // Токен в cookie: страницы плагина обращаются к /api/* от имени пользователя
        val token = prefs.activeToken
        if (token.isNotBlank()) {
            CookieManager.getInstance().setCookie(prefs.serverUrl, "silo_token=$token; Path=/")
            CookieManager.getInstance().setCookie(url, "silo_token=$token; Path=/")
            CookieManager.getInstance().flush()
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url
                // Внешние ссылки открываем в браузере
                return if (target.toString().startsWith(prefs.serverUrl)) {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, target))
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                titleView.text = view.title?.takeIf { it.isNotBlank() } ?: name
            }
        }

        webView.loadUrl(url)
    }

    /** Есть ли на устройстве рабочий провайдер WebView. */
    private fun hasWebView(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26+: системный сервис знает, какой пакет WebView выбран (или что его нет)
            WebView.getCurrentWebViewPackage() != null
        } else {
            // API 25: публичного API нет - проверяем фактическим созданием WebView
            WebView(this).destroy()
            true
        }
    } catch (t: Throwable) {
        false
    }

    /** WebView нет (AOSP-эмулятор, урезанная прошивка): объясняем причину вместо падения. */
    private fun showWebViewMissing() {
        AlertDialog.Builder(this)
            .setTitle(R.string.plugin_webview_missing_title)
            .setMessage(R.string.plugin_webview_missing_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun goBack() {
        if (web?.canGoBack() == true) web?.goBack() else finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Есть история в WebView - шаг назад, иначе отдаём обработку системе
        if (web?.canGoBack() == true) web?.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web?.destroy()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_HREF = "href"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, href: String, title: String) =
            Intent(context, PluginActivity::class.java)
                .putExtra(EXTRA_HREF, href)
                .putExtra(EXTRA_TITLE, title)
    }
}
