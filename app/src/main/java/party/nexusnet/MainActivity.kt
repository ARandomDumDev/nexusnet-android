package party.nexusnet

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.Window
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.widget.ImageButton

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var morePanel: LinearLayout

    private val blue = Color.rgb(37, 99, 235)
    private val cyan = Color.rgb(34, 211, 238)
    private val background = Color.rgb(11, 15, 25)
    private val surface = Color.rgb(18, 24, 38)
    private val text = Color.WHITE
    private val muted = Color.rgb(148, 163, 184)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = background
        window.navigationBarColor = background

        val root = FrameLayout(this)
        root.setBackgroundColor(background)

        webView = WebView(this)

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
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return false
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
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
            )
        )

        val bottomBar = createBottomBar()

        root.addView(
            bottomBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(76)
            ).apply {
                gravity = Gravity.BOTTOM
            }
        )

        morePanel = createMorePanel()

        root.addView(
            morePanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(390)
            ).apply {
                gravity = Gravity.BOTTOM
            }
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
                        morePanel.visibility == View.VISIBLE -> {
                            morePanel.visibility = View.GONE
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

    private fun createBottomBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedBackground(surface, 0)
            elevation = dp(12).toFloat()
        }

        bar.addView(
            navButton("Nodes", "◈") {
                navigate("/nodes")
            },
            weightParams()
        )

        bar.addView(
            navButton("Profile", "●") {
                navigate("/profile")
            },
            weightParams()
        )

        val create = TextView(this).apply {
            text = "+\nCREATE"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(blue)
            }
            elevation = dp(10).toFloat()

            setOnClickListener {
                navigate("/?app=true&compose=true")
            }
        }

        bar.addView(
            create,
            LinearLayout.LayoutParams(
                dp(72),
                dp(72)
            ).apply {
                setMargins(dp(6), -dp(24), dp(6), 0)
            }
        )

        bar.addView(
            navButton("Signals", "◉") {
                navigate("/signals")
            },
            weightParams()
        )

        bar.addView(
            navButton("Settings", "⚙") {
                navigate("/settings")
            },
            weightParams()
        )

        return bar
    }

    private fun navButton(
        label: String,
        icon: String,
        action: () -> Unit
    ): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), 0)

            setOnClickListener {
                action()
            }
        }

        val iconView = TextView(this).apply {
            text = icon
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(text)
        }

        val labelView = TextView(this).apply {
            this.text = label
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }

        container.addView(
            iconView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(30)
            )
        )

        container.addView(
            labelView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(20)
            )
        )

        return container
    }

    private fun createMorePanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(14), dp(22), dp(18))
            background = roundedBackground(surface, dp(26))
            elevation = dp(20).toFloat()
        }

        val handle = View(this).apply {
            background = roundedBackground(muted, dp(10))
        }

        panel.addView(
            handle,
            LinearLayout.LayoutParams(
                dp(48),
                dp(5)
            ).apply {
                gravity = Gravity.CENTER
                bottomMargin = dp(12)
            }
        )

        val title = TextView(this).apply {
            text = "NexusNet"
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(text)
            setPadding(0, 0, 0, dp(14))
        }

        panel.addView(title)

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        addMoreRow(
            grid,
            "Home",
            "Your NexusNet feed"
        ) {
            navigate("/")
            hideMore()
        }

        addMoreRow(
            grid,
            "Search",
            "Find people, posts and nodes"
        ) {
            navigate("/search")
            hideMore()
        }

        addMoreRow(
            grid,
            "Notifications",
            "See what's happening"
        ) {
            navigate("/notifications")
            hideMore()
        }

        addMoreRow(
            grid,
            "Minecraft",
            "NexusPlay"
        ) {
            navigate("/minecraft")
            hideMore()
        }

        addMoreRow(
            grid,
            "Changelog",
            "What's new"
        ) {
            navigate("/changelog")
            hideMore()
        }

        panel.addView(
            grid,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
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
        action: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedBackground(background, dp(14))

            setOnClickListener {
                action()
            }
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(text)
        }

        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(muted)
        }

        row.addView(titleView)
        row.addView(subtitleView)

        parent.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(55)
            ).apply {
                bottomMargin = dp(6)
            }
        )
    }

    private fun setupSwipeUp(bottomBar: View) {
        var downY = 0f

        bottomBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val distance = downY - event.rawY

                    if (distance > dp(60)) {
                        showMore()
                    }

                    true
                }

                else -> true
            }
        }
    }

    private fun showMore() {
        morePanel.alpha = 0f
        morePanel.translationY = dp(80).toFloat()
        morePanel.visibility = View.VISIBLE

        morePanel.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(220)
            .start()
    }

    private fun hideMore() {
        morePanel.animate()
            .translationY(dp(80).toFloat())
            .alpha(0f)
            .setDuration(180)
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
        return LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            1f
        )
    }

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
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
