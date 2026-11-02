package app.starpath.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.PersistableBundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import app.starpath.R
import app.starpath.coords.CoordinateParser
import app.starpath.coords.CoordinateResult
import app.starpath.coords.CurrentLocation
import app.starpath.coords.DegreeFormats
import app.starpath.coords.GeoPoint
import app.starpath.coords.InputKind

/**
 * Offline converter between decimal coordinates, DMS, DDM, Plus Codes and
 * Geohash. Coordinates can be typed, pasted, or taken from the device's
 * current position. Nothing is stored or transmitted; "Show on map" hands
 * the point to a map app the user chooses.
 */
class LocationToolsActivity : Activity() {

    companion object {
        private const val REQ_LOCATION = 201
        private const val KEY_INPUT = "input"

        private val FORMATS = listOf(
            "Auto-detect", "Decimal", "DMS", "DDM", "Plus Code", "Geohash",
        )
        private val KINDS = listOf(
            null, InputKind.DECIMAL, InputKind.DMS, InputKind.DDM,
            InputKind.PLUS_CODE, InputKind.GEOHASH,
        )
    }

    private lateinit var input: EditText
    private lateinit var formatSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var decimalValue: TextView
    private lateinit var dmsValue: TextView
    private lateinit var ddmValue: TextView
    private lateinit var plusValue: TextView
    private lateinit var geohashValue: TextView

    private var current: CoordinateResult.Found? = null
    private var reference: GeoPoint? = null
    private var locationSignal: CancellationSignal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        val backBtn = Button(this).apply {
            text = "← Back"
            textSize = 13f
            minWidth = 0
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener { finish() }
        }
        val titleView = TextView(this).apply {
            text = "Location Tools"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * dp).toInt(), 0, 0, 0)
        }
        headerRow.addView(backBtn)
        headerRow.addView(titleView)
        layout.addView(headerRow)

        layout.addView(TextView(this).apply {
            text = "Convert coordinates between Decimal, DMS, DDM, Plus Codes and Geohash.\n\n" +
                "Supported formats & examples:\n" +
                "• Decimal:    41.40363, 2.17436\n" +
                "• DMS:        41°24′13.1″ N, 2°10′27.7″ E\n" +
                "• DDM:        N 41° 24.218 E 002° 10.462\n" +
                "• Plus Code:  8FH4C53F+FP58\n" +
                "• Geohash:    sp3e93r3d"
            textSize = 12.5f
            setPadding(0, 0, 0, (12 * dp).toInt())
        })

        input = EditText(this).apply {
            hint = "Enter or paste coordinates…"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    convert()
                    true
                } else {
                    false
                }
            }
        }
        layout.addView(input)

        formatSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@LocationToolsActivity,
                android.R.layout.simple_spinner_item,
                FORMATS,
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        layout.addView(formatSpinner)

        fun actionButton(label: String, weight: Float = 1f, onClick: () -> Unit): Button =
            Button(this).apply {
                text = label
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    weight,
                ).apply {
                    marginEnd = (4 * dp).toInt()
                }
                setOnClickListener { onClick() }
            }

        val firstRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        firstRow.addView(actionButton("Convert") { convert() })
        firstRow.addView(actionButton("Paste") { paste() })
        layout.addView(firstRow)

        val secondRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        secondRow.addView(actionButton("Use current location") { useCurrentLocation() })
        secondRow.addView(actionButton("Clear", 0.6f) {
            input.text?.clear()
            input.clearFocus()
            formatSpinner.setSelection(0)
            current = null
            reference = null
            clearOutputs()
            status.text = ""
        })
        layout.addView(secondRow)

        status = TextView(this).apply {
            textSize = 14f
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
        layout.addView(status)

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val cardPad = (12 * dp).toInt()
            setPadding(cardPad, cardPad, cardPad, cardPad)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, (4 * dp).toInt(), 0, (12 * dp).toInt())
            }
            layoutParams = lp
            setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
        }

        decimalValue = addOutputRow(cardLayout, "Decimal", dp)
        dmsValue = addOutputRow(cardLayout, "DMS", dp)
        ddmValue = addOutputRow(cardLayout, "DDM", dp)
        plusValue = addOutputRow(cardLayout, "Plus Code", dp)
        geohashValue = addOutputRow(cardLayout, "Geohash", dp)
        layout.addView(cardLayout)

        val bottomRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottomRow.addView(actionButton("Copy all") { copyAll() })
        bottomRow.addView(actionButton("Show on map") { showOnMap() })
        layout.addView(bottomRow)

        setContentView(ScrollView(this).apply { addView(layout) })

        savedInstanceState?.getString(KEY_INPUT)?.let {
            input.setText(it)
            convert()
        }
    }

    private fun addOutputRow(parent: LinearLayout, label: String, dp: Float): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }
        val name = TextView(this).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                (84 * dp).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val value = TextView(this).apply {
            text = "—"
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            )
        }
        val copy = ImageButton(this).apply {
            setImageResource(R.drawable.ic_copy)
            contentDescription = "Copy $label"
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
            setBackgroundResource(typedValue.resourceId)
            val pad = (6 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            val size = (36 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                copyText(label, value.text.toString())
            }
        }
        row.addView(name)
        row.addView(value)
        row.addView(copy)
        parent.addView(row)
        return value
    }

    private fun convert() {
        val raw = input.text.toString()
        if (raw.isBlank()) {
            current = null
            clearOutputs()
            status.text = ""
            return
        }
        val preferred = KINDS.getOrNull(formatSpinner.selectedItemPosition)
        when (val result = CoordinateParser.parse(raw, preferred, reference)) {
            is CoordinateResult.Found -> {
                current = result
                show(result)
                status.text = buildString {
                    append("Read as ${kindLabel(result.kind)}")
                    result.note?.let { append(" — $it") }
                }
            }

            is CoordinateResult.Failed -> {
                current = null
                clearOutputs()
                status.text = when (result.reason) {
                    CoordinateResult.Reason.UNKNOWN_LOCALITY ->
                        "Locality '${result.detail.orEmpty()}' not found in offline cities. Try adding a major nearby city or using a full Plus Code."

                    CoordinateResult.Reason.SHORT_PLUS_CODE_NEEDS_REFERENCE ->
                        "Short Plus Code needs a nearby reference. Tap 'Use current location' first, or enter the full code."

                    CoordinateResult.Reason.INVALID ->
                        "Could not read that. Try decimal, DMS, DDM, a Plus Code or a Geohash."
                }
            }
        }
    }

    private fun show(parsed: CoordinateResult.Found) {
        val point = parsed.point
        decimalValue.text = CoordinateParser.formatDecimal(point)
        dmsValue.text = DegreeFormats.dms(point)
        ddmValue.text = DegreeFormats.ddm(point)
        plusValue.text = CoordinateParser.formatPlusCode(point)
        geohashValue.text = CoordinateParser.formatGeohash(point)
    }

    private fun clearOutputs() {
        decimalValue.text = "—"
        dmsValue.text = "—"
        ddmValue.text = "—"
        plusValue.text = "—"
        geohashValue.text = "—"
    }

    private fun kindLabel(kind: InputKind): String = when (kind) {
        InputKind.DECIMAL -> "Decimal"
        InputKind.DMS -> "DMS"
        InputKind.DDM -> "DDM"
        InputKind.PLUS_CODE -> "Plus Code"
        InputKind.GEOHASH -> "Geohash"
    }

    private fun paste() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }
        input.setText(text)
        convert()
    }

    private fun copyText(label: String, text: String) {
        if (text.isBlank() || text == "—") return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("StarPath $label", text).apply {
            val extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                } else {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
            description.extras = extras
        }
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
    }

    private fun copyAll() {
        val parsed = current ?: return
        copyText("all formats", allText(parsed))
    }

    private fun allText(parsed: CoordinateResult.Found): String {
        val point = parsed.point
        return buildString {
            appendLine("Decimal: ${CoordinateParser.formatDecimal(point)}")
            appendLine("DMS: ${DegreeFormats.dms(point)}")
            appendLine("DDM: ${DegreeFormats.ddm(point)}")
            appendLine("Plus Code: ${CoordinateParser.formatPlusCode(point)}")
            appendLine("Geohash: ${CoordinateParser.formatGeohash(point)}")
            append("Map: ${geoUri(point)}")
        }
    }

    private fun showOnMap() {
        val parsed = current ?: return
        val point = parsed.point
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri(point)))
        if (geo.resolveActivity(packageManager) != null) {
            try {
                startActivity(Intent.createChooser(geo, "Show on map"))
                return
            } catch (_: ActivityNotFoundException) {
                // Fall through to the web map.
            }
        }
        openOpenStreetMap(point)
    }

    private fun openOpenStreetMap(point: GeoPoint) {
        val web = "https://www.openstreetmap.org/?mlat=${point.latitude}" +
            "&mlon=${point.longitude}#map=17/${point.latitude}/${point.longitude}"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No map app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun geoUri(point: GeoPoint): String =
        "geo:${point.latitude},${point.longitude}?q=${point.latitude},${point.longitude}"

    private fun useCurrentLocation() {
        if (!CurrentLocation.hasPermission(this)) {
            requestPermissions(CurrentLocation.permissions, REQ_LOCATION)
            return
        }
        locate()
    }

    private fun locate() {
        locationSignal?.cancel()
        locationSignal = null
        status.text = "Locating (GPS)…"
        locationSignal = CurrentLocation.get(this) { result ->
            locationSignal = null
            when (result) {
                is CurrentLocation.Result.Found -> {
                    val point = GeoPoint.of(result.latitude, result.longitude)
                    if (point == null) {
                        status.text = "The device returned an invalid position."
                    } else {
                        reference = point
                        input.setText(CoordinateParser.formatDecimal(point))
                        formatSpinner.setSelection(1)
                        convert()
                    }
                }

                CurrentLocation.Result.Off -> {
                    reference = null
                    status.text = "GPS / Location is turned off on this device."
                }

                CurrentLocation.Result.NoFix -> {
                    reference = null
                    status.text = "No GPS fix obtained. Move outdoors or near a window and try again."
                }

                CurrentLocation.Result.Denied -> {
                    reference = null
                    status.text = "Precise location permission is needed to use your current position."
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_LOCATION) return
        if (CurrentLocation.hasPermission(this)) {
            locate()
        } else {
            status.text = "Precise location permission was denied. You can still enter coordinates manually."
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_INPUT, input.text.toString())
    }

    override fun onDestroy() {
        locationSignal?.cancel()
        locationSignal = null
        super.onDestroy()
    }
}
