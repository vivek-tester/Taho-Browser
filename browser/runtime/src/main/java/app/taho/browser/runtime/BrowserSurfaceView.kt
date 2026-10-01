package app.taho.browser.runtime

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ProgressBar
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import kotlin.math.abs

class BrowserSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val geckoView = GeckoView(context)
    private val refreshIndicator = ProgressBar(context).apply {
        isIndeterminate = true
        visibility = View.GONE
    }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val density = resources.displayMetrics.density
    private val refreshThresholdPx = 72f * density
    private var downX = 0f
    private var downY = 0f
    private var dragDistance = 0f
    private var eligibleForPull = false
    private var dragging = false
    private var refreshing = false

    var onRefresh: (() -> Unit)? = null

    init {
        addView(geckoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(
            refreshIndicator,
            LayoutParams((34 * density).toInt(), (34 * density).toInt()).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = (18 * density).toInt()
            },
        )
    }

    fun bind(session: GeckoSession) {
        if (geckoView.session === session) {
            session.setPrintDelegate(geckoView.printDelegate)
            return
        }
        geckoView.session?.setPrintDelegate(null)
        if (geckoView.session != null) geckoView.releaseSession()
        geckoView.setSession(session)
        session.setPrintDelegate(geckoView.printDelegate)
    }

    fun onPageLoadingChanged(isLoading: Boolean) {
        if (refreshing && !isLoading) {
            refreshing = false
            resetPull()
        }
    }

    fun unbind() {
        geckoView.session?.setPrintDelegate(null)
        if (geckoView.session != null) geckoView.releaseSession()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (refreshing || onRefresh == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragDistance = 0f
                dragging = false
                eligibleForPull = !geckoView.canScrollVertically(-1)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!eligibleForPull) return false
                val dx = event.x - downX
                val dy = event.y - downY
                if (dy > touchSlop && dy > abs(dx) * 1.15f) {
                    dragging = true
                    refreshIndicator.visibility = View.VISIBLE
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> resetPull()
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                dragDistance = (event.y - downY).coerceAtLeast(0f)
                refreshIndicator.translationY = (dragDistance * 0.34f).coerceAtMost(refreshThresholdPx * 0.55f)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val shouldRefresh = dragDistance >= refreshThresholdPx
                dragging = false
                eligibleForPull = false
                if (shouldRefresh) {
                    refreshing = true
                    refreshIndicator.visibility = View.VISIBLE
                    refreshIndicator.translationY = refreshThresholdPx * 0.32f
                    onRefresh?.invoke()
                } else {
                    resetPull()
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                resetPull()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun resetPull() {
        dragging = false
        eligibleForPull = false
        dragDistance = 0f
        if (!refreshing) {
            refreshIndicator.translationY = 0f
            refreshIndicator.visibility = View.GONE
        }
    }

    override fun onDetachedFromWindow() {
        unbind()
        super.onDetachedFromWindow()
    }
}
