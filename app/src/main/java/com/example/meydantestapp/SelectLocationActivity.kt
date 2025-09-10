package com.example.meydantestapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.meydantestapp.databinding.ActivitySelectLocationBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import java.util.*

class SelectLocationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivitySelectLocationBinding
    private lateinit var map: GoogleMap
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var selectedMarker: Marker? = null
    private var initialLatitude: Double? = null
    private var initialLongitude: Double? = null
    private val LOCATION_PERMISSION_REQUEST_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // تهيئة Places API هنا بشكل مبكر ونهائي (للتأكيد مرة أخرى)
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initialLatitude = intent.getDoubleExtra("latitude", 0.0).takeIf { it != 0.0 }
        initialLongitude = intent.getDoubleExtra("longitude", 0.0).takeIf { it != 0.0 }

        mapView = binding.mapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        // زر حفظ الموقع (FAB)
        binding.btnSaveLocation.setOnClickListener {
            selectedMarker?.let {
                val geocoder = Geocoder(this, Locale.getDefault())
                val address = geocoder.getFromLocation(it.position.latitude, it.position.longitude, 1)
                    ?.firstOrNull()?.getAddressLine(0) ?: ""

                val intent = Intent()
                intent.putExtra("latitude", it.position.latitude)
                intent.putExtra("longitude", it.position.longitude)
                intent.putExtra("address", address)
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }

        // زر تغيير نوع الخريطة (الطبقات)
        binding.layersButton.setOnClickListener {
            toggleMapType()
        }

        initPlacesSearch()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        applyMapStyle()

        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled = true // تأكد من تفعيل البوصلة
        map.uiSettings.isMapToolbarEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true // الإبقاء على زر GPS التلقائي

        // **تعديل الـ padding لدفع العناصر التلقائية بعيداً عن عناصرك المخصصة:**
        // القيم (left, top, right, bottom)
        // left: 80dp لدفع زر GPS التلقائي بعيداً عن زر الطبقات
        // top: 200dp لدفع كل شيء أسفل شريط البحث
        // right: 0 (لا يوجد عنصر مخصص على اليمين)
        // bottom: 100dp لدفع أزرار الزوم للأعلى بعيداً عن زر الحفظ
        map.setPadding(80, 200, 0, 100)

        if (initialLatitude != null && initialLongitude != null) {
            val latLng = LatLng(initialLatitude!!, initialLongitude!!)
            moveToLocation(latLng, "الموقع المحفوظ")
        } else {
            enableUserLocation() // تمكين موقع المستخدم عند الدخول لأول مرة
        }

        map.setOnMapClickListener { latLng ->
            moveToLocation(latLng, "الموقع المحدد")
        }
    }

    private fun moveToLocation(latLng: LatLng, title: String) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        selectedMarker?.remove()
        selectedMarker = map.addMarker(MarkerOptions().position(latLng).title(title))
    }

    private fun enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    moveToLocation(userLatLng, "موقعي الحالي")
                }
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
        try {
            val nightMode = AppCompatDelegate.getDefaultNightMode()
            val styleResId = if (nightMode == AppCompatDelegate.MODE_NIGHT_YES)
                R.raw.map_style_night else R.raw.map_style_day
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, styleResId))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleMapType() {
        map.mapType = when (map.mapType) {
            GoogleMap.MAP_TYPE_NORMAL -> GoogleMap.MAP_TYPE_SATELLITE
            GoogleMap.MAP_TYPE_SATELLITE -> GoogleMap.MAP_TYPE_TERRAIN
            GoogleMap.MAP_TYPE_TERRAIN -> GoogleMap.MAP_TYPE_HYBRID
            else -> GoogleMap.MAP_TYPE_NORMAL
        }
    }

    private fun initPlacesSearch() {
        // تم نقل تهيئة Places.initialize() إلى onCreate()
        val autocompleteFragment = supportFragmentManager
            .findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment

        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                place.latLng?.let {
                    moveToLocation(it, place.name ?: "")
                }
            }

            override fun onError(status: com.google.android.gms.common.api.Status) {
                Toast.makeText(this@SelectLocationActivity, "فشل في البحث: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}