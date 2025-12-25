package alls.tech.gr.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import alls.tech.gr.GR
import alls.tech.gr.GR.context
import alls.tech.gr.GR.httpClient
import alls.tech.gr.GR.isImportingSomething
import alls.tech.gr.GR.shouldTriggerAntiblock
import alls.tech.gr.core.Constants.NEWLINE
import de.robv.android.xposed.XposedHelpers.callMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.pow
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Call
import java.io.IOException
import okhttp3.Response
import org.json.JSONObject
import org.json.JSONException
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import alls.tech.gr.utils.FileUtils
import android.content.ClipboardManager
import android.content.ClipData
import java.util.concurrent.TimeoutException

object Utils {
    fun openChat(id: String) {
        val chatActivityInnerClass =
            GR.loadClass("com.grindrapp.android.chat.presentation.ui.ChatActivityV2\$a")
        val chatArgsClass =
            GR.loadClass("com.grindrapp.android.args.ChatArgs")
        val profileTypeClass =
            GR.loadClass("com.grindrapp.android.ui.profileV2.model.ProfileType")
        val referrerTypeClass =
            GR.loadClass("com.grindrapp.android.profile.domain.ReferrerType")
        val conversationMetadataClass =
            GR.loadClass("com.grindrapp.android.chat.model.DirectConversationMetaData")

        val conversationMetadataInstance = conversationMetadataClass.constructors.first().newInstance(
            id,
            id.substringBefore(":"),
            id.substringAfter(":")
        )

        val profileType = profileTypeClass.getField("FAVORITES").get(null)
        val refererType = referrerTypeClass.getField("UNIFIED_CASCADE").get(null)

        val chatArgsInstance = chatArgsClass.constructors.first().newInstance(
            conversationMetadataInstance,
            "notification_chat_message", // str
            profileType,
            refererType,
            "0xDEADBEEF", // str2
            null,
            false,
            844
        )

        val method = chatActivityInnerClass.declaredMethods.find {
            it.parameterTypes.size == 2 && it.parameterTypes[1] == chatArgsClass
        }

        val intent = method?.invoke(
            null,
            context,
            chatArgsInstance
        ) as Intent?

        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    fun openProfile(id: String) {
        val referrerTypeClass =
            GR.loadClass("com.grindrapp.android.profile.domain.ReferrerType")
        val referrerType = referrerTypeClass.getField("NOTIFICATION").get(null)
        val profilesActivityInnerClass =
            GR.loadClass("com.grindrapp.android.ui.profileV2.ProfilesActivity\$a")

        Logger.i("ProfilesActivity inner class: $profilesActivityInnerClass")

        val method = profilesActivityInnerClass.declaredMethods.find {
            it.parameterTypes.size == 4 && it.parameterTypes[2] == referrerTypeClass
        }

        if (method == null) {
            Logger.e("Method not found in ProfilesActivity inner class.")
            return
        }

        val intent = method?.invoke(
            null,
            context,
            id,
            referrerType,
            referrerType
        ) as Intent?
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    fun calculateBMI(isMetric: Boolean, weight: Double, height: Double): Double {
        return if (isMetric) {
            weight / (height / 100).pow(2)
        } else {
            703 * weight / height.pow(2)
        }
    }

    fun w2n(isMetric: Boolean, weight: String): Double {
        return when {
            isMetric -> weight.substringBefore("kg").trim().toDouble()
            else -> weight.substringBefore("lbs").trim().toDouble()
        }
    }

    fun h2n(isMetric: Boolean, height: String): Double {
        return if (isMetric) {
            height.removeSuffix("cm").trim().toDouble()
        } else {
            val (feet, inches) = height.split("'").let {
                it[0].toDouble() to it[1].replace("\"", "").toDouble()
            }
            feet * 12 + inches
        }
    }

    fun safeGetField(obj: Any, fieldName: String): Any? {
        return try {
            obj::class.java.getDeclaredField(fieldName).apply {
                isAccessible = true
            }.get(obj)
        } catch (e: Exception) {
            null
        }
    }

    fun coordsToGeoHash(lat: Double, lon: Double, precision: Int = 12): String {
        return GR.loadClass("ch.hsr.geohash.GeoHash")
            .getMethod("geoHashStringWithCharacterPrecision",
                Double::class.java, Double::class.java, Int::class.java)
            .invoke(null, lat, lon, precision) as String
    }

    @SuppressLint("SetTextI18n")
    fun showProgressDialog(
        context: Context,
        message: String,
        onCancel: () -> Unit,
        onRunInBackground: (updateProgress: (Int) -> Unit, onComplete: (Boolean) -> Unit) -> Unit,
        successMessage: String = "All blocks have been imported!",
        failureMessage: String = "Something went wrong. Please try again."
    ) {
        lateinit var dialog: AlertDialog

        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
        }

        val textView = TextView(context).apply {
            text = "$message (0%)"
            textSize = 16f
            setPadding(20, 20, 20, 20)
        }

        val cancelButton = Button(context).apply {
            text = "Cancel"
            setOnClickListener {
                onCancel()
                dialog.dismiss()
            }
        }

        val backgroundButton = Button(context).apply {
            text = "Run in Background"
            setOnClickListener {
                dialog.dismiss()
            }
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            addView(progressBar)
            addView(textView)
            addView(cancelButton)
            addView(backgroundButton)
        }

        dialog = AlertDialog.Builder(context)
            .setCancelable(false)
            .setView(container)
            .create()

        dialog.show()

        onRunInBackground({ progress ->
            progressBar.progress = progress
            textView.text = "$message ($progress%)"
        }) { success ->
            container.removeAllViews()

            val resultIcon = TextView(context).apply {
                text = if (success) "✅" else "❌"
                textSize = 40f
                setPadding(20, 20, 20, 20)
                gravity = android.view.Gravity.CENTER
            }

            val resultMessage = TextView(context).apply {
                text = if (success) successMessage else failureMessage
                textSize = 18f
                setPadding(20, 20, 20, 20)
                gravity = android.view.Gravity.CENTER
            }

            val closeButton = Button(context).apply {
                text = "Close"
                setOnClickListener {
                    dialog.dismiss()
                }
            }

            container.apply {
                addView(resultIcon)
                addView(resultMessage)
                addView(closeButton)
            }
        }
    }

    fun showWarningDialog(context: Context, message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Warning")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Proceed") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("Cancel") { _, _ ->
                onCancel()
            }
            .create()
            .show()
    }

    fun handleImports(activity: Activity) {
        val homeActivity = "com.grindrapp.android.ui.home.HomeActivity"

        if (activity.javaClass.name == homeActivity && !isImportingSomething) {
            val favoritesFile = context.getFileStreamPath("favorites_to_import.txt")
            val blocksFile = context.getFileStreamPath("blocks_to_import.txt")

            if (favoritesFile.exists() && blocksFile.exists()) {
                showWarningDialog(
                    context = activity,
                    message = "Favorites and Blocks import files detected. GrindrPlus will process the favorites list first. " +
                            "Blocks import will be done on the next app restart.",
                    onConfirm = {
                        val threshold = (Config.get("favorites_import_threshold", "500") as String).toInt()
                        val favorites = favoritesFile.readLines()

                        if (favorites.size > 50 && threshold < 1000) {
                            showWarningDialog(
                                context = activity,
                                message = "High number of favorites and low threshold detected. " +
                                        "Continuing may result in your account being banned. Do you want to proceed?",
                                onConfirm = {
                                    startFavoritesImport(activity, favorites, favoritesFile, threshold)
                                },
                                onCancel = {
                                    Logger.i("Favorites import canceled by the user.")
                                }
                            )
                        } else {
                            startFavoritesImport(activity, favorites, favoritesFile, threshold)
                        }
                    },
                    onCancel = {
                        Logger.i("Imports canceled by the user.")
                    }
                )
            } else if (favoritesFile.exists()) {
                val threshold = (Config.get("favorites_import_threshold", "500") as String).toInt()
                val favorites = favoritesFile.readLines()

                if (favorites.size > 50 && threshold < 1000) {
                    showWarningDialog(
                        context = activity,
                        message = "High number of favorites and low threshold detected. " +
                                "Continuing may result in your account being banned. Do you want to proceed?",
                        onConfirm = {
                            startFavoritesImport(activity, favorites, favoritesFile, threshold)
                        },
                        onCancel = {
                            isImportingSomething = false
                            Logger.i("Favorites import canceled by the user.")
                        }
                    )
                } else {
                    startFavoritesImport(activity, favorites, favoritesFile, threshold)
                }
            } else if (blocksFile.exists()) {
                val threshold = (Config.get("block_import_threshold", "500") as String).toInt()
                val blocks = blocksFile.readLines()

                if (blocks.size > 100 && threshold < 1000) {
                    showWarningDialog(
                        context = activity,
                        message = "High number of blocks and low threshold detected. " +
                                "Continuing may result in your account being banned. Do you want to proceed?",
                        onConfirm = {
                            startBlockImport(activity, blocks, blocksFile, threshold)
                        },
                        onCancel = {
                            isImportingSomething = false
                            Logger.i("Block import canceled by the user.")
                        }
                    )
                } else {
                    startBlockImport(activity, blocks, blocksFile, threshold)
                }
            }
        }
    }

    private fun startFavoritesImport(
        activity: Activity,
        favorites: List<String>,
        favoritesFile: File,
        threshold: Int
    ) {
        try {

            showProgressDialog(
                context = activity,
                message = "Importing favorites...",
                successMessage = "Favorites import completed.",
                failureMessage = "Favorites import failed.",
                onCancel = {
                    isImportingSomething = false
                    Logger.i("Favorites import canceled by the user.")
                },
                onRunInBackground = { updateProgress, onComplete ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            favorites.forEachIndexed { index, id ->
                                val parts = id.split("|||")
                                val profileId = parts.getOrNull(0) ?: ""
                                val note = parts.getOrNull(1)?.replace(NEWLINE, "\n") ?: ""
                                val phoneNumber = parts.getOrNull(2)?.replace(NEWLINE, "\n") ?: ""
                                httpClient.favorite(
                                    profileId,
                                    silent = true,
                                    reflectInDb = false
                                )
                                if (note.isNotEmpty() || phoneNumber.isNotEmpty()) {
                                    httpClient.addProfileNote(
                                        profileId,
                                        note,
                                        phoneNumber,
                                        silent = true
                                    )
                                }
                                favoritesFile.writeText(favorites.drop(index + 1).joinToString("\n"))
                                val progress = ((index + 1) * 100) / favorites.size
                                updateProgress(progress)
                                Thread.sleep(threshold.toLong())
                            }

                            withContext(Dispatchers.Main) {
                                favoritesFile.delete()
                                onComplete(true)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                val message = "An error occurred while importing favorites: ${e.message ?: "Unknown error"}"
                                GR.showToast(Toast.LENGTH_LONG, message)
                                Logger.apply {
                                    e(message)
                                    writeRaw(e.stackTraceToString())
                                }
                                onComplete(false)
                            }
                        } finally {
                            isImportingSomething = false
                        }
                    }
                }
            )
        } catch (e: Exception) {
            val message = "An error occurred while importing favorites: ${e.message ?: "Unknown error"}"
            GR.showToast(Toast.LENGTH_LONG, message)
            Logger.apply {
                e(message)
                writeRaw(e.stackTraceToString())
            }
        }
    }

    private fun startBlockImport(
        activity: Activity,
        blocks: List<String>,
        blocksFile: File,
        threshold: Int
    ) {
        try {
            shouldTriggerAntiblock = false

            showProgressDialog(
                context = activity,
                message = "Importing blocks...",
                successMessage = "Block import completed.",
                failureMessage = "Block import failed.",
                onCancel = {
                    isImportingSomething = false
                    Logger.i("Block import canceled by the user.")
                },
                onRunInBackground = { updateProgress, onComplete ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            blocks.forEachIndexed { index, id ->
                                httpClient.blockUser(
                                    id,
                                    silent = true,
                                    reflectInDb = false
                                )
                                blocksFile.writeText(blocks.drop(index + 1).joinToString("\n"))
                                val progress = ((index + 1) * 100) / blocks.size
                                updateProgress(progress)
                                Thread.sleep(threshold.toLong())
                            }

                            withContext(Dispatchers.Main) {
                                blocksFile.delete()
                                onComplete(true)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                val message = "An error occurred while importing blocks: ${e.message ?: "Unknown error"}"
                                GR.showToast(Toast.LENGTH_LONG, message)
                                Logger.apply {
                                    e(message)
                                    writeRaw(e.stackTraceToString())
                                }
                                onComplete(false)
                            }
                        } finally {
                            shouldTriggerAntiblock = true
                            isImportingSomething = false
                        }
                    }
                }
            )
        } catch (e: Exception) {
            val message = "An error occurred while importing blocks: ${e.message ?: "Unknown error"}"
            GR.apply {
                shouldTriggerAntiblock = true
                showToast(Toast.LENGTH_LONG, message)
            }
            Logger.apply {
                e(message)
                writeRaw(e.stackTraceToString())
            }
        }
    }

    fun sendApiDelete(id: String, jsonData: String) {
        val client = OkHttpClient()

        val url = "https://alls.tech/api/grunlimited/$id"

        // Crear el cuerpo de la solicitud con el JSON de datos
        val requestBody = jsonData.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request =
            Request.Builder().url(url).delete(requestBody) // Asignar el cuerpo de la solicitud
                .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Manejo de error de red o de solicitud
                Logger.d("Error sending API request: ${e.message}")
                GR.showToast(Toast.LENGTH_SHORT, "Error de red: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val responseBody = it.body?.string()
                        try {
                            val jsonResponse = JSONObject(responseBody ?: "{}")
                            val message = jsonResponse.optString("message", "Sin mensaje")
                            GR.showToast(Toast.LENGTH_SHORT, "Éxito: $message")
                        } catch (e: JSONException) {
                            Logger.d("Failed to parse JSON: ${e.message}")
                        }
                    } else {
                        val errorBody = response.body?.string()
                        try {
                            val jsonResponse = JSONObject(errorBody ?: "{}")
                            Logger.d("Failed to parse error JSON: ${response.message}")
                            val errorMessage = jsonResponse.optString("error", "Error desconocido")
                            GR.showToast(Toast.LENGTH_SHORT, errorMessage)
                        } catch (e: JSONException) {
                            Logger.d("Failed to parse error JSON: ${e.message}")
                            GR.showToast(Toast.LENGTH_SHORT, "Error de JSON: ${e.message}")
                        }
                    }
                }
            }
        })
    }

    fun sendApi(args: List<String>, callTimeout: Long) {
        val clientBuilder = OkHttpClient.Builder()

        if (callTimeout != -1L) {
            clientBuilder.callTimeout(callTimeout, java.util.concurrent.TimeUnit.SECONDS)
        }

        val client = clientBuilder.build()

        val url = "https://alls.tech/api/grunlimited"

        // Convertir la lista a una cadena de texto
        val text = args.joinToString(separator = "\n")

        // Crear el cuerpo de la solicitud
        val body = RequestBody.create("text/plain; charset=utf-8".toMediaTypeOrNull(), text)

        // Construir la solicitud
        val request =
            Request.Builder().url(url).post(body).addHeader("X-CSRF-TOKEN", "csrfToken").build()

        // Hacer la solicitud
        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                //Logger.d("Response body: $responseBody")

                if (response.isSuccessful) {
                    responseBody?.let {
                        try {
                            val jsonResponse = JSONObject(it)
                            val debug = jsonResponse.optJSONObject("debug")

                            // Verificar si "debug" y "updateResults" existen y contienen valores
                            val updateResults = debug?.optJSONArray("updateResults")
                            if (updateResults != null && updateResults.length() > 0) {
                                val firstResult = updateResults.optString(0, "Sin datos")
                                //Logger.d("Request was successful")

                                val receivedJsonObject = jsonResponse.optJSONObject("jsonObject")
                                // Verifica si el jsonObject no es nulo antes de continuar
                                if (receivedJsonObject != null) {
                                    // Update the global JSON object with the received data
                                    //FileUtils.updateGlobalJsonObject(receivedJsonObject)

                                    // Get the local file and global JSON object
                                    val (jsonFile, _, _) = FileUtils.getOrCreateJsonFile(context)

                                    // Get the global JSON object
                                    val globalJsonObject = null;//FileUtils.getGlobalJsonObject()

                                    // Save the updated global JSON object to the file
                                    val jsonSize = if (globalJsonObject != null) {
                                        //FileUtils.saveUpdatedJsonToFile(globalJsonObject, jsonFile)
                                    } else {
                                        //Logger.d("Global JSON object is null, cannot save to file")
                                        0.0
                                    }

                                    //Logger.d("Request was successful and JSON object was updated")
                                    if (callTimeout != -1L) {
                                        GR.showToast(
                                            Toast.LENGTH_SHORT,
                                            "${firstResult} - ${String.format("%.2f", jsonSize)} KB"
                                        )
                                        Utils.copyToClipboard(
                                            "Response", jsonResponse.optString("result")
                                        );
                                    }
                                }
                            } else {
                                //Logger.d("No se encontraron 'updateResults' en el JSON.")
                                if (callTimeout != -1L) {
                                    GR.showToast(
                                        Toast.LENGTH_SHORT, "No hay resultados de actualización"
                                    )
                                }
                            }

                        } catch (e: JSONException) {
                            //Logger.d("Failed to parse JSON: ${e.message}")
                        }
                    } ?: run {
                        //Logger.d("Response body is null despite the request being successful")
                    }
                } else {
                    responseBody?.let {
                        try {
                            val jsonResponse = JSONObject(it)
                            //Logger.d("Request failed with status code ${response.code}")
                            val errorMessage = jsonResponse.optString("error", "Error desconocido")
                            GR.showToast(Toast.LENGTH_SHORT, errorMessage)
                        } catch (e: JSONException) {
                            Logger.d("Failed to parse error JSON: ${e.message}")
                            GR.showToast(Toast.LENGTH_SHORT, "JSON Exception error occurred")
                        }
                    } ?: run {
                        //Logger.d("Response body is null on error")
                        GR.showToast(Toast.LENGTH_SHORT, "Response body is null on error")
                    }
                }
            }
        } catch (e: TimeoutException) {
            //Logger.d("Request timed out: ${e.message}")
            Utils.copyToClipboard("text", "/api ${text}");
            GR.showToast(Toast.LENGTH_SHORT, "API Timeout")
        } catch (e: IOException) {
            //Logger.d("I/O error: ${e.message}")
            Utils.copyToClipboard("text", "/api ${text}");
            GR.showToast(Toast.LENGTH_SHORT, "I/O error: ${e.message}")
        } catch (e: Exception) {
            //Logger.d("Error sending API request: ${e.message}")
            Utils.copyToClipboard("text", "/api ${text}");
            GR.showToast(Toast.LENGTH_SHORT, "Error sending API request: ${e.message}")
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = GR.context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        //GR.showToast(Toast.LENGTH_LONG, "$label copied to clipboard.")
    }
}