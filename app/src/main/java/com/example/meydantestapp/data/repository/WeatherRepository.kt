package com.example.meydantestapp.repository

import com.example.meydantestapp.WeatherApiService
import com.example.meydantestapp.WeatherResponse
import com.example.meydantestapp.network.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * مسؤول عن جلب حالة الطقس لليوم المحدد وإرجاع (درجة الحرارة القصوى + وصف الحالة).
 * يستخدم RetrofitInstance.api (المهيأ في الحزمة com.example.meydantestapp.network)
 * وواجهة WeatherApiService (في الحزمة com.example.meydantestapp).
 */
class WeatherRepository(
    private val api: WeatherApiService = RetrofitInstance.api
) {
    data class WeatherResult(
        val temperatureMax: Double?,
        val weatherDescription: String?
    )

    /**
     * @param lat  إحداثي خط العرض
     * @param lng  إحداثي خط الطول
     * @param dateIso  تاريخ التقرير بصيغة yyyy-MM-dd
     */
    suspend fun getDailyWeather(lat: Double, lng: Double, dateIso: String): WeatherResult =
        withContext(Dispatchers.IO) {
            try {
                // ملاحظة: @GET في WeatherApiService يحدد daily=weathercode,temperature_2m_max&timezone=auto
                val response = api.getForecastWeather(
                    latitude = lat,
                    longitude = lng,
                    startDate = dateIso,
                    endDate = dateIso
                )

                if (!response.isSuccessful) {
                    // يمكنك تسجيل كود/رسالة الخطأ إن رغبت
                    return@withContext WeatherResult(null, null)
                }

                val body: WeatherResponse? = response.body()
                val t = body?.daily?.temperatureMax?.firstOrNull()
                val code = body?.daily?.weathercode?.firstOrNull()

                WeatherResult(
                    temperatureMax = t,
                    weatherDescription = code?.let { toArabicDescription(it) }
                )
            } catch (e: Exception) {
                // في حال أي استثناء (شبكة/تحويل)
                WeatherResult(null, null)
            }
        }

    private fun toArabicDescription(code: Int): String = when (code) {
        0 -> "صافٍ"
        1, 2, 3 -> "غائم جزئيًا"
        45, 48 -> "ضباب"
        51, 53, 55 -> "رذاذ خفيف"
        56, 57 -> "رذاذ متجمد"
        61, 63, 65 -> "مطر"
        66, 67 -> "مطر متجمد"
        71, 73, 75 -> "ثلج"
        77 -> "حبيبات ثلج"
        80, 81, 82 -> "عواصف مطرية"
        85, 86 -> "عواصف ثلجية"
        95 -> "عواصف رعدية"
        96, 99 -> "عواصف رعدية مع حبات برد"
        else -> "غير معروف"
    }
}
