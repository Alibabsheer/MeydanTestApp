package com.example.meydantestapp

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import com.example.meydantestapp.WeatherResponse

interface WeatherApiService {
    // تعديل نقطة النهاية (endpoint) لاستخدام Forecast API
    @GET("v1/forecast?daily=weathercode,temperature_2m_max&timezone=auto")
    suspend fun getForecastWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String
    ): Response<WeatherResponse>
}