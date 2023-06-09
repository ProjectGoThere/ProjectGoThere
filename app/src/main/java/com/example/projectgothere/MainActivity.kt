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
import org.osmdroid.bonuspack.location.GeocoderNominatim
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
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
import java.io.File
import java.lang.Integer.parseInt
import java.text.SimpleDateFormat
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
private const val appDirectoryName = "ProjectGoThere"
@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity(){
    private lateinit var imageRoot: File
    private val date = Calendar.getInstance().time
    private val sdf = SimpleDateFormat("MM-dd-yyyy",Locale.US)
    private val fdate = sdf.format(date)
    private var curTripName = "My Trip $fdate"
    private lateinit var curTripDir: File
    private var currentPoint: GeoPoint? = null
    private var startMarker : Marker? = null
    private var endMarker : Marker? = null
    private var currentMarker: Marker? = null
    private lateinit var map : MapView
    private var destinationPolygon: Polygon? = null
    private var roads : Array<Road>? = null

    private var mItineraryMarkers = FolderOverlay()
    private var roadNodeMarkers = FolderOverlay()

    private lateinit var roadManager: RoadManager
    private var roadOverlay: ArrayList<Polyline>? = null
    private lateinit var geonamesAccount: String
    private lateinit var mViaPointInfoWindow: WaypointInfoWindow
    private lateinit var locationOverlay:MyLocationNewOverlay

    private var currentLocation: GeoPoint = GeoPoint(44.3242, -93.9760)
    private var extraStops: Int = 2
    private lateinit var binding: ActivityMainBinding
    private var cityAddress: String? = null
    private var countyAddress: String? = null
    private var streetAddress: String? = null
    private var completeAddress: String? = null
    private var desiredType: String? = null
    private lateinit var properties: ArrayList<Int>

    private var isReadPermissionGranted = false
    private var isWritePermissionGranted = false
    private var isLocationPermissionGranted = false
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private var trackingMode : Boolean = true
    private var mAzimuthAngleSpeed : Float = 0.0f


    override fun onCreate(savedInstanceState: Bundle?) {
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        imageRoot = File(this.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            File.separator+appDirectoryName)
        if (!imageRoot.exists()) imageRoot.mkdirs()
        curTripDir = File(imageRoot.absolutePath, File.separator+curTripName)
        if (!curTripDir.exists()) curTripDir.mkdirs()
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //activity launcher for permissions
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ permissions ->
            isReadPermissionGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: isReadPermissionGranted
            isWritePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: isWritePermissionGranted
            isLocationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: isLocationPermissionGranted
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

        //create an array to hold the start point, end point, and randomly chosen historical locations
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

        if (roads != null) updateUIWithRoads(roads!!)
        val mPoiMarkers = RadiusMarkerClusterer(this)
        val clusterIcon =
            BonusPackHelper.getBitmapFromVectorDrawable(this, R.drawable.marker_poi_cluster)
        mPoiMarkers.setIcon(clusterIcon)
        mPoiMarkers.mAnchorV = Marker.ANCHOR_BOTTOM
        mPoiMarkers.mTextAnchorU = 0.70f
        mPoiMarkers.mTextAnchorV = 0.27f
        mPoiMarkers.textPaint.textSize = 12 * resources.displayMetrics.density
        map.overlays.add(mPoiMarkers)

        //spinner for specifying propety type in the filter function
        val spinPropType : Spinner = binding.propTypeDd
        val propAdapter : ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(this,
            R.array.propTypes, android.R.layout.simple_spinner_item)
        propAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item)
        spinPropType.adapter = propAdapter
        getSpinnerVal(spinPropType)
        properties = ArrayList()

        //spinner for specifying how many stops are requested
        val spinStopsDes : Spinner = binding.stopsDesiredDd
        val stopsAdapter : ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(this,
            R.array.amtStopsDesired, android.R.layout.simple_spinner_item)
        stopsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item)
        spinStopsDes.adapter = stopsAdapter
        getSpinnerVal(spinStopsDes)

        binding.cameraButton.setOnClickListener{
            showDialog()
        }

        binding.editDeparture.setPrefKeys(SHARED_PREFS_APPKEY, PREF_LOCATIONS_KEY)

        binding.buttonSearchDep.setOnClickListener{
            handleSearchButton(
                START_INDEX,
                R.id.editDeparture
            )
        }

        binding.editDestination.setPrefKeys(SHARED_PREFS_APPKEY, PREF_LOCATIONS_KEY)

        binding.buttonSearchDest.setOnClickListener{
            for (i in 1 until waypoints.size-1){
                removePoint(i, mItineraryMarkers.items[i] as Marker)
            }
            handleSearchButton(
                DEST_INDEX,
                R.id.editDestination
            )
        }

        binding.trackingModeButton.setOnClickListener{
                trackingMode = (!trackingMode)
                updateUIWithTrackingMode(locationOverlay)
        }

        map.invalidate()
    }

    //creates bottom of screen dialog for camera funcitons
    private fun showDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.bottomsheetlayout)

        val currentTripLayout : LinearLayout = dialog.findViewById(R.id.layoutCurrentTrip)
        val prevTripLayout : LinearLayout = dialog.findViewById(R.id.layoutPreviousTrip)
        val takePictureLayout : LinearLayout = dialog.findViewById(R.id.layoutTakePicture)

        currentTripLayout.setOnClickListener {
            dialog.dismiss()
            val curDirPath = File(curTripDir.absolutePath)
            if (!curDirPath.exists()) curDirPath.mkdirs()
            val intent = Intent(this,ViewPicturesActivity::class.java)
            intent.putExtra("Direct",curDirPath.absolutePath)
            startActivity(intent)
        }

        prevTripLayout.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this,ListTripsActivity::class.java)
            intent.putExtra("rootDir",imageRoot.absolutePath)
            startActivity(intent)
        }

        takePictureLayout.setOnClickListener {
            dialog.dismiss()
            val cameraIntent = Intent(this, CameraActivity::class.java)
            cameraIntent.putExtra("rootDirPath",imageRoot.absolutePath)
            cameraIntent.putExtra("currentDir",curTripDir.absolutePath)
            startActivity(cameraIntent)
        }

        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(R.color.transparent)
        dialog.window?.attributes?.windowAnimations ?: (R.style.DialogAnimation)
        dialog.window?.setGravity(Gravity.BOTTOM)

    }

    //getSpinnerVal returns the value chosen in whatever spinner is input
    private fun getSpinnerVal(spinner: Spinner){
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View,
                pos: Int,
                id: Long
            ) {
                val item = parent.getItemAtPosition(pos)
                val selected = item.toString()
                if (selected.toIntOrNull() != null){ //if value chosen is a number
                    extraStops = parseInt(selected)
                }
                else if (selected != "Filter by" || selected != "Stops Desired"){
                    desiredType = selected
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    //rand generates a random number between two specified values
    private fun rand(start: Int, end: Int): Int {
        require(!(start > end || end - start + 1 > Int.MAX_VALUE)) { "Illegal Argument" }
        return Random(System.nanoTime()).nextInt(end - start + 1) + start
    }

    //returns the current location of the user in lat and long
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
                }
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

    //addWaypoints takes in how many historical locations are desired and adds them
    //to the waypoint array
    private fun addWaypoints(extraStops: Int){
        var k = 0
        do {
            if (desiredType != "Filter by"){
                //if a filter has been selected, only specific IDs of historic locations
                //can be selected
                filterPropertyType(desiredType!!)
            }
            else { //choose any waypoint from the entire database
                val waypointID = rand(0, 1334)
                getAddressDataSnapshot(waypointID)
            }
            k++
        } while(k<extraStops)
    }

    //getAddressDataSnapshot returns the address information for a specific location ID
    //in the database
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
                //compile address information into a readable address
                completeAddress = "$streetAddress $cityAddress MN"
                geocodingTask(completeAddress!!, WAYPOINT_INDEX)

            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.d(TAG, databaseError.message)
            }
        }
        addressRef.addListenerForSingleValueEvent(valueEventListener)
    }

    //filterPropertyType takes in the value returned from the property type spinner if one
    //is chosen, creates an array of values in the database that have that type, and chooses
    //a random ID number from the ones in the array to be added to the waypoints array
    private fun filterPropertyType(desiredType: String){
        val rootRef = FirebaseDatabase.getInstance().reference
        val propertyRef = rootRef.child("SpreadSheet").orderByChild("Property Type").equalTo(desiredType)
        val valueEventListener: ValueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if(snapshot.exists()){
                    //clear list
                    properties.clear()
                    for (i in snapshot.children){
                        //access waypointID of property, add to a list
                        val propertyID = i.child("ID").getValue(Long::class.java)
                        val currentWaypointID = propertyID?.toInt()
                        if (currentWaypointID != null) {
                            properties.add(currentWaypointID)
                        }
                    }
                    val waypointID = rand(0, properties.size-1)
                    getAddressDataSnapshot(properties[waypointID])
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

    //takes in a GeoPoint and returns the string value of the address associated with
    //that position
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

    //takes in the current location and focuses map on that location if button is enabled
    private fun updateUIWithTrackingMode(locationOverlay: MyLocationNewOverlay) {
        if (trackingMode) {
            binding.trackingModeButton.setBackgroundResource(R.drawable.btn_tracking_on)
            if (locationOverlay.isEnabled && locationOverlay.myLocation != null) {
                map.controller.animateTo(locationOverlay.myLocation)
            }
            map.mapOrientation = -mAzimuthAngleSpeed
            binding.trackingModeButton.keepScreenOn = true
        } else {
            binding.trackingModeButton.setBackgroundResource(R.drawable.btn_tracking_off)
            map.mapOrientation = 0.0f
            binding.trackingModeButton.keepScreenOn = false
        }
    }

    //takes in string address and index identifying location along route; asynchronously
    //updates that position on the route
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
                }
            },
            onPostExecute = {
                if (it == null){
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, "Geocoding error", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else if (it.size == 0){
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, "Address not found", Toast.LENGTH_SHORT)
                            .show()
                        if (desiredType != null){
                            val waypointID = rand(0, 1334)
                            getAddressDataSnapshot(waypointID)
                        }
                        else{
                            filterPropertyType(desiredType!!)
                        }
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
                            if (destinationPoint != null) {
                                if (waypoints.size == 2){
                                    waypoints.add(1, currentPoint!!)
                                } else {
                                    waypoints.add(waypoints.size - 2, currentPoint!!)
                                }
                            }
                            else {
                                waypoints.add(currentPoint!!)
                            }
                            currentMarker = updateItineraryMarker(
                                null, currentPoint, waypoints.indexOf(currentPoint),
                                R.string.waypoint, R.drawable.waypoint_marker, -1, addressDisplayName
                            )
                            map.controller.setCenter(currentPoint!!)
                        }
                    }
                    getRoadAsync()
                    //get and display enclosing polygon:
                    if (address.extras.containsKey("polygonpoints")) {
                        val polygon: ArrayList<GeoPoint?>? =
                            address.extras.getParcelableArrayList("polygonpoints")
                        updateUIWithPolygon(polygon, addressDisplayName)
                    } else {
                        updateUIWithPolygon(null, "")
                    }
                }
            }
        )
    }

    private val mItineraryListener: OnItineraryMarkerDragListener = OnItineraryMarkerDragListener()

    //takes in a marker, GeoPoint, index, title, marker type, image type, and string address and
    //returns a marker at the identified position and index with the title and address in the
    //bubble that pops up on click
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

    //update road overlay to represent current waypoint list
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

    //takes in an index and the type of search button and handles the search button functionality
    private fun handleSearchButton(index: Int, editResId: Int) {
        val locationEdit = findViewById<View>(editResId) as EditText
        //Hide the soft keyboard:
        val imm: InputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(locationEdit.windowToken, 0)
        val locationAddress = locationEdit.text.toString()
        if (locationAddress == "") {
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
    }

    //takes in an ArrayList of GeoPoints and finds the roads between the points
    private fun updateRoadTask(vararg params: ArrayList<GeoPoint>){
        lifecycleScope.executeAsyncTask(
            onPreExecute = {},
            doInBackground = {
                val gpList = params[0]
                val roadManager = OSRMRoadManager(applicationContext, "MY_USER_AGENT")
                roadManager.getRoads(gpList)
            },
            onPostExecute = {
                roads = it
                updateUIWithRoads(roads!!)
            }
        )
    }

    //shows all roads between waypoints and highlights the most efficent route in blue
    private fun selectRoad(roadIndex: Int) {
        putRoadNodes(roads!![roadIndex])
        for (i in 0 until roadOverlay!!.size) {
            val p: Paint = roadOverlay!![i].paint
            if (i == roadIndex) p.color = -0x7fffff01
            else p.color = -0x6f99999a
        }
        map.invalidate()
    }

    //class to handle clicking another road
    internal class RoadOnClickListener : Polyline.OnClickListener {
        override fun onClick(polyline: Polyline, mapView: MapView, eventPos: GeoPoint): Boolean {
            val selectedRoad = polyline.relatedObject as Int
            MainActivity().selectRoad(selectedRoad)
            polyline.infoWindowLocation = eventPos
            polyline.showInfoWindow()
            return true
        }
    }

    //takes in an Array of Roads and updates the overlay to show the roads
    private fun updateUIWithRoads(roads: Array<Road>) {
        roadNodeMarkers.items.clear()
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
        }
        selectRoad(0)
    }

    //takes in a BoundingBox and focuses the map on that area
    private fun setViewOn(bb: BoundingBox?) {
        if (bb != null) {
            map.zoomToBoundingBox(bb, true)
        }
    }

    //takes in an ArrayList of GeoPoints and a string name and updates the overlay to include that
    //identified area on the map
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
        )
        setViewOn(bb)
        map.invalidate()
    }

    //takes in a marker and asynchronously finds the address related to that marker
    private fun reverseGeocodingTask(vararg params: Marker?) {
        var marker:Marker? = null
        lifecycleScope.executeAsyncTask(
            onPreExecute = {},
            doInBackground = {
                marker = params[0]
                getAddress(marker!!.position)
            },
            onPostExecute = {
                if (marker != null){
                    marker!!.snippet = it
                    marker!!.showInfoWindow()
                }
            }
        )
    }

    //class to handle dragging itinerary markers
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

    //updates overlay to show all current markers based on the ArrayList of markers
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
                R.string.destination, R.drawable.marker_destination, -1, null
            )
        }
    }

    //takes in a Road and provides navigation along that route
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
            nodeMarker.setInfoWindow(infoWindow)
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

    //takes in an index and a marker and removes the point associated with the index and location
    //of the marker
    fun removePoint(index: Int, marker:Marker) {
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
            else {
                waypoints.removeLast()
                destinationPoint = null
            }
        } else {
            waypoints.remove(marker.position)
            updateUIWithItineraryMarkers()
        }
        getRoadAsync()
    }

    //request necessary permissions for app functionality
    private fun requestPermission(){
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

        val minSdkLevel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        isReadPermissionGranted = isReadPermission
        isWritePermissionGranted = isWritePermission || minSdkLevel
        isLocationPermissionGranted = isLocationPermission

        val permissionRequest = mutableListOf<String>()
        if (!isWritePermissionGranted) permissionRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (!isReadPermissionGranted) permissionRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (!isLocationPermission) permissionRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (permissionRequest.isNotEmpty()) permissionLauncher.launch(permissionRequest.toTypedArray())// send data to "onPostExecute"
    }

    //creates an asynchronous task
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