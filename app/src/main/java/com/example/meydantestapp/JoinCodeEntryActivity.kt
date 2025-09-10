package com.example.meydantestapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.meydantestapp.databinding.ActivityJoinCodeEntryBinding
import com.example.meydantestapp.utils.Constants

/**
 * شاشة إدخال كود الانضمام.
 * - تتحقق من الكود عبر الـ ViewModel.
 * - عند النجاح، تنتقل إلى RegisterAffiliatedUserActivity وتنقل معها
 *   organizationId + organizationName + joinCode لعرضها تلقائيًا هناك.
 */
class JoinCodeEntryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJoinCodeEntryBinding
    private lateinit var viewModel: JoinCodeEntryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJoinCodeEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[JoinCodeEntryViewModel::class.java]

        // زر الرجوع
        binding.backButton.setOnClickListener { finish() }

        // متابعة التحقق من الكود
        binding.continueButton.setOnClickListener {
            val joinCode = binding.joinCodeInput.text.toString().trim()
            viewModel.checkJoinCode(joinCode)
        }

        // تعطيل/تمكين الزر أثناء التحميل
        viewModel.isLoading.observe(this) { loading ->
            binding.continueButton.isEnabled = !loading
        }

        // رسائل قصيرة للمستخدم
        viewModel.showToast.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        // الانتقال إلى شاشة تسجيل المستخدم التابع عند نجاح التحقق
        viewModel.navigateToRegisterAffiliatedUser.observe(this) { navData ->
            navData?.let {
                val intent = Intent(this, RegisterAffiliatedUserActivity::class.java).apply {
                    // ✅ استخدام مفاتيح Constants بدل السلاسل النصية الخام
                    putExtra(Constants.EXTRA_ORGANIZATION_ID, it.organizationId)
                    putExtra(Constants.EXTRA_ORGANIZATION_NAME, it.organizationName)
                    putExtra(Constants.EXTRA_JOIN_CODE, it.joinCode)
                }
                startActivity(intent)
                // منع تكرار الملاحة بعد الرجوع/الدوران
                viewModel.clearNavigation()
            }
        }
    }
}
