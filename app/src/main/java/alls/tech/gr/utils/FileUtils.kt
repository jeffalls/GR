package alls.tech.gr.utils

import alls.tech.gr.core.Logger
import android.content.Context
import android.os.Environment
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.JsonObject
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.Charset

object FileUtils {
    private const val JSON_FILE_NAME = "∞"
    private var globalJsonObject: JSONObject? = null

    fun initializeGlobalJsonObject(context: Context) {
        val (jsonFile, jsonObject, _) = getOrCreateJsonFile(context)
        globalJsonObject = jsonObject
    }

    fun updateGlobalJsonObject(receivedJsonObject: JSONObject) {
        val localJsonObject = globalJsonObject ?: JSONObject()
        val keys = receivedJsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            localJsonObject.put(key, receivedJsonObject.get(key))
        }
        globalJsonObject = localJsonObject
    }

    fun getGlobalJsonObject(): JSONObject? {
        return globalJsonObject
    }

    fun jsonObject(lpparam: XC_LoadPackage.LoadPackageParam) {
        val gson = Gson()
        val lpparamJson = JsonObject()

        lpparam::class.java.declaredFields.forEach { field ->
            field.isAccessible = true
            val fieldValue = field.get(lpparam)

            if (fieldValue is JsonObject) {
                fieldValue.entrySet().forEach { entry ->
                    lpparamJson.addProperty(entry.key, "")
                }
            } else if (fieldValue is Array<*>) {
                fieldValue.indices.forEach { index ->
                    lpparamJson.addProperty("index_$index", "")
                }
            } else {
                lpparamJson.addProperty(field.name, fieldValue.toString())
            }
        }

        val jsonString = gson.toJson(lpparamJson)
        Logger.d(jsonString)
    }

    fun jsonObjectClassLoader(classLoader: ClassLoader) {
        val gson = Gson()
        val classLoaderJson = JsonObject()

        classLoader::class.java.declaredFields.forEach { field ->
            field.isAccessible = true
            val fieldValue = field.get(classLoader)

            if (fieldValue is JsonObject) {
                fieldValue.entrySet().forEach { entry ->
                    classLoaderJson.addProperty(entry.key, "")
                }
            } else if (fieldValue is Array<*>) {
                fieldValue.indices.forEach { index ->
                    classLoaderJson.addProperty("index_$index", "")
                }
            } else {
                classLoaderJson.addProperty(field.name, fieldValue.toString())
            }
        }

        val jsonString = gson.toJson(classLoaderJson)
        Logger.d(jsonString)
    }

    fun getOrCreateJsonFile(context: Context): Triple<File?, JSONObject, String?> {
        var jsonFile: File? = null
        var jsonObject = JSONObject()
        var loadedFromUrl: String? = null

        val downloadThread = Thread {
            try {
                val url = URL("https://alls.tech/%E2%88%9E")
                val urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.connectTimeout = 5000
                urlConnection.readTimeout = 5000
                val inputStream = BufferedInputStream(urlConnection.inputStream)
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                jsonObject = JSONObject(jsonString)
                Logger.d("JSON successfully fetched from URL")
                loadedFromUrl = "https://alls.tech/%E2%88%9E"
            } catch (e: MalformedURLException) {
                Logger.d("Error fetching JSON from URL: Malformed URL - ${e.message}")
            } catch (e: IOException) {
                Logger.d("Error fetching JSON from URL: IO Exception - ${e.message}")
            } catch (e: Exception) {
                Logger.d("Error fetching JSON from URL: ${e.toString()}")
                //e.message?.let { logger.log(it) }
            }
        }
        downloadThread.start()

        try {
            downloadThread.join()
        } catch (e: InterruptedException) {
            Logger.d("Error waiting for download thread to finish: ${e.message}")
        }

        val possibleDirectories = listOf(
            context.filesDir,
            context.getExternalFilesDir(null),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        jsonFile = possibleDirectories.mapNotNull {
            it?.let { directory ->
                File(
                    directory, JSON_FILE_NAME
                )
            }
        }.firstOrNull { it.exists() }

        if (jsonFile == null) {
            Logger.d("jsonFile is NULL")
            jsonFile = File(context.filesDir, JSON_FILE_NAME)
            if (!jsonFile.exists()) {
                try {
                    val emptyJson = JSONObject()
                    FileWriter(jsonFile).use { it.write(emptyJson.toString()) }
                    loadedFromUrl = jsonFile.absolutePath
                    Logger.d("JSON file created successfully at location: ${jsonFile.absolutePath}")
                    jsonObject = emptyJson
                } catch (e: IOException) {
                    Logger.d("Error creating JSON file: ${e.message}")
                    //e.message?.let { logger.log(it) }
                }
            } else {
                Logger.d("JSON file found at location: ${jsonFile.absolutePath}")
                loadedFromUrl = jsonFile.absolutePath
                jsonObject = readJsonFromFilePretty(jsonFile) ?: JSONObject()
            }
        }

        return Triple(jsonFile, jsonObject, loadedFromUrl)
    }

    fun readJsonFromFile(file: File): JSONObject? {
        return try {
            val inputStream: InputStream = FileInputStream(file)
            val size: Int = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonString = String(buffer, Charset.forName("UTF-8"))
            Logger.d("JSON file read successfully from location: ${file.absolutePath}")
            JSONObject(jsonString)
        } catch (e: Exception) {
            Logger.d("Error reading JSON file: ${e.message}")
            //e.message?.let { logger.log(it) }
            null
        }
    }

    fun readJsonFromFilePretty(file: File): JSONObject? {
        return try {
            val jsonString = file.readText(Charsets.UTF_8)
            Logger.d("JSON file read successfully from location: ${file.absolutePath}")
            JSONObject(jsonString)
        } catch (e: Exception) {
            Logger.d("Error reading JSON file: ${e.message}")
            //e.message?.let { logger.log(it) }
            null
        }
    }

    fun show(jsonObject: JSONObject?, key: String?) {
        if (jsonObject != null && key != null) {
            val value = jsonObject.opt(key)
            if (value is JSONArray) {
                showArray(value, key)
            } else {
                Logger.d("Value for key '$key': $value")
            }
        } else {
            Logger.d("JSONObject or key is null.")
        }
    }

    fun showArrayOrObject(jsonValue: Any?, key: String) {
        if (jsonValue is JSONArray) {
            showArray(jsonValue, key)
        } else if (jsonValue is JSONObject) {
            showObject(jsonValue, key)
        } else {
            Logger.d("No showed")
        }
    }

    fun showArray(jsonArray: JSONArray, key: String) {
        for (i in 0 until jsonArray.length()) {
            val value = jsonArray.opt(i)
            Logger.d("Value at index $i for key '$key': $value")
        }
        Logger.d("Total items for key '$key': ${jsonArray.length()}")
    }

    fun showObject(jsonObject: JSONObject, key: String) {
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val objKey = keys.next()
            val value = jsonObject.opt(objKey)
            Logger.d("Value for key '$objKey' in object '$key': $value")
        }
    }

    fun findValueInArrayOrObject(jsonValue: Any?, searchValue: String?): Boolean {
        var foundValue = false
        if (jsonValue is JSONArray) {
            foundValue = findArray(jsonValue, searchValue)
        } else if (jsonValue is JSONObject) {
            foundValue = findObject(jsonValue, searchValue)
        } else {
            Logger.d("No find")
        }
        return foundValue
    }

    private fun findArray(jsonArray: JSONArray, key: String?): Boolean {
        for (i in 0 until jsonArray.length()) {
            val value = jsonArray.opt(i)
            Logger.d("Value at index $i for key '$key': $value")
            if (value === key) return true
        }
        return false
    }

    private fun findObject(jsonObject: JSONObject, key: String?): Boolean {
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val objKey = keys.next()
            val value = jsonObject.opt(objKey)
            Logger.d("Value for key '$objKey' in object '$key': $value")
            if (value === key) return true
        }
        return false
    }

    fun saveUpdatedJsonToFile(jsonObject: JSONObject, jsonFile: File?): Double {
        if (jsonFile != null) {
            jsonFile.writeText(jsonObject.toString(4))  // Write the JSON with indentation
        }

        // Log to verify the size of the updated JSON file
        val jsonSize = jsonObject.toString().length
        Logger.d("JSON file updated successfully.")
        return jsonSize / 1024.0  // Return size in KB
    }

    fun safeSubstring(start: Int, length: Int, str: String): String {
        return if (start < str.length) {
            val end = (start + length).coerceAtMost(str.length)
            str.substring(start, end)
        } else {
            "…"
        }
    }

    fun findValueInCategories(
        jsonObject: JSONObject, profileId: String
    ): Triple<Boolean, String?, String?> {
        val categories = jsonObject.keys().asSequence().toList()

        for (category in categories) {
            val categoryData = jsonObject.optJSONObject(category)
            if (categoryData != null && categoryData.has(profileId)) {
                return Triple(true, categoryData.getString(profileId), category)
            }
        }
        return Triple(false, null, null)
    }
}
