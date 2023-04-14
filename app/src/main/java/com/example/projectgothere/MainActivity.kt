package com.example.projectgothere

import android.Manifest
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build.ID
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import android.preference.PreferenceManager
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.projectgothere.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
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
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import kotlin.random.Random


private const val TAG = "MainActivity"
private const val LOCATION_CODE = 101
private const val CAM_CODE = 102
private const val req_code = 100
private val permList = arrayOf(Manifest.permission.CAMERA,Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.ACCESS_FINE_LOCATION)
class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks{
    private lateinit var map : MapView
    private lateinit var mapController: MapController
    private lateinit var roadManager: RoadManager
    private lateinit var waypoints: ArrayList<GeoPoint>
    private lateinit var markers: ArrayList<Marker>
    private lateinit var road: Road
    private lateinit var roadOverlay: Polyline
    private lateinit var startingPoint: GeoPoint
    private lateinit var destinationPoint: GeoPoint
    private lateinit var myLocationManager: LocationManager
    private lateinit var departureText: AutoCompleteOnPreferences

    private var currentLocation: GeoPoint = GeoPoint(44.3242, -93.9760)
    private var extraStops: Int = 2
    private lateinit var binding: ActivityMainBinding
    private var cityAddress: String? = null
    private var countyAddress: String? = null
    private var streetAddress: String? = null
    private var completeAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName;
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //setContentView(R.layout.activity_main)

        handlePermissions()
        val intent = Intent(this,WelcomePageActivity::class.java)
        startActivity(intent)

        map = findViewById<View>(R.id.map) as MapView
        map.setMultiTouchControls(true)
        roadManager = OSRMRoadManager(this, "MY_USER_AGENT")

        getLocation()
        //ContextCompat.getDrawable(this,R.drawable.ic_camera)
        binding.cameraButton.setOnClickListener{
            Toast.makeText(applicationContext, "Camera Button is Clickable", Toast.LENGTH_SHORT).show()
            //val cameraIntent = Intent(this, CameraActivity::class.java)
            //startActivity(cameraIntent)
        }

        val startPoint = currentLocation
        val endPoint = GeoPoint(46.7867, -92.1005)

        waypoints = ArrayList()
        waypoints.add(startPoint)
        waypoints.add(endPoint)
        markers = ArrayList()

        addWaypoints(waypoints, extraStops)

        mapController = map.controller as MapController
        mapController.setZoom(9)
        mapController.setCenter(startPoint)

        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(applicationContext), map)
        locationOverlay.enableMyLocation()
        map.overlays.add(locationOverlay)

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

    private fun rand(start: Int, end: Int): Int {
        require(!(start > end || end - start + 1 > Int.MAX_VALUE)) { "Illegal Argument" }
        return Random(System.nanoTime()).nextInt(end - start + 1) + start
    }

    private fun getLocation() {
        var location: Location? = null
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (e: SecurityException) {
            Toast.makeText(applicationContext, "Permission Required", Toast.LENGTH_SHORT).show()
        }
        if (location != null) {
            val loc = GeoPoint(
                (location.latitude),
                (location.longitude)
            )
            currentLocation = loc
        }
    }

    private fun addWaypoints(waypoints: ArrayList<GeoPoint>, extraStops: Int){
        var k = 0
        while (k<extraStops){
            val waypointID = rand(0, 1334)
            val rootRef = FirebaseDatabase.getInstance().reference
            val addressRef = rootRef.child("SpreadSheet").child(waypointID.toString()).child("Address")
            val valueEventListener: ValueEventListener = object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    var switch = 0
                    for (ds in dataSnapshot.children) {
                        when (switch){
                            0 -> cityAddress = ds.getValue(String::class.java)
                            1 -> countyAddress = ds.getValue(String::class.java)
                            2 -> streetAddress = ds.getValue(String::class.java)
                        }
                        switch++
                    }
                    completeAddress = "$streetAddress $cityAddress MN"
                    Log.d(TAG, completeAddress!!)
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Log.d(TAG, databaseError.message)
                }
            }

            addressRef.addListenerForSingleValueEvent(valueEventListener)
            k++
           // change address from written to a GeoPoint

            // waypoints.add()//specific GeoPoint chosen randomly from database
        }

        for ((i, item) in waypoints.withIndex()){
            markers.add(Marker(map))
            val currentMarker = markers.elementAt(i)
            currentMarker.position = item
            currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(currentMarker)
        }
    }

    private fun showRouteSteps(){
        val nodeIcon = ContextCompat.getDrawable(this, R.drawable.marker_node)
        for(i in 0 until road.mNodes.size){
            val node = road.mNodes[i]
            val nodeMarker = Marker(map)
            nodeMarker.position = node.mLocation
            nodeMarker.icon = nodeIcon
            nodeMarker.title = "Step $i"
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
    private fun displayToast(s:String){
        Toast.makeText(applicationContext, "$s Permission Granted", Toast.LENGTH_SHORT).show()
    }
    private fun handlePermissions(){
        if (EasyPermissions.hasPermissions(this, *permList)) {
            Toast.makeText(this, "Permissions Granted", Toast.LENGTH_SHORT).show()
        } else {
            EasyPermissions.requestPermissions(
                this, R.string.rat.toString(),
                req_code, *permList
            )
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
            102 -> displayToast("Camera")
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
