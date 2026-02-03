package com.example.meydantestapp.util

import android.content.Context
import android.util.Log
import com.example.meydantestapp.R
import com.google.android.libraries.places.api.Places
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object PlacesInitializer {
    private val inited = AtomicBoolean(false)
    private const val TAG = "PlacesInitializer"

    fun initIfNeeded(context: Context): Boolean {
        if (!PlayServicesUtils.isAvailable(context)) {
            Log.w(TAG, "Google Play Services not available; skipping Places init.")
            return false
        }
        if (inited.get()) {
            Log.d(TAG, "Places already initialized")
            return true
        }
        return try {
            if (!Places.isInitialized()) {
                val key = context.getString(R.string.google_maps_key)
                if (key.isBlank()) {
                    Log.e(TAG, "Missing Google Maps API key; cannot initialize Places")
                    return false
                }
                Places.initialize(context.applicationContext, key, Locale.getDefault())
                Log.i(TAG, "Places API initialized")
            } else {
                Log.d(TAG, "Places API previously initialized by SDK")
            }
            inited.set(true)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize Places", e)
            false
        }
    }
}
