package com.example.meydantestapp

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val daily: Daily
)

data class Daily(
    val time: List<String>,
    @SerializedName("temperature_2m_max")
    val temperatureMax: List<Double>,
    val weathercode: List<Int>
)