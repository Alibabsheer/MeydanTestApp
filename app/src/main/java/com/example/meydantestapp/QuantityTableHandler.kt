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

class QuantityTableHandler(private val context: Context, private val projectId: String) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun loadTable(): MutableList<QuantityItem> = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext mutableListOf()
        val docRef = db.collection("organizations").document(userId)
            .collection("projects").document(projectId)

        val quantityList = mutableListOf<QuantityItem>()
        try {
            val doc = docRef.get().await()
            if (doc.exists()) {
                val rawList = doc.get("quantitiesTable")
                val list = if (rawList is List<*>) {
                    rawList.filterIsInstance<Map<String, Any>>()
                } else {
                    emptyList()
                }
                quantityList.addAll(list.map {
                    QuantityItem(
                        itemNumber = it["itemNumber"] as? String,
                        description = it["description"] as? String,
                        unit = it["unit"] as? String,
                        quantity = (it["quantity"] as? Number)?.toDouble(),
                        unitPrice = (it["unitPrice"] as? Number)?.toDouble(),
                        totalValue = (it["totalValue"] as? Number)?.toDouble()
                    )
                })
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "فشل في تحميل جدول الكميات: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        quantityList
    }

    suspend fun saveTable(quantityList: List<QuantityItem>): Boolean = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext false
        val docRef = db.collection("organizations").document(userId)
            .collection("projects").document(projectId)

        return@withContext try {
            docRef.update("quantitiesTable", quantityList).await()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "تم حفظ جدول الكميات بنجاح", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "فشل حفظ جدول الكميات: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    suspend fun deleteTable(): Boolean = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext false
        val docRef = db.collection("organizations").document(userId)
            .collection("projects").document(projectId)

        return@withContext try {
            docRef.update("quantitiesTable", null).await()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "تم حذف جدول الكميات بنجاح", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "فشل حذف جدول الكميات: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    suspend fun importExcel(uri: Uri): MutableList<QuantityItem> = withContext(Dispatchers.IO) {
        val quantityList = mutableListOf<QuantityItem>()
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext mutableListOf()
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)
            val dataFormatter = DataFormatter()
            val title = sheet.getRow(0)?.getCell(0)?.stringCellValue ?: ""

            if (title.contains("Quantity-Based")) {
                for (i in 2..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    val item = QuantityItem(
                        itemNumber = dataFormatter.formatCellValue(row.getCell(0)),
                        description = dataFormatter.formatCellValue(row.getCell(1)),
                        unit = dataFormatter.formatCellValue(row.getCell(2)),
                        quantity = row.getCell(3)?.numericCellValue ?: 0.0,
                        unitPrice = row.getCell(4)?.numericCellValue ?: 0.0,
                        totalValue = row.getCell(5)?.numericCellValue ?: 0.0
                    )
                    quantityList.add(item)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "تم استيراد جدول الكميات بنجاح", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "نوع ملف Excel غير مدعوم أو التنسيق غير صحيح. يرجى التأكد من أن الملف يبدأ بـ 'Quantity-Based'.", Toast.LENGTH_LONG).show()
                }
            }
            inputStream.close()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "فشل في استيراد ملف Excel: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        quantityList
    }

    suspend fun updateProjectContractValue(quantityList: List<QuantityItem>): Boolean = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext false
        val docRef = db.collection("organizations").document(userId)
            .collection("projects").document(projectId)

        val totalValue = quantityList.sumOf { it.totalValue ?: 0.0 }

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

