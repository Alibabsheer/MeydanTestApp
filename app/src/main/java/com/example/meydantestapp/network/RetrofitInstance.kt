package com.example.meydantestapp.network

import com.example.meydantestapp.WeatherApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {

    // تغيير الرابط الأساسي (BASE_URL) إلى Forecast API
    private const val BASE_URL = "https://api.open-meteo.com/"

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: WeatherApiService by lazy {
        retrofit.create(WeatherApiService::class.java)
    }
}