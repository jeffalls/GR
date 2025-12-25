package alls.tech.gr.hooks

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.widget.TextView
import android.widget.Toast
import alls.tech.gr.GR
import alls.tech.gr.core.Config
import alls.tech.gr.core.Logger
import alls.tech.gr.GR.context
import alls.tech.gr.core.Config.readMatrix
import alls.tech.gr.core.Utils
import alls.tech.gr.core.Utils.calculateBMI
import alls.tech.gr.core.Utils.h2n
import alls.tech.gr.core.Utils.w2n
import alls.tech.gr.core.logw
import alls.tech.gr.ui.Utils.copyToClipboard
import alls.tech.gr.ui.Utils.formatEpochSeconds
import alls.tech.gr.utils.FileUtils
import alls.tech.gr.utils.FileUtils.safeSubstring
import alls.tech.gr.utils.Hook
import alls.tech.gr.utils.HookStage
import alls.tech.gr.utils.hook
import alls.tech.gr.utils.hookConstructor
import android.os.Build
import android.view.ViewGroup
import android.widget.ScrollView
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import java.util.ArrayList
import kotlin.math.roundToInt
import org.json.JSONObject
import java.io.File
import alls.tech.gr.core.Utils.openProfile
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat

class ProfileDetails(private val jsonObject: JSONObject) : Hook(
    "Profile details", "Add extra fields and details to profiles"
) {
    private var boostedProfilesList = emptyList<String>()
    private val blockedProfilesObserver = "Hm.f" // search for 'Intrinsics.checkNotNullParameter(dataList, "dataList");' - typically the last match
    private val profileViewHolder = "bl.u\$c" // search for 'Intrinsics.checkNotNullParameter(individualUnblockActivityViewModel, "individualUnblockActivityViewModel");'

    private val distanceUtils = "com.grindrapp.android.utils.DistanceUtils"
    private val profileBarView = "com.grindrapp.android.ui.profileV2.ProfileBarView"
    private val profileViewState = "com.grindrapp.android.ui.profileV2.model.ProfileViewState"
    private val serverDrivenCascadeCachedState =
        "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCacheState"
    private val serverDrivenCascadeCachedProfile =
        "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCachedProfile"

    @SuppressLint("DefaultLocale")
    override fun init() {
        val matrixFile = File(context.filesDir, "∞.mtrx")
        val matrix = readMatrix(matrixFile)

        var currentRow = 0
        var currentCol = 0

        findClass(serverDrivenCascadeCachedState).hook("getItems", HookStage.AFTER) { param ->
            (param.getResult() as List<*>)
                .filter { (it?.javaClass?.name ?: "") == serverDrivenCascadeCachedProfile }
                .forEach {
                    if (getObjectField(it, "isBoosting") as Boolean) {
                        boostedProfilesList += callMethod(it, "getProfileId") as String
                    }
                }
        }

        findClass(blockedProfilesObserver).hook("onChanged", HookStage.AFTER) { param ->
            // recently got merged into a case statement, so filter for the right argument type
            if ((getObjectField(param.thisObject(), "a") as Int) != 0) return@hook

            // what is the expected class?It is Object in the decompiled source
            val obj = getObjectField(param.thisObject(), "b")
            val profileList = getObjectField(obj, "o") as ArrayList<*>

            for (profile in profileList) {
                val profileId = callMethod(profile, "getProfileId") as String
                val displayName =
                    (callMethod(profile, "getDisplayName") as? String)
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { "$it ($profileId)" } ?: profileId
                setObjectField(profile, "displayName", displayName)
            }
        }

        findClass(profileViewHolder).hookConstructor(HookStage.AFTER) { param ->
            val textView =
                getObjectField(param.thisObject(), "a") as TextView

            textView.setOnLongClickListener {
                val text = textView.text.toString()
                val profileId = if ("(" in text && ")" in text)
                    text.substringAfter("(").substringBefore(")")
                else text

                copyToClipboard("Profile ID", profileId)
                GR.showToast(Toast.LENGTH_LONG, "Profile ID: $profileId")
                true
            }
        }

        findClass(profileBarView).hook("setProfile", HookStage.BEFORE) { param ->
            val profileId = getObjectField(param.arg(0), "profileId") as String
            copyToClipboard(
                "for-open", "/open $profileId"
            );
            val accountCreationTime =
                formatEpochSeconds(GR.spline.invert(profileId.toDouble()).toLong())
            val distance = callMethod(param.arg(0), "getDistance") ?: "Unknown (hidden)"
            setObjectField(param.arg(0), "distance", distance)

            if (profileId in boostedProfilesList) {
                val lastSeen = callMethod(param.arg(0), "getLastSeenText")
                setObjectField(param.arg(0), "lastSeenText", "$lastSeen (Boosting)")
            }

            for (row in matrix.indices) {
                for (col in matrix[row].indices) {
                    if (matrix[row][col].toString() == profileId) {
                        currentRow = row
                        currentCol = col
                        break
                    }
                }
            }

            var (foundInfo, foundInfoData, category) = FileUtils.findValueInCategories(
                jsonObject, profileId
            )

            var name = callMethod(param.arg(0), "getDisplayName")

            var aboutMe = callMethod(param.arg(0), "getAboutMe")

            foundInfoData = if (foundInfo) foundInfoData else " … "
            val displayName = "${
                if (foundInfoData is String) "$profileId|${
                    safeSubstring(
                        0, 7, category.toString()
                    )
                }|${safeSubstring(0, 30, foundInfoData.toString())}" else " … "
            }|${name}"

            // val displayName = callMethod(param.arg(0), "getDisplayName") ?: profileId
            setObjectField(param.arg(0), "displayName", displayName)

            val viewBinding = getObjectField(param.thisObject(), "c")
            val displayNameTextView = getObjectField(viewBinding, "c") as TextView

            displayNameTextView.setOnLongClickListener {
                //GR.showToast(Toast.LENGTH_LONG, "Profile ID: $profileId")
                //copyToClipboard("Profile ID", profileId)
                copyToClipboard(
                    "API ",
                    "/api {\"id\":\"$profileId\",\"d\":\"$foundInfoData\",\"e\":\" … \"}"
                )
                true
            }

            displayNameTextView.setOnClickListener {
                val foundDataRegex =
                    "(wa\\d*|ig\\d*|x\\d*|fb\\d*|map\\d*|add\\d*):([^\\s]+)".toRegex()

                val extractedData = foundInfoData?.let { it1 ->
                    foundDataRegex.findAll(it1).map { matchResult ->
                        matchResult.groupValues[1] to matchResult.groupValues[2]
                    }.toList()
                }

                val properties =
                    mapOf(
                        profileId to foundInfoData,
                        "category" to category,
                        "name" to name,
                        "aboutMe" to aboutMe,
                        "Estimated creation" to accountCreationTime,
                        "Profile ID" to profileId,
                        "Approximate distance" to
                                Utils.safeGetField(param.arg(0), "approximateDistance") as? Boolean,
                        "Favorite" to
                                Utils.safeGetField(param.arg(0), "isFavorite") as? Boolean,
                        "From viewed me" to
                                Utils.safeGetField(param.arg(0), "isFromViewedMe") as? Boolean,
                        "JWT boosting" to
                                Utils.safeGetField(param.arg(0), "isJwtBoosting") as? Boolean,
                        "New" to Utils.safeGetField(param.arg(0), "isNew") as? Boolean,
                        "Teleporting" to
                                Utils.safeGetField(param.arg(0), "isTeleporting") as? Boolean,
                        "Online now" to
                                Utils.safeGetField(param.arg(0), "onlineNow") as? Boolean,
                        "Is roaming" to
                                Utils.safeGetField(param.arg(0), "isRoaming") as? Boolean,
                        "Found via roam" to
                                Utils.safeGetField(param.arg(0), "foundViaRoam") as? Boolean,
                        "Is top pick" to
                                Utils.safeGetField(param.arg(0), "isTopPick") as? Boolean,
                        "Is visiting" to
                                Utils.safeGetField(param.arg(0), "isVisiting") as? Boolean,
                        "Is distance approximate" to
                                Utils.safeGetField(param.arg(0), "approximateDistance") as? Boolean,
                    )
                        .filterValues { it != null }.toMutableMap()

                extractedData?.forEach { (key, value) ->
                    properties[key] = value
                }

                val propertiesMessage =
                    properties.map { (key, value) -> "• $key: $value" }.joinToString("\n")

                val scrollableContent = ScrollView(it.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    addView(TextView(it.context).apply {
                        text = propertiesMessage
                        textSize = 16f
                        setPadding(20, 20, 20, 20)
                    })
                }

                val detailsText = properties.map { (key, value) -> "• $key: $value" }.joinToString("\n")

                val dialog = AlertDialog.Builder(it.context).setTitle("Hidden profile details")
                    .setView(scrollableContent) // Agregar el ScrollView como vista personalizada
                    .setPositiveButton("Okay") { dialog, _ ->
                        val formattedData = (extractedData?.joinToString("\n") { (key, value) ->
                            when {
                                key.startsWith("wa") -> "https://api.whatsapp.com/send/?phone=$value"
                                key.startsWith("ig") -> "https://www.instagram.com/$value"
                                key.startsWith("x") -> "https://www.x.com/$value"
                                key.startsWith("fb") -> "https://www.facebook.com/$value"
                                key.startsWith("map") || key.startsWith("add") -> "https://www.google.com/maps?q=$value"
                                else -> "$key: $value"
                            }
                        } ?: "") + "\n$name\n$aboutMe"

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            copyToClipboard("", formattedData)
                        }

                        if (currentCol < matrix[currentRow].size - 1) {
                            currentCol += 1
                        } else {
                            if (currentRow < matrix.size - 1) {
                                currentRow += 1
                                currentCol = 0
                            } else {
                                currentRow = 0
                                currentCol = 0
                            }
                        }
                        openProfile(matrix[currentRow][currentCol].toString())

                        dialog.dismiss()
                    }.create()

                val gestureDetector = GestureDetectorCompat(
                    it.context,
                    object : GestureDetector.SimpleOnGestureListener() {
                        private val SWIPE_THRESHOLD = 100
                        private val SWIPE_VELOCITY_THRESHOLD = 100

                        override fun onFling(
                            e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
                        ): Boolean {
                            if (e1 != null && e2 != null) {
                                val diffX = e2.x - e1.x
                                val diffY = e2.y - e1.y

                                // Manejo de desplazamientos horizontales (izquierda/derecha) - Cambiar columnas
                                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                                    if (diffX > 0) {  // Desplazamiento hacia la derecha
                                        // Mover a la siguiente columna (cíclicamente)
                                        if (currentCol < matrix[currentRow].size - 1) {
                                            currentCol += 1
                                        } else {
                                            // Mover a la primera columna de la siguiente fila
                                            if (currentRow < matrix.size - 1) {
                                                currentRow += 1
                                                currentCol = 0
                                            } else {
                                                // Si estamos en la última fila, volver a la primera columna de la primera fila
                                                currentRow = 0
                                                currentCol = 0
                                            }
                                        }
                                    } else {  // Desplazamiento hacia la izquierda
                                        // Mover a la columna anterior (cíclicamente)
                                        if (currentCol > 0) {
                                            currentCol -= 1
                                        } else {
                                            // Mover a la última columna de la fila anterior
                                            if (currentRow > 0) {
                                                currentRow -= 1
                                                currentCol = matrix[currentRow].size - 1
                                            } else {
                                                // Si estamos en la primera fila, ir a la última columna de la última fila
                                                currentRow = matrix.size - 1
                                                currentCol = matrix[currentRow].size - 1
                                            }
                                        }
                                    }
                                    openProfile(matrix[currentRow][currentCol].toString())
                                    return true
                                }

                                // Manejo de desplazamientos verticales (arriba/abajo) - Cambiar filas
                                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                                    if (diffY > 0) {  // Desplazamiento hacia abajo
                                        // Mover a la siguiente fila (cíclicamente)
                                        currentRow = if (currentRow < matrix.size - 1) {
                                            currentRow + 1
                                        } else {
                                            0  // Volver a la primera fila si estamos en la última
                                        }
                                    } else {  // Desplazamiento hacia arriba
                                        // Mover a la fila anterior (cíclicamente)
                                        currentRow = if (currentRow > 0) {
                                            currentRow - 1
                                        } else {
                                            matrix.size - 1  // Volver a la última fila si estamos en la primera
                                        }
                                    }
                                    openProfile(matrix[currentRow][currentCol].toString())
                                    return true
                                }
                            }
                            return false
                        }
                    })

                dialog.setOnShowListener {
                    val window = dialog.window

                    // Configurar la altura del diálogo al 40% de la pantalla
                    val displayMetrics = context.resources.displayMetrics
                    window?.setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (displayMetrics.heightPixels * 0.3).toInt()
                    )

                    // Configurar el gesto de toque en cualquier parte del diálogo
                    dialog.window?.decorView?.setOnTouchListener { _, event ->
                        gestureDetector.onTouchEvent(event)
                        true
                    }
                }

                dialog.show()
            }
        }

        findClass(distanceUtils).hook("c", HookStage.AFTER) { param ->
            val distance = param.arg<Double>(0)
            val isFeet = param.arg<Boolean>(2)

            param.setResult(
                if (isFeet) {
                    val feet = (distance * 3.280839895).roundToInt()
                    if (feet < 5280) {
                        String.format("%d feet", feet)
                    } else {
                        String.format("%d miles %d feet", feet / 5280, feet % 5280)
                    }
                } else {
                    val meters = distance.roundToInt()
                    if (meters < 1000) {
                        String.format("%d meters", meters)
                    } else {
                        String.format("%d km %d m", meters / 1000, meters % 1000)
                    }
                }
            )
        }

        findClass(profileViewState).hook("getWeight", HookStage.AFTER) { param ->
            val weight = param.getResult()
            val height = callMethod(param.thisObject(), "getHeight")

            if (weight != null && height != null) {
                val BMI =
                    calculateBMI(
                        "kg" in weight.toString(),
                        w2n("kg" in weight.toString(), weight.toString()),
                        h2n("kg" in weight.toString(), height.toString())
                    )
                if (Config.get("do_gui_safety_checks", true) as Boolean) {
                    if (weight.toString().contains("(")) {
                        logw("BMI details are already present?")
                        return@hook
                    }
                }
                param.setResult(
                    "$weight - ${String.format("%.1f", BMI)} (${
                        mapOf(
                            "Underweight" to 18.5,
                            "Normal weight" to 24.9,
                            "Overweight" to 29.9,
                            "Obese" to Double.MAX_VALUE
                        ).entries.first { it.value > BMI }.key
                    })"
                )
            }
        }
    }
}
