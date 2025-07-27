package alls.tech.gr.hooks

import alls.tech.gr.utils.Hook
import alls.tech.gr.utils.HookStage
import alls.tech.gr.utils.RetrofitUtils
import alls.tech.gr.utils.RetrofitUtils.RETROFIT_NAME
import alls.tech.gr.utils.RetrofitUtils.createServiceProxy
import alls.tech.gr.utils.hook

class ChatIndicators : Hook(
    "Chat indicators",
    "Don't show chat markers / indicators to others"
) {
    private val chatRestService = "com.grindrapp.android.chat.data.datasource.api.service.ChatRestService"
    private val blacklistedPaths = setOf(
        "v4/chatstatus/typing"
    )

    override fun init() {
        val chatRestServiceClass = findClass(chatRestService)

        val methodBlacklist = blacklistedPaths.mapNotNull {
            RetrofitUtils.findPOSTMethod(chatRestServiceClass, it)?.name
        }

        findClass(RETROFIT_NAME)
            .hook("create", HookStage.AFTER) { param ->
                val service = param.getResult()
                if (service != null && chatRestServiceClass.isAssignableFrom(service.javaClass)) {
                    param.setResult(createServiceProxy(
                        service,
                        chatRestServiceClass,
                        methodBlacklist.toTypedArray()
                    ))
                }
            }
    }
}