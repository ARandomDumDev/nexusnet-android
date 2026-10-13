```kotlin
package party.nexusnet

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs
import kotlin.math.max

class MainActivity : ComponentActivity() {

    companion object {
        private const val HOME_URL =
            "https://nexusnet.party/?app=true"

        private const val BASE_URL =
            "https://nexusnet.party"

        private const val STATE_URL = "nexus_url"

        private const val BLUE = 0xFF2563EB.toInt()
        private const val CYAN = 0xFF22D3EE.toInt()
        private const val BACKGROUND = 0xFF0B0F19.toInt()
        private const val SURFACE = 0xFF121826.toInt()
        private const val SURFACE_LIGHT = 0xFF1A2335.toInt()
        private const val TEXT = 0xFFF8FAFC.toInt()
        private const val MUTED = 0xFF94A3B8.toInt()
        private const val BORDER = 0xFF273449.toInt()
        private const val ERROR = 0xFFF87171.toInt()
    }

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var bottomBar: LinearLayout
    private lateinit var morePanel: LinearLayout
    private lateinit var scrim: View
    private lateinit var progress: ProgressBar
    private lateinit var errorPanel: LinearLayout

    private var customFont: Typeface = Typeface.SANS_SERIF
    private var customFontBold: Typeface =
        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    private var isMoreOpen = false
    private var isLoadingPage = false
    private var lastPageUrl: String = HOME_URL
    private var currentTab = "Home"

    private val dp: (Int) -> Int = {
        (it * resources.displayMetrics.density + 0.5f).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        WindowCompat.setDecorFitsSystemWindows(window, true)

        createLayout()
        configureWebView()
        configureBackButton()

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
    }

    private fun createLayout() {
        root = FrameLayout(this).apply {
            setBackgroundColor(BACKGROUND)
            clipChildren = false
            clipToPadding = false
        }

        setContentView(root)

        webView = WebView(this).apply {
            setBackgroundColor(BACKGROUND)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            isLongClickable = false
            setOnLongClickListener { true }
        }

        val webParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        root.addView(webView, webParams)

        progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            progressTintList =
                android.content.res.ColorStateList.valueOf(CYAN)
            progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(SURFACE)
            max = 100
            progress = 0
            visibility = View.GONE
        }

        root.addView(
            progress,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3),
                Gravity.TOP
            )
        )

        errorPanel = createErrorPanel().apply {
            visibility = View.GONE
        }

        root.addView(
            errorPanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        scrim = View(this).apply {
            setBackgroundColor(0x99000000.toInt())
            alpha = 0f
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            contentDescription = "Close navigation menu"
            setOnClickListener { hideMore() }
        }

        root.addView(
            scrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        bottomBar = createBottomBar()

        root.addView(
            bottomBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(76),
                Gravity.BOTTOM
            )
        )

        morePanel = createMorePanel().apply {
            visibility = View.GONE
            alpha = 0f
            translationY = dp(35).toFloat()
        }

        root.addView(
            morePanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(410),
                Gravity.BOTTOM
            )
        )

        // Keep system navigation and status areas readable.
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            bottomBar.setPadding(
                dp(8),
                dp(6),
                dp(8),
                dp(6)
            )

            bottomBar.translationY = -systemBars.bottom.toFloat()

            webView.setPadding(0, 0, 0, 0)
            webView.clipToPadding = false

            insets
        }

        ViewCompat.requestApplyInsets(root)

        setupSwipeGesture()
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
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            userAgentString =
                "$userAgentString NexusNetAndroid/5.0"
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
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)

                isLoadingPage = true
                lastPageUrl = url
                progress.progress = 5
                progress.visibility = View.VISIBLE
                errorPanel.visibility = View.GONE

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
                        progress.visibility = View.GONE
                        progress.alpha = 1f
                        progress.progress = 0
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

    private fun createBottomBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = rounded(SURFACE, 0)
            elevation = dp(16).toFloat()
            clipChildren = false
            clipToPadding = false
        }

        bar.addView(
            navButton("Nodes", NavIcon.NODES) {
                navigate("/nodes")
            },
            weightedParams()
        )

        bar.addView(
            navButton("Profile", NavIcon.PROFILE) {
                navigate("/u")
            },
            weightedParams()
        )

        val create = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(BLUE)
                setStroke(dp(4), SURFACE)
            }

            elevation = dp(8).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = "Create a post"

            setOnClickListener {
                performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY
                )
                navigate("/?app=true&compose=true")
            }
        }

        create.addView(
            IconView(this, NavIcon.CREATE, Color.WHITE),
            FrameLayout.LayoutParams(
                dp(30),
                dp(30),
                Gravity.CENTER
            )
        )

        bar.addView(
            create,
            LinearLayout.LayoutParams(
                dp(58),
                dp(58)
            ).apply {
                setMargins(dp(5), -dp(20), dp(5), 0)
            }
        )

        bar.addView(
            navButton("Signals", NavIcon.SIGNALS) {
                navigate("/signals")
            },
            weightedParams()
        )

        bar.addView(
            navButton("More", NavIcon.MORE) {
                showMore()
            },
            weightedParams()
        )

        return bar
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
        RETRY
    }

    private fun navButton(
        label: String,
        icon: NavIcon,
        action: () -> Unit
    ): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(3), dp(2), dp(3))
            isClickable = true
            isFocusable = true
            contentDescription = label

            setOnClickListener {
                performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY
                )
                action()
            }
        }

        val selected = currentTab == label

        container.addView(
            IconView(
                this,
                icon,
                if (selected) CYAN else MUTED
            ),
            LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                bottomMargin = dp(3)
            }
        )

        val text = TextView(this).apply {
            this.text = label
            textSize = 10f
            gravity = Gravity.CENTER
            typeface = if (selected) customFontBold else customFont
            setTextColor(if (selected) TEXT else MUTED)
            maxLines = 1
        }

        container.addView(text)

        return container
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

        // Rebuild the lightweight navigation bar to refresh selection.
        if (::bottomBar.isInitialized) {
            val index = root.indexOfChild(bottomBar)

            if (index >= 0) {
                root.removeView(bottomBar)
                bottomBar = createBottomBar()

                root.addView(
                    bottomBar,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(76),
                        Gravity.BOTTOM
                    )
                )

                ViewCompat.requestApplyInsets(root)
            }
        }
    }

    private fun createMorePanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(24))
            background = GradientDrawable().apply {
                setColor(SURFACE)
                cornerRadii = floatArrayOf(
                    dp(28).toFloat(), dp(28).toFloat(),
                    dp(28).toFloat(), dp(28).toFloat(),
                    0f, 0f, 0f, 0f
                )
                setStroke(dp(1), BORDER)
            }
            elevation = dp(24).toFloat()
            isClickable = true
            isFocusable = true
        }

        val handle = View(this).apply {
            background = rounded(MUTED, dp(8))
        }

        panel.addView(
            handle,
            LinearLayout.LayoutParams(dp(42), dp(4)).apply {
                gravity = Gravity.CENTER
                bottomMargin = dp(16)
            }
        )

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        titleGroup.addView(
            TextView(this).apply {
                text = "NexusNet"
                textSize = 22f
                typeface = customFontBold
                setTextColor(TEXT)
            }
        )

        titleGroup.addView(
            TextView(this).apply {
                text = "Your corner of the internet."
                textSize = 12f
                typeface = customFont
                setTextColor(MUTED)
            }
        )

        heading.addView(titleGroup)

        val close = FrameLayout(this).apply {
            background = rounded(SURFACE_LIGHT, dp(14))
            isClickable = true
            isFocusable = true
            contentDescription = "Close menu"
            setOnClickListener { hideMore() }
        }

        close.addView(
            IconView(this, NavIcon.CLOSE, TEXT),
            FrameLayout.LayoutParams(
                dp(20),
                dp(20),
                Gravity.CENTER
            )
        )

        heading.addView(
            close,
            LinearLayout.LayoutParams(dp(42), dp(42))
        )

        panel.addView(
            heading,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(18)
            }
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val items = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        addMoreRow(
            items, "Home", "Your NexusNet feed", NavIcon.HOME
        ) {
            navigate("/")
        }

        addMoreRow(
            items, "Search", "People, posts and nodes", NavIcon.SEARCH
        ) {
            navigate("/search")
        }

        addMoreRow(
            items, "Notifications", "See what's happening",
            NavIcon.NOTIFICATIONS
        ) {
            navigate("/notifications")
        }

        addMoreRow(
            items, "Minecraft", "Your NexusPlay corner",
            NavIcon.MINECRAFT
        ) {
            navigate("/minecraft")
        }

        addMoreRow(
            items, "Settings", "Make NexusNet yours",
            NavIcon.SETTINGS
        ) {
            navigate("/settings")
        }

        addMoreRow(
            items, "Changelog", "See what's new",
            NavIcon.CHANGELOG
        ) {
            navigate("/changelog")
        }

        scroll.addView(items)

        panel.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        return panel
    }

    private fun addMoreRow(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        icon: NavIcon,
        action: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(SURFACE_LIGHT, dp(16))
            isClickable = true
            isFocusable = true
            contentDescription = "$title. $subtitle"

            setOnClickListener {
                performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY
                )
                hideMore()
                action()
            }
        }

        val iconHolder = FrameLayout(this).apply {
            background = rounded(BLUE, dp(12))
        }

        iconHolder.addView(
            IconView(this, icon, TEXT),
            FrameLayout.LayoutParams(
                dp(24),
                dp(24),
                Gravity.CENTER
            )
        )

        row.addView(
            iconHolder,
            LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginEnd = dp(12)
            }
        )

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        labels.addView(
            TextView(this).apply {
                text = title
                textSize = 14f
                typeface = customFontBold
                setTextColor(TEXT)
            }
        )

        labels.addView(
            TextView(this).apply {
                text = subtitle
                textSize = 11f
                typeface = customFont
                setTextColor(MUTED)
                maxLines = 1
            }
        )

        row.addView(labels)

        parent.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                bottomMargin = dp(7)
            }
        )
    }

    private fun showMore() {
        if (isMoreOpen) return

        isMoreOpen = true

        scrim.visibility = View.VISIBLE
        morePanel.visibility = View.VISIBLE

        scrim.animate()
            .alpha(1f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()

        morePanel.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun hideMore() {
        if (!isMoreOpen) return

        isMoreOpen = false

        scrim.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                scrim.visibility = View.GONE
            }
            .start()

        morePanel.animate()
            .alpha(0f)
            .translationY(dp(35).toFloat())
            .setDuration(200)
            .withEndAction {
                morePanel.visibility = View.GONE
            }
            .start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeGesture() {
        var startX = 0f
        var startY = 0f

        bottomBar.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val diffX = event.x - startX
                    val diffY = event.y - startY

                    if (
                        abs(diffY) > dp(45) &&
                        abs(diffY) > abs(diffX) * 1.2f
                    ) {
                        if (diffY < 0) {
                            showMore()
                        } else if (isMoreOpen) {
                            hideMore()
                        }
                    }

                    true
                }

                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }

        var panelStartY = 0f

        morePanel.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    panelStartY = event.rawY
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val diff = event.rawY - panelStartY

                    if (diff > dp(75)) {
                        hideMore()
                    }

                    true
                }

                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
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

    private fun createErrorPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setBackgroundColor(BACKGROUND)

            addView(
                IconView(this@MainActivity, NavIcon.HOME, CYAN),
                LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                    bottomMargin = dp(20)
                }
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = "Can't reach NexusNet"
                    textSize = 21f
                    typeface = customFontBold
                    setTextColor(TEXT)
                    gravity = Gravity.CENTER
                }
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = "Check your connection and try again."
                    textSize = 13f
                    typeface = customFont
                    setTextColor(MUTED)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(10), 0, dp(24))
                }
            )

            val retry = TextView(this@MainActivity).apply {
                text = "Try again"
                textSize = 14f
                typeface = customFontBold
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = rounded(BLUE, dp(14))
                isClickable = true
                isFocusable = true
                setPadding(dp(28), dp(14), dp(28), dp(14))

                setOnClickListener {
                    visibility = View.GONE
                    webView.loadUrl(
                        safeInternalRestoreUrl(lastPageUrl)
                    )
                }
            }

            addView(retry)
        }
    }

    private fun configureBackButton() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        isMoreOpen -> hideMore()

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

    private fun weightedParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        )
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

    /**
     * Lightweight vector-style icons drawn directly with Canvas.
     * No drawable downloads, external icon libraries, or emoji icons.
     */
    private class IconView(
        context: android.content.Context,
        private val icon: NavIcon,
        private val tint: Int
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            style = Paint.Style.STROKE
            strokeWidth = 2f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val path = Path()
        private val density = resources.displayMetrics.density

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
            }

            canvas.restoreToCount(save)
        }
    }
}
```
