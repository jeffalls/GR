package alls.tech.gr.hooks

import alls.tech.gr.GR
import alls.tech.gr.core.Logger
import alls.tech.gr.core.logd
import alls.tech.gr.core.loge
import alls.tech.gr.utils.Hook
import alls.tech.gr.utils.HookStage
import alls.tech.gr.utils.hook
import alls.tech.gr.utils.hookConstructor
import de.robv.android.xposed.XposedHelpers.setObjectField
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject


class DisableUpdates : Hook(
    "Disable updates",
    "Disable forced updates"
) {
    private val versionInfoEndpoint = "https://alls.tech/v%E2%88%9E" //"https://raw.githubusercontent.com/R0rt1z2/GrindrPlus/master/version.json"
    private val appUpdateInfo = "com.google.android.play.core.appupdate.AppUpdateInfo"
    private val appUpdateZzm = "com.google.android.play.core.appupdate.zzm" // search for 'requestUpdateInfo(%s)'
    private val appUpgradeManager = "K9.n" // search for 'Uri.parse("market://details?id=com.grindrapp.android");'
    private val appConfiguration = "com.grindrapp.android.platform.config.AppConfiguration"
    private var versionCode: Int = 0
    private var versionName: String = ""

    override fun init() {
        findClass(appUpdateInfo)
            .hook("updateAvailability", HookStage.BEFORE) { param ->
                param.setResult(1)
            }

        findClass(appUpdateInfo)
            .hook("isUpdateTypeAllowed", HookStage.BEFORE) { param ->
                param.setResult(false)
            }

        findClass(appUpgradeManager) // showDeprecatedVersionDialog()
            .hook("a", HookStage.BEFORE) { param ->
                param.setResult(null)
            }

        findClass(appUpdateZzm) // requestUpdateInfo()
            .hook("zza", HookStage.BEFORE) { param ->
                param.setResult(null)
            }

        Thread {
            fetchLatestVersionInfo()
        }.start()
    }

    private fun fetchLatestVersionInfo() {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(versionInfoEndpoint).build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonData = response.body?.string()
                if (jsonData != null) {
                    val json = JSONObject(jsonData)
                    versionCode = json.getInt("versionCode")
                    versionName = json.getString("versionName")
                    logd("Successfully fetched version info: $versionName ($versionCode)")
                    updateVersionInfo()
                }
            } else {
                Logger.e("Failed to fetch version info: ${response.message}")
            }
        } catch (e: Exception) {
            loge("Error fetching version info: ${e.message}")
            Logger.writeRaw(e.stackTraceToString())
        }
    }

    private fun updateVersionInfo() {
        if (versionName < GR.context.packageManager.getPackageInfo(
                GR.context.packageName,
                0
            ).versionName.toString()
        ) {
            findClass(appConfiguration).hookConstructor(HookStage.AFTER) { param ->
                setObjectField(param.thisObject(), "b", versionName)
                setObjectField(param.thisObject(), "c", versionCode)
                setObjectField(param.thisObject(), "z", "$versionName.$versionCode")
            }

            findClass(GR.userAgent).hookConstructor(HookStage.AFTER) { param ->
                param.thisObject().javaClass.declaredFields.forEach { field ->
                    field.isAccessible = true
                    val value = field.get(param.thisObject())
                    if (value is String && value.startsWith("grindr3/")) {
                        field.set(param.thisObject(), "grindr3/$versionName.$versionCode;$versionCode;")
                        return@forEach
                    }
                }
            }
        }
    }
}