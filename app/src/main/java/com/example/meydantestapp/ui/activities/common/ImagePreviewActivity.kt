package com.example.meydantestapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

/**
 * شاشة معاينة الصور قبل الرفع.
 *
 * تعديلات رئيسية:
 * - استبدال عنصر الصورة عند عودة نتيجة التحرير (بدلاً من تعديل خصائص داخلية).
 * - دعم استقبال URI من FileProvider (content://) مع تمرير صلاحية القراءة إلى المتلقي النهائي.
 * - حماية من الحالات الفارغة وتفادي أي اعتماد على Bundle قد يكون null أثناء إعادة النتيجة.
 */
class ImagePreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGES = "extra_images"               // ArrayList<SelectedImage>
        const val EXTRA_RESULT_IMAGES = "extra_result_images" // ArrayList<SelectedImage>
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var btnBack: ImageButton
    private lateinit var btnApprove: ImageButton
    private lateinit var btnEdit: ImageButton
    private lateinit var tvIndicator: TextView
    private lateinit var etCaption: EditText

    private lateinit var pagerAdapter: PreviewPagerAdapter
    private val images = arrayListOf<SelectedImage>()

    // استقبال نتيجة محرّر الصور
    private val editImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val pos = viewPager.currentItem
        if (pos !in images.indices) return@registerForActivityResult

        // 1) إن عاد عنصر محدث كاملًا
        val updated = data.getParcelableExtra<SelectedImage>(ImageEditorActivity.EXTRA_RESULT_IMAGE)
        if (updated != null) {
            images[pos] = updated
            pagerAdapter.notifyItemChanged(pos)
            etCaption.setText(updated.caption)
            return@registerForActivityResult
        }

        // 2) بديل: فقط URI محدث (من FileProvider)
        val updatedUri = data.getParcelableExtra<Uri>(ImageEditorActivity.EXTRA_RESULT_URI)
        if (updatedUri != null) {
            val old = images[pos]
            images[pos] = old.copy(uri = updatedUri)
            pagerAdapter.notifyItemChanged(pos)
            // لا نعدل التعليق
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        viewPager = findViewById(R.id.vpPreview)
        btnBack = findViewById(R.id.btnBack)
        btnApprove = findViewById(R.id.btnApprove)
        btnEdit = findViewById(R.id.btnEdit)
        tvIndicator = findViewById(R.id.tvIndicator)
        etCaption = findViewById(R.id.etCaption)

        val list = intent.getParcelableArrayListExtra<SelectedImage>(EXTRA_IMAGES)
        if (!list.isNullOrEmpty()) {
            images.addAll(list)
        } else {
            setResult(RESULT_CANCELED)
            finish(); return
        }

        pagerAdapter = PreviewPagerAdapter(images)
        viewPager.adapter = pagerAdapter

        // التعليق للعرض فقط هنا (كي لا نعدل موديلًا بحقوق val)
        etCaption.isEnabled = false
        etCaption.keyListener = null

        updateIndicator(0)
        etCaption.setText(images.firstOrNull()?.caption.orEmpty())

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicator(position)
                etCaption.setText(images.getOrNull(position)?.caption.orEmpty())
            }
        })

        btnBack.setOnClickListener { finish() }

        btnApprove.setOnClickListener {
            if (images.isEmpty()) {
                Toast.makeText(this, "لا توجد صور للإرسال", Toast.LENGTH_SHORT).show()
                setResult(RESULT_CANCELED)
                finish(); return@setOnClickListener
            }
            // إعادة القائمة كما هي (قد تكون بعض العناصر عُدّلت صورها)
            val data = Intent().apply {
                putParcelableArrayListExtra(EXTRA_RESULT_IMAGES, ArrayList(images))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            setResult(RESULT_OK, data)
            finish()
        }

        btnEdit.setOnClickListener {
            val item = images.getOrNull(viewPager.currentItem) ?: return@setOnClickListener
            val intent = Intent(this, ImageEditorActivity::class.java).apply {
                putExtra(ImageEditorActivity.EXTRA_IMAGE, item)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            editImageLauncher.launch(intent)
        }
    }

    private fun updateIndicator(position: Int) {
        val total = images.size
        tvIndicator.text = "${'$'}{position + 1} / ${'$'}total"
    }
}
