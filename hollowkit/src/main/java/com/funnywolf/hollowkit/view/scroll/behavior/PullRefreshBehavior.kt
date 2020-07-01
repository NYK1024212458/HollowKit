package com.funnywolf.hollowkit.view.scroll.behavior

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.ViewCompat
import com.funnywolf.hollowkit.R
import com.funnywolf.hollowkit.utils.dp
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * 下拉刷新
 *
 * @author https://github.com/funnywolfdadada
 * @since 2020/6/14
 */
class PullRefreshBehavior(
    override val midView: View
) : NestedScrollBehavior {

    var enable: Boolean = true
        set(value) {
            field = value
            if (!value) {
                isRefreshing = false
            }
        }

    var refreshListener: (()->Unit)? = null

    var isRefreshing: Boolean = false
        set(value) {
            if (value && value != field) {
                refreshListener?.invoke()
            }
            field = value
            bsvRef?.get()?.smoothScrollTo(if (value) { -refreshView.height / 2 } else { 0 })
            if (value) {
                refreshView.loadingView.alpha = 1F
                refreshView.animator.start()
            } else {
                refreshView.animator.cancel()
            }
        }

    private var process: Float = 0F
        set(value) {
            if (isRefreshing) {
                return
            }
            field = value
            refreshView.animator.cancel()
            refreshView.loadingView.alpha = process * 3
            refreshView.loadingView.rotation = process * 3 * 360
        }

    val refreshView = RefreshView(midView.context).also {
        it.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
    }

    private var bsvRef: WeakReference<BehavioralScrollView>? = null
    private val onScrollChanged: (BehavioralScrollView)->Unit = {
        process = abs(it.currProcess())
    }

    override val scrollVertical: Boolean = true
    override val prevView: View? = refreshView
    override val nextView: View? = null

    override fun afterLayout(v: BehavioralScrollView) {
        super.afterLayout(v)
        bsvRef = WeakReference(v)
        v.onScrollChangedListeners.add(onScrollChanged)
    }

    override fun handleDispatchTouchEvent(v: BehavioralScrollView, e: MotionEvent): Boolean? {
        // 防止动画被打断
        if (v.state == NestedScrollState.ANIMATION) {
            return true
        }
        // 抬手时，如果头部已经滚出来了，且未刷新，则根据滚出的距离设置刷新状态
        if (e.action == MotionEvent.ACTION_UP && v.scrollY != 0 && !isRefreshing) {
            isRefreshing = abs(v.scrollY) > refreshView.refreshHeight
            return true
        }
        return super.handleDispatchTouchEvent(v, e)
    }

    override fun scrollSelfFirst(v: BehavioralScrollView, scroll: Int, type: Int): Boolean {
        val handle = if (v.state == NestedScrollState.ANIMATION) {
            // 在动画过程中，且不是 touch 类型的滚动，即动画造成的滚动，自己优先处理
            type == ViewCompat.TYPE_NON_TOUCH
        } else {
            // 不在动画过程中，只在自身发生滚动，且不在刷新过程中，即拖拽头部 view 的过程中，优先自己处理
            v.scrollY != 0 && !isRefreshing && type == ViewCompat.TYPE_TOUCH
        }
        v.log("scrollSelfFirst $handle, state = ${v.state}, type = $type, isRefreshing = $isRefreshing")
        return handle
    }

    override fun handleScrollSelf(v: BehavioralScrollView, scroll: Int, type: Int): Boolean? {
        return when {
            // touch 类型的滚动都要拦下来
            type == ViewCompat.TYPE_TOUCH -> if (isRefreshing) {
                // 刷新中就不再滚动自身
                false
            } else {
                // 不再刷新中的就滚动自身，根据滚动方向决定是否添加阻尼效果
                v.scrollBy(0, scroll / if (scroll < 0) { 2 } else { 1 })
                true
            }
            // 动画的全部滚动
            v.state == NestedScrollState.ANIMATION -> null
            // 非 touch 的且不是动画滚动不处理
            else -> false
        }
    }

}

class RefreshView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
): FrameLayout(context, attrs, defStyleAttr) {

    val loadingView = ImageView(context)

    val refreshHeight = (36 * 2).dp

    val animator: ValueAnimator = ValueAnimator.ofFloat(0F, 360F).apply {
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        addUpdateListener {
            loadingView.rotation = it.animatedValue as? Float ?: 0F
        }
    }

    init {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, refreshHeight * 2)
        setPadding(0, refreshHeight, 0, 0)
        loadingView.setImageResource(R.drawable.ic_loading)
        loadingView.setPadding(refreshHeight / 4, refreshHeight / 4, refreshHeight / 4, refreshHeight / 4)
        addView(loadingView, LayoutParams(refreshHeight, refreshHeight).also {
            it.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        })
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}