package com.example.projectgothere

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.location.Address
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.example.projectgothere.databinding.ActivityMainBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.*
import okhttp3.internal.userAgent
import okio.IOException
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.bonuspack.location.GeoNamesPOIProvider
import org.osmdroid.bonuspack.location.GeocoderNominatim
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.bonuspack.location.OverpassAPIProvider
import org.osmdroid.bonuspack.location.POI
import org.osmdroid.bonuspack.utils.BonusPackHelper
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.util.ManifestUtil
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Marker.OnMarkerDragListener
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.lang.Integer.parseInt
import java.util.*
import kotlin.random.Random


private const val TAG = "MainActivity"
private const val SHARED_PREFS_APPKEY = "Project GoThere"
private const val PREF_LOCATIONS_KEY = "PREF_LOCATIONS"
private const val START_INDEX = -2
private const val DEST_INDEX = -1
private const val WAYPOINT_INDEX = -3
private var startingPoint: GeoPoint? = null
private var destinationPoint: GeoPoint? = null
private lateinit var waypoints: ArrayList<GeoPoint>

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity(){

    private var currentPoint: GeoPoint? = null
    private var startMarker : Marker? = null
    private var endMarker : Marker? = null
    private var currentMarker: Marker? = null
    private lateinit var map : MapView
    private var destinationPolygon: Polygon? = null
    private var roads : Array<Road>? = null

    private var mItineraryMarkers = FolderOverlay()
    private var roadNodeMarkers = FolderOverlay()
    private var pois: ArrayList<POI>? = null

    private lateinit var roadManager: RoadManager
    private var roadOverlay: ArrayList<Polyline>? = null
    private lateinit var geonamesAccount: String
    private lateinit var mViaPointInfoWindow: WaypointInfoWindow
    private lateinit var poiTagText : AutoCompleteTextView
    private lateinit var mPoiMarkers:RadiusMarkerClusterer
    private lateinit var locationOverlay:MyLocationNewOverlay
    private lateinit var poiProvider: GeoNamesPOIProvider

    private var currentLocation: GeoPoint = GeoPoint(44.3242, -93.9760)
    private var extraStops: Int = 2
    private lateinit var binding: ActivityMainBinding
    private var cityAddress: String? = null
    private var countyAddress: String? = null
    private var streetAddress: String? = null
    private var completeAddress: String? = null
    private var desiredType: String? = null
    private var propertyType: String? = null
    private var properties: ArrayList<String>? = null

    private var isReadPermissionGranted = false
    private var isWritePermissionGranted = false
    private var isLocationPermissionGranted = false
    private var isCameraPermissionGranted = false
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>


    override fun onCreate(savedInstanceState: Bundle?) {
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ permissions ->
            isReadPermissionGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: isReadPermissionGranted
            isWritePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: isWritePermissionGranted
            isLocationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: isLocationPermissionGranted
            isCameraPermissionGranted = permissions[Manifest.permission.CAMERA] ?: isCameraPermissionGranted
        }

        map = binding.map
        map.setMultiTouchControls(true)
        requestPermission()
        getLocation()
        val intent = Intent(this,WelcomePageActivity::class.java)
        startActivity(intent)

        geonamesAccount = ManifestUtil.retrieveKey(this, "GEONAMES_ACCOUNT")
        roadManager = OSRMRoadManager(this, "MY_USER_AGENT")

        startingPoint = currentLocation
        //GeoPoint(46.7867, -92.1005)

        waypoints = ArrayList()
        waypoints.add(startingPoint!!)

        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(applicationContext), map)
        locationOverlay.enableMyLocation()
        map.overlays.add(locationOverlay)

        map.controller.zoomTo(9.0)
        map.controller.setCenter(startingPoint)

        mItineraryMarkers = FolderOverlay()
        mItineraryMarkers.name = getString(R.string.itinerary_markers_title)
        map.overlays.add(mItineraryMarkers)
        mViaPointInfoWindow = WaypointInfoWindow(R.layout.itinerary_bubble, map)
        updateUIWithItineraryMarkers()

        poiProvider = GeoNamesPOIProvider(geonamesAccount)

        if (roads != null) updateUIWithRoads(roads!!)
        mPoiMarkers = RadiusMarkerClusterer(this)
        val clusterIcon =
            BonusPackHelper.getBitmapFromVectorDrawable(this, R.drawable.marker_poi_cluster)
        mPoiMarkers.setIcon(clusterIcon)
        mPoiMarkers.mAnchorV = Marker.ANCHOR_BOTTOM
        mPoiMarkers.mTextAnchorU = 0.70f
        mPoiMarkers.mTextAnchorV = 0.27f
        mPoiMarkers.textPaint.textSize = 12 * resources.displayMetrics.density
        map.overlays.add(mPoiMarkers)

        val spinPropType : Spinner = binding.propTypeDd
        val propAdapter : ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(this,
            R.array.propTypes, android.R.layout.simple_spinner_item)
        propAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item)
        spinPropType.adapter = propAdapter
        getSpinnerVal(spinPropType)

        val spinStopsDes : Spinner = binding.stopsDesiredDd
        val stopsAdapter : ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(this,
            R.array.amtStopsDesired, android.R.layout.simple_spinner_item)
        stopsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item)
        spinStopsDes.adapter = stopsAdapter
        getSpinnerVal(spinStopsDes)


        binding.cameraButton.setOnClickListener{
            showDialog()
        }
        //start
        binding.editDeparture.setPrefKeys(SHARED_PREFS_APPKEY, PREF_LOCATIONS_KEY)

        binding.buttonSearchDep.setOnClickListener{
            handleSearchButton(
                START_INDEX,
                R.id.editDeparture
            )
        }

        binding.editDestination.setPrefKeys(SHARED_PREFS_APPKEY, PREF_LOCATIONS_KEY)

        binding.buttonSearchDest.setOnClickListener{
            handleSearchButton(
                DEST_INDEX,
                R.id.editDestination
            )
        } //end

        map.invalidate()
    }

    private fun showDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.bottomsheetlayout)

        val currentTripLayout : LinearLayout = dialog.findViewById(R.id.layoutCurrentTrip)
        val prevTripLayout : LinearLayout = dialog.findViewById(R.id.layoutPreviousTrip)
        val takePictureLayout : LinearLayout = dialog.findViewById(R.id.layoutTakePicture)

        currentTripLayout.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this@MainActivity, "Current Trip is Clicked", Toast.LENGTH_SHORT).show()
        }

        prevTripLayout.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this@MainActivity, "Previous Trips is Clicked", Toast.LENGTH_SHORT).show()
        }

        takePictureLayout.setOnClickListener {
            dialog.dismiss()
            val cameraIntent = Intent(this, CameraActivity::class.java)
            startActivity(cameraIntent)
        }

        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(R.color.transparent)
        dialog.window?.attributes?.windowAnimations ?: (R.style.DialogAnimation)
        dialog.window?.setGravity(Gravity.BOTTOM)

    }


    private fun getSpinnerVal(spinner: Spinner){
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View,
                pos: Int,
                id: Long
            ) {
                val item = parent.getItemAtPosition(pos)
                //Log.d(TAG, item.toString()) //prints the text in spinner item.
                val selected = item.toString()
                if (selected.toIntOrNull() != null){
                    extraStops = parseInt(selected)
                    Log.d(TAG, extraStops!!.toString())
                }
                else if (selected != "Filter by" || selected != "Stops Desired"){
                    desiredType = selected
                    Log.d(TAG, desiredType!!)
                    filterPropertyType(desiredType!!)
                    Log.d(TAG, properties.toString())
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun rand(start: Int = 0, end: Int = 1334): Int {
        require(!(start > end || end - start + 1 > Int.MAX_VALUE)) { "Illegal Argument" }
        return Random(System.nanoTime()).nextInt(end - start + 1) + start
    }

    private fun getLocation() {
        var location: Location? = null
        lifecycleScope.executeAsyncTask(
            onPreExecute = {},
            doInBackground = {
                val lm = getSystemService(LOCATION_SERVICE) as LocationManager
                try {
                    location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                } catch (e: SecurityException) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, "Permission Required", Toast.LENGTH_SHORT)
                            .show()
                    }
                } // send data to "onPostExecute"
            },
            onPostExecute = {
                if (location != null) {
                    val loc = GeoPoint(
                        (location!!.latitude),
                        (location!!.longitude)
                    )
                    currentLocation = loc
                }
            }
        )
    }
    private fun addWaypoints(extraStops: Int){
        var k = 0
        while (k<extraStops){
            var waypointID = rand(0, 1334)
            getAddressDataSnapshot(waypointID)
            k++
        }
    }

    private fun getAddressDataSnapshot(waypointID: Int){
        val rootRef = FirebaseDatabase.getInstance().reference
        val addressRef = rootRef.child("SpreadSheet").child(waypointID.toString()).child("Address")
        val valueEventListener: ValueEventListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for ((switch, ds) in dataSnapshot.children.withIndex()) {
                    when (switch){
                        0 -> cityAddress = ds.getValue(String::class.java)
                        1 -> countyAddress = ds.getValue(String::class.java)
                        2 -> streetAddress = ds.getValue(String::class.java)
                    }
                }
                completeAddress = "$streetAddress $cityAddress MN"
                Log.d(TAG, completeAddress!!)
                geocodingTask(completeAddress!!, WAYPOINT_INDEX)

            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.d(TAG, databaseError.message)
            }
        }
        addressRef.addListenerForSingleValueEvent(valueEventListener)
    }
    private fun filterPropertyType(desiredType: String){
        val rootRef = FirebaseDatabase.getInstance().reference
        val propertyRef = rootRef.child("SpreadSheet").orderByChild("Property Type").equalTo(desiredType)
        val valueEventListener: ValueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if(snapshot.exists()){
                    //clear list
                    properties?.clear()
                    for (i in snapshot.children){
                        val property = i.getValue(String::class.java) //this is a hashmap, how to display?
                        if (property != null) {
                            //add to an array
                            properties?.add(property)
                        }
                    }
                    //studentAdapter.submitList(studentsList)
                    //binding.recyclerStudents.adapter = studentAdapter
                } else{
                    Toast.makeText(applicationContext, "Data is not found", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Log.d(TAG, databaseError.message)
            }
        }
        propertyRef.addValueEventListener(valueEventListener)
    }

    private fun getAddress(p: GeoPoint): String {
        val geocoder = GeocoderNominatim(userAgent)
        val theAddress: String? = try {
            val dLatitude = p.latitude
            val dLongitude = p.longitude
            val addresses = geocoder.getFromLocation(dLatitude, dLongitude, 1)
            val sb = StringBuilder()
            if (addresses.size > 0) {
                val address = addresses[0]
                val n = address.maxAddressLineIndex
                for (i in 0..n) {
                    if (i != 0) sb.append(", ")
                    sb.append(address.getAddressLine(i))
                }
                sb.toString()
            } else {
                null
            }
        } catch (e: IOException) {
            null
        }
        return theAddress ?: ""
    }

    private fun geocodingTask(vararg params: Any){
        var index = 0
        lifecycleScope.executeAsyncTask(
            onPreExecute = {},
            doInBackground = {
                val locationAddress = params[0] as String
                index = params[1] as Int
                val geocoder = GeocoderNominatim(userAgent)
                geocoder.setOptions(true) //ask for enclosing polygon (if any)
                try{
                    val viewbox = map.boundingBox
                    geocoder.getFromLocationName(
                        locationAddress, 3,
                        viewbox.latSouth, viewbox.lonEast,
                        viewbox.latNorth, viewbox.lonWest, false
                    )
                } catch (e: Exception){
                    null
                } // send data to "onPostExecute"
            },
            onPostExecute = {
                // ... here "it" is a data returned from "doInBackground"
                if (it == null){
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, "Geocoding error", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else if (it.size == 0){
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, "Address not found", Toast.LENGTH_SHORT)
                            .show()
                        val waypointID = rand(0, 1334)
                        getAddressDataSnapshot(waypointID)
                    }
                } else {
                    val address: Address = it[0] //get first address
                    val addressDisplayName: String? = address.extras.getString("display_name")
                    when (index) {
                        START_INDEX -> {
                            startingPoint = GeoPoint(address.latitude, address.longitude)
                            startMarker = updateItineraryMarker(
                                startMarker, startingPoint, START_INDEX,
                                R.string.departure, R.drawable.marker_departure, -1, addressDisplayName
                            )
                            waypoints[0] = startingPoint!!
                            map.controller.setCenter(startingPoint)
                        }
                        DEST_INDEX -> {
                            destinationPoint = GeoPoint(address.latitude, address.longitude)
                            endMarker = updateItineraryMarker(
                                endMarker, destinationPoint, DEST_INDEX,
                                R.string.destination, R.drawable.marker_destination, -1, addressDisplayName
                            )
                            addWaypoints(extraStops)
                            waypoints.add(destinationPoint!!)
                            map.controller.setCenter(destinationPoint)
                        }
                        WAYPOINT_INDEX -> {
                            currentPoint = GeoPoint(address.latitude, address.longitude)
                            currentMarker = updateItineraryMarker(
                                null, currentPoint, index,
                                R.string.waypoint, R.drawable.waypoint_marker, -1, addressDisplayName
                            )
                            if (destinationPoint != null) waypoints.add(waypoints.size-2, currentPoint!!)
                            else waypoints.add(currentPoint!!)
                            map.controller.setCenter(currentPoint!!)
                        }
                    }
                    Log.d(TAG,"Waypoints: $waypoints")
                    getRoadAsync()
                    //get and display enclosing polygon:
                    if (address.extras.containsKey("polygonpoints")) {
                        val polygon: ArrayList<GeoPoint?>? =
                            address.extras.getParcelableArrayList("polygonpoints")
                        //Log.d("DEBUG", "polygon:"+polygon.size());
                        updateUIWithPolygon(polygon, addressDisplayName)
                    } else {
                        updateUIWithPolygon(null, "")
                    }
                }
            }
        )
    }

    private val mItineraryListener: OnItineraryMarkerDragListener = OnItineraryMarkerDragListener()
    private fun updateItineraryMarker(
        inMarker: Marker?, p: GeoPoint?, index: Int,
        titleResId: Int, markerResId: Int, imageResId: Int, address: String?): Marker {
        var marker = inMarker
        if (marker == null) {
            marker = Marker(map)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.setInfoWindow(mViaPointInfoWindow)
            marker.isDraggable = true
            marker.setOnMarkerDragListener(mItineraryListener)
            mItineraryMarkers.add(marker)
        }
        val title = titleResId.toString()
        marker.title = title
        marker.position = p
        val icon = ContextCompat.getDrawable(this, markerResId)
        marker.icon = icon
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        if (imageResId != -1) marker.image =
            ContextCompat.getDrawable(this, imageResId)
        marker.relatedObject = index
        map.invalidate()
        if (address != null) marker.snippet =
            address else  //Start geocoding task to get the address and update the Marker description:
            reverseGeocodingTask(marker)
        return marker
    }
    private fun getRoadAsync() {
        roads = arrayOf()
        var roadStartPoint: GeoPoint?= null
        if (startingPoint != null){
            roadStartPoint = startingPoint
        } else if (locationOverlay.isEnabled && locationOverlay.myLocation != null){
            roadStartPoint = locationOverlay.myLocation
        }
        if (roadStartPoint == null || destinationPoint == null) {
            updateUIWithRoads(roads!!)
            return
        }
        val newWayPoints = ArrayList<GeoPoint>(2)
        //add intermediate via points:
        for (p in waypoints) {
            newWayPoints.add(p)
        }
        waypoints = newWayPoints
        updateRoadTask(waypoints)
    }
    private fun handleSearchButton(index: Int, editResId: Int) {
        val locationEdit = findViewById<View>(editResId) as EditText
        //Hide the soft keyboard:
        val imm: InputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(locationEdit.windowToken, 0)
        val locationAddress = locationEdit.text.toString()
        if (locationAddress == "") {
            removePoint(index)
            map.invalidate()
            return
        }
        Toast.makeText(this, "Searching:\n$locationAddress", Toast.LENGTH_LONG).show()
        AutoCompleteOnPreferences.storePreference(
            this,
            locationAddress,
            SHARED_PREFS_APPKEY,
            PREF_LOCATIONS_KEY
        )
        geocodingTask(locationAddress, index)
    }//endHandleSearchButton
    private fun updateRoadTask(vararg params: ArrayList<GeoPoint>){
        lifecycleScope.executeAsyncTask(
            onPreExecute = {},
            doInBackground = {
                val gpList = params[0]
                Log.d(TAG, "gpList: $gpList")
                val roadManager = OSRMRoadManager(applicationContext, "MY_USER_AGENT")
                roadManager.getRoads(gpList)
            },
            onPostExecute = {
                roads = it
                Log.d(TAG, "Roads: "+ roads.toString())
                updateUIWithRoads(roads!!)
                poiTagText = AutoCompleteTextView(applicationContext)
                getPOIAsync(poiTagText.text.toString())
            }
        )
    }

    private fun getPOIAsync(tag: String?) {
        mPoiMarkers.items.clear()
        pOILoadingTask(tag)
    }
    private fun getOSMTag(humanReadableFeature: String): String? {
        val map = BonusPackHelper.parseStringMapResource(
            applicationContext, R.array.osm_poi_tags
        )
        return map[humanReadableFeature.lowercase(Locale.getDefault())]
    }
    private fun pOILoadingTask (vararg params: String?){
        var message: String? = null
        val mFeatureTag = params[0]
        val bb = map.boundingBox
        val result = when (mFeatureTag){
            null -> null
            "" -> null
            else -> {
                val overpassProvider = OverpassAPIProvider()
                val osmTag: String? = getOSMTag(mFeatureTag)
                if (osmTag == null) {
                    message = "$mFeatureTag is not a valid feature."
                    null
                }
                val oUrl = overpassProvider.urlForPOISearch(osmTag, bb, 100, 10)
                overpassProvider.getPOIsFromUrl(oUrl)
            }
        }
        if (result != null) {
            pois = result
        }
        if (mFeatureTag == "") {
            //no search, no message
        } else if (pois == null) {
            if (message != null) Toast.makeText(
                applicationContext,
                message,
                Toast.LENGTH_LONG
            ).show() else Toast.makeText(
                applicationContext,
                "Technical issue when getting $mFeatureTag POI.", Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                applicationContext,
                mFeatureTag + " found:" + pois!!.size,
                Toast.LENGTH_LONG
            ).show()
        }
        updateUIWithPOI(pois, mFeatureTag)
    }
    private fun updateUIWithPOI(pois: ArrayList<POI>?, featureTag: String?) {
        if (pois != null) {
            val poiInfoWindow = POIInfoWindow(map)
            //pois = poiProvider.getPOIInside(map.boundingBox, 30)
            for (poi in pois) {
                val poiMarker = Marker(map)
                poiMarker.title = poi.mType
                poiMarker.snippet = poi.mDescription
                poiMarker.position = poi.mLocation
                val icon = ContextCompat.getDrawable(this, R.drawable.marker_node)
                poiMarker.setAnchor(Marker.ANCHOR_CENTER, 1.0f)
                poiMarker.subDescription = poi.mCategory
                poiMarker.icon = icon
                poiMarker.relatedObject = poi
                poiMarker.setInfoWindow(poiInfoWindow)
                //thumbnail loading moved in async task for better performances.
                mPoiMarkers.add(poiMarker)
            }
        }
        mPoiMarkers.name = featureTag
        mPoiMarkers.invalidate()

    }
    private fun selectRoad(roadIndex: Int) {
        putRoadNodes(roads!![roadIndex])
        //Set route info in the text view:
        //val textView = findViewById<View>(R.id.routeInfo) as TextView
        //textView.text = roads[roadIndex].getLengthDurationText(this, -1)
        for (i in 0 until roadOverlay!!.size) {
            val p: Paint = roadOverlay!![i].paint
            if (i == roadIndex) p.color = -0x7fffff01 //blue
            else p.color = -0x6f99999a //grey
        }
        map.invalidate()
    }

    internal class RoadOnClickListener : Polyline.OnClickListener {
        override fun onClick(polyline: Polyline, mapView: MapView, eventPos: GeoPoint): Boolean {
            val selectedRoad = polyline.relatedObject as Int
            MainActivity().selectRoad(selectedRoad)
            polyline.infoWindowLocation = eventPos
            polyline.showInfoWindow()
            return true
        }
    }

    private fun updateUIWithRoads(roads: Array<Road>) {
        roadNodeMarkers.items.clear()
        //val textView = findViewById<View>(R.id.routeInfo) as TextView
        //textView.text = ""
        val mapOverlays = map.overlays
        if (roadOverlay != null) {
            for (i in 0 until roadOverlay!!.size) mapOverlays.remove(roadOverlay!![i])
            roadOverlay = null
        }
        if (roads.isEmpty()) return
        if (roads[0].mStatus == Road.STATUS_TECHNICAL_ISSUE) Toast.makeText(
            map.context,
            "Technical issue when getting the route",
            Toast.LENGTH_SHORT
        ).show() else if (roads[0].mStatus > Road.STATUS_TECHNICAL_ISSUE) //functional issues
            Toast.makeText(map.context, "No possible route here", Toast.LENGTH_SHORT).show()
        roadOverlay = ArrayList<Polyline>()
        for (i in roads.indices) {
            val roadPolyline = RoadManager.buildRoadOverlay(roads[i])
            roadOverlay!!.add(roadPolyline)
            val routeDesc = roads[i].getLengthDurationText(this, -1)
            roadPolyline.title = getString(R.string.route) + " - " + routeDesc
            roadPolyline.infoWindow =
                BasicInfoWindow(org.osmdroid.bonuspack.R.layout.bonuspack_bubble, map)
            roadPolyline.relatedObject = i
            roadPolyline.setOnClickListener(RoadOnClickListener())
            mapOverlays.add(1, roadPolyline)
            //we insert the road overlays at the "bottom", just above the MapEventsOverlay,
            //to avoid covering the other overlays.
        }
        selectRoad(0)
    }

    private fun setViewOn(bb: BoundingBox?) {
        if (bb != null) {
            map.zoomToBoundingBox(bb, true)
        }
    }
    private fun updateUIWithPolygon(polygon: ArrayList<GeoPoint?>?, name: String?) {
        val mapOverlays = map.overlays
        val location = if (destinationPolygon != null) mapOverlays.indexOf(destinationPolygon) else -1
        destinationPolygon = Polygon()
        destinationPolygon!!.fillColor = 0x15FF0080
        destinationPolygon!!.strokeColor = -0x7fffff01
        destinationPolygon!!.strokeWidth = 5.0f
        destinationPolygon!!.title = name
        var bb: BoundingBox? = null
        if (polygon != null) {
            destinationPolygon!!.points = polygon
            bb = BoundingBox.fromGeoPoints(polygon)
        }
        if (location != -1) mapOverlays[location] = destinationPolygon else mapOverlays.add(
            1,
            destinationPolygon
        ) //insert just above the MapEventsOverlay.
        setViewOn(bb)
        map.invalidate()
    }

    //Async task to reverse-geocode the marker position in a separate thread:
    private fun reverseGeocodingTask(vararg params: Marker?) {
        var marker:Marker? = null
        lifecycleScope.executeAsyncTask(
            onPreExecute = {},
            doInBackground = {
                marker = params[0]
                getAddress(marker!!.position)
            },
            onPostExecute = {
                //...here "it" is a data returned from "doInBackground"
                if (marker != null){
                    marker!!.snippet = it
                    marker!!.showInfoWindow()
                }
            }
        )
    }

    internal class OnItineraryMarkerDragListener : OnMarkerDragListener {
        override fun onMarkerDrag(marker: Marker) {}
        override fun onMarkerDragEnd(marker: Marker) {
            when (marker.relatedObject as Int) {
                 START_INDEX -> startingPoint = marker.position
                 DEST_INDEX -> destinationPoint = marker.position
                 else -> waypoints[marker.relatedObject as Int] = marker.position
             }
            //update location:
            MainActivity().reverseGeocodingTask(marker)
            //update route:
            MainActivity().getRoadAsync()
        }

        override fun onMarkerDragStart(marker: Marker) {}
    }

    private fun updateUIWithItineraryMarkers() {
        mItineraryMarkers.closeAllInfoWindows()
        mItineraryMarkers.items.clear()
        //Start marker:
        if (startMarker != null) {
            startMarker = updateItineraryMarker(
                null, startingPoint, START_INDEX,
                R.string.departure, R.drawable.marker_departure, -1, null
            )
        }
        //Via-points markers if any:
        for (index in 1 until waypoints.size - 1) {
            updateItineraryMarker(
                null, waypoints[index], index,
                R.string.waypoint, R.drawable.waypoint_marker, -1, null
            )
        }
        //Destination marker if any:
        if (endMarker != null) {
            endMarker = updateItineraryMarker(
                null, destinationPoint, DEST_INDEX,
                R.string.destination, R.drawable.marker_departure, -1, null
            )
        }
    }
    private fun putRoadNodes(road: Road) {
        roadNodeMarkers.items.clear()
        val icon = ResourcesCompat.getDrawable(resources, R.drawable.marker_node, null)
        val n = road.mNodes.size
        val infoWindow = MarkerInfoWindow(org.osmdroid.bonuspack.R.layout.bonuspack_bubble, map)
        val iconIds = resources.obtainTypedArray(R.array.direction_icons)
        for (i in 0 until n) {
            val node = road.mNodes[i]
            val instructions = if (node.mInstructions == null) "" else node.mInstructions
            val nodeMarker = Marker(map)
            nodeMarker.title = getString(R.string.step) + " " + (i + 1)
            nodeMarker.snippet = instructions
            nodeMarker.subDescription =
                Road.getLengthDurationText(this, node.mLength, node.mDuration)
            nodeMarker.position = node.mLocation
            nodeMarker.icon = icon
            nodeMarker.setInfoWindow(infoWindow) //use a shared infowindow.
            val iconId = iconIds.getResourceId(node.mManeuverType, R.drawable.ic_empty)
            if (iconId != R.drawable.ic_empty) {
                val image = ResourcesCompat.getDrawable(resources, iconId, null)
                nodeMarker.image = image
            }
            nodeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            roadNodeMarkers.add(nodeMarker)
        }
        iconIds.recycle()
        map.overlays.add(roadNodeMarkers)
    }

    fun removePoint(index: Int) {
        if (index == START_INDEX) {
            if (startMarker != null) {
                startMarker!!.closeInfoWindow()
                mItineraryMarkers.remove(startMarker)
                startMarker = null
            }
            if (waypoints.size > 2){
                startingPoint = waypoints[1]
                waypoints.removeAt(0)
            }
            else {
                waypoints.removeAt(0)
                startingPoint = null
            }
        } else if (index == DEST_INDEX) {
            if (endMarker != null) {
                endMarker!!.closeInfoWindow()
                mItineraryMarkers.remove(endMarker)
                endMarker = null
            }
            if (waypoints.size > 2){
                startingPoint = waypoints[1]
                waypoints.removeAt(0)
            }
            else {
                waypoints.removeAt(0)
                startingPoint = null
            }
        } else {
            waypoints.removeAt(index)
            updateUIWithItineraryMarkers()
        }
        getRoadAsync()
    }

    private fun requestPermission(){
        lifecycleScope.executeAsyncTask(
            onPreExecute = {},
            doInBackground = {
                val isReadPermission = ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED

                val isWritePermission = ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED

                val isLocationPermission = ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                val isCameraPermission = ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                val minSdkLevel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

                isReadPermissionGranted = isReadPermission
                isWritePermissionGranted = isWritePermission || minSdkLevel
                isLocationPermissionGranted = isLocationPermission
                isCameraPermissionGranted = isCameraPermission

                val permissionRequest = mutableListOf<String>()
                if (!isWritePermissionGranted) permissionRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                if (!isReadPermissionGranted) permissionRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (!isLocationPermission) permissionRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
                if (!isCameraPermission) permissionRequest.add(Manifest.permission.CAMERA)

                if (permissionRequest.isNotEmpty()) permissionLauncher.launch(permissionRequest.toTypedArray())// send data to "onPostExecute"
            },
            onPostExecute = {}
        )
    }
    
    private fun <R> CoroutineScope.executeAsyncTask(
        onPreExecute: () -> Unit,
        doInBackground: () -> R,
        onPostExecute: (R) -> Unit
    ) = launch {
        onPreExecute()
        val result = withContext(Dispatchers.IO) { // runs in background thread without blocking the Main Thread
            doInBackground()
        }
        onPostExecute(result)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }
    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}