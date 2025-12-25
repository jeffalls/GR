package alls.tech.gr.hooks

import alls.tech.gr.core.Config
import alls.tech.gr.utils.Hook
import alls.tech.gr.utils.HookStage
import alls.tech.gr.utils.hook
import kotlin.time.Duration.Companion.minutes

class OnlineIndicator : Hook(
    "Online indicator",
    "Customize online indicator duration"
) {
    val utils = "Vm.m0" // search for ' <= 600000;'

    override fun init() {
        findClass(utils) // shouldShowOnlineIndicator()
            .hook("a", HookStage.BEFORE) { param ->
                val savedDuration = Config.get("online_indicator", 3).toString().toInt()
                param.setResult(System.currentTimeMillis() - param.arg<Long>(0) <= savedDuration.minutes.inWholeMilliseconds)
            }
    }
}