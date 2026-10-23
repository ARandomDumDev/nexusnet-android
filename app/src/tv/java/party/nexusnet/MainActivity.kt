package party.nexusnet

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * NexusNet for Android TV.
 *
 * Remote controls
 *  - Side rail: UP/DOWN to move, CENTER to open, RIGHT to enter the page.
 *  - Page: D-pad drives an on-screen pointer (hold to accelerate). CENTER clicks.
 *    Push the pointer against the top/bottom edge to scroll. LEFT at the left edge
 *    returns to the side rail. PAGE/CHANNEL UP/DOWN scroll a page at a time.
 *  - MENU toggles the "More" panel. BACK closes panels / goes back / exits.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val HOME_URL =
            "https://nexusnet.party/?app=true"

        private const val BASE_URL =
            "https://nexusnet.party"

        private const val STATE_URL = "nexus_url"

        private const val PREFS_NAME = "nexusnet_tv"
        private const val PREF_ZOOM = "zoom_percent"

        private const val ZOOM_MIN = 50
        private const val ZOOM_MAX = 200
        private const val ZOOM_STEP = 5
        private const val ZOOM_DEFAULT = 75

        private const val RAIL_WIDTH_DP = 220
        private const val PANEL_WIDTH_DP = 400
        private const val CURSOR_IDLE_MS = 5000L

        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        private const val BLUE = 0xFF2563EB.toInt()
        private const val CYAN = 0xFF22D3EE.toInt()
        private const val BACKGROUND = 0xFF0B0F19.toInt()
        private const val SURFACE = 0xFF121826.toInt()
        private const val SURFACE_LIGHT = 0xFF1A2335.toInt()
        private const val SURFACE_FOCUS = 0xFF24324D.toInt()
        private const val SELECTED_BG = 0x2222D3EE.toInt()
        private const val TEXT = 0xFFF8FAFC.toInt()
        private const val MUTED = 0xFF94A3B8.toInt()
        private const val BORDER = 0xFF273449.toInt()
        private const val ERROR = 0xFFF87171.toInt()
    }

    private enum class Overlay { NONE, MORE, ZOOM }

    private lateinit var root: FrameLayout
    private lateinit var mainRow: LinearLayout
    private lateinit var rail: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var webHost: WebHost
    private lateinit var webView: WebView
    private lateinit var cursorView: CursorView
    private lateinit var scrim: View
    private lateinit var morePanel: LinearLayout
    private lateinit var moreFirstRow: View
    private lateinit var zoomPanel: LinearLayout
    private lateinit var zoomBar: ZoomBar
    private lateinit var zoomValueLabel: TextView
    private lateinit var progress: ProgressBar
    private lateinit var errorPanel: LinearLayout
    private lateinit var retryButton: TextView
    private lateinit var prefs: SharedPreferences

    private val railItems = mutableListOf<RailItem>()

    private var customFont: Typeface = Typeface.SANS_SERIF
    private var customFontBold: Typeface =
        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    private var overlay = Overlay.NONE
    private var focusBeforeOverlay: View? = null
    private var isLoadingPage = false
    private var lastPageUrl: String = HOME_URL
    private var currentTab = "Home"
    private var zoomPercent = ZOOM_DEFAULT

    // Fullscreen video support
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Pointer (cursor) state, in content-area pixels
    private val uiHandler = Handler(Looper.getMainLooper())
    private var cursorX = 0f
    private var cursorY = 0f
    private var cursorPlaced = false
    private var cursorShown = false
    private var centerHeld = false
    private val heldDirs = HashSet<Int>()
    private var holdStartMs = 0L
    private var lastTickMs = 0L

    private val dp: (Int) -> Int = {
        (it * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun dpf(value: Float): Float =
        value * resources.displayMetrics.density

    private fun zoomFactor(): Float = zoomPercent / 100f

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        zoomPercent = prefs.getInt(PREF_ZOOM, ZOOM_DEFAULT)
            .coerceIn(ZOOM_MIN, ZOOM_MAX)

        try {
            customFont = ResourcesCompat.getFont(
                this,
                R.font.montserrat
            ) ?: Typeface.SANS_SERIF

            customFontBold = Typeface.create(
                customFont,
                Typeface.BOLD
            )
        } catch (_: Exception) {
            customFont = Typeface.SANS_SERIF
            customFontBold = Typeface.DEFAULT_BOLD
        }

        window.statusBarColor = BACKGROUND
        window.navigationBarColor = BACKGROUND
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        WindowCompat.setDecorFitsSystemWindows(window, true)

        createLayout()
        configureWebView()
        configureBackButton()
        applyZoom(zoomPercent, persist = false)

        if (savedInstanceState != null) {
            lastPageUrl = savedInstanceState.getString(
                STATE_URL,
                HOME_URL
            ) ?: HOME_URL

            webView.restoreState(savedInstanceState)
            updateSelectedTab(lastPageUrl)

            if (webView.url == null) {
                webView.loadUrl(safeInternalRestoreUrl(lastPageUrl))
            }
        } else {
            webView.loadUrl(HOME_URL)
        }

        webView.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        lastPageUrl = webView.url ?: lastPageUrl

        outState.putString(STATE_URL, lastPageUrl)
        webView.saveState(outState)
    }

    override fun onPause() {
        stopCursorMotion()
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) stopCursorMotion()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)

        if (::webView.isInitialized) {
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null

            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.removeAllViews()
            webView.destroy()
        }

        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private fun createLayout() {
        root = FrameLayout(this).apply {
            setBackgroundColor(BACKGROUND)
        }

        setContentView(root)

        mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        root.addView(mainRow, FrameLayout.LayoutParams(MATCH, MATCH))

        rail = createRail()

        mainRow.addView(
            rail,
            LinearLayout.LayoutParams(dp(RAIL_WIDTH_DP), MATCH)
        )

        mainRow.addView(
            View(this).apply { setBackgroundColor(BORDER) },
            LinearLayout.LayoutParams(dp(1), MATCH)
        )

        content = FrameLayout(this).apply {
            setBackgroundColor(BACKGROUND)
        }

        mainRow.addView(content, LinearLayout.LayoutParams(0, MATCH, 1f))

        webView = WebView(this).apply {
            setBackgroundColor(BACKGROUND)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            isLongClickable = false
            setOnLongClickListener { true }
            isFocusable = true
            isFocusableInTouchMode = true
        }

        webHost = WebHost(this)
        webHost.addView(webView)

        content.addView(webHost, FrameLayout.LayoutParams(MATCH, MATCH))

        progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            progressTintList = ColorStateList.valueOf(CYAN)
            progressBackgroundTintList = ColorStateList.valueOf(SURFACE)
            max = 100
            progress = 0
            visibility = View.GONE
        }

        content.addView(
            progress,
            FrameLayout.LayoutParams(MATCH, dp(4), Gravity.TOP)
        )

        errorPanel = createErrorPanel().apply {
            visibility = View.GONE
        }

        content.addView(errorPanel, FrameLayout.LayoutParams(MATCH, MATCH))

        cursorView = CursorView(this).apply { alpha = 0f }

        content.addView(cursorView, FrameLayout.LayoutParams(MATCH, MATCH))

        content.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val w = right - left
            val h = bottom - top

            if (w <= 0 || h <= 0) {
                return@addOnLayoutChangeListener
            }

            if (!cursorPlaced) {
                cursorX = w / 2f
                cursorY = h / 2f
                cursorPlaced = true
                showCursor()
            }

            cursorX = cursorX.coerceIn(0f, w - 1f)
            cursorY = cursorY.coerceIn(0f, h - 1f)
            cursorView.moveTo(cursorX, cursorY)
        }

        scrim = View(this).apply {
            setBackgroundColor(0x99000000.toInt())
            alpha = 0f
            visibility = View.GONE
            isClickable = true
            isFocusable = false
            contentDescription = "Close menu"
            setOnClickListener { closeOverlay() }
        }

        root.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))

        morePanel = createMorePanel().apply {
            visibility = View.GONE
            alpha = 0f
            translationX = -dp(40).toFloat()
        }

        root.addView(
            morePanel,
            FrameLayout.LayoutParams(
                dp(PANEL_WIDTH_DP),
                MATCH,
                Gravity.START
            ).apply { leftMargin = dp(RAIL_WIDTH_DP) }
        )

        zoomPanel = createZoomPanel().apply {
            visibility = View.GONE
            alpha = 0f
            translationX = -dp(40).toFloat()
        }

        root.addView(
            zoomPanel,
            FrameLayout.LayoutParams(
                dp(PANEL_WIDTH_DP),
                MATCH,
                Gravity.START
            ).apply { leftMargin = dp(RAIL_WIDTH_DP) }
        )

        railItems.forEach { it.selected = it.tab == currentTab }
    }

    // ------------------------------------------------------------------
    // WebView
    // ------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        CookieManager.getInstance().setAcceptCookie(true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = false
            loadWithOverviewMode = false
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            userAgentString =
                "$userAgentString NexusNetAndroid/5.0 NexusNetTV/1.0"
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (!request.isForMainFrame) {
                    return false
                }

                val uri = request.url

                if (isTrustedInternalUrl(uri)) {
                    return false
                }

                // Never launch arbitrary non-web URI schemes.
                if (
                    uri.scheme == "https" ||
                    uri.scheme == "http"
                ) {
                    if (uri.scheme != "https") {
                        Toast.makeText(
                            this@MainActivity,
                            "Insecure links cannot be opened.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return true
                    }

                    openExternalLink(uri)
                }

                return true
            }

            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)

                isLoadingPage = true
                lastPageUrl = url

                progress.animate().cancel()
                progress.alpha = 1f
                progress.progress = 5
                progress.visibility = View.VISIBLE

                val hadError = errorPanel.visibility == View.VISIBLE
                errorPanel.visibility = View.GONE

                if (hadError && overlay == Overlay.NONE && !railHasFocus()) {
                    webView.requestFocusFromTouch()
                }

                updateSelectedTab(url)
            }

            override fun onPageFinished(
                view: WebView,
                url: String
            ) {
                super.onPageFinished(view, url)

                isLoadingPage = false
                lastPageUrl = url
                progress.progress = 100

                progress.animate()
                    .alpha(0f)
                    .setDuration(180)
                    .withEndAction {
                        if (!isLoadingPage) {
                            progress.visibility = View.GONE
                            progress.alpha = 1f
                            progress.progress = 0
                        }
                    }
                    .start()

                updateSelectedTab(url)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)

                if (request.isForMainFrame) {
                    isLoadingPage = false
                    progress.visibility = View.GONE
                    errorPanel.visibility = View.VISIBLE

                    stopCursorMotion()
                    hideCursorNow()

                    if (overlay == Overlay.NONE && !railHasFocus()) {
                        retryButton.requestFocusFromTouch()
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(
                view: WebView,
                newProgress: Int
            ) {
                super.onProgressChanged(view, newProgress)

                progress.progress = newProgress

                if (newProgress in 1..99) {
                    progress.visibility = View.VISIBLE
                }
            }

            override fun onShowCustomView(
                view: View,
                callback: WebChromeClient.CustomViewCallback
            ) {
                enterCustomView(view, callback)
            }

            override fun onHideCustomView() {
                exitCustomView()
            }
        }
    }

    private fun enterCustomView(
        view: View,
        callback: WebChromeClient.CustomViewCallback
    ) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }

        stopCursorMotion()
        hideCursorNow()

        customView = view
        customViewCallback = callback

        view.setBackgroundColor(Color.BLACK)
        root.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        mainRow.visibility = View.INVISIBLE
    }

    private fun exitCustomView() {
        val view = customView ?: return
        val callback = customViewCallback

        customView = null
        customViewCallback = null

        root.removeView(view)
        mainRow.visibility = View.VISIBLE
        callback?.onCustomViewHidden()

        if (overlay == Overlay.NONE) {
            webView.requestFocusFromTouch()
            showCursor()
        }
    }

    private fun isTrustedInternalUrl(uri: Uri): Boolean {
        return uri.scheme.equals("https", true) &&
            (
                uri.host.equals("nexusnet.party", true) ||
                uri.host.equals("www.nexusnet.party", true)
            )
    }

    private fun safeInternalRestoreUrl(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull()

        return if (uri != null && isTrustedInternalUrl(uri)) {
            url
        } else {
            HOME_URL
        }
    }

    private fun openExternalLink(uri: Uri) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "No browser is available to open this link.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun navigate(path: String) {
        val destination = when {
            path == "/" -> HOME_URL
            path.startsWith("/?") -> "$BASE_URL$path"
            else -> "$BASE_URL$path?app=true"
        }

        val uri = Uri.parse(destination)

        if (!isTrustedInternalUrl(uri)) {
            return
        }

        if (webView.url == destination) {
            webView.evaluateJavascript(
                "window.scrollTo({top: 0, behavior: 'smooth'});",
                null
            )
            return
        }

        webView.loadUrl(destination)
    }

    // ------------------------------------------------------------------
    // Zoom
    // ------------------------------------------------------------------

    private fun applyZoom(percent: Int, persist: Boolean = true) {
        zoomPercent = percent.coerceIn(ZOOM_MIN, ZOOM_MAX)
        webHost.zoom = zoomFactor()

        if (::zoomValueLabel.isInitialized) {
            zoomValueLabel.text = "$zoomPercent%"
        }

        if (::zoomBar.isInitialized && zoomBar.value != zoomPercent) {
            zoomBar.value = zoomPercent
        }

        if (persist) {
            prefs.edit().putInt(PREF_ZOOM, zoomPercent).apply()
        }
    }

    // ------------------------------------------------------------------
    // Side rail
    // ------------------------------------------------------------------

    private fun createRail(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(14), dp(24), dp(14), dp(16))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
        }

        header.addView(
            TextView(this).apply {
                text = "NexusNet"
                textSize = 22f
                typeface = customFontBold
                setTextColor(TEXT)
            }
        )

        header.addView(
            TextView(this).apply {
                text = "Your corner of the internet."
                textSize = 11f
                typeface = customFont
                setTextColor(MUTED)
            }
        )

        panel.addView(
            header,
            LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(20)
            }
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isFocusable = false
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        fun add(item: RailItem) {
            railItems.add(item)
            column.addView(
                item.view,
                LinearLayout.LayoutParams(MATCH, dp(54)).apply {
                    bottomMargin = dp(6)
                }
            )
        }

        add(RailItem("Home", "Home", NavIcon.HOME, false) {
            navigate("/")
        })

        add(RailItem("Nodes", "Nodes", NavIcon.NODES, false) {
            navigate("/nodes")
        })

        add(RailItem("Profile", "Profile", NavIcon.PROFILE, false) {
            navigate("/u")
        })

        add(RailItem(null, "Create", NavIcon.CREATE, true) {
            navigate("/?app=true&compose=true")
        })

        add(RailItem("Signals", "Signals", NavIcon.SIGNALS, false) {
            navigate("/signals")
        })

        add(RailItem("More", "More", NavIcon.MORE, false) {
            openOverlay(Overlay.MORE)
        })

        scroll.addView(column)

        panel.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        return panel
    }

    private inner class RailItem(
        val tab: String?,
        val label: String,
        icon: NavIcon,
        private val prominent: Boolean,
        private val action: () -> Unit
    ) {
        private var focused = false

        var selected = false
            set(value) {
                field = value
                refresh()
            }

        val view: LinearLayout = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            isClickable = true
            isFocusable = true
            contentDescription = label

            setOnClickListener { action() }

            setOnFocusChangeListener { v, hasFocus ->
                focused = hasFocus
                refresh()

                val s = if (hasFocus) 1.04f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
            }
        }

        private val iconView = IconView(this@MainActivity, icon, MUTED)

        private val labelView = TextView(this@MainActivity).apply {
            text = label
            textSize = 16f
            maxLines = 1
            typeface = customFont
            setTextColor(MUTED)
        }

        init {
            view.addView(
                iconView,
                LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                    marginEnd = dp(14)
                }
            )
            view.addView(labelView)
            refresh()
        }

        private fun refresh() {
            val fill = when {
                prominent -> BLUE
                focused -> SURFACE_FOCUS
                selected -> SELECTED_BG
                else -> Color.TRANSPARENT
            }

            val strokePx = when {
                focused && prominent -> dp(3)
                focused -> dp(2)
                else -> 0
            }

            view.background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(fill)
                if (strokePx > 0) setStroke(strokePx, CYAN)
            }

            val active = selected || focused

            iconView.tint = when {
                prominent -> Color.WHITE
                selected -> CYAN
                focused -> TEXT
                else -> MUTED
            }

            labelView.setTextColor(if (prominent || active) TEXT else MUTED)
            labelView.typeface =
                if (prominent || active) customFontBold else customFont
        }
    }

    private fun updateSelectedTab(url: String) {
        val path = runCatching {
            Uri.parse(url).path.orEmpty()
        }.getOrDefault("")

        currentTab = when {
            path.startsWith("/nodes") -> "Nodes"
            path.startsWith("/u") -> "Profile"
            path.startsWith("/signals") -> "Signals"
            path.startsWith("/settings") -> "More"
            else -> "Home"
        }

        // Update selection in place so D-pad focus on the rail is never lost.
        railItems.forEach { it.selected = it.tab == currentTab }
    }

    private fun railHasFocus(): Boolean =
        ::rail.isInitialized && rail.hasFocus()

    private fun focusRail() {
        val target = railItems.firstOrNull { it.tab == currentTab }
            ?: railItems.first()

        stopCursorMotion()

        if (target.view.requestFocusFromTouch()) {
            hideCursorNow()
        }
    }

    private fun focusContent() {
        if (errorPanel.visibility == View.VISIBLE) {
            retryButton.requestFocusFromTouch()
            return
        }

        cursorX = max(cursorX, dpf(48f))
        cursorView.moveTo(cursorX, cursorY)

        webView.requestFocusFromTouch()

        if (!railHasFocus()) {
            showCursor()
        }
    }

    // ------------------------------------------------------------------
    // Overlay panels (More, Display & Zoom)
    // ------------------------------------------------------------------

    private fun panelFor(target: Overlay): View? = when (target) {
        Overlay.MORE -> morePanel
        Overlay.ZOOM -> zoomPanel
        Overlay.NONE -> null
    }

    private fun openOverlay(target: Overlay) {
        if (target == Overlay.NONE || overlay == target) return

        val incoming = panelFor(target) ?: return
        val previous = overlay

        stopCursorMotion()

        if (previous == Overlay.NONE) {
            focusBeforeOverlay = currentFocus ?: webView
            mainRow.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            hideCursorNow()
            scrim.visibility = View.VISIBLE
        } else {
            panelFor(previous)?.let { hidePanel(it) }
        }

        overlay = target

        scrim.animate()
            .alpha(if (target == Overlay.ZOOM) 0.5f else 1f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()

        incoming.visibility = View.VISIBLE

        incoming.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(260)
            .setInterpolator(DecelerateInterpolator())
            .start()

        val focusTarget: View =
            if (target == Overlay.ZOOM) zoomBar else moreFirstRow

        focusTarget.requestFocusFromTouch()
    }

    private fun closeOverlay(restoreFocus: Boolean = true) {
        if (overlay == Overlay.NONE) return

        val closing = panelFor(overlay)
        overlay = Overlay.NONE

        closing?.let { hidePanel(it) }

        scrim.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                if (overlay == Overlay.NONE) {
                    scrim.visibility = View.GONE
                }
            }
            .start()

        mainRow.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS

        val target = focusBeforeOverlay?.takeIf {
            it.isAttachedToWindow && it.visibility == View.VISIBLE
        }

        focusBeforeOverlay = null

        if (restoreFocus) {
            if (target == null || !target.requestFocusFromTouch()) {
                webView.requestFocusFromTouch()
            }

            if (!railHasFocus() && errorPanel.visibility != View.VISIBLE) {
                showCursor()
            }
        }
    }

    private fun hidePanel(panel: View) {
        panel.animate()
            .alpha(0f)
            .translationX(-dp(40).toFloat())
            .setDuration(200)
            .withEndAction {
                if (panelFor(overlay) !== panel) {
                    panel.visibility = View.GONE
                }
            }
            .start()
    }

    private fun sidePanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(22))
            background = GradientDrawable().apply {
                setColor(SURFACE)
                val r = dp(24).toFloat()
                cornerRadii = floatArrayOf(
                    0f, 0f,
                    r, r,
                    r, r,
                    0f, 0f
                )
                setStroke(dp(1), BORDER)
            }
            elevation = dp(24).toFloat()
            isClickable = true
            isFocusable = false
        }
    }

    private fun createPanelHeader(
        title: String,
        subtitle: String
    ): LinearLayout {
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        titleGroup.addView(
            TextView(this).apply {
                text = title
                textSize = 22f
                typeface = customFontBold
                setTextColor(TEXT)
            }
        )

        titleGroup.addView(
            TextView(this).apply {
                text = subtitle
                textSize = 12f
                typeface = customFont
                setTextColor(MUTED)
            }
        )

        heading.addView(titleGroup, LinearLayout.LayoutParams(0, WRAP, 1f))

        val close = FrameLayout(this).apply {
            contentDescription = "Close menu"
            setOnClickListener { closeOverlay() }
        }

        styleFocusable(close, SURFACE_LIGHT, SURFACE_FOCUS, 14)

        close.addView(
            IconView(this, NavIcon.CLOSE, TEXT),
            FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
        )

        heading.addView(close, LinearLayout.LayoutParams(dp(42), dp(42)))

        return heading
    }

    private fun createMorePanel(): LinearLayout {
        val panel = sidePanel()

        panel.addView(
            createPanelHeader("More", "Everything else in NexusNet"),
            LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(16)
            }
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isFocusable = false
        }

        val items = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        moreFirstRow = navRow(
            items, "Home", "Your NexusNet feed", NavIcon.HOME, "/"
        )

        navRow(
            items, "Search", "People, posts and nodes",
            NavIcon.SEARCH, "/search"
        )

        navRow(
            items, "Notifications", "See what's happening",
            NavIcon.NOTIFICATIONS, "/notifications"
        )

        navRow(
            items, "Minecraft", "Your NexusPlay corner",
            NavIcon.MINECRAFT, "/minecraft"
        )

        navRow(
            items, "Settings", "Make NexusNet yours",
            NavIcon.SETTINGS, "/settings"
        )

        navRow(
            items, "Changelog", "See what's new",
            NavIcon.CHANGELOG, "/changelog"
        )

        addMoreRow(
            items, "Display & Zoom", "Make NexusNet larger or smaller",
            NavIcon.DISPLAY
        ) {
            openOverlay(Overlay.ZOOM)
        }

        scroll.addView(items)

        panel.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        return panel
    }

    private fun navRow(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        icon: NavIcon,
        path: String
    ): View {
        return addMoreRow(parent, title, subtitle, icon) {
            closeOverlay()
            navigate(path)
        }
    }

    private fun addMoreRow(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        icon: NavIcon,
        action: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            contentDescription = "$title. $subtitle"
            setOnClickListener { action() }
        }

        styleFocusable(row, SURFACE_LIGHT, SURFACE_FOCUS, 16, 1.02f)

        val iconHolder = FrameLayout(this).apply {
            background = rounded(BLUE, 12)
        }

        iconHolder.addView(
            IconView(this, icon, TEXT),
            FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
        )

        row.addView(
            iconHolder,
            LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginEnd = dp(14)
            }
        )

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        labels.addView(
            TextView(this).apply {
                text = title
                textSize = 15f
                typeface = customFontBold
                setTextColor(TEXT)
            }
        )

        labels.addView(
            TextView(this).apply {
                text = subtitle
                textSize = 12f
                typeface = customFont
                setTextColor(MUTED)
                maxLines = 1
            }
        )

        row.addView(labels, LinearLayout.LayoutParams(0, WRAP, 1f))

        parent.addView(
            row,
            LinearLayout.LayoutParams(MATCH, dp(60)).apply {
                bottomMargin = dp(8)
            }
        )

        return row
    }

    private fun createZoomPanel(): LinearLayout {
        val panel = sidePanel()

        panel.addView(
            createPanelHeader("Display & Zoom", "Size NexusNet for your TV"),
            LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(18)
            }
        )

        zoomValueLabel = TextView(this).apply {
            text = "$zoomPercent%"
            textSize = 40f
            typeface = customFontBold
            setTextColor(CYAN)
            gravity = Gravity.CENTER
        }

        panel.addView(zoomValueLabel, LinearLayout.LayoutParams(MATCH, WRAP))

        zoomBar = ZoomBar(this, ZOOM_MIN, ZOOM_MAX, ZOOM_STEP).apply {
            value = zoomPercent
            contentDescription = "Zoom level"
            onChange = { applyZoom(it) }
        }

        panel.addView(
            zoomBar,
            LinearLayout.LayoutParams(MATCH, dp(56)).apply {
                topMargin = dp(8)
                bottomMargin = dp(6)
            }
        )

        panel.addView(
            TextView(this).apply {
                text = "Press Left or Right on your remote to adjust. " +
                    "Lower values fit more on screen, higher values make " +
                    "everything bigger. The notch marks 100%."
                textSize = 12f
                typeface = customFont
                setTextColor(MUTED)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(8)
            }
        )

        panel.addView(
            panelButton("Reset to default ($ZOOM_DEFAULT%)", false) {
                applyZoom(ZOOM_DEFAULT)
            },
            LinearLayout.LayoutParams(MATCH, dp(50)).apply {
                topMargin = dp(10)
            }
        )

        panel.addView(
            panelButton("Done", true) {
                closeOverlay()
            },
            LinearLayout.LayoutParams(MATCH, dp(50)).apply {
                topMargin = dp(10)
            }
        )

        return panel
    }

    private fun panelButton(
        label: String,
        primary: Boolean,
        action: () -> Unit
    ): TextView {
        val button = TextView(this).apply {
            text = label
            textSize = 15f
            typeface = customFontBold
            gravity = Gravity.CENTER
            setTextColor(TEXT)
            contentDescription = label
            setOnClickListener { action() }
        }

        styleFocusable(
            button,
            if (primary) BLUE else SURFACE_LIGHT,
            if (primary) BLUE else SURFACE_FOCUS,
            14
        )

        return button
    }

    // ------------------------------------------------------------------
    // Error panel
    // ------------------------------------------------------------------

    private fun createErrorPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setBackgroundColor(BACKGROUND)
            isClickable = true
        }

        panel.addView(
            IconView(this, NavIcon.HOME, CYAN),
            LinearLayout.LayoutParams(dp(64), dp(64)).apply {
                bottomMargin = dp(22)
            }
        )

        panel.addView(
            TextView(this).apply {
                text = "Can't reach NexusNet"
                textSize = 26f
                typeface = customFontBold
                setTextColor(TEXT)
                gravity = Gravity.CENTER
            }
        )

        panel.addView(
            TextView(this).apply {
                text = "Check your connection and try again."
                textSize = 15f
                typeface = customFont
                setTextColor(MUTED)
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(26))
            }
        )

        retryButton = TextView(this).apply {
            text = "Try again"
            textSize = 16f
            typeface = customFontBold
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(36), dp(16), dp(36), dp(16))
            contentDescription = "Try again"

            // The original hid this button after the first tap, which made
            // it vanish on every later error. It now stays put.
            setOnClickListener {
                webView.loadUrl(safeInternalRestoreUrl(lastPageUrl))
            }
        }

        styleFocusable(retryButton, BLUE, BLUE, 16, 1.06f)

        panel.addView(retryButton)

        return panel
    }

    private fun configureBackButton() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        customView != null -> exitCustomView()

                        overlay != Overlay.NONE -> closeOverlay()

                        errorPanel.visibility == View.VISIBLE -> {
                            errorPanel.visibility = View.GONE
                            webView.loadUrl(HOME_URL)
                        }

                        webView.canGoBack() -> webView.goBack()

                        else -> finish()
                    }
                }
            }
        )
    }

    // ------------------------------------------------------------------
    // Remote control input
    // ------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN

        // Fullscreen video: forward everything except BACK to the page.
        if (customView != null) {
            if (code != KeyEvent.KEYCODE_BACK && webView.dispatchKeyEvent(event)) {
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        // MENU toggles the More panel from anywhere.
        if (code == KeyEvent.KEYCODE_MENU) {
            if (down && event.repeatCount == 0) {
                if (overlay == Overlay.NONE) {
                    openOverlay(Overlay.MORE)
                } else {
                    closeOverlay()
                }
            }
            return true
        }

        // Panels own the D-pad while open.
        if (overlay != Overlay.NONE) {
            if (
                (code == KeyEvent.KEYCODE_DPAD_LEFT ||
                    code == KeyEvent.KEYCODE_DPAD_RIGHT) &&
                currentFocus !is ZoomBar
            ) {
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        // Page-at-a-time scrolling.
        when (code) {
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (down) scrollPage(-6f)
                return true
            }

            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (down) scrollPage(6f)
                return true
            }
        }

        if (railHasFocus()) {
            return when (code) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (down && event.repeatCount == 0) focusContent()
                    true
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> true

                else -> super.dispatchKeyEvent(event)
            }
        }

        if (errorPanel.visibility == View.VISIBLE) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (down && event.repeatCount == 0) focusRail()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_RIGHT -> return true
            }

            if (!retryButton.isFocused) {
                retryButton.requestFocusFromTouch()
            }

            return super.dispatchKeyEvent(event)
        }

        return handleContentKey(event) || super.dispatchKeyEvent(event)
    }

    /** Returns true when the key was consumed by the pointer logic. */
    private fun handleContentKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN

        when (code) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (down) {
                    if (event.repeatCount == 0) {
                        if (
                            code == KeyEvent.KEYCODE_DPAD_LEFT &&
                            cursorX <= dpf(4f)
                        ) {
                            focusRail()
                            return true
                        }

                        val wasIdle = heldDirs.isEmpty()
                        heldDirs.add(code)
                        showCursor()

                        if (wasIdle) {
                            val now = SystemClock.uptimeMillis()
                            holdStartMs = now
                            lastTickMs = now

                            applyMotion(dirX(), dirY(), dpf(16f), 0.8f)

                            uiHandler.removeCallbacks(cursorTick)
                            uiHandler.postDelayed(cursorTick, 120L)
                        }
                    }
                } else {
                    heldDirs.remove(code)

                    if (heldDirs.isEmpty()) {
                        uiHandler.removeCallbacks(cursorTick)
                    }
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                // A real keyboard's Enter belongs to the page (forms, chat).
                if (
                    (code == KeyEvent.KEYCODE_ENTER ||
                        code == KeyEvent.KEYCODE_NUMPAD_ENTER) &&
                    isTypingKeyboard(event)
                ) {
                    return false
                }

                if (down) {
                    if (event.repeatCount == 0) centerHeld = true
                } else if (centerHeld) {
                    centerHeld = false

                    if (!cursorShown) {
                        // First press only wakes the pointer.
                        showCursor()
                    } else {
                        keepCursorAlive()
                        clickAtCursor()
                    }
                }

                return true
            }
        }

        return false
    }

    private fun isTypingKeyboard(event: KeyEvent): Boolean {
        return event.device?.keyboardType ==
            InputDevice.KEYBOARD_TYPE_ALPHABETIC
    }

    // ------------------------------------------------------------------
    // Pointer emulation
    // ------------------------------------------------------------------

    private val hideCursorRunnable = Runnable {
        cursorShown = false
        cursorView.animate().alpha(0f).setDuration(300).start()
    }

    private val cursorTick = object : Runnable {
        override fun run() {
            if (heldDirs.isEmpty()) return

            val now = SystemClock.uptimeMillis()
            val dt = (now - lastTickMs).coerceIn(1L, 50L) / 1000f
            lastTickMs = now

            val held = (now - holdStartMs) / 1000f
            val ramp = min(held / 1.2f, 1f)

            val speed = dpf(240f) + ramp * dpf(900f)
            val scrollRate = 7f + ramp * 14f

            keepCursorAlive()
            applyMotion(dirX(), dirY(), speed * dt, scrollRate * dt)

            uiHandler.postDelayed(this, 16L)
        }
    }

    private fun dirX(): Int =
        (if (KeyEvent.KEYCODE_DPAD_RIGHT in heldDirs) 1 else 0) -
            (if (KeyEvent.KEYCODE_DPAD_LEFT in heldDirs) 1 else 0)

    private fun dirY(): Int =
        (if (KeyEvent.KEYCODE_DPAD_DOWN in heldDirs) 1 else 0) -
            (if (KeyEvent.KEYCODE_DPAD_UP in heldDirs) 1 else 0)

    private fun stopCursorMotion() {
        heldDirs.clear()
        centerHeld = false
        uiHandler.removeCallbacks(cursorTick)
    }

    private fun showCursor() {
        cursorShown = true
        cursorView.animate().cancel()
        cursorView.alpha = 1f
        keepCursorAlive()
    }

    private fun keepCursorAlive() {
        uiHandler.removeCallbacks(hideCursorRunnable)
        uiHandler.postDelayed(hideCursorRunnable, CURSOR_IDLE_MS)
    }

    private fun hideCursorNow() {
        uiHandler.removeCallbacks(hideCursorRunnable)
        cursorShown = false
        cursorView.animate().cancel()
        cursorView.alpha = 0f
    }

    private fun applyMotion(
        dx: Int,
        dy: Int,
        distancePx: Float,
        scrollTicks: Float
    ) {
        val norm = if (dx != 0 && dy != 0) 0.7071f else 1f

        moveCursorBy(dx * distancePx * norm, dy * distancePx * norm)

        val h = content.height.toFloat()
        val edge = dpf(6f)

        if (dy < 0 && cursorY <= edge) {
            scrollPage(-scrollTicks)
        } else if (dy > 0 && cursorY >= h - 1f - edge) {
            scrollPage(scrollTicks)
        }
    }

    private fun moveCursorBy(dx: Float, dy: Float) {
        val w = content.width.toFloat()
        val h = content.height.toFloat()

        if (w <= 0f || h <= 0f) return

        cursorX = (cursorX + dx).coerceIn(0f, w - 1f)
        cursorY = (cursorY + dy).coerceIn(0f, h - 1f)

        cursorView.moveTo(cursorX, cursorY)
        sendHover()
    }

    private fun pointerEvent(
        action: Int,
        px: Float,
        py: Float,
        source: Int,
        tool: Int,
        downTime: Long,
        eventTime: Long,
        vScroll: Float = 0f
    ): MotionEvent {
        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = tool
            }
        )

        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = px
                y = py
                pressure =
                    if (source == InputDevice.SOURCE_TOUCHSCREEN) 1f else 0f
                size = 1f
                if (vScroll != 0f) {
                    setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
                }
            }
        )

        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            props,
            coords,
            0,
            0,
            1f,
            1f,
            0,
            0,
            source,
            0
        )
    }

    /** Sends a mouse-move so hover menus and tooltips work. */
    private fun sendHover() {
        if (!::webView.isInitialized) return

        val now = SystemClock.uptimeMillis()
        val ev = pointerEvent(
            MotionEvent.ACTION_HOVER_MOVE,
            cursorX / zoomFactor(),
            cursorY / zoomFactor(),
            InputDevice.SOURCE_MOUSE,
            MotionEvent.TOOL_TYPE_MOUSE,
            now,
            now
        )

        webView.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    /** Mouse-wheel scroll at the pointer. Positive ticks scroll down. */
    private fun scrollPage(ticks: Float) {
        if (!::webView.isInitialized) return

        val now = SystemClock.uptimeMillis()
        val ev = pointerEvent(
            MotionEvent.ACTION_SCROLL,
            cursorX / zoomFactor(),
            cursorY / zoomFactor(),
            InputDevice.SOURCE_MOUSE,
            MotionEvent.TOOL_TYPE_MOUSE,
            now,
            now,
            -ticks
        )

        webView.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    private fun dispatchTouch(
        action: Int,
        lx: Float,
        ly: Float,
        downTime: Long
    ) {
        val now = SystemClock.uptimeMillis()
        val ev = pointerEvent(
            action,
            lx,
            ly,
            InputDevice.SOURCE_TOUCHSCREEN,
            MotionEvent.TOOL_TYPE_FINGER,
            downTime,
            now
        )

        webView.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun clickAtCursor() {
        if (!::webView.isInitialized) return

        if (!webView.hasFocus()) {
            webView.requestFocusFromTouch()
        }

        val lx = cursorX / zoomFactor()
        val ly = cursorY / zoomFactor()

        sendHover()

        val downTime = SystemClock.uptimeMillis()

        dispatchTouch(MotionEvent.ACTION_DOWN, lx, ly, downTime)
        cursorView.pulse()

        uiHandler.postDelayed({
            if (::webView.isInitialized) {
                dispatchTouch(MotionEvent.ACTION_UP, lx, ly, downTime)
            }
        }, 40L)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun styleFocusable(
        v: View,
        normal: Int,
        focusedColor: Int,
        radiusDp: Int,
        focusScale: Float = 1.03f
    ) {
        v.isFocusable = true
        v.isClickable = true

        fun render(isFocused: Boolean) {
            v.background = GradientDrawable().apply {
                cornerRadius = dp(radiusDp).toFloat()
                setColor(if (isFocused) focusedColor else normal)
                if (isFocused) setStroke(dp(2), CYAN)
            }
        }

        render(false)

        v.setOnFocusChangeListener { view, hasFocus ->
            render(hasFocus)

            val s = if (hasFocus) focusScale else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
    }

    private fun rounded(
        color: Int,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    // ------------------------------------------------------------------
    // Custom views
    // ------------------------------------------------------------------

    /**
     * Hosts the WebView at (screen size / zoom) and scales it back up to fill
     * the screen. This is a true browser-style zoom: the page lays out at a
     * different CSS width, so lower values fit more content and higher values
     * make everything bigger.
     */
    private class WebHost(context: Context) : FrameLayout(context) {

        var zoom: Float = 1f
            set(value) {
                if (field != value) {
                    field = value
                    requestLayout()
                }
            }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val h = MeasureSpec.getSize(heightMeasureSpec)

            setMeasuredDimension(w, h)

            val cw = max(1, (w / zoom).roundToInt())
            val ch = max(1, (h / zoom).roundToInt())

            for (i in 0 until childCount) {
                getChildAt(i).measure(
                    MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY)
                )
            }
        }

        override fun onLayout(
            changed: Boolean,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int
        ) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)

                child.layout(0, 0, child.measuredWidth, child.measuredHeight)
                child.pivotX = 0f
                child.pivotY = 0f
                child.scaleX = zoom
                child.scaleY = zoom
            }
        }
    }

    /** On-screen pointer driven by the remote's D-pad. */
    private class CursorView(context: Context) : View(context) {

        private val density = resources.displayMetrics.density

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BACKGROUND
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            strokeJoin = Paint.Join.ROUND
        }

        private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x5522D3EE.toInt()
            style = Paint.Style.FILL
        }

        private val arrow = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, 19f)
            lineTo(4.6f, 14.6f)
            lineTo(8.2f, 22.5f)
            lineTo(11f, 21.2f)
            lineTo(7.6f, 13.4f)
            lineTo(13.8f, 13.4f)
            close()
        }

        private var cx = 0f
        private var cy = 0f
        private var press = 1f

        init {
            isClickable = false
            isFocusable = false
        }

        fun moveTo(x: Float, y: Float) {
            cx = x
            cy = y
            invalidate()
        }

        fun pulse() {
            ValueAnimator.ofFloat(1f, 0.78f, 1f).apply {
                duration = 160
                addUpdateListener {
                    press = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val s = 1.5f * density * press
            val save = canvas.save()

            canvas.translate(cx, cy)
            canvas.scale(s, s)
            canvas.drawCircle(0f, 0f, 13f, halo)
            canvas.drawPath(arrow, fill)
            canvas.drawPath(arrow, outline)

            canvas.restoreToCount(save)
        }
    }

    /** D-pad friendly slider: focus it, then press Left / Right. */
    @SuppressLint("ClickableViewAccessibility")
    private class ZoomBar(
        context: Context,
        private val minValue: Int,
        private val maxValue: Int,
        private val step: Int
    ) : View(context) {

        var value: Int = minValue
            set(v) {
                field = v.coerceIn(minValue, maxValue)
                invalidate()
            }

        var onChange: ((Int) -> Unit)? = null

        private val density = resources.displayMetrics.density

        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BORDER
            style = Paint.Style.FILL
        }

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLUE
            style = Paint.Style.FILL
        }

        private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CYAN
            style = Paint.Style.FILL
        }

        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
        }

        private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            strokeCap = Paint.Cap.ROUND
        }

        private val rect = RectF()

        init {
            isFocusable = true

            setOnKeyListener { _, keyCode, event ->
                if (
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        change(
                            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -step
                            else step
                        )
                    }
                    true
                } else {
                    false
                }
            }

            setOnTouchListener { _, e ->
                if (
                    e.actionMasked == MotionEvent.ACTION_DOWN ||
                    e.actionMasked == MotionEvent.ACTION_MOVE
                ) {
                    val pad = 18f * density
                    val frac = ((e.x - pad) / (width - 2 * pad))
                        .coerceIn(0f, 1f)
                    val raw = minValue + frac * (maxValue - minValue)
                    val snapped = ((raw / step).roundToInt() * step)
                        .coerceIn(minValue, maxValue)

                    if (snapped != value) {
                        value = snapped
                        onChange?.invoke(snapped)
                    }
                    true
                } else {
                    false
                }
            }
        }

        private fun change(delta: Int) {
            val next = (value + delta).coerceIn(minValue, maxValue)

            if (next != value) {
                value = next
                onChange?.invoke(next)
            }
        }

        override fun onFocusChanged(
            gainFocus: Boolean,
            direction: Int,
            previouslyFocusedRect: android.graphics.Rect?
        ) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val pad = 18f * density
            val cy = height / 2f
            val th = 8f * density
            val left = pad
            val right = width - pad
            val span = (maxValue - minValue).toFloat()

            val x = left + (right - left) * ((value - minValue) / span)
            val x100 = left + (right - left) * ((100 - minValue) / span)

            rect.set(left, cy - th / 2f, right, cy + th / 2f)
            canvas.drawRoundRect(rect, th / 2f, th / 2f, track)

            rect.set(left, cy - th / 2f, x, cy + th / 2f)
            canvas.drawRoundRect(rect, th / 2f, th / 2f, fill)

            canvas.drawLine(x100, cy - th * 1.4f, x100, cy + th * 1.4f, tick)

            canvas.drawCircle(x, cy, 12f * density, thumb)

            if (isFocused) {
                canvas.drawCircle(x, cy, 16f * density, ring)
            }
        }
    }

    private enum class NavIcon {
        HOME,
        NODES,
        PROFILE,
        CREATE,
        SIGNALS,
        MORE,
        SEARCH,
        NOTIFICATIONS,
        MINECRAFT,
        SETTINGS,
        CHANGELOG,
        CLOSE,
        RETRY,
        DISPLAY
    }

    /**
     * Lightweight vector-style icons drawn directly with Canvas.
     * No drawable downloads, external icon libraries, or emoji icons.
     */
    private class IconView(
        context: Context,
        private val icon: NavIcon,
        initialTint: Int
    ) : View(context) {

        var tint: Int = initialTint
            set(value) {
                field = value
                paint.color = value
                invalidate()
            }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = initialTint
            style = Paint.Style.STROKE
            strokeWidth = 2f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val save = canvas.save()

            val size = minOf(width, height).toFloat()
            val scale = size / 24f

            canvas.translate(
                (width - size) / 2f,
                (height - size) / 2f
            )
            canvas.scale(scale, scale)

            paint.strokeWidth = 1.9f
            paint.style = Paint.Style.STROKE
            path.reset()

            when (icon) {
                NavIcon.HOME -> {
                    path.moveTo(3f, 11f)
                    path.lineTo(12f, 3f)
                    path.lineTo(21f, 11f)
                    canvas.drawPath(path, paint)
                    canvas.drawRoundRect(
                        5f, 10f, 19f, 21f,
                        2f, 2f, paint
                    )
                    canvas.drawLine(10f, 21f, 10f, 15f, paint)
                    canvas.drawLine(14f, 21f, 14f, 15f, paint)
                }

                NavIcon.NODES -> {
                    canvas.drawCircle(12f, 5f, 3f, paint)
                    canvas.drawCircle(5f, 18f, 3f, paint)
                    canvas.drawCircle(19f, 18f, 3f, paint)
                    canvas.drawLine(10.5f, 7.5f, 6.5f, 15f, paint)
                    canvas.drawLine(13.5f, 7.5f, 17.5f, 15f, paint)
                    canvas.drawLine(8f, 18f, 16f, 18f, paint)
                }

                NavIcon.PROFILE -> {
                    canvas.drawCircle(12f, 8f, 4f, paint)
                    canvas.drawArc(
                        4f, 13f, 20f, 24f,
                        180f, 180f, false, paint
                    )
                }

                NavIcon.CREATE -> {
                    canvas.drawLine(12f, 5f, 12f, 19f, paint)
                    canvas.drawLine(5f, 12f, 19f, 12f, paint)
                }

                NavIcon.SIGNALS -> {
                    canvas.drawCircle(12f, 12f, 2f, paint)
                    canvas.drawArc(
                        5f, 5f, 19f, 19f,
                        215f, 110f, false, paint
                    )
                    canvas.drawArc(
                        1f, 1f, 23f, 23f,
                        215f, 110f, false, paint
                    )
                    canvas.drawArc(
                        5f, 5f, 19f, 19f,
                        35f, 110f, false, paint
                    )
                    canvas.drawArc(
                        1f, 1f, 23f, 23f,
                        35f, 110f, false, paint
                    )
                }

                NavIcon.MORE -> {
                    canvas.drawCircle(5f, 12f, 1.5f, paint)
                    canvas.drawCircle(12f, 12f, 1.5f, paint)
                    canvas.drawCircle(19f, 12f, 1.5f, paint)
                }

                NavIcon.SEARCH -> {
                    canvas.drawCircle(10f, 10f, 6f, paint)
                    canvas.drawLine(14.5f, 14.5f, 21f, 21f, paint)
                }

                NavIcon.NOTIFICATIONS -> {
                    path.moveTo(5f, 17f)
                    path.lineTo(7f, 14f)
                    path.lineTo(7f, 10f)
                    path.cubicTo(7f, 3f, 17f, 3f, 17f, 10f)
                    path.lineTo(17f, 14f)
                    path.lineTo(19f, 17f)
                    path.close()
                    canvas.drawPath(path, paint)
                    canvas.drawLine(9f, 20f, 15f, 20f, paint)
                }

                NavIcon.MINECRAFT -> {
                    canvas.drawRect(3f, 3f, 21f, 21f, paint)
                    canvas.drawRect(7f, 7f, 11f, 11f, paint)
                    canvas.drawRect(14f, 7f, 17f, 10f, paint)
                    canvas.drawLine(3f, 14f, 21f, 14f, paint)
                }

                NavIcon.SETTINGS -> {
                    canvas.drawCircle(12f, 12f, 7f, paint)
                    canvas.drawCircle(12f, 12f, 2.5f, paint)
                    for (angle in 0..315 step 45) {
                        val radians = Math.toRadians(angle.toDouble())
                        val x1 = 12f + 7f * kotlin.math.cos(radians).toFloat()
                        val y1 = 12f + 7f * kotlin.math.sin(radians).toFloat()
                        val x2 = 12f + 9f * kotlin.math.cos(radians).toFloat()
                        val y2 = 12f + 9f * kotlin.math.sin(radians).toFloat()
                        canvas.drawLine(x1, y1, x2, y2, paint)
                    }
                }

                NavIcon.CHANGELOG -> {
                    canvas.drawRoundRect(
                        4f, 3f, 20f, 21f, 2f, 2f, paint
                    )
                    canvas.drawLine(8f, 8f, 16f, 8f, paint)
                    canvas.drawLine(8f, 12f, 16f, 12f, paint)
                    canvas.drawLine(8f, 16f, 13f, 16f, paint)
                }

                NavIcon.CLOSE -> {
                    canvas.drawLine(6f, 6f, 18f, 18f, paint)
                    canvas.drawLine(18f, 6f, 6f, 18f, paint)
                }

                NavIcon.RETRY -> {
                    canvas.drawArc(
                        4f, 4f, 20f, 20f,
                        40f, 285f, false, paint
                    )
                    path.moveTo(16f, 3f)
                    path.lineTo(20f, 4f)
                    path.lineTo(19f, 8f)
                    canvas.drawPath(path, paint)
                }

                NavIcon.DISPLAY -> {
                    canvas.drawRoundRect(
                        3f, 4f, 21f, 16f,
                        2f, 2f, paint
                    )
                    canvas.drawLine(12f, 16f, 12f, 20f, paint)
                    canvas.drawLine(8f, 20f, 16f, 20f, paint)
                }
            }

            canvas.restoreToCount(save)
        }
    }
}
