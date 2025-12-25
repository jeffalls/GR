package alls.tech.gr.hooks

import alls.tech.gr.core.logi
import alls.tech.gr.utils.Hook
import alls.tech.gr.utils.HookStage
import alls.tech.gr.utils.RetrofitUtils.RETROFIT_NAME
import alls.tech.gr.utils.RetrofitUtils.createServiceProxy
import alls.tech.gr.utils.hook

class DisableAnalytics : Hook(
    "Disable analytics",
    "Disable Grindr analytics (data collection)"
) {
    private val analyticsRestService = "E6.a" // search for 'v1/telemetry'

    override fun init() {
        val analyticsRestServiceClass = findClass(analyticsRestService)

        // First party analytics
        /*
        findClass(RETROFIT_NAME)
            .hook("create", HookStage.AFTER) { param ->
                val service = param.getResult()
                if (service != null && analyticsRestServiceClass.isAssignableFrom(service.javaClass)) {
                    param.setResult(createServiceProxy(service, analyticsRestServiceClass))
                }
            }
        */

        /*
        // Amplitude Analytics
        findClass("com.amplitude.android.Configuration")
            .hook("getOptOut", HookStage.AFTER) { param ->
                param.setResult(true)
            }
        */

        // Braze
        findClass("com.braze.Braze\$Companion")
            // See https://braze-inc.github.io/braze-android-sdk/kdoc/braze-android-sdk/com.braze/-braze/-companion/outbound-network-requests-offline.html
            .hook("setOutboundNetworkRequestsOffline", HookStage.BEFORE) {
                    param -> param.setArg(0, true)
            }

        // Digital Turbine
        findClass("com.fyber.inneractive.sdk.network.i")
            .hook("a", HookStage.BEFORE) {
                    param -> param.setResult(null)
            }

        // Google Analytics
        findClass("com.google.firebase.analytics.FirebaseAnalytics")
            .hook("setAnalyticsCollectionEnabled", HookStage.BEFORE) { param ->
                param.setArg(0, false)
            }

        // Google Crashlytics
        findClass("com.google.firebase.crashlytics.FirebaseCrashlytics")
            .hook("setCrashlyticsCollectionEnabled", HookStage.BEFORE) { param ->
                param.setArg(0, false)
            }

        // Ironsource
        findClass("com.ironsource.mediationsdk.server.ServerURL")
            .hook("getRequestURL", HookStage.BEFORE) {
                    param -> param.setResult(null)
            }

        // Liftoff (Vungle)
        findClass("com.vungle.ads.internal.network.VungleApiClient")
            .hook("config", HookStage.BEFORE) {
                    param -> param.setResult(null)
            }

        // Unity
        findClass("com.unity3d.services.ads.UnityAdsImplementation")
            .hook("getInstance", HookStage.BEFORE) {
                    param -> param.setResult(null)
            }
    }
}