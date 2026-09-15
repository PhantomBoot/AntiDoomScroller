package com.antidoomscroller.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.antidoomscroller.core.detect.ScreenRect

/**
 * Everything the guard draws on top of another app.
 *
 * Two shapes:
 *  - a *patch*: an opaque box sized to the video itself, so the app around it - the post header
 *    above a reels unit, the navigation bar below a full-bleed player - stays visible and usable.
 *    This is what covering a feed uses.
 *  - a *panel*: a full-screen card carrying the user's own message, used when the guard is about
 *    to leave the screen anyway, and for scroll reminders.
 *
 * Both use an accessibility overlay window, which needs no "draw over other apps" permission and
 * cannot be dismissed by the app underneath.
 */
class BlockOverlay(private val service: AccessibilityService) {

    private enum class Mode { NONE, PANEL, PATCH }

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var mode: Mode = Mode.NONE
    private var shownAtMs: Long = 0

    val isShowing: Boolean get() = view != null

    /** Full-screen card with the message and a way out. */
    fun showPanel(
        title: String,
        body: String,
        actionLabel: String,
        minimumVisibleMs: Long,
        onAction: () -> Unit,
    ) {
        if (mode == Mode.PANEL && view != null) {
            updatePanelText(title, body)
            return
        }
        replace(
            newMode = Mode.PANEL,
            newView = buildPanel(title, body, actionLabel) {
                if (System.currentTimeMillis() - shownAtMs >= minimumVisibleMs) {
                    dismiss()
                    onAction()
                }
            },
            newParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.CENTER },
        )
    }

    /**
     * Opaque box over exactly [bounds] - the video and nothing else. Taps land on the box instead
     * of the video; everything outside it, including the app's own navigation, keeps working.
     */
    fun showPatch(bounds: ScreenRect, title: String, body: String) {
        val layout = WindowManager.LayoutParams(
            bounds.width.coerceAtLeast(1),
            bounds.height.coerceAtLeast(1),
            bounds.left,
            bounds.top,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE,
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val existing = view
        if (mode == Mode.PATCH && existing != null) {
            // The same box moving with the feed: reposition instead of tearing it down and back up.
            params = layout
            updatePatchText(existing, bounds, title, body)
            runCatching { windowManager?.updateViewLayout(existing, layout) }
            return
        }
        replace(Mode.PATCH, buildPatch(bounds, title, body), layout)
    }

    /** Shows a panel briefly and takes it away again - used for scroll reminders. */
    fun flash(title: String, body: String, actionLabel: String, timeoutMs: Long) {
        showPanel(title, body, actionLabel, minimumVisibleMs = 0) {}
        handler.postDelayed({ dismiss() }, timeoutMs)
    }

    fun dismiss() {
        val current = view ?: return
        view = null
        params = null
        mode = Mode.NONE
        handler.removeCallbacksAndMessages(null)
        runCatching { windowManager?.removeView(current) }
    }

    private fun replace(newMode: Mode, newView: View, newParams: WindowManager.LayoutParams) {
        dismiss()
        runCatching {
            windowManager?.addView(newView, newParams)
            view = newView
            params = newParams
            mode = newMode
            shownAtMs = System.currentTimeMillis()
        }
    }

    private fun updatePanelText(title: String, body: String) {
        val root = view as? LinearLayout ?: return
        val card = root.getChildAt(0) as? LinearLayout ?: return
        (card.getChildAt(0) as? TextView)?.text = title
        (card.getChildAt(1) as? TextView)?.text = body
    }

    /** The title is dropped on a short box, where two lines would not fit legibly. */
    private fun roomForTitle(bounds: ScreenRect): Boolean = bounds.height >= dp(140)

    private fun buildPatch(bounds: ScreenRect, title: String, body: String): View =
        LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B0B0D"))
            setPadding(dp(20), dp(16), dp(20), dp(16))
            isClickable = true
            addView(
                TextView(service).apply {
                    text = title
                    setTextColor(Color.parseColor("#7C8493"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    letterSpacing = 0.06f
                    gravity = Gravity.CENTER
                    isVisible = roomForTitle(bounds)
                },
            )
            addView(
                TextView(service).apply {
                    text = body
                    setTextColor(Color.parseColor("#E8EAF2"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, 0)
                },
            )
        }

    private fun updatePatchText(view: View, bounds: ScreenRect, title: String, body: String) {
        val box = view as? LinearLayout ?: return
        (box.getChildAt(0) as? TextView)?.let {
            it.text = title
            it.isVisible = roomForTitle(bounds)
        }
        (box.getChildAt(1) as? TextView)?.text = body
    }

    private fun buildPanel(title: String, body: String, actionLabel: String, onAction: () -> Unit): View {
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B0B0D"))
            setPadding(dp(24), dp(24), dp(24), dp(24))
            isClickable = true
        }

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#16181F"))
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dp(24), dp(24), dp(24), dp(20))
        }

        card.addView(
            TextView(service).apply {
                text = title
                setTextColor(Color.parseColor("#9AA3B2"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                letterSpacing = 0.06f
            },
        )
        card.addView(
            TextView(service).apply {
                text = body
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
                setPadding(0, dp(10), 0, dp(20))
                setLineSpacing(dp(4).toFloat(), 1f)
            },
        )
        card.addView(
            Button(service).apply {
                text = actionLabel
                isAllCaps = false
                setTextColor(Color.parseColor("#101114"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#E8EAF2"))
                }
                setOnClickListener { onAction() }
            },
        )

        root.addView(card, LinearLayout.LayoutParams(dp(320), LinearLayout.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()
}
