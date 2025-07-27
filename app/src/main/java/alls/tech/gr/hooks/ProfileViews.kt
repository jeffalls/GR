package alls.tech.gr.hooks

import alls.tech.gr.utils.Hook
import alls.tech.gr.utils.HookStage
import alls.tech.gr.utils.RetrofitUtils.RETROFIT_NAME
import alls.tech.gr.utils.RetrofitUtils.createServiceProxy
import alls.tech.gr.utils.RetrofitUtils.findPOSTMethod
import alls.tech.gr.utils.hook

class ProfileViews : Hook("Profile views", "Don't let others know you viewed their profile") {
    private val profileRestService = "com.grindrapp.android.api.ProfileRestService"
    private val blacklistedPaths = setOf(
        "v4/views/{profileId}",
        "v5/views/{profileId}",
        "v4/views"
    )

    override fun init() {
        val profileRestServiceClass = findClass(profileRestService)

        val methodBlacklist =
            blacklistedPaths.mapNotNull { findPOSTMethod(profileRestServiceClass, it)?.name }

        findClass(RETROFIT_NAME).hook("create", HookStage.AFTER) { param ->
            val service = param.getResult()
            if (service != null && profileRestServiceClass.isAssignableFrom(service.javaClass)) {
                param.setResult(
                    createServiceProxy(
                        service,
                        profileRestServiceClass,
                        methodBlacklist.toTypedArray()
                    )
                )
            }
        }
    }
}
