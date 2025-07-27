package alls.tech.gr.utils

import alls.tech.gr.core.Config
import alls.tech.gr.core.Config.genRandom
import alls.tech.gr.core.Logger
import alls.tech.gr.hooks.AllowScreenshots
import alls.tech.gr.hooks.AntiBlock
import alls.tech.gr.hooks.AntiDetection
import alls.tech.gr.hooks.BanManagement
import alls.tech.gr.hooks.ChatIndicators
import alls.tech.gr.hooks.ChatTerminal
import alls.tech.gr.hooks.DisableAnalytics
import alls.tech.gr.hooks.DisableBoosting
import alls.tech.gr.hooks.DisableShuffle
import alls.tech.gr.hooks.DisableUpdates
import alls.tech.gr.hooks.EmptyCalls
import alls.tech.gr.hooks.EnableUnlimited
import alls.tech.gr.hooks.ExpiringMedia
import alls.tech.gr.hooks.Favorites
import alls.tech.gr.hooks.FeatureGranting
import alls.tech.gr.hooks.LocalSavedPhrases
import alls.tech.gr.hooks.LocationSpoofer
import alls.tech.gr.hooks.NotificationAlerts
import alls.tech.gr.hooks.OnlineIndicator
import alls.tech.gr.hooks.ProfileDetails
import alls.tech.gr.hooks.ProfileViews
import alls.tech.gr.hooks.QuickBlock
import alls.tech.gr.hooks.StatusDialog
import alls.tech.gr.hooks.TimberLogging
import alls.tech.gr.hooks.UnlimitedAlbums
import alls.tech.gr.hooks.UnlimitedProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass
import android.os.Handler
import kotlin.time.ExperimentalTime
import android.content.Context
import alls.tech.gr.core.Config.writeDeviceIdToFile
import android.widget.Toast
import alls.tech.gr.hooks.ProfileSpoofer
import android.provider.Settings

class HookManager {
    private var hooks = mutableMapOf<KClass<out Hook>, Hook>()
    private var handler: Handler? = null
    private var androidId: String? = null
    private var profileIdRandom: String? = null
    private val delayMillisDeviceId: Long = 10 * 1000
    private val delayMillisUserId: Long = 1 * 10 * 1000

    @ExperimentalTime
    private fun startHandler(context: Context) {
        handler = Handler(context.mainLooper)
        handler?.postDelayed(object : Runnable {
            override fun run() {
                androidId?.let { writeDeviceIdToFile(context, it) }
                handler?.postDelayed(this, delayMillisDeviceId)
            }
        }, delayMillisDeviceId)

        handler?.postDelayed(object : Runnable {
            override fun run() {
                profileIdRandom = genRandom(6, 9)
                //profileIdRandom?.let { writeUserIdToFile(context, it) }
                handler?.postDelayed(this, delayMillisUserId)
            }
        }, delayMillisUserId)
    }

    @OptIn(ExperimentalTime::class)
    fun registerHooks(context: Context, init: Boolean = true) {

        val (jsonFile, jsonObject, message) = FileUtils.getOrCreateJsonFile(context)
        val jsonSize = jsonObject.toString().length
        Toast.makeText(
            context, "$message : ${String.format("%.2f", jsonSize / 1024.0)} KB", Toast.LENGTH_LONG
        ).show()

        runBlocking(Dispatchers.IO) {
            val hookList = listOf(
                TimberLogging(),
                BanManagement(),
                FeatureGranting(),
                EnableUnlimited(),
                AntiDetection(),
                StatusDialog(),
                AntiBlock(),
                NotificationAlerts(),
                DisableUpdates(),
                DisableBoosting(),
                DisableShuffle(),
                AllowScreenshots(),
                ChatIndicators(),
                ChatTerminal(),
                DisableAnalytics(),
                ExpiringMedia(),
                Favorites(),
                LocalSavedPhrases(),
                LocationSpoofer(),
                ProfileSpoofer(jsonObject),
                OnlineIndicator(),
                UnlimitedProfiles(),
                ProfileDetails(jsonObject),
                ProfileViews(),
                QuickBlock(),
                EmptyCalls(),
                UnlimitedAlbums()
            )

            hookList.forEach { hook ->
                Config.initHookSettings(
                    hook.hookName, hook.hookDesc, true
                )
            }

            if (!init) return@runBlocking

            hooks = hookList.associateBy { it::class }.toMutableMap()

            androidId =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

            context?.let { startHandler(it) }

            hooks.values.forEach { hook ->
                if (Config.isHookEnabled(hook.hookName)) {
                    hook.init()
                    Logger.s("Initialized hook: ${hook.hookName}")
                } else {
                    Logger.i("Hook ${hook.hookName} is disabled.")
                }
            }
        }
    }

    fun init(context: Context) {
        registerHooks(context)
    }
}