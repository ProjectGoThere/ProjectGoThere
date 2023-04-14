package com.example.projectgothere

import android.Manifest
import android.location.LocationManager
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.preference.PreferenceManager
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.projectgothere.databinding.AutoCompleteOnPreferencesBinding
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions


private const val TAG = "MainActivity";
class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks{
    private lateinit var map : MapView
    private lateinit var mapController: MapController
    private lateinit var roadManager: RoadManager
    private lateinit var waypoints: ArrayList<GeoPoint>
    private lateinit var road: Road
    private lateinit var roadOverlay: Polyline
    private lateinit var startingPoint: GeoPoint
    private lateinit var destinationPoint: GeoPoint
    private lateinit var myLocationManager: LocationManager
    private lateinit var departureText: AutoCompleteOnPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
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

        val startMarker = Marker(map)
        startMarker.position = startPoint
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.overlays.add(startMarker)

        val endMarker = Marker(map)
        endMarker.position = endPoint
        endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.overlays.add(endMarker)

        road = roadManager.getRoad(waypoints)
        roadOverlay = RoadManager.buildRoadOverlay(road)
        map.overlays.add(roadOverlay)

        showRouteSteps()

        map.invalidate()


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

        val destinationText = findViewById<View>(R.id.editDestination) as AutoCompleteOnPreferencesBinding


        destinationText.setPrefKeys(SHARED_PREFS_APPKEY, PREF_LOCATIONS_KEY)

        val searchDestButton: Button = findViewById<View>(R.id.buttonSearchDest) as Button
        searchDestButton.setOnClickListener(View.OnClickListener {
            handleSearchButton(
                DEST_INDEX,
                R.id.editDestination
            )
        }) //end
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
