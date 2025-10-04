package com.example.meydantestapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
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
import com.google.android.libraries.places.R as PlacesR
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
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
    private var placesClient: PlacesClient? = null
    private var autocompleteSessionToken: AutocompleteSessionToken? = null
    private var autocompleteFragment: AutocompleteSupportFragment? = null
    private var searchInputView: EditText? = null
    private var pendingCameraUpdate: PendingCameraUpdate? = null
    private var searchButtonView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initialLatitude = intent.getDoubleExtra("latitude", 0.0).takeIf { it != 0.0 }
        initialLongitude = intent.getDoubleExtra("longitude", 0.0).takeIf { it != 0.0 }

        mapView = binding.mapView

        val servicesOk = PlayServicesUtils.ensureAvailableOrExplain(this)
        arePlacesReady = if (servicesOk) PlacesInitializer.initIfNeeded(this) else false

        binding.layersButton.isEnabled = false

        if (!arePlacesReady) {
            Log.w(TAG, "Maps/Places disabled due to missing services.")
            disableMapsUi()
            binding.layersButton.isEnabled = false
            Toast.makeText(
                this,
                "خدمات Google غير متوفرة. تم تعطيل ميزات الخريطة مؤقتًا.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            placesClient = Places.createClient(this)
            autocompleteSessionToken = AutocompleteSessionToken.newInstance()
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

    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        val savedType = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(KEY_MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL)
        googleMap.mapType = savedType
        Log.d(MAP_LAYERS_TAG, "Map ready, restored type $savedType")
        Log.d(TAG, "Map is ready")

        applyMapStyle()

        setupLayersButton()

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

        pendingCameraUpdate?.let { pending ->
            pendingCameraUpdate = null
            moveToLocation(pending.latLng, pending.title, pending.searchMetadata)
        }
    }

    private fun moveToLocation(
        latLng: LatLng,
        title: String,
        searchMetadata: SearchMetadata? = null
    ) {
        val currentMap = map
        if (currentMap == null) {
            pendingCameraUpdate = PendingCameraUpdate(latLng, title, searchMetadata)
            if (searchMetadata != null) {
                Log.d(
                    PLACES_TAG,
                    "Map not ready, queuing camera move for query=\"${searchMetadata.query}\""
                )
            } else {
                Log.w(TAG, "moveToLocation called before map ready")
            }
            Toast.makeText(this, "الخريطة غير جاهزة بعد", Toast.LENGTH_SHORT).show()
            return
        }
        pendingCameraUpdate = null
        currentMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        selectedMarker?.remove()
        selectedMarker = currentMap.addMarker(MarkerOptions().position(latLng).title(title))
        searchMetadata?.let {
            val elapsed = SystemClock.elapsedRealtime() - it.startTimeMs
            Log.d(
                PLACES_TAG,
                "Query=\"${it.query}\" moveCamera in ${elapsed}ms (placeId=${it.placeId})"
            )
        }
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

    private fun setupLayersButton() {
        val layersButton = binding.layersButton
        layersButton.isEnabled = true
        layersButton.alpha = 1f
        layersButton.setOnClickListener {
            val currentMap = map
            if (currentMap == null) {
                Log.w(MAP_LAYERS_TAG, "Layers button tapped before map ready")
                Toast.makeText(this, "الخريطة غير جاهزة بعد", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val nextType = nextMapType(currentMap.mapType)
            currentMap.mapType = nextType
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(KEY_MAP_TYPE, nextType)
                .apply()
            Log.d(MAP_LAYERS_TAG, "Map type set to $nextType")
            val label = when (nextType) {
                GoogleMap.MAP_TYPE_NORMAL -> "الوضع: عادي"
                GoogleMap.MAP_TYPE_SATELLITE -> "الوضع: قمر صناعي"
                GoogleMap.MAP_TYPE_TERRAIN -> "الوضع: تضاريس"
                GoogleMap.MAP_TYPE_HYBRID -> "الوضع: هجين"
                else -> "الوضع: عادي"
            }
            Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
        }
    }

    private fun nextMapType(current: Int): Int = when (current) {
        GoogleMap.MAP_TYPE_NORMAL -> GoogleMap.MAP_TYPE_SATELLITE
        GoogleMap.MAP_TYPE_SATELLITE -> GoogleMap.MAP_TYPE_TERRAIN
        GoogleMap.MAP_TYPE_TERRAIN -> GoogleMap.MAP_TYPE_HYBRID
        else -> GoogleMap.MAP_TYPE_NORMAL
    }

    private fun initPlacesSearch() {
        try {
            val fragment = supportFragmentManager
                .findFragmentById(R.id.autocomplete_fragment) as? AutocompleteSupportFragment

            if (fragment == null) {
                Log.w(TAG, "Autocomplete fragment not found")
                return
            }

            if (placesClient == null || autocompleteSessionToken == null) {
                Log.w(PLACES_TAG, "Places client or token not ready for search init")
                return
            }

            autocompleteFragment = fragment

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

            fragment.viewLifecycleOwnerLiveData.observe(this) { owner ->
                if (owner != null) {
                    configureSearchInputs(fragment)
                }
            }

            fragment.view?.let { configureSearchInputs(fragment) }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to initialize Places search", error)
        }
    }

    private fun configureSearchInputs(fragment: AutocompleteSupportFragment) {
        val fragmentView = fragment.view
        if (fragmentView == null) {
            Log.w(TAG, "Autocomplete fragment view not available yet")
            return
        }
        val input = fragmentView.findViewById<EditText>(PlacesR.id.places_autocomplete_search_input)
        val searchButton = fragmentView.findViewById<View>(PlacesR.id.places_autocomplete_search_button)

        searchInputView = input
        searchButtonView = searchButton

        input?.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performPlacesSearch(textView.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }

        searchButton?.setOnClickListener {
            performPlacesSearch(input?.text?.toString().orEmpty())
        }
    }

    private fun performPlacesSearch(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "يرجى إدخال نص للبحث.", Toast.LENGTH_SHORT).show()
            return
        }

        val client = placesClient
        if (client == null) {
            Toast.makeText(this, "خدمة البحث غير متاحة حالياً.", Toast.LENGTH_SHORT).show()
            Log.w(PLACES_TAG, "Places client missing when attempting search")
            return
        }

        val token = autocompleteSessionToken ?: AutocompleteSessionToken.newInstance().also {
            autocompleteSessionToken = it
        }

        val startTime = SystemClock.elapsedRealtime()

        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(token)
            .build()

        client.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val predictions = response.autocompletePredictions
                Log.d(PLACES_TAG, "Query=\"$query\" predictions=${predictions.size}")
                if (predictions.isEmpty()) {
                    Toast.makeText(this, "لم يتم العثور على نتائج للبحث", Toast.LENGTH_SHORT).show()
                    Log.d(PLACES_TAG, "No results for query=\"$query\"")
                    return@addOnSuccessListener
                }

                val topPrediction = predictions.first()
                val placeId = topPrediction.placeId
                Log.d(PLACES_TAG, "Query=\"$query\" using placeId=$placeId")

                val fetchRequest = FetchPlaceRequest.builder(
                    placeId,
                    listOf(Place.Field.LAT_LNG, Place.Field.NAME)
                )
                    .setSessionToken(token)
                    .build()

                client.fetchPlace(fetchRequest)
                    .addOnSuccessListener { fetchResponse ->
                        val place = fetchResponse.place
                        val latLng = place.latLng
                        if (latLng != null) {
                            val title = place.name ?: query
                            autocompleteFragment?.setText(title)
                            val metadata = SearchMetadata(query, placeId, startTime)
                            moveToLocation(latLng, title, metadata)
                        } else {
                            Toast.makeText(
                                this,
                                "تعذّر تحديد موقع هذا المكان.",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.w(
                                PLACES_TAG,
                                "Query=\"$query\" placeId=$placeId missing latLng"
                            )
                        }
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(
                            this,
                            "تعذّر جلب تفاصيل الموقع المحدد.",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e(
                            PLACES_TAG,
                            "Failed to fetch place for query=\"$query\" placeId=$placeId",
                            error
                        )
                    }
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "حدث خطأ أثناء البحث: ${error.localizedMessage ?: ""}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e(PLACES_TAG, "findAutocompletePredictions failed for query=\"$query\"", error)
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
        searchInputView?.isEnabled = false
        searchButtonView?.isEnabled = false
        supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)?.let { fragment ->
            fragment.view?.let { view ->
                view.isEnabled = false
                view.alpha = 0.5f
                view.findViewById<View>(PlacesR.id.places_autocomplete_search_button)?.isEnabled = false
                view.findViewById<EditText>(PlacesR.id.places_autocomplete_search_input)?.isEnabled = false
            }
            supportFragmentManager.beginTransaction().hide(fragment).commitAllowingStateLoss()
        }
    }

    private data class SearchMetadata(val query: String, val placeId: String, val startTimeMs: Long)

    private data class PendingCameraUpdate(
        val latLng: LatLng,
        val title: String,
        val searchMetadata: SearchMetadata?
    )

    companion object {
        private const val TAG = "SelectLocationActivity"
        private const val PLACES_TAG = "PlacesSearch"
        private const val PREFS_NAME = "map_prefs"
        private const val KEY_MAP_TYPE = "map_type"
        private const val MAP_LAYERS_TAG = "MapLayers"
    }
}
