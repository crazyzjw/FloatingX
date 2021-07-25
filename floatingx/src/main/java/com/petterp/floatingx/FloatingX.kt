package com.petterp.floatingx

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import androidx.annotation.MainThread
import com.petterp.floatingx.assist.FxHelper
import com.petterp.floatingx.ext.topActivity
import com.petterp.floatingx.impl.FxControlImpl
import com.petterp.floatingx.impl.FxLifecycleCallbackImpl
import com.petterp.floatingx.listener.IFxControl

/**
 * @Author petterp
 * @Date 2021/5/20-7:48 下午
 * @Email ShiyihuiCloud@163.com
 * @Function Single Control To Fx
 */
@SuppressLint("StaticFieldLeak")
object FloatingX {
    private var fxControl: IFxControl? = null
    internal var helper: FxHelper? = null
    internal var iFxAppLifecycleImpl: FxLifecycleCallbackImpl? = null

    /** dsl初始化 */
    fun init(obj: FxHelper.Builder.() -> Unit) =
        init(FxHelper.builder(obj))

    /** 悬浮窗配置信息 */
    @JvmStatic
    fun init(helper: FxHelper) {
        this.helper = helper
        initControl()
    }

    /** 显示fx
     * 如果未传递activity,请确保Fx初始化时已经调用FxHelper.show()
     * activity==null,将根据app-lifecycle使用当前栈顶的top-activity
     * */
    @MainThread
    @JvmStatic
    fun show(activity: Activity? = null) {
        if (activity == null && topActivity == null)
            throw NullPointerException("Make sure Fx is properly initialized and that FxHelper.show () is called in the application.")
        if (!config().enableFx) config().enableFx = true
        initControl()
        control().show(activity ?: topActivity!!)
    }

    @MainThread
    @JvmStatic
    fun dismiss() {
        fxControl?.dismiss()
    }

    @MainThread
    @JvmStatic
    fun hide() {
        fxControl?.hide()
    }

    /** 清除历史坐标信息，如果开启了历史存储 */
    @JvmStatic
    fun clearConfig() {
        helper?.iFxConfigStorage?.clear()
    }

    /** 调用此方法将关闭悬浮窗,保留配置信息helper与AppLifecycle监听
     * 如果放弃配置信息与appLifecycle,再次调用show将导致无法获取栈顶Activity
     * */
    @JvmStatic
    fun cancel() {
        dismiss()
        getConfigApplication().unregisterActivityLifecycleCallbacks(iFxAppLifecycleImpl)
        fxControl = null
        iFxAppLifecycleImpl = null
    }

    private fun initControl() {
        if (fxControl == null && config().enableFx) {
            fxControl = FxControlImpl(config())
            initAppLifecycle()
            iFxAppLifecycleImpl?.control = fxControl
        }
    }

    private fun initAppLifecycle() {
        if (iFxAppLifecycleImpl == null) {
            val application = getConfigApplication()
            iFxAppLifecycleImpl = FxLifecycleCallbackImpl(config())
            iFxAppLifecycleImpl?.control = fxControl
            application.registerActivityLifecycleCallbacks(iFxAppLifecycleImpl)
        }
    }

    private fun getConfigApplication(): Application {
        if (config().context is Application)
            return config().context as Application
        else throw ClassCastException(
            "If you use the global floating window, FxConfig.context must" +
                " be Application,otherwise the appLifecycle cannot be registered"
        )
    }

    private fun config(): FxHelper =
        helper ?: throw NullPointerException("config==null!!!,FxConfig Cannot be null")

    @JvmStatic
    fun control(): IFxControl =
        fxControl
            ?: throw NullPointerException("control==null!!!,IFloatingControl Cannot be null")
}
