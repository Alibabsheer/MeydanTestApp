package com.example.meydantestapp.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    
    private val displayDateFormat = SimpleDateFormat(Constants.DATE_FORMAT_DISPLAY, Locale.getDefault())
    private val timestampFormat = SimpleDateFormat(Constants.DATE_FORMAT_TIMESTAMP, Locale.getDefault())
    
    fun formatDateForDisplay(date: Date): String {
        return displayDateFormat.format(date)
    }
    
    fun formatDateWithTime(date: Date): String {
        return timestampFormat.format(date)
    }
    
    fun getCurrentDate(): Date {
        return Date()
    }
    
    fun getCurrentDateString(): String {
        return formatDateForDisplay(getCurrentDate())
    }
    
    fun getCurrentTimestamp(): String {
        return formatDateWithTime(getCurrentDate())
    }
    
    fun parseDateFromString(dateString: String): Date? {
        return try {
            displayDateFormat.parse(dateString)
        } catch (e: Exception) {
            null
        }
    }
    
    fun isToday(date: Date): Boolean {
        val today = Calendar.getInstance()
        val targetDate = Calendar.getInstance().apply { time = date }
        
        return today.get(Calendar.YEAR) == targetDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == targetDate.get(Calendar.DAY_OF_YEAR)
    }
    
    fun getDaysDifference(startDate: Date, endDate: Date): Long {
        val diffInMillis = endDate.time - startDate.time
        return diffInMillis / (1000 * 60 * 60 * 24)
    }
}

