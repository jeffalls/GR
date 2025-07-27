package alls.tech.gr.hooks

import alls.tech.gr.GR
import alls.tech.gr.utils.Hook
import alls.tech.gr.utils.HookStage
import alls.tech.gr.utils.hook
import alls.tech.gr.utils.hookConstructor

class EmptyCalls : Hook(
    "Video calls",
    "Allow video calls on empty chats"
) {
    private val individualChatNavViewModel = "l6.d0" // search for 'com.grindrapp.android.chat.presentation.viewmodel.IndividualChatNavViewModel'

    override fun init() {
        findClass(individualChatNavViewModel) // isTalkBefore()
            .hook("E",  HookStage.BEFORE) { param ->
                param.setResult(true)
            }
    }
}