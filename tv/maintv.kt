package party.nexusnet

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    companion object {
        private const val HOME_URL = "https://nexusnet.party/?app=true"
        private const val BASE_URL = "https://nexusnet.party"
        private const val STATE_URL = "nexus_tv_url"

        private const val BG = 0xFF0B0F19.toInt()
        private const val SIDEBAR = 0xFF101725.toInt()
        private const val SURFACE = 0xFF192438.toInt()
        private const val BLUE = 0xFF2563EB.toInt()
        private const val CYAN = 0xFF22D3EE.toInt()
        private const val WHITE = 0xFFF8FAFC.toInt()
        private const val MUTED = 0xFF94A3B8.toInt()
        private const val BORDER = 0xFF29374C.toInt()
        private const val ERROR = 0xFFF87171.toInt()
    }

    private lateinit var root: LinearLayout
    private lateinit var sidebar: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var errorPanel: LinearLayout
    private lateinit var scroll: ScrollView

    private val navItems = mutableMapOf<String, TextView>()

    private var currentTab = "Home"
    private var lastPageUrl = HOME_URL
    private var loading = false
    private var webHadFocus = false

    private val density: Float
        get() = resources.displayMetrics.density

    private fun dp(value: Int): Int =
        (value * density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        WindowCompat.setDecorFitsSystemWindows(window, true)

        createLayout()
        configureWebView()
        configureBackButton()

        if (savedInstanceState != null) {
            lastPageUrl = safeInternalUrl(
                savedInstanceState.getString(STATE_URL, HOME_URL) ?: HOME_URL
            )

            webView.restoreState(savedInstanceState)
            if (webView.url == null) {
                webView.loadUrl(lastPageUrl)
            }
        } else {
            webView.loadUrl(HOME_URL)
        }

        updateNavigation(lastPageUrl)
    }

    private fun createLayout() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(BG)
            clipChildren = false
            clipToPadding = false
        }

        setContentView(root)

        createSidebar()

        content = FrameLayout(this).apply {
            setBackgroundColor(BG)
            clipChildren = false
            clipToPadding = false
        }

        root.addView(
            content,
            LinearLayout.LayoutParams(0, -1, 1f)
        )

        webView = WebView(this).apply {
            setBackgroundColor(BG)
            isFocusable = true
            isFocusableInTouchMode = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }

        content.addView(
            webView,
            FrameLayout.LayoutParams(-1, -1)
        )

        progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progressTintList =
                android.content.res.ColorStateList.valueOf(CYAN)
            progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(SURFACE)
            visibility = View.GONE
        }

        content.addView(
            progress,
            FrameLayout.LayoutParams(-1, dp(4), Gravity.TOP)
        )

        errorPanel = createErrorPanel().apply {
            visibility = View.GONE
        }

        content.addView(
            errorPanel,
            FrameLayout.LayoutParams(-1, -1)
        )
    }

    private fun createSidebar() {
        sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(22), dp(14), dp(18))
            setBackgroundColor(SIDEBAR)
            elevation = dp(8).toFloat()
            clipChildren = false
            clipToPadding = false
        }

        root.addView(
            sidebar,
            LinearLayout.LayoutParams(dp(224), -1)
        )

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(6), dp(20))
        }

        brand.addView(
            TextView(this).apply {
                text = "NexusNet"
                textSize = 25f
                typeface = Typeface.create(
                    "sans-serif",
                    Typeface.BOLD
                )
                setTextColor(WHITE)
                letterSpacing = -0.03f
            }
        )

        brand.addView(
            TextView(this).apply {
                text = "YOUR CORNER OF THE INTERNET"
                textSize = 9f
                letterSpacing = 0.09f
                setTextColor(MUTED)
                setPadding(0, dp(5), 0, 0)
            }
        )

        sidebar.addView(brand)

        val separator = View(this).apply {
            setBackgroundColor(BORDER)
        }

        sidebar.addView(
            separator,
            LinearLayout.LayoutParams(-1, dp(1)).apply {
                bottomMargin = dp(16)
            }
        )

        scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }

        val items = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        addSectionLabel(items, "YOUR SPACE")

        addNavItem(items, "Home", "⌂", "/")
        addNavItem(items, "Nodes", "◈", "/nodes")
        addNavItem(items, "Search", "⌕", "/search")
        addNavItem(items, "Signals", "◎", "/signals")
        addNavItem(items, "Profile", "○", "/u")

        addSectionLabel(items, "DISCOVER")

        addNavItem(
            items,
            "Notifications",
            "♧",
            "/notifications"
        )

        addNavItem(
            items,
            "Minecraft",
            "▦",
            "/minecraft"
        )

        addSectionLabel(items, "PERSONALIZE")

        addNavItem(
            items,
            "Settings",
            "⚙",
            "/settings"
        )

        addNavItem(
            items,
            "Changelog",
            "≡",
            "/changelog"
        )

        scroll.addView(items)

        sidebar.addView(
            scroll,
            LinearLayout.LayoutParams(0, 0, 1f)
        )

        val footer = TextView(this).apply {
            text = "NEXUSNET  •  TV"
            textSize = 10f
            letterSpacing = 0.08f
            setTextColor(MUTED)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(14), dp(4), dp(2))
        }

        sidebar.addView(
            footer,
            LinearLayout.LayoutParams(-1, dp(34))
        )
    }

    private fun addSectionLabel(
        parent: LinearLayout,
        label: String
    ) {
        parent.addView(
            TextView(this).apply {
                text = label
                textSize = 10f
                letterSpacing = 0.10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(MUTED)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(12), 0, dp(7))
                isFocusable = false
            },
            LinearLayout.LayoutParams(-1, dp(38))
        )
    }

    private fun addNavItem(
        parent: LinearLayout,
        label: String,
        symbol: String,
        path: String
    ) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            clipChildren = false
            clipToPadding = false

            background = focusBackground()

            setPadding(dp(12), 0, dp(8), 0)

            contentDescription = label

            setOnClickListener {
                navigate(path)
            }

            setOnFocusChangeListener { view, focused ->
                view.animate()
                    .scaleX(if (focused) 1.035f else 1f)
                    .scaleY(if (focused) 1.035f else 1f)
                    .setDuration(120)
                    .start()

                view.elevation =
                    if (focused) dp(8).toFloat() else 0f

                val icon = getChildAt(0) as TextView
                val title = getChildAt(1) as TextView

                icon.setTextColor(
                    if (focused || currentTab == label) CYAN
                    else MUTED
                )

                title.setTextColor(
                    if (focused) WHITE
                    else if (currentTab == label) CYAN
                    else MUTED
                )

                title.typeface = Typeface.create(
                    "sans-serif",
                    if (focused || currentTab == label) {
                        Typeface.BOLD
                    } else {
                        Typeface.NORMAL
                    }
                )
            }
        }

        val icon = TextView(this).apply {
            text = symbol
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(
                if (currentTab == label) CYAN else MUTED
            )
        }

        item.addView(
            icon,
            LinearLayout.LayoutParams(dp(36), dp(48)).apply {
                marginEnd = dp(8)
            }
        )

        val title = TextView(this).apply {
            text = label
            textSize = 15f
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(
                if (currentTab == label) CYAN else MUTED
            )
            typeface = Typeface.create(
                "sans-serif",
                if (currentTab == label) Typeface.BOLD
                else Typeface.NORMAL
            )
        }

        item.addView(
            title,
            LinearLayout.LayoutParams(0, -1, 1f)
        )

        navItems[label] = title

        parent.addView(
            item,
            LinearLayout.LayoutParams(-1, dp(54)).apply {
                bottomMargin = dp(4)
            }
        )

        item.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
            ) {
                webHadFocus = true
                webView.requestFocus()
                true
            } else {
                false
            }
        }
    }

    private fun focusBackground(): android.graphics.drawable.Drawable {
        val normal = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(12).toFloat()
        }

        val focused = GradientDrawable().apply {
            setColor(SURFACE)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(2), CYAN)
        }

        return android.graphics.drawable.StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                focused
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                focused
            )
            addState(
                intArrayOf(),
                normal
            )
        }
    }

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
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode =
                WebSettings.MIXED_CONTENT_NEVER_ALLOW

            userAgentString =
                "$userAgentString NexusNetAndroidTV/1.0"
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (!request.isForMainFrame) return false

                val uri = request.url

                if (isTrustedInternalUrl(uri)) return false

                if (
                    uri.scheme.equals("https", true) ||
                    uri.scheme.equals("http", true)
                ) {
                    if (!uri.scheme.equals("https", true)) {
                        Toast.makeText(
                            this@MainActivity,
                            "Insecure links are blocked.",
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
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                loading = true
                lastPageUrl = url
                progress.visibility = View.VISIBLE
                progress.progress = 5
                errorPanel.visibility = View.GONE
                updateNavigation(url)
            }

            override fun onPageFinished(
                view: WebView,
                url: String
            ) {
                super.onPageFinished(view, url)
                loading = false
                lastPageUrl = url
                progress.progress = 100

                progress.postDelayed({
                    progress.visibility = View.GONE
                    progress.progress = 0
                }, 250)

                updateNavigation(url)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)

                if (request.isForMainFrame) {
                    loading = false
                    progress.visibility = View.GONE
                    errorPanel.visibility = View.VISIBLE
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(
                view: WebView,
                newProgress: Int
            ) {
                progress.progress = newProgress
                progress.visibility =
                    if (newProgress in 1..99) View.VISIBLE
                    else progress.visibility
            }
        }

        webView.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT
            ) {
                webHadFocus = true
                focusCurrentNavigation()
                true
            } else {
                false
            }
        }

        webView.setOnFocusChangeListener { _, focused ->
            if (focused) webHadFocus = true
        }
    }

    private fun isTrustedInternalUrl(uri: Uri): Boolean {
        return uri.scheme.equals("https", true) &&
            (
                uri.host.equals("nexusnet.party", true) ||
                uri.host.equals("www.nexusnet.party", true)
            )
    }

    private fun safeInternalUrl(url: String): String {
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
                "No browser is available.",
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

        if (!isTrustedInternalUrl(Uri.parse(destination))) return

        if (webView.url == destination) {
            webView.evaluateJavascript(
                "window.scrollTo({top:0,behavior:'smooth'});",
                null
            )
            webView.requestFocus()
            return
        }

        webView.loadUrl(destination)
    }

    private fun updateNavigation(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val path = uri?.path.orEmpty()

        currentTab = when {
            path.startsWith("/nodes") -> "Nodes"
            path.startsWith("/search") -> "Search"
            path.startsWith("/signals") -> "Signals"
            path.startsWith("/notifications") -> "Notifications"
            path.startsWith("/minecraft") -> "Minecraft"
            path.startsWith("/settings") -> "Settings"
            path.startsWith("/changelog") -> "Changelog"
            path.startsWith("/u") -> "Profile"
            else -> "Home"
        }

        navItems.forEach { (label, title) ->
            title.setTextColor(
                if (label == currentTab) CYAN else MUTED
            )
        }
    }

    private fun focusCurrentNavigation() {
        val current = sidebar.findViewWithTag<View>(currentTab)

        if (current != null) {
            current.requestFocus()
        } else {
            val first = navItems.keys.firstOrNull()
            if (first != null) {
                focusNavigation(first)
            }
        }
    }

    private fun focusNavigation(label: String) {
        val item = findNavigationView(sidebar, label)
        item?.requestFocus()
    }

    private fun findNavigationView(
        parent: ViewGroup,
        label: String
    ): View? {
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)

            if (child.contentDescription == label) return child

            if (child is ViewGroup) {
                val found = findNavigationView(child, label)
                if (found != null) return found
            }
        }

        return null
    }

    private fun createErrorPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setBackgroundColor(BG)

            addView(
                TextView(this@MainActivity).apply {
                    text = "Can't reach NexusNet"
                    textSize = 28f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(WHITE)
                    gravity = Gravity.CENTER
                }
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = "Check your connection and try again."
                    textSize = 17f
                    setTextColor(MUTED)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, dp(24))
                }
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = "TRY AGAIN"
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(WHITE)
                    setPadding(dp(32), dp(16), dp(32), dp(16))
                    background = focusBackground()
                    isFocusable = true
                    isClickable = true

                    setOnClickListener {
                        visibility = View.GONE
                        webView.loadUrl(safeInternalUrl(lastPageUrl))
                    }
                }
            )
        }
    }

    private fun configureBackButton() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        errorPanel.visibility == View.VISIBLE -> {
                            errorPanel.visibility = View.GONE
                            webView.loadUrl(HOME_URL)
                        }

                        webView.hasFocus() -> {
                            if (webView.canGoBack()) {
                                webView.goBack()
                            } else {
                                focusCurrentNavigation()
                            }
                        }

                        webView.canGoBack() -> {
                            webView.goBack()
                        }

                        else -> {
                            finish()
                        }
                    }
                }
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastPageUrl = webView.url ?: lastPageUrl
        outState.putString(STATE_URL, lastPageUrl)
        webView.saveState(outState)
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.removeAllViews()
            webView.destroy()
        }

        super.onDestroy()
    }
}
