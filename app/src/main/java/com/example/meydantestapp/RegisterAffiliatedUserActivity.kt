package com.example.meydantestapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.meydantestapp.databinding.ActivityRegisterAffiliatedUserBinding
import com.example.meydantestapp.utils.Constants

class RegisterAffiliatedUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterAffiliatedUserBinding
    private val viewModel: RegisterAffiliatedUserViewModel by viewModels()

    private var organizationId: String? = null
    private var organizationName: String? = null
    private var joinCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterAffiliatedUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // استقبال البيانات من الشاشة السابقة
        organizationId = intent.getStringExtra(Constants.EXTRA_ORGANIZATION_ID)
        organizationName = intent.getStringExtra(Constants.EXTRA_ORGANIZATION_NAME)
        joinCode = intent.getStringExtra(Constants.EXTRA_JOIN_CODE)

        // عرض ملخص المؤسسة والكود (حسب النص في strings.xml)
        binding.orgInfoText.text = getString(
            R.string.org_info_text,
            organizationName ?: "",
            joinCode ?: ""
        )

        // زر الرجوع
        binding.backButton.setOnClickListener { finish() }

        // تفعيل زر التسجيل فقط عند التأكيد
        binding.registerButton.isEnabled = binding.confirmCheck.isChecked
        binding.confirmCheck.setOnCheckedChangeListener { _, isChecked ->
            binding.registerButton.isEnabled = isChecked && (viewModel.isLoading.value != true)
        }

        // مراقبة حالة التحميل: تعطيل/تمكين عناصر الإدخال
        viewModel.isLoading.observe(this) { loading ->
            // لا نستخدم progressBar لأن التخطيط لا يحتوي عليه
            setInputsEnabled(!loading)
            binding.registerButton.isEnabled = !loading && binding.confirmCheck.isChecked
        }

        // مراقبة نتيجة التسجيل
        viewModel.registrationResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "تم إنشاء الحساب بنجاح", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, UserProjectsActivity::class.java)
                intent.putExtra(Constants.EXTRA_ORGANIZATION_ID, organizationId)
                startActivity(intent)
                finish()
            }.onFailure {
                Toast.makeText(this, it.message ?: "فشل في إنشاء الحساب", Toast.LENGTH_LONG).show()
            }
        }

        // زر التسجيل
        binding.registerButton.setOnClickListener { registerUser() }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        binding.usernameInput.isEnabled = enabled
        binding.emailInput.isEnabled = enabled
        binding.countryCodeInput.isEnabled = enabled
        binding.phoneInput.isEnabled = enabled
        binding.passwordInput.isEnabled = enabled
        binding.confirmPasswordInput.isEnabled = enabled
    }

    private fun registerUser() {
        val name = binding.usernameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val countryCode = binding.countryCodeInput.text.toString().trim()
        val phone = binding.phoneInput.text.toString().trim() // محفوظ ضمن الحقل الكامل إن لزم
        val password = binding.passwordInput.text.toString()
        val confirmPassword = binding.confirmPasswordInput.text.toString()

        if (name.isEmpty() || email.isEmpty() || countryCode.isEmpty() || phone.isEmpty() ||
            password.isEmpty() || confirmPassword.isEmpty()
        ) {
            Toast.makeText(this, "يرجى تعبئة جميع الحقول", Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, "كلمتا المرور غير متطابقتين", Toast.LENGTH_SHORT).show()
            return
        }

        val orgId = organizationId
        val jCode = joinCode
        if (orgId.isNullOrBlank() || jCode.isNullOrBlank()) {
            Toast.makeText(this, "بيانات المؤسسة أو كود الانضمام غير متوفر", Toast.LENGTH_SHORT).show()
            return
        }

        // تمرير العملية كاملة عبر الـ ViewModel → AuthRepository
        viewModel.registerAffiliatedUser(
            userName = name,
            email = email,
            password = password,
            confirmPassword = confirmPassword,
            organizationId = orgId,
            joinCode = jCode
        )
    }
}
