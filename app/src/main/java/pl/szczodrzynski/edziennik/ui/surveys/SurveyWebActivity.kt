package pl.szczodrzynski.edziennik.ui.surveys

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.api.events.ApiTaskErrorEvent
import pl.szczodrzynski.edziennik.data.api.events.SurveySessionEvent
import pl.szczodrzynski.edziennik.databinding.ActivitySurveyWebBinding
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.toDrawable

internal fun isSurveyWebUrlAllowed(url: String): Boolean {
    val uri = Uri.parse(url)
    return uri.scheme == "https" && uri.host == "synergia.librus.pl" &&
        uri.userInfo == null && (uri.port == -1 || uri.port == 443)
}

class SurveyWebActivity : AppCompatActivity() {
    companion object {
        private var active: SurveyWebActivity? = null
        fun open(activity: AppCompatActivity, profileId: Int, surveyId: Long, title: String) {
            activity.startActivity(Intent(activity, SurveyWebActivity::class.java)
                .putExtra("profileId", profileId).putExtra("surveyId", surveyId).putExtra("surveyTitle", title))
        }
    }
    private val app by lazy { application as App }
    private lateinit var b: ActivitySurveyWebBinding
    private var profileId = 0
    private var surveyId = 0L
    private var requestId = ""
    private var timeout: Job? = null
    private var retried = false
    private var destroyed = false
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.uiManager.applyTheme(this)
        app.uiManager.applyLanguage(this)
        profileId = intent.getIntExtra("profileId", 0)
        surveyId = intent.getLongExtra("surveyId", 0L)
        if (profileId <= 0 || surveyId <= 0) { finish(); return }
        active?.let { previous -> previous.dispose(); previous.finish() }
        active = this
        b = ActivitySurveyWebBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.title = intent.getStringExtra("surveyTitle") ?: getString(R.string.surveys_title)
        b.toolbar.navigationIcon = CommunityMaterial.Icon.cmd_close.toDrawable(this, R.attr.colorPrimary.resolveAttr(this))
        b.toolbar.setNavigationContentDescription(R.string.close)
        b.toolbar.setNavigationOnClickListener { finish() }
        placeToolbar()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(b.root)
        EventBus.getDefault().register(this)
        b.browser.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(b.browser, false)
        b.browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String) = !isSurveyWebUrlAllowed(url)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = !isSurveyWebUrlAllowed(request.url.toString())
            override fun onPageFinished(view: WebView, url: String) {
                if (destroyed) return
                if (Uri.parse(url).path?.contains("loguj") == true) {
                    if (!retried) { retried = true; requestSession(true) }
                    else showError()
                    return
                }
                view.evaluateJavascript("document.querySelector('input[type=password]') !== null") { loginPage ->
                    if (destroyed) return@evaluateJavascript
                    if (loginPage == "true") {
                        if (!retried) { retried = true; requestSession(true) }
                        else showError()
                    } else {
                        timeout?.cancel()
                        b.progress.isVisible = false
                    }
                }
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showError()
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400) showError()
            }
        }
        requestSession(false)
    }

    private fun placeToolbar() {
        b.root.removeView(b.toolbar)
        if (app.config.ui.toolbarAtBottom) b.root.addView(b.toolbar)
        else b.root.addView(b.toolbar, 0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        placeToolbar()
        ViewCompat.requestApplyInsets(b.root)
    }

    private fun requestSession(forceRefresh: Boolean) {
        b.error.isVisible = false
        b.progress.isVisible = true
        b.browser.isVisible = false
        requestId = UUID.randomUUID().toString()
        timeout?.cancel()
        timeout = lifecycleScope.launch { delay(90_000L); showError() }
        EdziennikTask.surveyOpen(profileId, requestId, forceRefresh).enqueue(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSession(event: SurveySessionEvent) {
        if (event.profileId != profileId || event.requestId != requestId || active !== this || destroyed) return
        val cookies = CookieManager.getInstance()
        // WebView has a cookie store separate from the HTTP client. Clear the previous profile first.
        cookies.removeAllCookies {
            if (active !== this || destroyed) return@removeAllCookies
            cookies.setCookie("https://synergia.librus.pl/", "DZIENNIKSID=${event.sessionId}; Path=/; Secure; HttpOnly") {
                if (active !== this || destroyed) return@setCookie
                b.browser.isVisible = true
                b.browser.loadUrl("https://synergia.librus.pl/ankieta-${surveyId}")
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSessionError(event: ApiTaskErrorEvent) {
        val request = event.request as? EdziennikTask.SurveyOpenRequest ?: return
        if (request.requestId == requestId && event.error.profileId == profileId) showError()
    }

    private fun showError() {
        if (destroyed) return
        timeout?.cancel()
        requestId = ""
        b.browser.stopLoading()
        b.progress.isVisible = false
        b.browser.isVisible = false
        b.error.isVisible = true
    }

    private fun dispose() {
        if (destroyed) return
        destroyed = true
        timeout?.cancel()
        b.browser.stopLoading()
        b.browser.destroy()
    }

    override fun onDestroy() {
        if (active === this) active = null
        if (::b.isInitialized) dispose()
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        super.onDestroy()
    }
}
