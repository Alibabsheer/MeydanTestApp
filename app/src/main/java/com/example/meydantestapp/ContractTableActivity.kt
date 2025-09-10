package com.example.meydantestapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meydantestapp.databinding.ActivityContractTableBinding
import kotlinx.coroutines.launch

class ContractTableActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContractTableBinding
    private lateinit var importExcelLauncher: ActivityResultLauncher<Intent>

    private var projectId: String? = null
    private var workType: String? = null

    private lateinit var quantityTableHandler: QuantityTableHandler
    private lateinit var lumpSumTableHandler: LumpSumTableHandler

    private var currentQuantityList = mutableListOf<QuantityItem>()
    private var currentLumpSumList = mutableListOf<LumpSumItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContractTableBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectId = intent.getStringExtra("projectId")
        workType = intent.getStringExtra("workType")

        if (projectId == null) {
            Toast.makeText(this, "معرف المشروع غير موجود.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        quantityTableHandler = QuantityTableHandler(this, projectId!!)
        lumpSumTableHandler = LumpSumTableHandler(this, projectId!!)

        binding.titleText.text = when (workType) {
            "جدول كميات" -> "جدول الكميات (عقد الكميات)"
            "مقطوعية" -> "جدول الكميات (عقد المقطوعية)"
            else -> "نوع غير معروف"
        }

        binding.backButton.setOnClickListener { finish() }
        binding.contractRecyclerView.layoutManager = LinearLayoutManager(this)

        importExcelLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                handleExcelImport(uri)
            }
        }

        binding.importExcelButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            importExcelLauncher.launch(Intent.createChooser(intent, "اختر ملف Excel"))
        }

        binding.deleteTableButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("تأكيد الحذف")
                .setMessage("هل أنت متأكد من حذف بيانات الجدول؟")
                .setPositiveButton("نعم") { _, _ -> deleteContractTable() }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        binding.saveTableButton.setOnClickListener {
            saveContractTable()
        }

        loadTable()
    }

    private fun loadTable() {
        lifecycleScope.launch {
            when (workType) {
                "جدول كميات" -> {
                    currentQuantityList = quantityTableHandler.loadTable()
                    binding.contractRecyclerView.adapter = QuantityTableAdapter(currentQuantityList)
                }
                "مقطوعية" -> {
                    currentLumpSumList = lumpSumTableHandler.loadTable()
                    binding.contractRecyclerView.adapter = LumpSumTableAdapter(currentLumpSumList)
                }
                else -> {
                    showImportInstructions()
                }
            }
            if (currentQuantityList.isEmpty() && currentLumpSumList.isEmpty()) {
                showImportInstructions()
            } else {
                binding.importExcelButton.visibility = View.VISIBLE
                binding.workTypeInfoText.visibility = View.GONE
            }
        }
    }

    private fun showImportInstructions() {
        binding.importExcelButton.visibility = View.VISIBLE
        binding.workTypeInfoText.visibility = View.VISIBLE
        binding.workTypeInfoText.text = getString(R.string.import_instruction, workType)
    }

    private fun deleteContractTable() {
        lifecycleScope.launch {
            val success = when (workType) {
                "جدول كميات" -> quantityTableHandler.deleteTable()
                "مقطوعية" -> lumpSumTableHandler.deleteTable()
                else -> false
            }
            if (success) {
                loadTable() // Reload to update UI
            }
        }
    }

    private fun saveContractTable() {
        lifecycleScope.launch {
            val success = when (workType) {
                "جدول كميات" -> quantityTableHandler.saveTable(currentQuantityList) && quantityTableHandler.updateProjectContractValue(currentQuantityList)
                "مقطوعية" -> lumpSumTableHandler.saveTable(currentLumpSumList) && lumpSumTableHandler.updateProjectContractValue(currentLumpSumList)
                else -> false
            }
            if (success) {
                finish()
            }
        }
    }

    private fun handleExcelImport(uri: Uri) {
        lifecycleScope.launch {
            when (workType) {
                "جدول كميات" -> {
                    val importedList = quantityTableHandler.importExcel(uri)
                    if (importedList.isNotEmpty()) {
                        currentQuantityList.clear()
                        currentQuantityList.addAll(importedList)
                        binding.contractRecyclerView.adapter = QuantityTableAdapter(currentQuantityList)
                        binding.workTypeInfoText.visibility = View.GONE
                    }
                }
                "مقطوعية" -> {
                    val importedList = lumpSumTableHandler.importExcel(uri)
                    if (importedList.isNotEmpty()) {
                        currentLumpSumList.clear()
                        currentLumpSumList.addAll(importedList)
                        binding.contractRecyclerView.adapter = LumpSumTableAdapter(currentLumpSumList)
                        binding.workTypeInfoText.visibility = View.GONE
                    }
                }
            }
        }
    }
}
