package com.example.meydantestapp

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class LumpSumTableHandler(private val context: Context, private val projectId: String) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun loadTable(): MutableList<LumpSumItem> = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext mutableListOf()
        val docRef = db.collection("organizations").document(userId)
            .collection("projects").document(projectId)

        val lumpSumList = mutableListOf<LumpSumItem>()
        try {
            val doc = docRef.get().await()
            if (doc.exists()) {
                val rawList = doc.get("lumpSumTable")
                val list = if (rawList is List<*>) {
                    rawList.filterIsInstance<Map<String, Any>>()
                } else {
                    emptyList()
                }
                lumpSumList.addAll(list.map {
                    LumpSumItem(
                        itemNumber = it["itemNumber"] as? String,
                        description = it["description"] as? String,
                        totalValue = (it["totalValue"] as? Number)?.toDouble()
                    )
                })
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "فشل في تحميل جدول المقطوعية: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        lumpSumList
    }

    suspend fun saveTable(lumpSumList: List<LumpSumItem>): Boolean = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext false
        val docRef = db.collection("organizations").document(userId)
            .collection("projects").document(projectId)

        return@withContext try {
            docRef.update("lumpSumTable", lumpSumList).await()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "تم حفظ جدول المقطوعية بنجاح", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "فشل حفظ جدول المقطوعية: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    suspend fun deleteTable(): Boolean = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext false
        val docRef = db.collection("organizations").document(userId)
            .collection("projects").document(projectId)

        return@withContext try {
            docRef.update("lumpSumTable", null).await()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "تم حذف جدول المقطوعية بنجاح", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "فشل حذف جدول المقطوعية: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    suspend fun importExcel(uri: Uri): MutableList<LumpSumItem> = withContext(Dispatchers.IO) {
        val lumpSumList = mutableListOf<LumpSumItem>()
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext mutableListOf()
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)
            val dataFormatter = DataFormatter()
            val title = sheet.getRow(0)?.getCell(0)?.stringCellValue ?: ""

            if (title.contains("Lump-Sum")) {
                for (i in 2..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    val item = LumpSumItem(
                        itemNumber = dataFormatter.formatCellValue(row.getCell(0)),
                        description = dataFormatter.formatCellValue(row.getCell(1)),
                        totalValue = row.getCell(2)?.numericCellValue ?: 0.0
                    )
                    lumpSumList.add(item)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "تم استيراد جدول المقطوعية بنجاح", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "نوع ملف Excel غير مدعوم أو التنسيق غير صحيح. يرجى التأكد من أن الملف يبدأ بـ 'Lump-Sum'.", Toast.LENGTH_LONG).show()
                }
            }
            inputStream.close()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "فشل في استيراد ملف Excel: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        lumpSumList
    }

    suspend fun updateProjectContractValue(lumpSumList: List<LumpSumItem>): Boolean = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext false
        val docRef = db.collection("organizations").document(userId)
            .collection("projects").document(projectId)

        val totalValue = lumpSumList.sumOf { it.totalValue ?: 0.0 }

        return@withContext try {
            docRef.update("contractValue", totalValue).await()
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "فشل في تحديث قيمة العقد: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }
}

