package com.example.projectgothere

import android.Manifest
import android.app.Activity
import android.content.Context
import android.gesture.OrientedBoundingBox
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.location.Address
import android.location.LocationManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.preference.PreferenceManager
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import okhttp3.internal.userAgent
import okio.IOException
import org.osmdroid.bonuspack.location.GeocoderNominatim
import org.osmdroid.bonuspack.routing.*
import org.osmdroid.config.Configuration
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
import org.osmdroid.views.overlay.mylocation.DirectedLocationOverlay
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.util.*


private const val TAG = "MainActivity";
private const val SHARED_PREFS_APPKEY = "Project GoThere"
private const val PREF_LOCATIONS_KEY = "PREF_LOCATIONS"
private const val START_INDEX = -2
private const val DEST_INDEX = -1
private lateinit var startingPoint: GeoPoint
private lateinit var destinationPoint: GeoPoint
private lateinit var startMarker : Marker
private lateinit var endMarker : Marker
private lateinit var mapController: MapController
private lateinit var map : MapView
private lateinit var destinationPolygon: Polygon
private lateinit var roads : MutableList<Road>
private lateinit var mViaPointInfoWindow: WaypointInfoWindow
private lateinit var waypoints: ArrayList<GeoPoint>
private lateinit var mItineraryMarkers : FolderOverlay
private lateinit var roadNodeMarkers : FolderOverlay

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks{

    private lateinit var activity : Activity
    private lateinit var roadManager: RoadManager
    private lateinit var road: Road
    private lateinit var roadOverlay: Polyline
    private lateinit var myLocationManager: LocationManager
    private lateinit var departureText: AutoCompleteOnPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        this.activity = this
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName;
        setContentView(R.layout.activity_main)
        handlePermissions()

        map = findViewById<View>(R.id.map) as MapView
        map.setMultiTouchControls(true)
        roadManager = OSRMRoadManager(this, "MY_USER_AGENT")

        val startPoint = GeoPoint(44.3242, -93.9760)
        val endPoint = GeoPoint(46.7867, -92.1005)

        waypoints = ArrayList<GeoPoint>()
        waypoints.add(startPoint)
        waypoints.add(endPoint)

        mapController = map.controller as MapController
        mapController.setZoom(9)
        mapController.setCenter(startPoint)

        startMarker = Marker(map)
        startMarker.position = startPoint
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.overlays.add(startMarker)

        endMarker = Marker(map)
        endMarker.position = endPoint
        endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.overlays.add(endMarker)

        road = roadManager.getRoad(waypoints)
        roadOverlay = RoadManager.buildRoadOverlay(road)
        map.overlays.add(roadOverlay)

        showRouteSteps()

        map.invalidate()

        val mapController = map.controller
        val myLocationOverlay = DirectedLocationOverlay(this)
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
    }

    fun getAddress(p: GeoPoint): String? {
        val geocoder = GeocoderNominatim(userAgent)
        val theAddress: String?
        theAddress = try {
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


    class GeocodingTask :
        AsyncTask<Any?, Void?, List<Address>?>() {
        var mIndex = 0
        protected fun doInBackground(vararg params: Any): List<Address>? {
            val locationAddress = params[0] as String
            mIndex = params[1] as Int
            val geocoder = GeocoderNominatim(userAgent)
            geocoder.setOptions(true) //ask for enclosing polygon (if any)
            return try {
                val viewbox : OrientedBoundingBox
                geocoder.getFromLocationName(
                    locationAddress, 1,
                    viewbox.latSouth, viewbox.lonEast,
                    viewbox.latNorth, viewbox.lonWest, false
                )
            } catch (e: Exception) {
                null
            }
        }
        private val mItineraryListener: OnItineraryMarkerDragListener = OnItineraryMarkerDragListener()
        private fun updateItineraryMarker(
            marker: Marker?, p: GeoPoint?, index: Int,
            titleResId: Int, markerResId: Int, imageResId: Int, address: String?): Marker {
            var marker = marker
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
                ReverseGeocodingTask().execute(marker)
            return marker
        }
        fun getRoadAsync() {
            roads
            var roadStartPoint: GeoPoint? = null
            roadStartPoint = startingPoint
            if (roadStartPoint == null || destinationPoint == null) {
                updateUIWithRoads(roads)
                return
            }
            val waypoints = ArrayList<GeoPoint>(2)
            waypoints.add(roadStartPoint)
            //add intermediate via points:
            for (p in waypoints) {
                waypoints.add(p)
            }
            waypoints.add(destinationPoint)
            UpdateRoadTask(this).execute(waypoints)
        }
        @Deprecated("Deprecated in Java")
        override fun onPostExecute(foundAdresses: List<Address>?) {
            if (foundAdresses == null) {
                Toast.makeText(this, "Geocoding error", Toast.LENGTH_SHORT).show()
            } else if (foundAdresses.size == 0) { //if no address found, display an error
                Toast.makeText(applicationContext, "Address not found",Toast.LENGTH_SHORT).show()
            } else {
                val address: Address = foundAdresses[0] //get first address
                val addressDisplayName: String? = address.getExtras().getString("display_name")
                if (mIndex == START_INDEX) {
                    startingPoint = GeoPoint(address.getLatitude(), address.getLongitude())
                    startMarker = updateItineraryMarker(
                        startMarker, startingPoint, START_INDEX,
                        R.string.departure, R.drawable.marker_departure, -1, addressDisplayName
                    )
                    mapController.setCenter(startingPoint)
                } else if (mIndex == DEST_INDEX) {
                    destinationPoint = GeoPoint(address.getLatitude(), address.getLongitude())
                    endMarker = updateItineraryMarker(
                        endMarker, destinationPoint, DEST_INDEX,
                        R.string.destination, R.drawable.marker_destination, -1, addressDisplayName
                    )
                    map.getController().setCenter(destinationPoint)
                }
                getRoadAsync()
                //get and display enclosing polygon:
                val extras: Bundle = address.getExtras()
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
        override fun doInBackground(vararg p0: Any?): List<Address>? {
            TODO("Not yet implemented")
        }
    }
    fun handleSearchButton(index: Int, editResId: Int) {
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
        GeocodingTask().execute(locationAddress, index)
    }//endHandleSearchButton

    fun updateUIWithRoads(roads: Array<Road>?) {
        roadNodeMarkers.getItems().clear()
        val textView = findViewById<View>(R.id.routeInfo) as TextView
        textView.text = ""
        val mapOverlays = map.overlays
        if (roadOverlay != null) {
            for (i in 0 until roadOverlay.length) mapOverlays.remove(roadOverlay.get(i))
            roadOverlay = null
        }
        if (roads == null) return
        if (roads[0].mStatus == Road.STATUS_TECHNICAL_ISSUE) Toast.makeText(
            map.context,
            "Technical issue when getting the route",
            Toast.LENGTH_SHORT
        ).show() else if (roads[0].mStatus > Road.STATUS_TECHNICAL_ISSUE) //functional issues
            Toast.makeText(map.context, "No possible route here", Toast.LENGTH_SHORT).show()
        roadOverlay = arrayOfNulls<Polyline>(roads.size)
        for (i in roads.indices) {
            val roadPolyline = RoadManager.buildRoadOverlay(roads[i])
            roadOverlay.get(i) = roadPolyline
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
    private class UpdateRoadTask(private val mContext: Context) :
        AsyncTask<ArrayList<GeoPoint?>?, Void?, Array<Road>?>() {

        override fun onPostExecute(result: Array<Road>?) {
            roads = result
            updateUIWithRoads(result)
            getPOIAsync(poiTagText.getText().toString())
        }

        override fun doInBackground(vararg params: ArrayList<GeoPoint?>): Array<Road>? {
            val waypoints = params[0]
            val roadManager = OSRMRoadManager(this, "MY_USER_AGENT")
            val locale = Locale.getDefault()
            return roadManager.getRoads(waypoints)
        }
    }



    fun setViewOn(bb: BoundingBox?) {
        if (bb != null) {
            map.zoomToBoundingBox(bb, true)
        }
    }
    open fun updateUIWithPolygon(polygon: ArrayList<GeoPoint?>?, name: String?) {
        val mapOverlays = map.overlays
        var location = -1
        if (destinationPolygon != null) location = mapOverlays.indexOf(destinationPolygon)
        destinationPolygon = Polygon()
        destinationPolygon.setFillColor(0x15FF0080)
        destinationPolygon.setStrokeColor(-0x7fffff01)
        destinationPolygon.setStrokeWidth(5.0f)
        destinationPolygon.setTitle(name)
        var bb: BoundingBox? = null
        if (polygon != null) {
            destinationPolygon.setPoints(polygon)
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
    public class ReverseGeocodingTask :
        AsyncTask<Marker?, Void?, String>() {
        var marker: Marker? = null


        override fun onPostExecute(result: String) {
            marker!!.snippet = result
            marker!!.showInfoWindow()
        }

        override fun doInBackground(vararg params: Marker?): String {
            marker = params[0]
            return getAddress(marker!!.position)
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
            ReverseGeocodingTask().execute(marker)
            //update route:
            getRoadAsync()
        }

        override fun onMarkerDragStart(marker: Marker) {}
    }

    fun updateUIWithItineraryMarkers() {
        mItineraryMarkers.closeAllInfoWindows()
        mItineraryMarkers.getItems().clear()
        //Start marker:
        if (startingPoint != null) {
            startMarker = updateItineraryMarker(
                null, startingPoint, START_INDEX,
                R.string.departure, R.drawable.marker_departure, -1, null
            )
        }
        //Via-points markers if any:
        for (index in 0 until waypoints.size()) {
            updateItineraryMarker(
                null, waypoints.get(index), index,
                R.string.waypoint, R.drawable.waypoint_marker, -1, null
            )
        }
        //Destination marker if any:
        if (destinationPoint != null) {
            endMarker = updateItineraryMarker(
                null, destinationPoint, DEST_INDEX,
                R.string.destination, R.drawable.marker_destination, -1, null
            )
        }
    }
    fun removePoint(index: Int) {
        if (index == START_INDEX) {
            startingPoint = null
            if (startMarker != null) {
                startMarker.closeInfoWindow()
                waypoints.remove(startMarker)
                startMarker = null
            }
        } else if (index == DEST_INDEX) {
            destinationPoint = null
            endMarker.closeInfoWindow()
            waypoints.remove(endMarker)
            endMarker = null
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

        for(i in 0 until road.mNodes.size){
            val node = road.mNodes[i]
            val nodeMarker = Marker(map)
            nodeMarker.position = node.mLocation
            nodeMarker.icon = nodeIcon
            nodeMarker.title = "Step "+i
            nodeMarker.snippet = node.mInstructions
            nodeMarker.subDescription = Road.getLengthDurationText(this,node.mLength,node.mDuration)
            val icon = when (node.mManeuverType){
                //roundabout
                29 -> ContextCompat.getDrawable(this,R.drawable.ic_roundabout)
                //straight
                2 -> ContextCompat.getDrawable(this,R.drawable.ic_continue)
                //slight left
                3 -> ContextCompat.getDrawable(this,R.drawable.ic_slight_left)
                //left turn
                4 -> ContextCompat.getDrawable(this, R.drawable.ic_turn_left)
                //sharp left
                5 -> ContextCompat.getDrawable(this,R.drawable.ic_sharp_left)
                //exit right/slight right
                1, 6 -> ContextCompat.getDrawable(this,R.drawable.ic_slight_right)
                //right turn
                7 -> ContextCompat.getDrawable(this,R.drawable.ic_turn_right)
                //sharp right
                8 -> ContextCompat.getDrawable(this, R.drawable.ic_sharp_right)
                20 -> ContextCompat.getDrawable(this,R.drawable.ic_continue)
                else -> null
            }
            nodeMarker.image = icon
            map.overlays.add(nodeMarker)
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


