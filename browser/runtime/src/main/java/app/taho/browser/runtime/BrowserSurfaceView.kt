package app.taho.browser.runtime

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class BrowserSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val geckoView = GeckoView(context)

    init {
        addView(
            geckoView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    fun bind(session: GeckoSession) {
        if (geckoView.session === session) return
        if (geckoView.session != null) geckoView.releaseSession()
        geckoView.setSession(session)
    }

    fun unbind() {
        if (geckoView.session != null) geckoView.releaseSession()
    }

    override fun onDetachedFromWindow() {
        unbind()
        super.onDetachedFromWindow()
    }
}
