package alls.tech.gr.hooks

import alls.tech.gr.GR
import alls.tech.gr.core.Config
import alls.tech.gr.core.Config.writeMatrix
import alls.tech.gr.core.Utils.openProfile
import alls.tech.gr.core.Utils.sendApiDelete
import alls.tech.gr.ui.Utils
import alls.tech.gr.utils.FileUtils.findValueInCategories
import alls.tech.gr.utils.FileUtils.safeSubstring
import alls.tech.gr.utils.Hook
import alls.tech.gr.utils.HookStage
import alls.tech.gr.utils.hookConstructor
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import org.json.JSONObject
import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ProfileSpoofer(private val jsonObject: JSONObject) : Hook(
    "Profile Spoofer", "Select profiles"
) {
    private val chatBottomToolbar = "com.grindrapp.android.chat.presentation.ui.view.ChatBottomToolbar"

    init {
    }

    private val categories = jsonObject.keys().asSequence().toList()
    private var currentDataList: List<Long> = emptyList()
    private var currentCategory: String? = null
    private val clickCountMap = mutableMapOf<Long, Int>()
    private var filterText: String = ""

    private var currentDialog: AlertDialog? = null

    private var verticalScrollPosition = 0
    private var horizontalScrollPosition = 0
    var currentCategoryProfiles: Map<String, List<List<Long>>> = emptyMap()
    val validCategory = "defaultCategory"

    @RequiresApi(Build.VERSION_CODES.N)
    override fun init() {
        findClass(chatBottomToolbar)?.hookConstructor(HookStage.AFTER) { param ->
            val profileListView = param.thisObject() as LinearLayout
            val customProfileButton = createCustomProfileButton(profileListView)
            profileListView.addView(customProfileButton)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun createCustomProfileButton(parent: LinearLayout): ImageButton {
        return ImageButton(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT)
                .apply { weight = 1f }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusable = ImageButton.FOCUSABLE
            isClickable = true
            setBackgroundResource(
                Utils.getId(
                    "image_button_ripple", "drawable", parent.context
                )
            )
            setImageResource(
                Utils.getId(
                    "ic_note_filled", "drawable", parent.context
                )
            )
            setPadding(
                (parent.getChildAt(0) as? ImageButton)?.paddingLeft ?: 0,
                (parent.getChildAt(0) as? ImageButton)?.paddingTop ?: 0,
                (parent.getChildAt(0) as? ImageButton)?.paddingRight ?: 0,
                (parent.getChildAt(0) as? ImageButton)?.paddingBottom ?: 0
            )
            setOnClickListener { showProfileDialog(context) }
        }
    }

    fun updateMatrixFile(category: String, columns: Int) {
        val categoryProfiles = currentCategoryProfiles[category]

        if (categoryProfiles == null) {
            return
        }

        val rows = categoryProfiles.size

        val matrix = Array(rows) { rowIndex ->
            val profile = categoryProfiles[rowIndex]

            IntArray(columns) { colIndex ->
                if (colIndex < profile.size) {
                    profile[colIndex].toInt()
                } else {
                    0
                }
            }
        }

        // Log de la matriz
        //GR.logger.log("Matriz generada para la categoría '$category': ${matrix.contentDeepToString()}")

        // Escribir la matriz en el archivo
        writeMatrix(matrix)
    }


    @RequiresApi(Build.VERSION_CODES.N)
    private fun showProfileDialog(context: Context) {
        // Cerrar el diálogo actual si está abierto
        currentDialog?.let {
            if (it.isShowing && (it.window?.decorView?.isAttachedToWindow == true)) {
                try {
                    it.dismiss()
                } catch (e: IllegalArgumentException) {
                }
            }
        }

        // Crear el nuevo diálogo
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )

            // Horizontal scroll view for category buttons
            val categoryScrollView = HorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            categoryScrollView.viewTreeObserver.addOnScrollChangedListener {
                horizontalScrollPosition = categoryScrollView.scrollX
            }

            categoryScrollView.post {
                categoryScrollView.scrollTo(horizontalScrollPosition, 0)
            }

            val buttonLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(1, 1, 1, 1)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Create filter button
            val filterButton = Button(context).apply {
                text = "Filtrar"
                setBackgroundColor(Color.LTGRAY)
                setTextColor(Color.BLACK)
                setPadding(5, 5, 5, 5)
                setOnClickListener {
                    showFilterDialog(context)
                }
            }
            buttonLayout.addView(filterButton)

            val currentDate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LocalDate.now()
            } else {
                TODO("VERSION.SDK_INT < O")
            }

            val dateFormatter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DateTimeFormatter.ofPattern("dd/MM")
            } else {
                TODO("VERSION.SDK_INT < O")
            }

            // Create buttons for each category
            categories.forEach { category ->
                val button = Button(context).apply {
                    text = category
                    setBackgroundColor(Color.LTGRAY)
                    setTextColor(Color.WHITE)
                    setPadding(2, 2, 2, 2)
                    setOnClickListener {
                        currentCategory = category
                        currentDataList = when (category) {
                            "data" -> jsonObject.optJSONArray("data")?.let { array ->
                                List(array.length()) { index -> array.getLong(index) }
                            } ?: emptyList()

                            "profiles" -> jsonObject.optJSONObject("profiles")?.let { obj ->
                                obj.keys().asSequence().map { key ->
                                    val value = obj.getString(key)
                                    value.toLong() to key
                                }.toMap().keys.toList()
                            } ?: emptyList()

                            else -> jsonObject.optJSONObject(category)?.keys()?.asSequence()
                                ?.map { key ->
                                    key.toLong()
                                }?.toList() ?: emptyList()
                        }
                        showProfileDialog(context)
                    }
                }
                buttonLayout.addView(button)
            }

            categoryScrollView.addView(buttonLayout)

            // Scrollable grid for profile buttons
            val scrollView = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            scrollView.viewTreeObserver.addOnScrollChangedListener {
                verticalScrollPosition = scrollView.scrollY
            }

            scrollView.post {
                scrollView.scrollTo(0, verticalScrollPosition)
            }

            val columnCount = 5

            val gridLayout = GridLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                rowCount = (currentDataList.size + 4) / 5
                setPadding(1, 1, 1, 1)
            }

            fun obtenerFechaMasReciente(description: String, currentDate: LocalDate): LocalDate {
                // Ahora soporta <dd/MM/yy>
                val dateRegex = Regex("<(\\d{2})/(\\d{2})/(\\d{2})>")

                val fechas = dateRegex.findAll(description).mapNotNull { match ->
                    val (day, month, yearTwoDigits) = match.destructured
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@mapNotNull null

                    // Convertir el año de 2 dígitos a 4 dígitos (asumiendo 2000+)
                    val year = 2000 + yearTwoDigits.toInt()

                    val fecha = try {
                        LocalDate.of(year, month.toInt(), day.toInt())
                    } catch (e: Exception) {
                        return@mapNotNull null // evitar error por fecha inválida tipo 31/02
                    }

                    // Ajuste: si la fecha está en el futuro respecto a currentDate, se mantiene
                    // ya no es necesario restar años porque ya tenemos el año explícito
                    fecha
                }.toList()

                return fechas.maxOrNull() ?: LocalDate.MIN
            }


            val orderedDataList = currentDataList.filter { profileId ->
                val description = when (currentCategory) {
                    "profiles" -> findValueInCategories(jsonObject, profileId.toString()).second
                        ?: ""

                    else -> jsonObject.optJSONObject(currentCategory)
                        ?.optString(profileId.toString(), "") ?: ""
                }
                description.contains(filterText, ignoreCase = true)
            }.sortedByDescending { profileId ->
                val description = when (currentCategory) {
                    "profiles" -> findValueInCategories(jsonObject, profileId.toString()).second
                        ?: ""

                    else -> jsonObject.optJSONObject(currentCategory)
                        ?.optString(profileId.toString(), "") ?: ""
                }

                // Extraer la fecha en el formato <dd/MM/yy>
                val dateMatches = Regex("<(\\d{2})/(\\d{2})/(\\d{2})>").findAll(description)
                if (dateMatches.any()) {
                    dateMatches.mapNotNull { match ->
                        val (day, month, yearTwoDigits) = match.destructured
                        currentDate?.let {
                            try {
                                val year = 2000 + yearTwoDigits.toInt() // convertir 24 → 2024
                                LocalDate.of(year, month.toInt(), day.toInt())
                            } catch (e: DateTimeException) {
                                null // Ignorar fechas inválidas
                            }
                        }
                    }.maxOrNull() ?: LocalDate.MIN // Tomar la fecha más reciente
                } else {
                    LocalDate.MIN // Si no hay fechas, devolver MIN
                }
            }

            // Add profile buttons for filtered profiles
            orderedDataList.forEachIndexed { index, profileId ->
                val description = when (currentCategory) {
                    "profiles" -> findValueInCategories(jsonObject, profileId.toString()).second
                        ?: " - "

                    else -> jsonObject.optJSONObject(currentCategory)
                        ?.optString(profileId.toString(), " … ") ?: " … "
                }

                val button = createButton(
                    context,
                    profileId.toString(),
                    description,
                    profileId,
                    (index / columnCount * 25) % 255
                ).apply {
                    setOnLongClickListener {
                        /*
                        val profileDescription = when (currentCategory) {
                            "profiles" -> jsonObject.optJSONObject("info")?.optString(profileId.toString(), " … ") ?: " … "
                            else -> jsonObject.optJSONObject(currentCategory)?.optString(profileId.toString(), " … ") ?: " … "
                        }
                        Utils.copyToClipboard("Profile Details", "/api {\"id\":\"$profileId\",\"d\":\"$profileDescription\",\"e\":\" … \"}")
                        true
                        */
                        AlertDialog.Builder(context).apply {
                            setTitle("Confirmar eliminación")
                            setMessage("¿Eliminar: $profileId?")
                            setPositiveButton("Sí") { dialog, _ ->
                                sendApiDelete("$profileId", "{\"$currentCategory\":\"$profileId\"}")
                                dialog.dismiss()
                            }
                            setNegativeButton("No") { dialog, _ ->
                                dialog.dismiss()
                            }
                            create()
                            show()
                        }
                        true;
                    }
                }

                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(index % 5, 1f)
                    rowSpec = GridLayout.spec(index / 5)
                    setMargins(1, 1, 1, 1)
                }

                button.layoutParams = params
                gridLayout.addView(button)
            }

            // Convertir la lista de Long a String antes de hacer el chunking
            val stringOrderedDataList = orderedDataList.map { it.toString() }

            // Convertir la lista de String a Long
            val longOrderedDataList = stringOrderedDataList.map { it.toLong() }

            // Dividir la lista en sublistas (por ejemplo, 5 elementos por cada sublista)
            val chunkedData = longOrderedDataList.chunked(5)

            // Crear un mapa mutable para guardar los perfiles por categoría
            val categoryProfilesMap = mutableMapOf<String, List<List<Long>>>()

            // Guardar las sublistas en el mapa bajo la clave validCategory (nunca nula)
            categoryProfilesMap[validCategory] = chunkedData

            // Ahora actualizamos la variable global
            currentCategoryProfiles = categoryProfilesMap

            scrollView.addView(gridLayout)

            // Add category buttons and content grid to dialog view
            addView(categoryScrollView)
            addView(scrollView)
        }

        currentDialog = AlertDialog.Builder(context).apply {
            setTitle("Profile Actions")
            setView(dialogView)
            setPositiveButton("Close") { dialog, _ ->
                try {
                    dialog.dismiss()
                } catch (e: IllegalArgumentException) {
                }
                currentDialog = null
            }
            setOnDismissListener {
                currentDialog = null
            }

            updateMatrixFile(validCategory, 5)
            //GR.logger.log(currentCategoryProfiles.toString())
            currentCategoryProfiles.forEach { (category, profilesList) ->
                //GR.logger.log(profilesList.toString())
                //GR.logger.log("Categoría: $category, Tamaño: ${profilesList.size}")
            }
        }.show()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun showFilterDialog(context: Context) {
        val input = EditText(context).apply {
            hint = "Buscar descripción..."
        }

        AlertDialog.Builder(context).apply {
            setTitle("Filtrar por descripción")
            setView(input)
            setPositiveButton("Aplicar") { dialog, _ ->
                filterText = input.text.toString()
                showProfileDialog(context)
                try {
                    dialog.dismiss()
                } catch (e: IllegalArgumentException) {
                }
            }
            setNegativeButton("Cancelar") { dialog, _ ->
                try {
                    dialog.dismiss()
                } catch (e: IllegalArgumentException) {
                }
            }
            show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun createButton(
        context: Context, text: String, description: String, profileId: Long, row: Int
    ): Button {
        return Button(context).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
            }

            this.text = text + "\n" + safeSubstring(0, 9, description) + "\n" + safeSubstring(
                10, 9, description
            )

            val clickCount = clickCountMap.getOrDefault(profileId, 0)
            setOnClickListener {
                clickCountMap[profileId] = clickCount + 1
                openProfile(profileId.toString())
                (context as? Activity)?.runOnUiThread {
                    // Actualizar el color en el hilo principal
                    val color = when (clickCount + 1) {
                        0 -> Color.GRAY
                        1 -> Color.GREEN
                        2 -> Color.YELLOW
                        else -> Color.RED
                    }
                    setBackgroundColor(color)
                }
            }

            // Inicializar el color del botón basado en el clickCount
            val hex = String.format("#%02x%02x%02x", row, row, row)

            val initialColor = when (clickCount) {
                0 -> Color.parseColor(hex)
                1 -> Color.GREEN
                2 -> Color.YELLOW
                else -> Color.RED
            }
            setBackgroundColor(initialColor)
        }
    }

}
