package com.petterp.floatingx.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import com.petterp.floatingx.assist.Direction
import com.petterp.floatingx.assist.helper.AppHelper
import com.petterp.floatingx.assist.helper.BasisHelper
import com.petterp.floatingx.util.topActivity

/**
 * 基础悬浮窗View 源自 ->
 *
 * https://github.com/shenzhen2017/EasyFloat/blob/main/easyfloat/src/main/java/com/zj/easyfloat/floatingview/FloatingMagnetView.java
 */
@SuppressLint("ViewConstructor")
class FxMagnetView @JvmOverloads constructor(
    context: Context,
    private val helper: BasisHelper,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    private var mOriginalRawX = 0f
    private var mOriginalRawY = 0f
    private var mOriginalX = 0f
    private var mOriginalY = 0f

    // 最后触摸的时间
    private var mLastTouchDownTime: Long = 0
    private var mMoveAnimator: MoveAnimator? = null
    private var mParentWidth = 0f
    private var mParentHeight = 0f

    private var isNearestLeft = true
    private var mPortraitY = 0f
    private var touchDownX = 0f
    private var touchDownId: Int = 0

    @Volatile
    private var isClickEnable: Boolean = true

    @Volatile
    private var isMoveLoading = false

    private var _childFxView: View? = null
    val childFxView: View? get() = _childFxView

    init {
        initView()
    }

    private fun initView() {
        mMoveAnimator = MoveAnimator()
        isClickable = true
        _childFxView = inflateLayoutView() ?: inflateLayoutId()
        if (_childFxView == null) return
        if (_childFxView == null) helper.fxLog?.e("fxView--> inflateView, Error")
        val hasConfig = helper.iFxConfigStorage?.hasConfig() ?: false
        layoutParams = defaultLayoutParams(hasConfig)
        x = if (hasConfig) helper.iFxConfigStorage!!.getX() else helper.defaultX
        y = if (hasConfig) helper.iFxConfigStorage!!.getY() else initDefaultY()
        helper.fxLog?.d("fxView->x&&y   hasConfig-($hasConfig),x-($x),y-($y)")
        setBackgroundColor(Color.TRANSPARENT)
    }

    private fun inflateLayoutView(): View? {
        val view = helper.layoutView?.get()
        if (view != null) {
            val lp = layoutParams ?: LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addViewInLayout(view, -1, lp, true)
            helper.fxLog?.d("fxView-->init, way-[layoutView]")
        }
        return view
    }

    private fun inflateLayoutId(): View? {
        if (helper.layoutId != 0) {
            helper.fxLog?.d("fxView-->init, way-[layoutId]")
            val view = inflate(context, helper.layoutId, this)
            helper.layoutParams?.let {
                view.layoutParams = it
            }
            return view
        }
        return null
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        var intercepted = false
        if (!helper.enableTouch) return intercepted
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                intercepted = false
                helper.fxLog?.v("fxView---onInterceptTouchEvent-[down],interceptedTouch-$intercepted")
                touchDownX = ev.x
                // 初始化按下后的信息
                initTouchDown(ev)
                helper.iFxScrollListener?.down()
            }
            MotionEvent.ACTION_MOVE -> {
                // 判断是否要拦截事件
                intercepted =
                    kotlin.math.abs(touchDownX - ev.x) >= ViewConfiguration.get(
                    context
                ).scaledTouchSlop
                helper.fxLog?.v("fxView---onInterceptTouchEvent-[move], interceptedTouch-$intercepted")
            }
            MotionEvent.ACTION_UP -> {
                intercepted = false
                helper.fxLog?.v("fxView---onInterceptTouchEvent-[up], interceptedTouch-$intercepted")
            }
        }
        return intercepted
    }

    private fun initTouchDown(ev: MotionEvent) {
        changeOriginalTouchParams(ev)
        updateSize()
        touchDownId = ev.getPointerId(0)
        mMoveAnimator?.stop()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (updateSize() && helper.enableAbsoluteFix) {
            fixLocation()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        helper.iFxScrollListener?.eventIng(event)
        when (event.action) {
            MotionEvent.ACTION_MOVE ->
                updateViewPosition(event)
            MotionEvent.ACTION_UP -> {
                helper.fxLog?.v("fxView---onTouchEvent--up")
                actionTouchCancel()
                clickManagerView()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                helper.fxLog?.v("fxView---onTouchEvent--POINTER_UP")
                if (event.findPointerIndex(touchDownId) == 0) {
                    moveToEdge()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                helper.fxLog?.v("fxView---onTouchEvent--CANCEL")
                actionTouchCancel()
            }
        }
        return helper.enableTouch || super.onTouchEvent(event)
    }

    private fun clickManagerView() {
        if (helper.enableClickListener && isClickEnable && isOnClickEvent()) {
            isClickEnable = false
            helper.fxLog?.d("fxView -> click")
            helper.iFxClickListener?.onClick(this)
                ?: helper.fxLog?.e("fxView -> click, clickListener = null!!!")
            postDelayed({ isClickEnable = true }, helper.clickTime)
        }
    }

    private fun actionTouchCancel() {
        helper.iFxScrollListener?.up()
        clearPortraitY()
        touchDownId = 0
        moveToEdge()
    }

    private fun initDefaultY(): Float {
        var defaultY = helper.defaultY
        if (helper.defaultY > 0 || helper.gravity == Direction.RIGHT_OR_TOP || helper.gravity == Direction.LEFT_OR_TOP) {
            defaultY += helper.statsBarHeight + helper.borderMargin.t
        } else if (helper.defaultY < 0 || helper.gravity == Direction.LEFT_OR_BOTTOM || helper.gravity == Direction.RIGHT_OR_BOTTOM) {
            defaultY -= helper.navigationBarHeight - helper.borderMargin.b
        }
        return defaultY
    }

    private fun isOnClickEvent(): Boolean {
        return System.currentTimeMillis() - mLastTouchDownTime < TOUCH_TIME_THRESHOLD
    }

    private fun updateViewPosition(event: MotionEvent) {
        event.findPointerIndex(touchDownId).takeIf {
            it == 0
        }?.let {
            if (!helper.enableTouch) {
                helper.iFxScrollListener?.dragIng(event, x, y)
                helper.fxLog?.v("fxView---scrollListener--drag-event--x(${event.x})-y(${event.y})")
                return
            }
            // 按下时的坐标+当前手指距离屏幕的坐标-最开始距离屏幕的坐标
            var desX = mOriginalX + event.rawX - mOriginalRawX
            var desY = mOriginalY + event.rawY - mOriginalRawY
            // 如果允许边缘回弹，则Y轴只需要考虑状态栏与导航栏
            if (helper.enableEdgeRebound) {
                if (desX < 0f) desX = 0f
                else if (desX > mParentWidth) desX = mParentWidth

                if (desY < helper.statsBarHeight) {
                    desY = helper.statsBarHeight.toFloat()
                }
                val statusY = mParentHeight - helper.navigationBarHeight
                if (desY > statusY) {
                    desY = statusY
                }
            } else {
                val moveMinX = helper.borderMargin.l
                val moveMaxX = mParentWidth - helper.borderMargin.r
                val moveMinY = helper.statsBarHeight + helper.borderMargin.t
                val moveMaxY =
                    mParentHeight - helper.navigationBarHeight - helper.borderMargin.b
                if (desX < moveMinX) desX = moveMinX
                if (desX > moveMaxX) desX = moveMaxX
                if (desY < moveMinY) desY = moveMinY
                if (desY > moveMaxY) desY = moveMaxY
            }
            x = desX
            y = desY
            helper.fxLog?.v("fxView---scrollListener--drag--x($desX)-y($desY)")
            helper.iFxScrollListener?.dragIng(event, desX, desY)
        }
    }

    private fun changeOriginalTouchParams(event: MotionEvent) {
        mOriginalX = x
        mOriginalY = y
        mOriginalRawX = event.rawX
        mOriginalRawY = event.rawY
        mLastTouchDownTime = System.currentTimeMillis()
    }

    private fun updateSize(): Boolean {
        // 如果此时浮窗被父布局移除,parent将为null,此时就别更新位置了,没意义
        val parentGroup = (parent as? ViewGroup) ?: return false

        // 这里先减掉自身大小可以避免后期再重复减掉
        val parentWidth = (parentGroup.width - this@FxMagnetView.width).toFloat()
        val parentHeight = (parentGroup.height - this@FxMagnetView.height).toFloat()
        helper.fxLog?.d("fxView->size oldW-($mParentWidth),oldH-($mParentHeight),newW-($parentWidth),newH-($parentHeight)")
        if (mParentHeight != parentHeight || mParentWidth != parentWidth) {
            mParentWidth = parentWidth
            mParentHeight = parentHeight
            return true
        }
        return false
    }

    private fun clearPortraitY() {
        mPortraitY = 0f
    }

    private fun isNearestLeft(): Boolean {
        val middle = mParentWidth / 2
        isNearestLeft = x < middle
        return isNearestLeft
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        helper.fxLog?.d("fxView--lifecycle-> onConfigurationChanged--")
        val parentGroup = (parent as? ViewGroup) ?: return

        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        var isNavigationCHanged = false
        // 对于全局的处理
        if (helper is AppHelper) {
            val navigationBarHeight = helper.navigationBarHeight
            helper.updateNavigationBar(topActivity)
            isNavigationCHanged = navigationBarHeight != helper.navigationBarHeight
        }
        if (isLandscape || isNavigationCHanged) {
            mPortraitY = y
        }
        isMoveLoading = false

        // 如果视图大小改变,则更新位置
        parentGroup.post {
            if (updateSize()) {
                moveToEdge(isLandscape = isLandscape)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        helper.iFxViewLifecycle?.attach()
        helper.fxLog?.d("fxView-lifecycle-> onAttachedToWindow")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        helper.iFxViewLifecycle?.detached()
        helper.fxLog?.d("fxView-lifecycle-> onDetachedFromWindow")
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        helper.iFxViewLifecycle?.windowsVisibility(visibility)
        helper.fxLog?.d("fxView-lifecycle-> onWindowVisibilityChanged")
    }

    @JvmSynthetic
    internal fun moveToEdge(isLeft: Boolean = isNearestLeft(), isLandscape: Boolean = false) {
        if (isMoveLoading) return

        // 允许边缘吸附
        if (helper.enableEdgeAdsorption) {
            autoMove(isLeft, isLandscape)
            return
        }

        // 允许边缘回弹
        if (helper.enableEdgeRebound) {
            val configEdge = helper.edgeOffset
            val currentX = x
                .coerceAtLeast(configEdge + helper.borderMargin.l)
                .coerceAtMost(mParentWidth - helper.borderMargin.r - configEdge)
            val currentY = y
                .coerceAtLeast(helper.borderMargin.t + configEdge + helper.statsBarHeight)
                .coerceAtMost(mParentHeight - helper.borderMargin.b - configEdge - helper.navigationBarHeight)
            if (currentX != x || currentY != y) {
                isMoveLoading = true
                moveLocation(currentX, currentY)
            }
        }
    }

    private fun autoMove(isLeft: Boolean, isLandscape: Boolean) {
        isMoveLoading = true
        val configEnableEdge = helper.enableEdgeRebound
        val edgeOffset = if (configEnableEdge) helper.edgeOffset else 0f
        var moveY = y
        val moveX = if (isLeft) edgeOffset + helper.borderMargin.l
        else mParentWidth - edgeOffset - helper.borderMargin.r
        // 对于重建之后的位置保存
        if (isLandscape && mPortraitY != 0f) {
            moveY = mPortraitY
            clearPortraitY()
        }
        // 拿到y轴目前应该在的距离
        moveY = (helper.borderMargin.t + edgeOffset + helper.statsBarHeight)
            .coerceAtLeast(moveY)
            .coerceAtMost((mParentHeight - helper.borderMargin.b - edgeOffset - helper.navigationBarHeight))
        moveLocation(moveX, moveY)
    }

    @JvmSynthetic
    internal fun updateLocation(x: Float, y: Float) {
        (layoutParams as LayoutParams).gravity = Direction.DEFAULT.value
        this.x = x
        this.y = y
        helper.fxLog?.d("fxView-updateManagerView-> RestoreLocation  x->$x,y->$y")
    }

    /** 修复位置显示 */
    @JvmSynthetic
    internal fun fixLocation() {
        if (helper.enableEdgeAdsorption) {
            moveToEdge()
            return
        }
        var moveX = x
        var moveY = y
        val minX = helper.borderMargin.l
        val maxX = mParentWidth - helper.borderMargin.r
        val minY = helper.borderMargin.t + helper.statsBarHeight
        val maxY = mParentHeight - helper.borderMargin.b - helper.navigationBarHeight
        if (x < minX) moveX = minX else if (x > maxX) moveX = maxX
        if (y < minY) moveY = minY else if (y > maxY) moveY = maxY
        moveLocation(moveX, moveY)
    }

    private fun moveLocation(moveX: Float, moveY: Float) {
        if (moveX == x && moveY == y) {
            isMoveLoading = false
            return
        }
        mMoveAnimator?.start(moveX, moveY)
        helper.fxLog?.d("fxView-->moveToEdge---x-($x)，y-($y) ->  moveX-($moveX),moveY-($moveY)")
        if (helper.enableSaveDirection) {
            saveConfig(moveX, moveY)
        }
    }

    private inner class MoveAnimator : Runnable {
        private var destinationX = 0f
        private var destinationY = 0f
        private var startingTime: Long = 0
        fun start(x: Float, y: Float) {
            destinationX = x
            destinationY = y
            startingTime = System.currentTimeMillis()
            HANDLER.post(this)
        }

        override fun run() {
            if (childFxView == null || childFxView?.parent == null) {
                return
            }
            val progress =
                MAX_PROGRESS.coerceAtMost((System.currentTimeMillis() - startingTime) / 400f)
            x += (destinationX - x) * progress
            y += (destinationY - y) * progress
            if (progress < MAX_PROGRESS) {
                HANDLER.post(this)
            } else {
                isMoveLoading = false
            }
        }

        fun stop() {
            isMoveLoading = false
            HANDLER.removeCallbacks(this)
        }
    }

    private fun defaultLayoutParams(hasConfig: Boolean) = LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT
    ).apply {
        if (!hasConfig) {
            gravity = helper.gravity.value
        }
    }

    private fun saveConfig(moveX: Float, moveY: Float) {
        if (helper.iFxConfigStorage == null) {
            helper.fxLog?.e("fxView-->saveDirection---iFxConfigStorageImpl does not exist, save failed!")
            return
        }
        helper.iFxConfigStorage?.update(moveX, moveY)
        helper.fxLog?.d("fxView-->saveDirection---x-($moveX)，y-($moveY)")
    }

    companion object {
        private const val TOUCH_TIME_THRESHOLD = 150L
        private const val MAX_PROGRESS = 1f
        private val HANDLER = Handler(Looper.getMainLooper())
    }
}
