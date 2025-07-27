package alls.tech.gr

import android.app.Application
import alls.tech.gr.core.Constants.GRINDR_PACKAGE_NAME
import alls.tech.gr.hooks.spoofSignatures
import alls.tech.gr.hooks.sslUnpinning
import alls.tech.gr.utils.HookStage
import alls.tech.gr.utils.hook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookZygoteInit, IXposedHookLoadPackage {
    private lateinit var modulePath: String

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!lpparam.packageName.contains(GRINDR_PACKAGE_NAME)) return

        spoofSignatures(lpparam)
        if (BuildConfig.DEBUG) {
            sslUnpinning(lpparam)
        }

        Application::class.java.hook("attach", HookStage.AFTER) {
            val application = it.thisObject()
            GR.init(modulePath, application)
        }
    }
}