package com.example.meydantestapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE = "extra_image"
        const val EXTRA_RESULT_IMAGE = "extra_result_image"
        const val EXTRA_RESULT_URI = "extra_result_uri"
    }

    private lateinit var ivBase: ImageView
    private lateinit var stickersLayer: FrameLayout
    private lateinit var drawingView: DrawingView

    private lateinit var btnClose: ImageButton
    private lateinit var btnDone: ImageButton
    private lateinit var btnBrush: ImageButton
    private lateinit var btnText: ImageButton
    private lateinit var btnSticker: ImageButton
    private lateinit var btnCrop: ImageButton
    private lateinit var btnRotate: ImageButton
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton

    private lateinit var brushPanel: LinearLayout
    private lateinit var colorRow: LinearLayout
    private lateinit var sizeSeek: SeekBar

    private lateinit var textPanel: LinearLayout
    private lateinit var etText: EditText
    private lateinit var btnTextColor: ImageButton
    private lateinit var btnTextApply: ImageButton

    private lateinit var cropOverlay: FrameLayout

    private var current: SelectedImage? = null
    private var currentTextColor: Int = Color.WHITE
    private var cropSquare: Boolean = false

    private val palette by lazy {
        intArrayOf(
            Color.WHITE, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE,
            Color.YELLOW, Color.CYAN, Color.MAGENTA, 0xFFFF9800.toInt(), 0xFF9C27B0.toInt()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_editor)

        bindViews()
        setupPaletteUI()
        setupBrushPanel()
        setupTextPanel()
        setupButtons()
        loadIncomingImage()
    }

    private fun bindViews() {
        ivBase = findViewById(R.id.ivBase)
        stickersLayer = findViewById(R.id.stickersLayer)
        drawingView = findViewById(R.id.drawingView)

        btnClose = findViewById(R.id.btnClose)
        btnDone = findViewById(R.id.btnDone)
        btnBrush = findViewById(R.id.btnBrush)
        btnText = findViewById(R.id.btnText)
        btnSticker = findViewById(R.id.btnSticker)
        btnCrop = findViewById(R.id.btnCrop)
        btnRotate = findViewById(R.id.btnRotate)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)

        brushPanel = findViewById(R.id.brushPanel)
        colorRow = findViewById(R.id.colorRow)
        sizeSeek = findViewById(R.id.sizeSeek)

        textPanel = findViewById(R.id.textPanel)
        etText = findViewById(R.id.etText)
        btnTextColor = findViewById(R.id.btnTextColor)
        btnTextApply = findViewById(R.id.btnTextApply)

        cropOverlay = findViewById(R.id.cropOverlay)
    }

    private fun setupButtons() {
        btnClose.setOnClickListener { finish() }

        btnDone.setOnClickListener { exportAndFinish() }
        btnBrush.setOnClickListener { toggleBrushMode() }
        btnText.setOnClickListener { showTextPanel() }
        btnSticker.setOnClickListener { addEmojiSticker() }
        btnRotate.setOnClickListener { ivBase.rotation = (ivBase.rotation + 90f) % 360f }
        btnCrop.setOnClickListener {
            cropSquare = !cropSquare
            cropOverlay.isVisible = cropSquare
            Toast.makeText(this, if (cropSquare) "تم تفعيل القص المربّع" else "تم إلغاء القص", Toast.LENGTH_SHORT).show()
        }
        btnUndo.setOnClickListener {
            if (!drawingView.undo()) {
                if (stickersLayer.childCount > 0) stickersLayer.removeViewAt(stickersLayer.childCount - 1)
            }
        }
        btnRedo.setOnClickListener { drawingView.redo() }
    }

    private fun setupPaletteUI() {
        colorRow.removeAllViews()
        val size = dp(28)
        palette.forEach { color ->
            val v = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
                background = circle(color)
                contentDescription = "color"
                setOnClickListener {
                    currentTextColor = color
                    drawingView.setBrushColor(color)
                }
            }
            colorRow.addView(v)
        }
    }

    private fun setupBrushPanel() {
        drawingView.setBrushColor(currentTextColor)
        drawingView.setBrushSize(dp(4).toFloat())
        sizeSeek.progress = 16
        sizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                drawingView.setBrushSize(progress.coerceAtLeast(1).toFloat())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupTextPanel() {
        btnTextColor.setOnClickListener {
            val idx = palette.indexOf(currentTextColor)
            val next = palette[(idx + 1).mod(palette.size)]
            currentTextColor = next
            btnTextColor.setColorFilter(next)
        }
        btnTextApply.setOnClickListener {
            val text = etText.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                addMovableText(text, currentTextColor)
                etText.setText("")
                textPanel.isVisible = false
            }
        }
    }

    private fun toggleBrushMode() {
        val enable = !drawingView.isVisible
        drawingView.visibility = if (enable) View.VISIBLE else View.GONE
        brushPanel.isVisible = enable
        if (enable) Toast.makeText(this, "وضع القلم مفعّل", Toast.LENGTH_SHORT).show()
    }

    private fun showTextPanel() {
        brushPanel.isVisible = false
        drawingView.visibility = View.GONE
        textPanel.isVisible = true
        etText.requestFocus()
    }

    private fun addEmojiSticker() {
        val tv = TextView(this).apply {
            text = "🙂"
            textSize = 48f
            setTextColor(Color.WHITE)
            setPadding(dp(8))
            setShadowLayer(8f, 0f, 0f, 0x80000000.toInt())
            background = circle(0x33000000)
        }
        tv.setOnLongClickListener {
            (tv.parent as? ViewGroup)?.removeView(tv)
            true
        }
        tv.setOnTouchListener(MultiTouchListener())
        val size = dp(100)
        stickersLayer.addView(tv, FrameLayout.LayoutParams(size, size).apply { gravity = android.view.Gravity.CENTER })
    }

    private fun addMovableText(value: String, color: Int) {
        val tv = TextView(this).apply {
            text = value
            textSize = 24f
            setTextColor(color)
            setPadding(dp(8))
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
            background = circle(0x22000000)
        }
        tv.setOnLongClickListener { (tv.parent as? ViewGroup)?.removeView(tv); true }
        tv.setOnTouchListener(MultiTouchListener())
        stickersLayer.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = android.view.Gravity.CENTER })
    }

    private fun loadIncomingImage() {
        current = intent.getParcelableExtra(EXTRA_IMAGE)
        val uri: Uri? = current?.uri
        if (uri == null) {
            Toast.makeText(this, "لا توجد صورة", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                val bmp = ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
                ivBase.setImageBitmap(bmp)
            } else {
                @Suppress("DEPRECATION")
                val bmp = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                ivBase.setImageBitmap(bmp)
            }
        } catch (e: Exception) {
            ivBase.setImageURI(uri)
        }
    }

    private fun exportAndFinish() {
        val w = ivBase.width.takeIf { it > 0 } ?: ivBase.measuredWidth
        val h = ivBase.height.takeIf { it > 0 } ?: ivBase.measuredHeight
        if (w <= 0 || h <= 0) { Toast.makeText(this, "تعذّر تصدير الصورة الآن", Toast.LENGTH_SHORT).show(); return }

        // دمج الطبقات إلى Bitmap واحد
        val base = drawViewToBitmap(ivBase, w, h)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(base, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.drawBitmap(drawViewToBitmap(drawingView, w, h), 0f, 0f, null)
        canvas.drawBitmap(drawViewToBitmap(stickersLayer, w, h), 0f, 0f, null)

        val finalBmp = if (cropSquare) centerSquareCrop(result) else result
        val out = saveToCache(finalBmp) ?: run {
            Toast.makeText(this, "فشل حفظ الصورة", Toast.LENGTH_SHORT).show(); return
        }

        // FileProvider authority الصحيح: ${applicationId}.fileprovider في المانيفست
        val contentUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            out
        )

        val updated = current?.copy(uri = contentUri) ?: SelectedImage(uri = contentUri)
        val data = Intent().apply {
            putExtra(EXTRA_RESULT_IMAGE, updated)
            putExtra(EXTRA_RESULT_URI, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}

        setResult(RESULT_OK, data)
        finish()
    }

    private fun drawViewToBitmap(view: View, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.layout(0, 0, width, height)
        view.draw(canvas)
        return bitmap
    }

    private fun centerSquareCrop(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val left = (src.width - size) / 2
        val top = (src.height - size) / 2
        return Bitmap.createBitmap(src, left, top, size, size)
    }

    private fun saveToCache(bmp: Bitmap): File? {
        return try {
            val dir = File(cacheDir, "images").apply { mkdirs() }
            val name = "edited_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            val file = File(dir, name)
            FileOutputStream(file).use { fos -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos) }
            file
        } catch (e: Exception) { null }
    }

    private fun circle(color: Int): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(1), 0x66FFFFFF.toInt())
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
