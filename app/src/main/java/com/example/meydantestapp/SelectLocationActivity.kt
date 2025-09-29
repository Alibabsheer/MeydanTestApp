package com.example.meydantestapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import com.example.meydantestapp.databinding.ActivitySelectLocationBinding
import com.example.meydantestapp.util.PlacesInitializer
import com.example.meydantestapp.util.PlayServicesUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import java.util.Locale

class SelectLocationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivitySelectLocationBinding
    private var map: GoogleMap? = null
    private var mapView: MapView? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var selectedMarker: Marker? = null
    private var initialLatitude: Double? = null
    private var initialLongitude: Double? = null
    private val LOCATION_PERMISSION_REQUEST_CODE = 1002
    private var isMapViewInitialized = false
    private var arePlacesReady = false

    companion object {
        private const val TAG = "SelectLocationActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initialLatitude = intent.getDoubleExtra("latitude", 0.0).takeIf { it != 0.0 }
        initialLongitude = intent.getDoubleExtra("longitude", 0.0).takeIf { it != 0.0 }

        mapView = binding.mapView

        val servicesOk = PlayServicesUtils.ensureAvailableOrExplain(this)
        arePlacesReady = if (servicesOk) PlacesInitializer.initIfNeeded(this) else false

        if (!arePlacesReady) {
            Log.w(TAG, "Maps/Places disabled due to missing services.")
            disableMapsUi()
            Toast.makeText(
                this,
                "خدمات Google غير متوفرة. تم تعطيل ميزات الخريطة مؤقتًا.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            mapView?.onCreate(savedInstanceState)
            isMapViewInitialized = true
            mapView?.getMapAsync(this)
            initPlacesSearch()
        }

        binding.btnSaveLocation.setOnClickListener {
            selectedMarker?.let {
                val geocoder = Geocoder(this, Locale.getDefault())
                val geoAddress = geocoder.getFromLocation(it.position.latitude, it.position.longitude, 1)
                    ?.firstOrNull()
                val addressText = geoAddress?.getAddressLine(0) ?: ""
                val plusCode = extractPlusCode(geoAddress)

                val intent = Intent()
                intent.putExtra("latitude", it.position.latitude)
                intent.putExtra("longitude", it.position.longitude)
                intent.putExtra("address", addressText)
                plusCode?.let { code -> intent.putExtra("plusCode", code) }
                setResult(Activity.RESULT_OK, intent)
                finish()
            } ?: run {
                Toast.makeText(this, "يرجى اختيار موقع على الخريطة أولاً.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.layersButton.setOnClickListener {
            if (!arePlacesReady || map == null) {
                Toast.makeText(
                    this,
                    "الخريطة غير جاهزة بعد.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                toggleMapType()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        Log.d(TAG, "Map is ready")

        applyMapStyle()

        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isCompassEnabled = true // تأكد من تفعيل البوصلة
        googleMap.uiSettings.isMapToolbarEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = true // الإبقاء على زر GPS التلقاي

        // **تعديل الـ padding لدفع العناصر التلقائية بعيداً عن عناصرك المخصصة:**
        // القيم (left, top, right, bottom)
        // left: 80dp لدفع زر GPS التلقائي بعيداً عن زر الطبقات
        // top: 200dp لدفع كل شيء أسفل شريط البحث
        // right: 0 (لا يوجد عنصر مخصص على اليمين)
        // bottom: 100dp لدفع أزرار الزوم للأعلى بعيداً عن زر الحفظ
        googleMap.setPadding(80, 200, 0, 100)

        if (initialLatitude != null && initialLongitude != null) {
            val latLng = LatLng(initialLatitude!!, initialLongitude!!)
            moveToLocation(latLng, "الموقع المحفوظ")
        } else {
            enableUserLocation() // تمكين موقع المستخدم عند الدخول لأول مرة
        }

        googleMap.setOnMapClickListener { latLng ->
            moveToLocation(latLng, "الموقع المحدد")
        }
    }

    private fun moveToLocation(latLng: LatLng, title: String) {
        val currentMap = map
        if (currentMap == null) {
            Log.w(TAG, "moveToLocation called before map ready")
            return
        }
        currentMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        selectedMarker?.remove()
        selectedMarker = currentMap.addMarker(MarkerOptions().position(latLng).title(title))
    }

    private fun enableUserLocation() {
        val currentMap = map
        val client = fusedLocationClient
        if (currentMap == null || client == null) {
            Log.w(TAG, "enableUserLocation called before dependencies ready")
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                currentMap.isMyLocationEnabled = true
            } catch (security: SecurityException) {
                Log.e(TAG, "Failed to enable my location layer", security)
            }
            client.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    moveToLocation(userLatLng, "موقعي الحالي")
                }
            }.addOnFailureListener { error ->
                Log.e(TAG, "Failed to obtain last location", error)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation()
        } else {
            Toast.makeText(this, "يرجى منح إذن الموقع لعرض موقعك.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyMapStyle() {
        val currentMap = map ?: return
        try {
            val nightMode = AppCompatDelegate.getDefaultNightMode()
            val styleResId = if (nightMode == AppCompatDelegate.MODE_NIGHT_YES)
                R.raw.map_style_night else R.raw.map_style_day
            currentMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, styleResId))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply map style", e)
        }
    }

    private fun toggleMapType() {
        val currentMap = map
        if (currentMap == null) {
            Log.w(TAG, "toggleMapType called before map ready")
            Toast.makeText(this, "الخريطة غير جاهزة بعد.", Toast.LENGTH_SHORT).show()
            return
        }
        currentMap.mapType = when (currentMap.mapType) {
            GoogleMap.MAP_TYPE_NORMAL -> GoogleMap.MAP_TYPE_SATELLITE
            GoogleMap.MAP_TYPE_SATELLITE -> GoogleMap.MAP_TYPE_TERRAIN
            GoogleMap.MAP_TYPE_TERRAIN -> GoogleMap.MAP_TYPE_HYBRID
            else -> GoogleMap.MAP_TYPE_NORMAL
        }
    }

    private fun initPlacesSearch() {
        try {
            val fragment = supportFragmentManager
                .findFragmentById(R.id.autocomplete_fragment) as? AutocompleteSupportFragment

            if (fragment == null) {
                Log.w(TAG, "Autocomplete fragment not found")
                return
            }

            fragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))

            fragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
                override fun onPlaceSelected(place: Place) {
                    place.latLng?.let {
                        moveToLocation(it, place.name ?: "")
                    }
                }

                override fun onError(status: com.google.android.gms.common.api.Status) {
                    Log.e(TAG, "Autocomplete error: ${status.statusMessage}")
                    Toast.makeText(
                        this@SelectLocationActivity,
                        "فشل في البحث: ${status.statusMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        } catch (error: Exception) {
            Log.e(TAG, "Failed to initialize Places search", error)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isMapViewInitialized) {
            mapView?.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isMapViewInitialized) {
            mapView?.onPause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isMapViewInitialized) {
            mapView?.onDestroy()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (isMapViewInitialized) {
            mapView?.onLowMemory()
        }
    }

    private fun extractPlusCode(address: Address?): String? {
        if (address == null) return null
        val extras = address.extras
        val fromExtras = extras?.let {
            sequenceOf("plus_code", "PLUS_CODE", "plusCode", "global_code", "compound_code")
                .mapNotNull { key -> it.getString(key)?.trim()?.takeIf { value -> value.isNotEmpty() } }
                .firstOrNull()
        }
        if (!fromExtras.isNullOrBlank()) {
            return fromExtras
        }
        val feature = address.featureName?.trim()
        return feature?.takeIf { it.isNotEmpty() && it.contains('+') }
    }

    private fun disableMapsUi() {
        binding.mapView.visibility = View.INVISIBLE
        binding.layersButton.isEnabled = false
        binding.layersButton.alpha = 0.5f
        supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)?.let { fragment ->
            supportFragmentManager.beginTransaction().hide(fragment).commitAllowingStateLoss()
        }
    }
}
