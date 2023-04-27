package com.example.projectgothere

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.Paint
import android.location.Address
import android.location.LocationManager
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.preference.PreferenceManager
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import okhttp3.internal.userAgent
import okio.IOException
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.bonuspack.location.GeocoderNominatim
import org.osmdroid.bonuspack.location.OverpassAPIProvider
import org.osmdroid.bonuspack.location.POI
import org.osmdroid.bonuspack.routing.*
import org.osmdroid.bonuspack.utils.BonusPackHelper
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.util.ManifestUtil
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Marker.OnMarkerDragListener
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import org.osmdroid.views.overlay.mylocation.DirectedLocationOverlay
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.util.*


private const val TAG = "MainActivity"
private const val SHARED_PREFS_APPKEY = "Project GoThere"
private const val PREF_LOCATIONS_KEY = "PREF_LOCATIONS"
private const val START_INDEX = -2
private const val DEST_INDEX = -1
private var startingPoint: GeoPoint? = null
private var destinationPoint: GeoPoint? = null
private var startMarker : Marker? = null
private var endMarker : Marker? = null
private lateinit var mapController: MapController
private lateinit var map : MapView
private lateinit var destinationPolygon: Polygon
private var roads : MutableList<Road> = mutableListOf()
private lateinit var waypoints: MutableList<GeoPoint>
private var mItineraryMarkers = FolderOverlay()
private var roadNodeMarkers = FolderOverlay()
private var mPOIs: ArrayList<POI>? = null

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks{

    private lateinit var activity : Activity
    private lateinit var roadManager: RoadManager
    private lateinit var road: Road
    private var roadOverlay: ArrayList<Polyline>? = null
    private lateinit var myLocationManager: LocationManager
    private lateinit var departureText: AutoCompleteOnPreferences
    private lateinit var geonamesAccount: String
    private lateinit var mViaPointInfoWindow: WaypointInfoWindow
    private lateinit var poiTagText : AutoCompleteTextView
    private lateinit var mPoiMarkers:RadiusMarkerClusterer

    override fun onCreate(savedInstanceState: Bundle?) {
        val myLocationOverlay = DirectedLocationOverlay(applicationContext)
        this.activity = this
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName;
        setContentView(R.layout.activity_main)
        handlePermissions()

        geonamesAccount = ManifestUtil.retrieveKey(this, "GEONAMES_ACCOUNT")
        map = findViewById<View>(R.id.map) as MapView
        map.setMultiTouchControls(true)
        roadManager = OSRMRoadManager(this, "MY_USER_AGENT")

        startingPoint = GeoPoint(44.3242, -93.9760)
        destinationPoint = GeoPoint(46.7867, -92.1005)

        waypoints = ArrayList<GeoPoint>()
        waypoints.add(startingPoint!!)
        waypoints.add(destinationPoint!!)

        mapController = map.controller as MapController
        mapController.setZoom(9)
        mapController.setCenter(startingPoint)

        mItineraryMarkers = FolderOverlay()
        mItineraryMarkers.name = getString(R.string.itinerary_markers_title)
        map.overlays.add(mItineraryMarkers)
        mViaPointInfoWindow = WaypointInfoWindow(R.layout.itinerary_bubble, map)
        updateUIWithItineraryMarkers()

        showRouteSteps()
        val mPoiMarkers: RadiusMarkerClusterer = RadiusMarkerClusterer(this)
        val clusterIcon =
            BonusPackHelper.getBitmapFromVectorDrawable(this, R.drawable.marker_poi_cluster)
        mPoiMarkers.setIcon(clusterIcon)
        mPoiMarkers.mAnchorV = Marker.ANCHOR_BOTTOM
        mPoiMarkers.mTextAnchorU = 0.70f
        mPoiMarkers.mTextAnchorV = 0.27f
        mPoiMarkers.textPaint.textSize = 12 * resources.displayMetrics.density
        map.overlays.add(mPoiMarkers)

        val mapController = map.controller

        map.overlays.add(myLocationOverlay)

        //start
        val departureText = findViewById<View>(R.id.editDeparture) as AutoCompleteOnPreferences
        departureText.setPrefKeys(SHARED_PREFS_APPKEY, PREF_LOCATIONS_KEY)

        val searchDepButton: Button = findViewById<View>(R.id.buttonSearchDep) as Button
        searchDepButton.setOnClickListener(View.OnClickListener {
            handleSearchButton(
                START_INDEX,
                R.id.editDeparture
            )
        })

        val destinationText = findViewById<View>(R.id.editDestination) as AutoCompleteOnPreferences
        destinationText.setPrefKeys(SHARED_PREFS_APPKEY, PREF_LOCATIONS_KEY)

        val searchDestButton: Button = findViewById<View>(R.id.buttonSearchDest) as Button
        searchDestButton.setOnClickListener(View.OnClickListener {
            handleSearchButton(
                DEST_INDEX,
                R.id.editDestination
            )
        }) //end
        map.invalidate()
    }

    private fun getAddress(p: GeoPoint): String? {
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


    private fun GeocodingTask(vararg params: Any){
        var mIndex = 0
        MainScope().async {
            val locationAddress = params[0] as String
            mIndex = params[1] as Int
            val geocoder = GeocoderNominatim(userAgent)
            geocoder.setOptions(true) //ask for enclosing polygon (if any)
            val foundAddresses =
                try {
                    val viewbox = map.boundingBox
                    geocoder.getFromLocationName(
                        locationAddress, 1,
                        viewbox.latSouth, viewbox.lonEast,
                        viewbox.latNorth, viewbox.lonWest, false
                    )
                } catch (e: Exception) {
                    null
                }
            if (foundAddresses == null) {
                Toast.makeText(applicationContext, "Geocoding error", Toast.LENGTH_SHORT).show()
            } else if (foundAddresses.size == 0) { //if no address found, display an error
                Toast.makeText(applicationContext, "Address not found", Toast.LENGTH_SHORT).show()
            } else {
                val address: Address = foundAddresses[0] //get first address
                val addressDisplayName: String? = address.getExtras().getString("display_name")
                if (mIndex == START_INDEX) {
                    startingPoint = GeoPoint(address.latitude, address.longitude)
                    startMarker = updateItineraryMarker(
                        startMarker, startingPoint, START_INDEX,
                        R.string.departure, R.drawable.marker_departure, -1, addressDisplayName
                    )
                    waypoints.add(0, startingPoint!!)
                    mapController.setCenter(startingPoint)
                } else if (mIndex == DEST_INDEX) {
                    destinationPoint = GeoPoint(address.latitude, address.longitude)
                    endMarker = updateItineraryMarker(
                        endMarker, destinationPoint, DEST_INDEX,
                        R.string.destination, R.drawable.marker_destination, -1, addressDisplayName
                    )
                    waypoints.add(destinationPoint!!)
                    mapController.setCenter(destinationPoint)
                }
                getRoadAsync()
                //get and display enclosing polygon:
                val extras: Bundle = address.extras
                if (extras.containsKey("polygonpoints")) {
                    val polygon: ArrayList<GeoPoint?>? =
                        extras.getParcelableArrayList<GeoPoint>("polygonpoints")
                    //Log.d("DEBUG", "polygon:"+polygon.size());
                    updateUIWithPolygon(polygon, addressDisplayName)
                } else {
                    updateUIWithPolygon(null, "")
                }
            }
        }
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
            val title = R.string.titleResId.toString()
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
        var roadStartPoint = startingPoint!!
        if (destinationPoint == null) {
            updateUIWithRoads(roads)
            return
        }
        val waypoints = ArrayList<GeoPoint>(2)
        waypoints.add(roadStartPoint)
        //add intermediate via points:
        for (p in waypoints) {
            waypoints.add(p)
        }
        waypoints.add(destinationPoint!!)
        updateRoadTask(this, waypoints)
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
        GeocodingTask(locationAddress, index)
    }//endHandleSearchButton
    private fun updateRoadTask(mContext: Context, vararg params: ArrayList<GeoPoint>){
        MainScope().async {
            val waypoints = params[0]
            val roadManager = OSRMRoadManager(this@MainActivity, "MY_USER_AGENT")
            val locale = Locale.getDefault()
            val result = roadManager.getRoads(waypoints).toMutableList()
            roads = result
            updateUIWithRoads(result)
            getPOIAsync(poiTagText.text.toString())
        }
    }

    private fun getPOIAsync(tag: String?) {
        mPoiMarkers.items.clear()
        pOILoadingTask(tag)
    }
    private fun getOSMTag(humanReadableFeature: String): String {
        val map = BonusPackHelper.parseStringMapResource(
            applicationContext, R.array.osm_poi_tags
        )
        return map[humanReadableFeature.lowercase(Locale.getDefault())]!!
    }
    private fun pOILoadingTask (vararg params: String?){
        var mFeatureTag: String
        var message: String? = null
        MainScope().async {
            mFeatureTag = params[0]!!
            val bb = map.boundingBox
            val result = when (mFeatureTag){
                null -> null
                "" -> null
                else -> {
                    val overpassProvider = OverpassAPIProvider()
                    val osmTag: String = getOSMTag(mFeatureTag)
                    if (osmTag == null) {
                        message = "$mFeatureTag is not a valid feature."
                        null
                    }
                    val oUrl = overpassProvider.urlForPOISearch(osmTag, bb, 100, 10)
                    overpassProvider.getPOIsFromUrl(oUrl)
                }
            }
            if (result != null) {
                mPOIs = result
            }
            if (mFeatureTag == "") {
                //no search, no message
            } else {
                Toast.makeText(
                    applicationContext,
                    mFeatureTag + " found:" + mPOIs!!.size,
                    Toast.LENGTH_LONG
                ).show()
            }
            updateUIWithPOI(mPOIs, mFeatureTag)
        }
    }
    private fun updateUIWithPOI(pois: ArrayList<POI>?, featureTag: String?) {
        if (pois != null) {
            val poiInfoWindow = POIInfoWindow(map)
            for (poi in pois) {
                val poiMarker = Marker(map)
                poiMarker.title = poi!!.mType
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
        map.invalidate()
    }
    fun selectRoad(roadIndex: Int) {
        val selectedRoad = roadIndex
        putRoadNodes(roads.get(roadIndex))
        //Set route info in the text view:
        val textView = findViewById<View>(R.id.routeInfo) as TextView
        textView.setText(roads.get(roadIndex).getLengthDurationText(this, -1))
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

    private fun updateUIWithRoads(roads: MutableList<Road>) {
        roadNodeMarkers.items.clear()
        val textView = findViewById<View>(R.id.routeInfo) as TextView
        textView.text = ""
        val mapOverlays = map.overlays
        for (i in 0 until roadOverlay!!.size) mapOverlays.remove(roadOverlay!![i])
        roadOverlay
        if (roads[0].mStatus == Road.STATUS_TECHNICAL_ISSUE) Toast.makeText(
            map.context,
            "Technical issue when getting the route",
            Toast.LENGTH_SHORT
        ).show() else if (roads[0].mStatus > Road.STATUS_TECHNICAL_ISSUE) //functional issues
            Toast.makeText(map.context, "No possible route here", Toast.LENGTH_SHORT).show()
        roadOverlay = ArrayList<Polyline>()
        for (i in roads.indices) {
            val roadPolyline = RoadManager.buildRoadOverlay(roads[i])
            roadOverlay!![i] = roadPolyline
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
        var location = -1
        location = mapOverlays.indexOf(destinationPolygon)
        destinationPolygon = Polygon()
        destinationPolygon.setFillColor(0x15FF0080)
        destinationPolygon.setStrokeColor(-0x7fffff01)
        destinationPolygon.setStrokeWidth(5.0f)
        destinationPolygon.setTitle(name)
        var bb: BoundingBox? = null
        if (polygon != null) {
            destinationPolygon.points = polygon
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
        var marker: Marker? = null
        MainScope().async{
            marker = params[0]
            val result = getAddress(marker!!.position)
            marker!!.snippet = result
            marker!!.showInfoWindow()
        }
    }

    internal class OnItineraryMarkerDragListener : OnMarkerDragListener {
        override fun onMarkerDrag(marker: Marker) {}
        override fun onMarkerDragEnd(marker: Marker) {
            val index = marker.relatedObject as Int
            if (index == START_INDEX) startingPoint =
                marker.position else if (index == DEST_INDEX) destinationPoint =
                marker.position else waypoints.set(index, marker.position)
            //update location:
            MainActivity().reverseGeocodingTask(marker)
            //update route:
            MainActivity().getRoadAsync()
        }

        override fun onMarkerDragStart(marker: Marker) {}
    }

    private fun updateUIWithItineraryMarkers() {
        mItineraryMarkers.closeAllInfoWindows()
        mItineraryMarkers.getItems().clear()
        //Start marker:
        if (startMarker != null) {
            startMarker = updateItineraryMarker(
                null, startingPoint, START_INDEX,
                R.string.departure, R.drawable.marker_departure, -1, null
            )
        }
        //Via-points markers if any:
        for (index in 1 until waypoints.size-1) {
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

    private fun displayToast(s:String){
        Toast.makeText(applicationContext, "$s Permission Granted",Toast.LENGTH_SHORT).show()
    }
    private fun handlePermissions(){
        if (EasyPermissions.hasPermissions(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)){
            val s = "Location"
            displayToast(s)
        }
        else {
            EasyPermissions.requestPermissions(
                this@MainActivity,
                "App needs your location",
                101,
                Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun showRouteSteps(){
        val nodeIcon = ContextCompat.getDrawable(this, R.drawable.marker_node)
        for (j in 0 until roads.size) {
            for (i in 0 until roads[j].mNodes.size) {
                val node = roads[j].mNodes[i]
                val nodeMarker = Marker(map)
                nodeMarker.position = node.mLocation
                nodeMarker.icon = nodeIcon
                nodeMarker.title = "Step $i"
                nodeMarker.snippet = node.mInstructions
                nodeMarker.subDescription =
                    Road.getLengthDurationText(this, node.mLength, node.mDuration)
                val icon = when (node.mManeuverType) {
                    //roundabout
                    29 -> ContextCompat.getDrawable(this, R.drawable.ic_roundabout)
                    //straight
                    2 -> ContextCompat.getDrawable(this, R.drawable.ic_continue)
                    //slight left
                    3 -> ContextCompat.getDrawable(this, R.drawable.ic_slight_left)
                    //left turn
                    4 -> ContextCompat.getDrawable(this, R.drawable.ic_turn_left)
                    //sharp left
                    5 -> ContextCompat.getDrawable(this, R.drawable.ic_sharp_left)
                    //exit right/slight right
                    1, 6 -> ContextCompat.getDrawable(this, R.drawable.ic_slight_right)
                    //right turn
                    7 -> ContextCompat.getDrawable(this, R.drawable.ic_turn_right)
                    //sharp right
                    8 -> ContextCompat.getDrawable(this, R.drawable.ic_sharp_right)
                    20 -> ContextCompat.getDrawable(this, R.drawable.ic_continue)
                    else -> null
                }
                nodeMarker.image = icon
                map.overlays.add(nodeMarker)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode,permissions,grantResults,this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        when (requestCode){
            101 -> displayToast("Location")
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this,perms)){
            AppSettingsDialog.Builder(this).build().show()
        } else {
            Toast.makeText(applicationContext,"Permission Denied", Toast.LENGTH_SHORT).show()
        }
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