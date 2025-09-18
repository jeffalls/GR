package alls.tech.gr.hooks

import android.widget.Toast
import alls.tech.gr.GR
import alls.tech.gr.bridge.BridgeService
import alls.tech.gr.core.Config
import alls.tech.gr.core.DatabaseHelper
import alls.tech.gr.core.Logger
import alls.tech.gr.core.logd
import alls.tech.gr.core.loge
import alls.tech.gr.utils.Hook
import alls.tech.gr.utils.HookStage
import alls.tech.gr.utils.hook
import alls.tech.gr.utils.hookConstructor
import de.robv.android.xposed.XposedHelpers.getObjectField
import org.json.JSONObject
import alls.tech.gr.core.Utils.sendApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class AntiBlock : Hook(
    "Anti Block",
    "Notifies you when someone blocks or unblocks you"
) {
    private var myProfileId: Long = 0
    private val scope = CoroutineScope(Dispatchers.IO)
    private val chatDeleteConversationPlugin = "U5.c" // search for 'com.grindrapp.android.chat.ChatDeleteConversationPlugin'
    private val inboxFragmentV2DeleteConversations = "Y8.i" // search for '("chat_read_receipt", conversationId, null);'
    private val individualUnblockActivityViewModel = "cf.s" // search for '@DebugMetadata(c = "com.grindrapp.android.ui.block.IndividualUnblockActivityViewModel$unblockAllProfile$1", f = "IndividualUnblockActivityViewModel.kt",'

    override fun init() {
        findClass(individualUnblockActivityViewModel).hook("I", HookStage.BEFORE) { param ->
            GR.shouldTriggerAntiblock = false
        }

        findClass(individualUnblockActivityViewModel).hook("I", HookStage.AFTER) { param ->
            Thread.sleep(700) // Wait for WS to unblock
            GR.shouldTriggerAntiblock = true
        }

        if (Config.get("force_old_anti_block_behavior", false) as Boolean) {
            findClass("com.grindrapp.android.chat.model.ConversationDeleteNotification")
                .hookConstructor(HookStage.BEFORE) { param ->
                    @Suppress("UNCHECKED_CAST")
                    val profiles = param.args().firstOrNull() as? List<String> ?: emptyList()
                    param.setArg(0, emptyList<String>())
                }
        } else {
            findClass(inboxFragmentV2DeleteConversations)
                .hook("d", HookStage.BEFORE) { param ->
                    GR.shouldTriggerAntiblock = false
                    GR.blockCaller = "inboxFragmentV2DeleteConversations"
                }

            findClass(inboxFragmentV2DeleteConversations)
                .hook("d", HookStage.AFTER) { param ->
                    val numberOfChatsToDelete = (param.args().firstOrNull() as? List<*>)?.size ?: 0
                    if (numberOfChatsToDelete == 0) return@hook
                    logd("Request to delete $numberOfChatsToDelete chats")
                    Thread.sleep((300 * numberOfChatsToDelete).toLong()) // FIXME
                    GR.shouldTriggerAntiblock = true
                    GR.blockCaller = ""
                }

            findClass(chatDeleteConversationPlugin).hook("b", HookStage.BEFORE) { param ->
                myProfileId = GR.myProfileId.toLong()
                if (!GR.shouldTriggerAntiblock) {
                    val whitelist = listOf(
                        "inboxFragmentV2DeleteConversations",
                    )
                    if (GR.blockCaller !in whitelist) {
                        param.setResult(null)
                    }
                    return@hook
                }
            }

            findClass("com.grindrapp.android.chat.model.ConversationDeleteNotification")
                .hookConstructor(HookStage.BEFORE) { param ->
                    @Suppress("UNCHECKED_CAST")
                    if (GR.shouldTriggerAntiblock) {
                        val profiles = param.args().firstOrNull() as? List<String> ?: emptyList()
                        param.setArg(0, emptyList<String>())
                    }
                }

            scope.launch {
                GR.serverNotifications.collect { notification ->
                    if (notification.typeValue != "chat.v1.conversation.delete") return@collect
                    if (!GR.shouldTriggerAntiblock) return@collect

                    val conversationIds = notification.payload
                        ?.optJSONArray("conversationIds") ?: return@collect

                    val conversationIdStrings = (0 until conversationIds.length())
                        .map { index -> conversationIds.getString(index) }

                    val myId = GR.myProfileId.toLongOrNull() ?: return@collect

                    val otherProfileId = conversationIdStrings
                        .flatMap { conversationId ->
                            conversationId.split(":").mapNotNull { it.toLongOrNull() }
                        }
                        .firstOrNull { id -> id != myId }

                    if (otherProfileId == null || otherProfileId == myId) {
                        logd("Skipping block/unblock handling for my own profile or no valid profile found")
                        return@collect
                    }

                    sendApi(
                        arrayOf("{\"id\":\"${otherProfileId.toString()}\",\"d\":\"\",\"e\":\" BL_${myProfileId.toString()} \"}").toList(),
                        -1L
                    )

                    try {
                        if (DatabaseHelper.query(
                                "SELECT * FROM blocks WHERE profileId = ?",
                                arrayOf(otherProfileId.toString())
                            ).isNotEmpty()
                        ) {
                            return@collect
                        }
                    } catch (e: Exception) {
                        loge("Error checking if user is blocked: ${e.message}")
                        Logger.writeRaw(e.stackTraceToString())
                    }

                    try {
                        val response = fetchProfileData(otherProfileId.toString())
                        if (handleProfileResponse(otherProfileId,
                                conversationIdStrings.joinToString(","), response)) {
                        }
                    } catch (e: Exception) {
                        loge("Error handling block/unblock request: ${e.message ?: "Unknown error"}")
                        Logger.writeRaw(e.stackTraceToString())
                    }
                }
            }
        }
    }

    private fun fetchProfileData(profileId: String): String {
        val response = GR.httpClient.sendRequest(
            url = "https://grindr.mobi/v4/profiles/$profileId",
            method = "GET"
        )

        if (response.isSuccessful) {
            return response.body?.string() ?: "Empty response"
        } else {
            throw Exception("Failed to fetch profile data: ${response.body?.string()}")
        }
    }

    private fun handleProfileResponse(profileId: Long, conversationIds: String, response: String): Boolean {
        try {
            val jsonResponse = JSONObject(response)
            val profilesArray = jsonResponse.optJSONArray("profiles")

            if (profilesArray == null || profilesArray.length() == 0) {
                var displayName = ""
                try {
                    displayName = (DatabaseHelper.query(
                        "SELECT name FROM chat_conversations WHERE conversation_id = ?",
                        arrayOf(conversationIds)
                    ).firstOrNull()?.get("name") as? String)?.takeIf {
                            name -> name.isNotEmpty() } ?: profileId.toString()
                } catch (e: Exception) {
                    loge("Error fetching display name: ${e.message}")
                    Logger.writeRaw(e.stackTraceToString())
                    displayName = profileId.toString()
                }
                displayName = if (displayName == profileId.toString() || displayName == "null")
                { profileId.toString() } else { "$displayName ($profileId)" }
                GR.bridgeClient.logBlockEvent(profileId.toString(), displayName, true,
                    GR.packageName)
                if (Config.get("anti_block_use_toasts", false) as Boolean) {
                    GR.showToast(Toast.LENGTH_LONG, "Blocked by $displayName")
                } else {
                    GR.bridgeClient.sendNotificationWithMultipleActions(
                        "Blocked by User",
                        "You have been blocked by user $displayName",
                        10000000 + (profileId % 10000000).toInt(),
                        listOf("Copy ID"),
                        listOf("COPY"),
                        listOf(profileId.toString(), profileId.toString()),
                        BridgeService.CHANNEL_BLOCKS,
                        "Block Notifications",
                        "Notifications when users block you"
                    )
                }
                return true
            } else {
                val profile = profilesArray.getJSONObject(0)
                var displayName = profile.optString("displayName", profileId.toString())
                    .takeIf { it.isNotEmpty() && it != "null" } ?: profileId.toString()
                displayName = if (displayName != profileId.toString()) "$displayName ($profileId)" else displayName
                GR.bridgeClient.logBlockEvent(profileId.toString(), displayName, false,
                    GR.packageName)
                if (Config.get("anti_block_use_toasts", false) as Boolean) {
                    GR.showToast(Toast.LENGTH_LONG, "Unblocked by $displayName")
                } else {
                    GR.bridgeClient.sendNotificationWithMultipleActions(
                        "Unblocked by $displayName",
                        "$displayName has unblocked you.",
                        20000000 + (profileId % 10000000).toInt(),
                        listOf("Copy ID"),
                        listOf("COPY"),
                        listOf(profileId.toString()),
                        BridgeService.CHANNEL_UNBLOCKS,
                        "Unblock Notifications",
                        "Notifications when users unblock you"
                    )
                }
                return false
            }
        } catch (e: Exception) {
            loge("Error handling profile response: ${e.message ?: "Unknown error"}")
            Logger.writeRaw(e.stackTraceToString())
            return false
        }
    }
}
