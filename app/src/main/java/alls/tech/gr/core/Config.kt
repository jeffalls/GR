package alls.tech.gr.core

import android.content.Context
import alls.tech.gr.GR
import alls.tech.gr.manager.utils.AppCloneUtils
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import alls.tech.gr.bridge.BridgeService
import kotlin.random.Random
import timber.log.Timber

object Config {
    private var localConfig = JSONObject()
    private var currentPackageName = Constants.GRINDR_PACKAGE_NAME
    private val GLOBAL_SETTINGS = listOf("first_launch", "analytics", "discreet_icon", "material_you", "debug_mode", "disable_permission_checks", "custom_manifest", "maps_api_key")

    fun initialize(context: Context, packageName: String? = null) {
        if (packageName != null) {
            Logger.d("Initializing config for package: $packageName", LogSource.MANAGER)
        }

        localConfig = readRemoteConfig()

        if (packageName != null) {
            currentPackageName = packageName
        }

        matrixFile = File(context.filesDir, "∞.mtrx")
        if (packageName != null) {
            Logger.d("Initializing config for package: $packageName", LogSource.MANAGER)
        }
        if (!matrixFile.exists()) {
            try {
                matrixFile.createNewFile()
                val initialMatrix = arrayOf(
                    intArrayOf(1, 2, 3, 4, 5),
                    intArrayOf(6, 7, 8, 9, 10),
                    intArrayOf(11, 12, 13, 14, 15)
                )
                writeMatrix(initialMatrix)
            } catch (e: IOException) {
                Logger.d( "Failed to create matrix file")
            }
        }

        matrix = readMatrix(matrixFile)

        migrateToMultiCloneFormat()
    }

    private fun isGlobalSetting(name: String): Boolean {
        return name in GLOBAL_SETTINGS
    }

    private fun migrateToMultiCloneFormat() {
        if (!localConfig.has("clones")) {
            Logger.d("Migrating to multi-clone format", LogSource.MANAGER)
            val cloneSettings = JSONObject()

            if (localConfig.has("hooks")) {
                val defaultPackageConfig = JSONObject()
                defaultPackageConfig.put("hooks", localConfig.get("hooks"))
                cloneSettings.put(Constants.GRINDR_PACKAGE_NAME, defaultPackageConfig)

                val keysToMove = mutableListOf<String>()
                val keys = localConfig.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key != "hooks" && !isGlobalSetting(key)) {
                        defaultPackageConfig.put(key, localConfig.get(key))
                        keysToMove.add(key)
                    }
                }
                keysToMove.forEach { localConfig.remove(it) }
            } else {
                cloneSettings.put(Constants.GRINDR_PACKAGE_NAME, JSONObject().put("hooks", JSONObject()))
            }

            localConfig.put("clones", cloneSettings)
            writeRemoteConfig(localConfig)
        }

        ensurePackageExists(currentPackageName)
    }

    fun setCurrentPackage(packageName: String) {
        Logger.d("Setting current package to $packageName", LogSource.MANAGER)
        currentPackageName = packageName
        ensurePackageExists(packageName)
    }

    fun getCurrentPackage(): String {
        return currentPackageName
    }

    private fun ensurePackageExists(packageName: String) {
        Logger.d("Ensuring package $packageName exists in config", LogSource.MANAGER)
        val clones = localConfig.optJSONObject("clones") ?: JSONObject().also {
            localConfig.put("clones", it)
        }

        if (!clones.has(packageName)) {
            clones.put(packageName, JSONObject().put("hooks", JSONObject()))
            writeRemoteConfig(localConfig)
        }
    }

    fun getAvailablePackages(context: Context): List<String> {
        Logger.d("Getting available packages", LogSource.MANAGER)
        val installedClones = listOf(Constants.GRINDR_PACKAGE_NAME) + AppCloneUtils.getExistingClones(context)
        val clones = localConfig.optJSONObject("clones") ?: return listOf(Constants.GRINDR_PACKAGE_NAME)

        return installedClones.filter { pkg ->
            clones.has(pkg)
        }
    }

    fun readRemoteConfig(): JSONObject {
        return try {
            GR.bridgeClient.getConfig()
        } catch (e: Exception) {
            Logger.e("Failed to read config file: ${e.message}", LogSource.MANAGER)
            Logger.writeRaw(e.stackTraceToString())
            JSONObject().put("clones", JSONObject().put(
                Constants.GRINDR_PACKAGE_NAME,
                JSONObject().put("hooks", JSONObject()))
            )
        }
    }

    fun writeRemoteConfig(json: JSONObject) {
        try {
            GR.bridgeClient.setConfig(json)
        } catch (e: IOException) {
            Logger.e("Failed to write config file: ${e.message}", LogSource.MANAGER)
            Logger.writeRaw(e.stackTraceToString())
        }
    }

    private fun getCurrentPackageConfig(): JSONObject {
        val clones = localConfig.optJSONObject("clones")
            ?: JSONObject().also { localConfig.put("clones", it) }

        return clones.optJSONObject(currentPackageName)
            ?: JSONObject().also { clones.put(currentPackageName, it) }
    }

    fun put(name: String, value: Any) {
        Logger.d("Setting $name to $value", LogSource.MANAGER)
        if (isGlobalSetting(name)) {
            localConfig.put(name, value)
        } else {
            val packageConfig = getCurrentPackageConfig()
            packageConfig.put(name, value)
        }

        writeRemoteConfig(localConfig)
    }

    fun get(name: String, default: Any, autoPut: Boolean = false): Any {
        val rawValue = if (isGlobalSetting(name)) {
            localConfig.opt(name)
        } else {
            val packageConfig = getCurrentPackageConfig()
            packageConfig.opt(name)
        }

        if (rawValue == null) {
            if (autoPut) put(name, default)
            return default
        }

        return when (default) {
            is Number -> {
                if (rawValue is String) {
                    try {
                        rawValue.toInt()
                    } catch (_: NumberFormatException) {
                        try {
                            rawValue.toDouble()
                        } catch (_: NumberFormatException) {
                            default
                        }
                    }
                } else {
                    rawValue as? Number ?: default
                }
            }
            else -> rawValue
        }
    }

    fun setHookEnabled(hookName: String, enabled: Boolean) {
        Logger.d("Setting hook $hookName to $enabled", LogSource.MANAGER)
        val packageConfig = getCurrentPackageConfig()
        val hooks = packageConfig.optJSONObject("hooks")
            ?: JSONObject().also { packageConfig.put("hooks", it) }

        hooks.optJSONObject(hookName)?.put("enabled", enabled)
        writeRemoteConfig(localConfig)
    }

    fun isHookEnabled(hookName: String): Boolean {
        Logger.d("Checking if hook $hookName is enabled", LogSource.MANAGER)
        val packageConfig = getCurrentPackageConfig()
        val hooks = packageConfig.optJSONObject("hooks") ?: return false
        return hooks.optJSONObject(hookName)?.getBoolean("enabled") == true
    }

    fun setTaskEnabled(taskId: String, enabled: Boolean) {
        Logger.d("Setting task $taskId to $enabled", LogSource.MANAGER)
        val packageConfig = getCurrentPackageConfig()
        val tasks = packageConfig.optJSONObject("tasks")
            ?: JSONObject().also { packageConfig.put("tasks", it) }

        tasks.optJSONObject(taskId)?.put("enabled", enabled)
        writeRemoteConfig(localConfig)
    }

    fun isTaskEnabled(taskId: String): Boolean {
        Logger.d("Checking if task $taskId is enabled", LogSource.MANAGER)
        val packageConfig = getCurrentPackageConfig()
        val tasks = packageConfig.optJSONObject("tasks") ?: return false
        return tasks.optJSONObject(taskId)?.getBoolean("enabled") == true
    }

    fun getTasksSettings(): Map<String, Pair<String, Boolean>> {
        Logger.d("Getting tasks settings", LogSource.MANAGER)
        val packageConfig = getCurrentPackageConfig()
        val tasks = packageConfig.optJSONObject("tasks") ?: return emptyMap()
        val map = mutableMapOf<String, Pair<String, Boolean>>()

        val keys = tasks.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = tasks.getJSONObject(key)
            map[key] = Pair(obj.getString("description"), obj.getBoolean("enabled"))
        }

        return map
    }

    fun initTaskSettings(taskId: String, description: String, state: Boolean) {
        Logger.d("Initializing task settings for $taskId", LogSource.MANAGER)
        val packageConfig = getCurrentPackageConfig()
        val tasks = packageConfig.optJSONObject("tasks")
            ?: JSONObject().also { packageConfig.put("tasks", it) }

        if (tasks.optJSONObject(taskId) == null) {
            tasks.put(taskId, JSONObject().apply {
                put("description", description)
                put("enabled", state)
            })

            writeRemoteConfig(localConfig)
        }
    }

    fun initHookSettings(name: String, description: String, state: Boolean) {
        Logger.d("Initializing hook settings for $name", LogSource.MANAGER)
        val packageConfig = getCurrentPackageConfig()
        val hooks = packageConfig.optJSONObject("hooks")
            ?: JSONObject().also { packageConfig.put("hooks", it) }

        if (hooks.optJSONObject(name) == null) {
            hooks.put(name, JSONObject().apply {
                put("description", description)
                put("enabled", state)
            })

            writeRemoteConfig(localConfig)
        }
    }

    fun getHooksSettings(): Map<String, Pair<String, Boolean>> {
        Logger.d("Getting hooks settings", LogSource.MANAGER)
        val packageConfig = getCurrentPackageConfig()
        val hooks = packageConfig.optJSONObject("hooks") ?: return emptyMap()
        val map = mutableMapOf<String, Pair<String, Boolean>>()

        val keys = hooks.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = hooks.getJSONObject(key)
            map[key] = Pair(obj.getString("description"), obj.getBoolean("enabled"))
        }

        return map
    }

    fun writeDeviceIdToFile(context: Context, deviceId: String) {
        val amplitudeDir =
            File(context.filesDir.parentFile, "app_amplitude-kotlin-\$default_instance")
        val identityFile = File(amplitudeDir, "amplitude-identity-\$default_instance.properties")
        val properties = Properties()
        if (identityFile.exists()) {
            identityFile.inputStream().use { properties.load(it) }
            val currentDeviceId = properties.getProperty("device_id")
            if (currentDeviceId != deviceId) {
                properties.setProperty("device_id", deviceId)
                FileOutputStream(identityFile).use { properties.store(it, null) }
                Logger.d("Set Device ID: $deviceId / $currentDeviceId")
                GR.bridgeClient.sendNotificationWithMultipleActions(
                    "Device ID",
                    "$deviceId / $currentDeviceId",
                    10000000 + (1 % 10000000).toInt(),
                    listOf("Copy ID"),
                    listOf("COPY"),
                    listOf("$deviceId / $currentDeviceId"),
                    BridgeService.CHANNEL_BLOCKS,
                    "ID Notifications",
                    "Notifications when change ID"
                )
            } else {
            }
        } else {
            Logger.d("identityFile no exist.")
        }
    }

    fun genRandom(init: Int, end: Int): String {
        val longitud = (init..end).random()
        return (1..longitud).joinToString("") { Random.nextInt(0, 10).toString() }
    }

    private lateinit var matrixFile: File
    private lateinit var matrix: Array<IntArray>

    fun writeMatrix(matrix: Array<IntArray>) {
        try {
            matrixFile.printWriter().use { writer ->
                matrix.forEach { row ->
                    writer.println(row.joinToString(","))
                }
            }
        } catch (e: IOException) {
            Timber.tag("∞").e(e, "Failed to write matrix file")
        }
    }

    fun readMatrix(file: File): Array<IntArray> {
        return try {
            val matrix = mutableListOf<IntArray>()
            file.forEachLine { line ->
                val row = line.split(",").map { it.trim().toInt() }.toIntArray()
                matrix.add(row)
            }
            matrix.toTypedArray()
        } catch (e: Exception) {
            Timber.tag("∞").e(e, "Error reading matrix file")
            emptyArray()
        }
    }
}