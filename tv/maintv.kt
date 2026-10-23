package party.nexusnet

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
import android.os.Build
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
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * NexusNet for Android TV.
 *
 * Remote controls
 *  - Side rail: UP/DOWN to move, CENTER to open, RIGHT to enter the page.
 *  - Page: the D-pad moves a glowing focus ring between buttons, links, inputs
 *    and cards (spatial navigation). CENTER activates. If nothing is left to
 *    focus above/below, the page scrolls. LEFT at the left edge returns to the
 *    side rail. PAGE / CHANNEL UP/DOWN scroll a screen at a time.
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

        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        private const val BLUE = 0xFF2563EB.toInt()
        private const val SKY = 0xFF0EA5E9.toInt()
        private const val CYAN = 0xFF22D3EE.toInt()
        private const val BACKGROUND = 0xFF0B0F19.toInt()
        private const val SURFACE = 0xFF121826.toInt()
        private const val SURFACE_LIGHT = 0xFF1A2335.toInt()
        private const val FOCUS_A = 0xFF2A4BA8.toInt()
        private const val FOCUS_B = 0xFF1B6F88.toInt()
        private const val SELECTED_BG = 0x2222D3EE.toInt()
        private const val TEXT = 0xFFF8FAFC.toInt()
        private const val MUTED = 0xFF94A3B8.toInt()
        private const val BORDER = 0xFF273449.toInt()

        /**
         * Spatial navigation injected into every page. It picks the best
         * focusable element in the pressed direction, draws a glowing ring
         * over it, scrolls it into view, and reports back what happened.
         * (No dollar signs in here: this is a Kotlin raw string.)
         */
        private val NAV_SCRIPT = """
(function () {
  if (window.__nx && window.__nx.v === 2) return;

  var SEL = 'a[href],button,input:not([type=hidden]),select,textarea,summary,' +
    '[role=button],[role=link],[role=tab],[role=menuitem],[role=checkbox],' +
    '[role=switch],[role=radio],[role=option],[role=slider],' +
    '[contenteditable=""],[contenteditable=true],' +
    '[tabindex]:not([tabindex="-1"]),[onclick],video,audio[controls]';
  var GEN = 'div,span,li,img,svg,label,article,section,p,h1,h2,h3,h4';
  var cur = null, active = true, ring = null, fastTimer = 0;

  function init() {
    if (ring && ring.isConnected) return;
    if (!document.getElementById('__nx-style')) {
      var st = document.createElement('style');
      st.id = '__nx-style';
      st.textContent =
        '#__nx-ring{position:fixed;left:0;top:0;z-index:2147483647;' +
        'pointer-events:none;box-sizing:border-box;opacity:0;' +
        'border:3px solid #22D3EE;background:rgba(34,211,238,.10);' +
        'box-shadow:0 0 0 2px rgba(11,15,25,.9),0 0 26px 6px rgba(34,211,238,.45),inset 0 0 16px rgba(34,211,238,.20);' +
        'transition:transform .17s cubic-bezier(.2,.8,.2,1),width .17s cubic-bezier(.2,.8,.2,1),' +
        'height .17s cubic-bezier(.2,.8,.2,1),border-radius .17s,opacity .15s;' +
        'animation:__nxp 1.7s ease-in-out infinite}' +
        '#__nx-ring.fast{transition:opacity .15s}' +
        '#__nx-ring.dim{animation:none;opacity:.35!important}' +
        '@keyframes __nxp{' +
        '0%,100%{box-shadow:0 0 0 2px rgba(11,15,25,.9),0 0 22px 4px rgba(34,211,238,.38),inset 0 0 14px rgba(34,211,238,.16)}' +
        '50%{box-shadow:0 0 0 2px rgba(11,15,25,.9),0 0 36px 10px rgba(59,130,246,.55),inset 0 0 20px rgba(34,211,238,.26)}}';
      (document.head || document.documentElement).appendChild(st);
    }
    ring = document.createElement('div');
    ring.id = '__nx-ring';
    document.documentElement.appendChild(ring);
  }

  function vis(el, r, vw, vh) {
    if (r.width < 6 || r.height < 6) return false;
    if (r.bottom < -vh || r.top > vh * 2 || r.right < -vw * 0.5 || r.left > vw * 1.5) return false;
    var cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.display === 'none' || parseFloat(cs.opacity) < 0.05) return false;
    if (el.disabled || el.getAttribute('aria-disabled') === 'true') return false;
    return true;
  }

  function topmost(el, r, vw, vh) {
    if (r.bottom < 0 || r.top > vh || r.right < 0 || r.left > vw) return true;
    var cx = Math.min(Math.max(r.left + r.width / 2, 1), vw - 1);
    var cy = Math.min(Math.max(r.top + r.height / 2, 1), vh - 1);
    var t = document.elementFromPoint(cx, cy);
    return !!t && (el === t || el.contains(t) || t.contains(el));
  }

  function collect() {
    var vw = innerWidth, vh = innerHeight, out = [], seen = new Set();
    var i, el, r, cs, p;
    var nodes = document.querySelectorAll(SEL);
    for (i = 0; i < nodes.length; i++) {
      el = nodes[i];
      r = el.getBoundingClientRect();
      if (vis(el, r, vw, vh) && topmost(el, r, vw, vh)) {
        out.push({ el: el, r: r });
        seen.add(el);
      }
    }
    var gens = document.querySelectorAll(GEN);
    var n = 0;
    for (i = 0; i < gens.length && n < 4000; i++) {
      el = gens[i];
      n++;
      if (seen.has(el)) continue;
      r = el.getBoundingClientRect();
      if (r.width < 6 || r.height < 6) continue;
      if (r.bottom < -vh || r.top > vh * 2 || r.right < -vw * 0.5 || r.left > vw * 1.5) continue;
      if (r.width > vw * 0.98 && r.height > vh * 0.9) continue;
      cs = getComputedStyle(el);
      if (cs.cursor !== 'pointer') continue;
      p = el.parentElement;
      if (p && getComputedStyle(p).cursor === 'pointer') continue;
      if (vis(el, r, vw, vh) && topmost(el, r, vw, vh)) {
        out.push({ el: el, r: r });
      }
    }
    return out;
  }

  function curRect() {
    if (!cur || !cur.isConnected) return null;
    var r = cur.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) return null;
    return r;
  }

  function entry(list) {
    var vw = innerWidth, vh = innerHeight, best = null, bs = Infinity, any = null, as = Infinity;
    for (var i = 0; i < list.length; i++) {
      var r = list[i].r, s = Math.max(r.top, 0) + Math.max(r.left, 0) * 0.3;
      if (s < as) { as = s; any = list[i]; }
      if (r.bottom > 0 && r.top < vh && r.right > 0 && r.left < vw && s < bs) { bs = s; best = list[i]; }
    }
    return best || any;
  }

  function pick(dir, list, c) {
    var vw = innerWidth, vh = innerHeight;
    var horiz = dir === 'left' || dir === 'right';
    var sign = (dir === 'right' || dir === 'down') ? 1 : -1;
    var ccx = (c.left + c.right) / 2, ccy = (c.top + c.bottom) / 2;
    var best = null, bestScore = Infinity;
    for (var i = 0; i < list.length; i++) {
      var it = list[i];
      if (it.el === cur || it.el.contains(cur)) continue;
      var r = it.r;
      if (r.width * r.height > vw * vh * 0.85) continue;
      var rcx = (r.left + r.right) / 2, rcy = (r.top + r.bottom) / 2;
      var delta = horiz ? (rcx - ccx) : (rcy - ccy);
      if (delta * sign <= 2) continue;
      var edgeOk = horiz
        ? (sign > 0 ? r.right > c.right + 1 : r.left < c.left - 1)
        : (sign > 0 ? r.bottom > c.bottom + 1 : r.top < c.top - 1);
      if (!edgeOk) continue;
      var main = horiz
        ? (sign > 0 ? Math.max(0, r.left - c.right) : Math.max(0, c.left - r.right))
        : (sign > 0 ? Math.max(0, r.top - c.bottom) : Math.max(0, c.top - r.bottom));
      var crossGap = horiz
        ? Math.max(0, Math.max(r.top - c.bottom, c.top - r.bottom))
        : Math.max(0, Math.max(r.left - c.right, c.left - r.right));
      var crossCenter = horiz ? Math.abs(rcy - ccy) : Math.abs(rcx - ccx);
      var cSize = horiz ? c.height : c.width;
      if (crossGap > Math.max(48, cSize * 0.6)) continue;
      var score = main + crossCenter * 2 + crossGap * 4;
      if (score < bestScore) { bestScore = score; best = it; }
    }
    return best;
  }

  function reveal(el) {
    var r = el.getBoundingClientRect(), vh = innerHeight, vw = innerWidth, m = 90;
    if (r.top < m || r.bottom > vh - m || r.left < 0 || r.right > vw) {
      try {
        el.scrollIntoView({ block: (r.height > vh * 0.7 ? 'nearest' : 'center'), inline: 'nearest', behavior: 'smooth' });
      } catch (e) { el.scrollIntoView(); }
    }
  }

  function place() {
    if (!ring) return;
    var r = curRect();
    if (!r) { ring.style.opacity = '0'; return; }
    var vw = innerWidth, vh = innerHeight;
    var pad = Math.min(6, r.left - 2, r.top - 2, vw - r.right - 2, vh - r.bottom - 2);
    pad = Math.max(-3, pad);
    var rad = 0;
    try {
      var br = getComputedStyle(cur).borderTopLeftRadius || '0';
      rad = parseFloat(br) || 0;
      if (br.indexOf('%') >= 0) rad = Math.min(r.width, r.height) * rad / 100;
    } catch (e) { rad = 0; }
    rad = Math.min(Math.max(rad + pad, 8), 40);
    var wasHidden = ring.style.opacity !== '1';
    if (wasHidden) ring.classList.add('fast');
    ring.style.width = (r.width + pad * 2) + 'px';
    ring.style.height = (r.height + pad * 2) + 'px';
    ring.style.transform = 'translate(' + (r.left - pad) + 'px,' + (r.top - pad) + 'px)';
    ring.style.borderRadius = rad + 'px';
    ring.style.opacity = '1';
    if (wasHidden) {
      void ring.offsetWidth;
      setTimeout(function () { ring.classList.remove('fast'); }, 40);
    }
  }

  function select(el, scroll) {
    cur = el;
    if (scroll) reveal(el);
    place();
  }

  function scroller(el) {
    var p = el;
    while (p && p !== document.body && p !== document.documentElement) {
      var cs = getComputedStyle(p);
      if ((cs.overflowY === 'auto' || cs.overflowY === 'scroll') && p.scrollHeight > p.clientHeight + 2) return p;
      p = p.parentElement;
    }
    return document.scrollingElement || document.documentElement;
  }

  function isDoc(s) { return s === document.scrollingElement || s === document.documentElement; }

  function canScroll(s, sign) {
    return sign > 0 ? (s.scrollTop + s.clientHeight < s.scrollHeight - 2) : (s.scrollTop > 1);
  }

  function doScroll(sign, frac) {
    var s = scroller(cur || document.body);
    if (!canScroll(s, sign)) {
      s = document.scrollingElement || document.documentElement;
      if (!canScroll(s, sign)) return false;
    }
    var amt = (isDoc(s) ? innerHeight : s.clientHeight) * frac * sign;
    if (isDoc(s)) window.scrollBy({ top: amt, behavior: 'smooth' });
    else s.scrollBy({ top: amt, behavior: 'smooth' });
    return true;
  }

  function onScroll() {
    if (!ring) return;
    ring.classList.add('fast');
    place();
    clearTimeout(fastTimer);
    fastTimer = setTimeout(function () { ring.classList.remove('fast'); }, 140);
  }

  function move(dir) {
    init();
    active = true;
    ring.classList.remove('dim');
    var list = collect();
    var c = curRect();
    if (!c) {
      var first = entry(list);
      if (first) { select(first.el, true); return { t: 'move' }; }
      if (dir === 'down' || dir === 'up') {
        return { t: doScroll(dir === 'down' ? 1 : -1, 0.6) ? 'scroll' : 'none' };
      }
      return dir === 'left' ? { t: 'edge', d: 'left' } : { t: 'none' };
    }
    var best = pick(dir, list, c);
    if (best) { select(best.el, true); return { t: 'move' }; }
    if (dir === 'down' || dir === 'up') {
      if (doScroll(dir === 'down' ? 1 : -1, 0.6)) return { t: 'scroll' };
    }
    return { t: 'edge', d: dir };
  }

  function activate() {
    var r = curRect();
    if (!r) return { t: 'none' };
    var el = cur, vw = innerWidth, vh = innerHeight;
    var l = Math.max(r.left, 0), t = Math.max(r.top, 0);
    var rr = Math.min(r.right, vw), b = Math.min(r.bottom, vh);
    if (rr - l < 2 || b - t < 2) { reveal(el); return { t: 'none' }; }
    var pts = [
      [(l + rr) / 2, (t + b) / 2],
      [l + (rr - l) * 0.25, (t + b) / 2],
      [l + (rr - l) * 0.75, (t + b) / 2],
      [(l + rr) / 2, t + (b - t) * 0.25],
      [(l + rr) / 2, t + (b - t) * 0.75]
    ];
    for (var i = 0; i < pts.length; i++) {
      var h = document.elementFromPoint(pts[i][0], pts[i][1]);
      if (h && (el === h || el.contains(h))) {
        var d = window.devicePixelRatio || 1;
        return { t: 'tap', x: pts[i][0] * d, y: pts[i][1] * d };
      }
    }
    try { el.click(); } catch (e) {}
    return { t: 'click' };
  }

  function enter() {
    init();
    active = true;
    ring.classList.remove('dim');
    if (curRect()) { place(); return { t: 'ok' }; }
    var f = entry(collect());
    if (f) { select(f.el, false); return { t: 'ok' }; }
    return { t: 'none' };
  }

  function setActive(a) {
    active = !!a;
    if (!ring) return;
    if (active) ring.classList.remove('dim'); else ring.classList.add('dim');
    place();
  }

  function pageScroll(dir) {
    return { t: doScroll(dir === 'down' ? 1 : -1, 0.85) ? 'scroll' : 'none' };
  }

  addEventListener('scroll', onScroll, true);
  addEventListener('resize', function () { place(); });
  setInterval(function () { if (cur) place(); }, 500);

  window.__nx = { v: 2, move: move, activate: activate, enter: enter, setActive: setActive, pageScroll: pageScroll };
})();
""".trimIndent()
    }

    private enum class Overlay { NONE, MORE, ZOOM }

    private lateinit var root: FrameLayout
    private lateinit var mainRow: LinearLayout
    private lateinit var rail: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var webHost: WebHost
    private lateinit var webView: WebView
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

    // Remote input state
    private val uiHandler = Handler(Looper.getMainLooper())
    private var centerHeld = false
    private var navBusy = false
    private var lastNavMs = 0L

    private val dp: (Int) -> Int = {
        (it * resources.displayMetrics.density + 0.5f).toInt()
    }

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
        centerHeld = false
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) centerHeld = false
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

                // Install spatial navigation, then show the focus ring if
                // the page (not the side rail) currently owns the remote.
                view.evaluateJavascript(NAV_SCRIPT) {
                    if (
                        overlay == Overlay.NONE &&
                        customView == null &&
                        !railHasFocus() &&
                        errorPanel.visibility != View.VISIBLE
                    ) {
                        view.evaluateJavascript(
                            "window.__nx ? window.__nx.enter() : null",
                            null
                        )
                    }
                }
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

        centerHeld = false

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
            jsSetActive(!railHasFocus())
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
            setPadding(dp(8), dp(24), dp(8), dp(16))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(12), 0)
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
                bottomMargin = dp(14)
            }
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isFocusable = false
            clipToPadding = false
        }

        // Generous padding so the focus glow and slide never clip.
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(12), dp(12), dp(14), dp(14))
        }

        fun add(item: RailItem) {
            railItems.add(item)
            column.addView(
                item.view,
                LinearLayout.LayoutParams(MATCH, dp(54)).apply {
                    bottomMargin = dp(8)
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

        private val accent = View(this@MainActivity).apply {
            background = rounded(CYAN, 2)
            alpha = 0f
        }

        private val iconHolder = FrameLayout(this@MainActivity)

        private val iconView = IconView(this@MainActivity, icon, MUTED)

        private val labelView = TextView(this@MainActivity).apply {
            text = label
            textSize = 16f
            maxLines = 1
            typeface = customFont
            setTextColor(MUTED)
        }

        private val row = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(10), 0)
        }

        val view: FrameLayout = FrameLayout(this@MainActivity).apply {
            isClickable = true
            isFocusable = true
            contentDescription = label

            setOnClickListener { action() }

            setOnFocusChangeListener { _, hasFocus ->
                focused = hasFocus
                refresh()
            }
        }

        init {
            iconHolder.addView(
                iconView,
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
            )

            row.addView(
                iconHolder,
                LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                    marginEnd = dp(12)
                }
            )

            row.addView(labelView)

            view.addView(row, FrameLayout.LayoutParams(MATCH, MATCH))

            view.addView(
                accent,
                FrameLayout.LayoutParams(
                    dp(4),
                    dp(24),
                    Gravity.START or Gravity.CENTER_VERTICAL
                ).apply { leftMargin = dp(4) }
            )

            refresh()
        }

        private fun refresh() {
            val active = selected || focused

            view.background = when {
                prominent -> gradientBg(
                    BLUE, SKY, 14,
                    if (focused) 2 else 0,
                    Color.WHITE
                )
                focused -> gradientBg(FOCUS_A, FOCUS_B, 14, 2, CYAN)
                selected -> rounded(SELECTED_BG, 14)
                else -> null
            }

            setGlow(view, focused)

            view.animate()
                .translationX(if (focused) dp(6).toFloat() else 0f)
                .setDuration(140)
                .start()

            accent.animate()
                .alpha(if (active && !prominent) 1f else 0f)
                .setDuration(140)
                .start()

            iconHolder.background =
                if (focused && !prominent) {
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0x3322D3EE)
                    }
                } else {
                    null
                }

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

        centerHeld = false

        if (target.view.requestFocusFromTouch()) {
            jsSetActive(false)
        }
    }

    private fun focusContent() {
        if (errorPanel.visibility == View.VISIBLE) {
            retryButton.requestFocusFromTouch()
            return
        }

        webView.requestFocusFromTouch()

        if (!railHasFocus()) {
            webView.evaluateJavascript(
                "window.__nx ? window.__nx.enter() : null",
                null
            )
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

        centerHeld = false

        if (previous == Overlay.NONE) {
            focusBeforeOverlay = currentFocus ?: webView
            mainRow.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            jsSetActive(false)
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

            if (errorPanel.visibility != View.VISIBLE) {
                jsSetActive(!railHasFocus())
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

        styleFocusable(close, SURFACE_LIGHT, 14)

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
                bottomMargin = dp(10)
            }
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isFocusable = false
            clipToPadding = false
        }

        val items = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(12), dp(12), dp(18), dp(16))
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

        styleFocusable(row, SURFACE_LIGHT, 16, 6)

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
                bottomMargin = dp(10)
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

        styleFocusable(button, if (primary) BLUE else SURFACE_LIGHT, 14)

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

        styleFocusable(retryButton, BLUE, 16)

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
                if (down) pageScroll("up")
                return true
            }

            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (down) pageScroll("down")
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

    /** Returns true when the key was consumed by page navigation. */
    private fun handleContentKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN

        when (code) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (down) {
                    val dir = when (code) {
                        KeyEvent.KEYCODE_DPAD_UP -> "up"
                        KeyEvent.KEYCODE_DPAD_DOWN -> "down"
                        KeyEvent.KEYCODE_DPAD_LEFT -> "left"
                        else -> "right"
                    }

                    // Only a fresh press may jump to the rail, so holding
                    // LEFT never throws you out of the page by accident.
                    navigateFocus(dir, event.repeatCount == 0)
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
                    activateFocus()
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
    // Spatial navigation bridge
    // ------------------------------------------------------------------

    private fun jsSetActive(active: Boolean) {
        if (!::webView.isInitialized) return

        webView.evaluateJavascript(
            "window.__nx ? window.__nx.setActive($active) : null",
            null
        )
    }

    private fun parseResult(raw: String?): JSONObject? {
        if (raw == null || raw == "null") return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun navigateFocus(dir: String, allowEdge: Boolean) {
        val now = SystemClock.uptimeMillis()

        // Skip repeats that arrive while the last move is still in flight.
        if (navBusy && now - lastNavMs < 400L) return
        if (now - lastNavMs < 70L) return

        navBusy = true
        lastNavMs = now

        webView.evaluateJavascript(
            "window.__nx ? window.__nx.move('$dir') : null"
        ) { raw ->
            navBusy = false

            val result = parseResult(raw)

            // No result means the page has no navigation yet (still
            // loading), so LEFT must still be able to reach the rail.
            val atLeftEdge = if (result == null) {
                dir == "left"
            } else {
                result.optString("t") == "edge" &&
                    result.optString("d") == "left"
            }

            if (atLeftEdge && allowEdge) {
                focusRail()
            }
        }
    }

    private fun pageScroll(dir: String) {
        webView.evaluateJavascript(
            "window.__nx ? window.__nx.pageScroll('$dir') : null",
            null
        )
    }

    private fun activateFocus() {
        webView.evaluateJavascript(
            "window.__nx ? window.__nx.activate() : null"
        ) { raw ->
            val result = parseResult(raw)

            if (result != null) {
                when (result.optString("t")) {
                    "tap" -> tapAt(
                        result.optDouble("x").toFloat(),
                        result.optDouble("y").toFloat()
                    )

                    "none" -> webView.evaluateJavascript(
                        "window.__nx ? window.__nx.enter() : null",
                        null
                    )
                }
            }
        }
    }

    /**
     * Real touch tap at WebView-local pixels. A genuine tap counts as a user
     * gesture, so it opens the keyboard on inputs and lets videos play.
     */
    private fun tapAt(x: Float, y: Float) {
        if (!webView.hasFocus()) {
            webView.requestFocusFromTouch()
        }

        val downTime = SystemClock.uptimeMillis()

        dispatchTouch(MotionEvent.ACTION_DOWN, x, y, downTime)

        uiHandler.postDelayed({
            if (::webView.isInitialized) {
                dispatchTouch(MotionEvent.ACTION_UP, x, y, downTime)
            }
        }, 40L)
    }

    private fun dispatchTouch(
        action: Int,
        x: Float,
        y: Float,
        downTime: Long
    ) {
        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        )

        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = 1f
                size = 1f
            }
        )

        val ev = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
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
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )

        webView.dispatchTouchEvent(ev)
        ev.recycle()
    }

    // ------------------------------------------------------------------
    // Styling helpers
    // ------------------------------------------------------------------

    private fun rounded(
        color: Int,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun gradientBg(
        from: Int,
        to: Int,
        radiusDp: Int,
        strokeDp: Int = 0,
        strokeColor: Int = CYAN
    ): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(from, to)
        ).apply {
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }
    }

    /** Soft cyan glow under a focused view (tinted on Android 9+). */
    private fun setGlow(v: View, on: Boolean) {
        v.elevation = if (on) dp(12).toFloat() else 0f

        if (on && Build.VERSION.SDK_INT >= 28) {
            v.outlineSpotShadowColor = CYAN
            v.outlineAmbientShadowColor = CYAN
        }
    }

    /**
     * Focus look for rows and buttons: gradient fill, cyan outline, glow and
     * a small slide. Nothing is scaled, so it can never clip at an edge.
     */
    private fun styleFocusable(
        v: View,
        normal: Int,
        radiusDp: Int,
        slideDp: Int = 0
    ) {
        v.isFocusable = true
        v.isClickable = true
        v.background = rounded(normal, radiusDp)

        v.setOnFocusChangeListener { view, hasFocus ->
            view.background = if (hasFocus) {
                gradientBg(FOCUS_A, FOCUS_B, radiusDp, 2, CYAN)
            } else {
                rounded(normal, radiusDp)
            }

            setGlow(view, hasFocus)

            view.animate()
                .translationX(if (hasFocus) dp(slideDp).toFloat() else 0f)
                .setDuration(140)
                .start()
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
