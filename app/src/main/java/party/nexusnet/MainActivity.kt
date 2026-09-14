package party.nexusnet

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.res.ResourcesCompat
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var morePanel: LinearLayout
    private lateinit var gestureDetector: GestureDetector
    
    private var customFont: Typeface? = null
    private var customFontBold: Typeface? = null

    private val blue = Color.rgb(37, 99, 235)
    private val cyan = Color.rgb(34, 211, 238)
    private val background = Color.rgb(11, 15, 25)
    private val surface = Color.rgb(18, 24, 38)
    private val textColor = Color.WHITE
    private val muted = Color.rgb(148, 163, 184)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            customFont = ResourcesCompat.getFont(this, R.font.montserrat)
            customFontBold = Typeface.create(customFont!!, Typeface.BOLD)
        } catch (e: Exception) {
            customFont = Typeface.SANS_SERIF
            customFontBold = Typeface.defaultFromStyle(Typeface.BOLD)
        }

        window.statusBarColor = background
        window.navigationBarColor = background

        // Disable clipping on root layout so create button can extend outward
        val root = FrameLayout(this).apply {
            setBackgroundColor(background)
            clipChildren = false
            clipToPadding = false
        }

        webView = WebView(this).apply {
            // Disable long click context menus and native text selection
            isLongClickable = false
            setOnLongClickListener { true }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString = "$userAgentString NexusNetAndroid/4.0"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host?.contains("nexusnet.party") == true) {
                    return false
                }
                startActivity(Intent(Intent.ACTION_VIEW, url))
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Inject CSS to disable web text selection
                view.evaluateJavascript(
                    "document.documentElement.style.webkitUserSelect='none'; document.documentElement.style.userSelect='none';",
                    null
                )
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    view.loadUrl("https://nexusnet.party?app=true")
                }
            }
        }

        webView.webChromeClient = WebChromeClient()

        root.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                bottomMargin = dp(76)
            }
        )

        val bottomBar = createBottomBar()
        root.addView(
            bottomBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(76)
            ).apply { gravity = Gravity.BOTTOM }
        )

        morePanel = createMorePanel()
        root.addView(
            morePanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(390)
            ).apply { gravity = Gravity.BOTTOM }
        )

        morePanel.visibility = View.GONE
        setupSwipeUp(bottomBar)
        setContentView(root)

        webView.loadUrl("https://nexusnet.party?app=true")

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        morePanel.visibility == View.VISIBLE -> hideMore()
                        webView.canGoBack() -> webView.goBack()
                        else -> finish()
                    }
                }
            }
        )
    }

    private fun createBottomBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedBackground(surface, 0)
            elevation = dp(12).toFloat()
            isClickable = true
            // Allow child views to draw outside bounds
            clipChildren = false
            clipToPadding = false
        }

        bar.addView(navButton("Nodes", android.R.drawable.ic_menu_compass) { navigate("/nodes") }, weightParams())
        bar.addView(navButton("Profile", android.R.drawable.ic_menu_myplaces) { navigate("/profile") }, weightParams())

        val create = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // Circle with 4dp stroke outline matching navigation bar color (surface)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(blue)
                setStroke(dp(4), surface)
            }
            elevation = dp(10).toFloat()
            setOnClickListener { navigate("/?app=true&compose=true") }
        }

        val createIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        }
        create.addView(createIcon)

        bar.addView(
            create,
            LinearLayout.LayoutParams(dp(64), dp(64)).apply {
                setMargins(dp(8), -dp(28), dp(8), 0)
            }
        )

        bar.addView(navButton("Signals", android.R.drawable.ic_menu_share) { navigate("/signals") }, weightParams())
        bar.addView(navButton("Settings", android.R.drawable.ic_menu_preferences) { navigate("/settings") }, weightParams())

        return bar
    }

    private fun navButton(label: String, iconRes: Int, action: () -> Unit): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))

            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)

            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

        val iconView = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(textColor)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                bottomMargin = dp(4)
            }
        }

        val labelView = TextView(this).apply {
            this.text = label
            textSize = 10f
            gravity = Gravity.CENTER
            typeface = customFont
            setTextColor(muted)
        }

        container.addView(iconView)
        container.addView(labelView)

        return container
    }

    private fun createMorePanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(14), dp(22), dp(18))
            background = roundedBackground(surface, dp(26))
            elevation = dp(20).toFloat()
            isClickable = true
        }

        val handle = View(this).apply {
            background = roundedBackground(muted, dp(10))
        }

        panel.addView(
            handle,
            LinearLayout.LayoutParams(dp(48), dp(5)).apply {
                gravity = Gravity.CENTER
                bottomMargin = dp(12)
            }
        )

        val title = TextView(this).apply {
            text = "NexusNet"
            textSize = 22f
            typeface = customFontBold
            setTextColor(textColor)
            setPadding(0, 0, 0, dp(14))
        }

        panel.addView(title)

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        addMoreRow(grid, "Home", "Your NexusNet feed") { navigate("/"); hideMore() }
        addMoreRow(grid, "Search", "Find people, posts and nodes") { navigate("/search"); hideMore() }
        addMoreRow(grid, "Notifications", "See what's happening") { navigate("/notifications"); hideMore() }
        addMoreRow(grid, "Minecraft", "NexusPlay") { navigate("/minecraft"); hideMore() }
        addMoreRow(grid, "Changelog", "What's new") { navigate("/changelog"); hideMore() }

        panel.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return panel
    }

    private fun addMoreRow(parent: LinearLayout, title: String, subtitle: String, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)

            isClickable = true
            setOnClickListener { action() }
        }

        val titleView = TextView(this).apply {
            this.text = title
            textSize = 16f
            typeface = customFontBold
            setTextColor(textColor)
        }

        val subtitleView = TextView(this).apply {
            this.text = subtitle
            textSize = 12f
            typeface = customFont
            setTextColor(muted)
        }

        row.addView(titleView)
        row.addView(subtitleView)

        parent.addView(
            row,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(55)).apply {
                bottomMargin = dp(6)
            }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeUp(bottomBar: View) {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 60
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY < 0) {
                        showMore()
                        return true
                    }
                }
                return false
            }
        })

        bottomBar.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun showMore() {
        if (morePanel.visibility == View.VISIBLE) return

        morePanel.alpha = 0f
        morePanel.translationY = dp(40).toFloat()
        morePanel.visibility = View.VISIBLE

        morePanel.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(250)
            .start()
    }

    private fun hideMore() {
        morePanel.animate()
            .translationY(dp(40).toFloat())
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                morePanel.visibility = View.GONE
            }
            .start()
    }

    private fun navigate(path: String) {
        val url = when {
            path == "/" -> "https://nexusnet.party?app=true"
            path.startsWith("/?") -> "https://nexusnet.party$path"
            else -> "https://nexusnet.party$path?app=true"
        }
        webView.loadUrl(url)
    }

    private fun weightParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
