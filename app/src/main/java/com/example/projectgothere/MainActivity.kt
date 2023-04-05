package com.example.projectgothere

import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.view.View
import android.Manifest
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager

import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.MapController
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
    override fun onCreate(savedInstanceState: Bundle?) {
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName;
        setContentView(R.layout.activity_main)
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


        map = findViewById<View>(R.id.map) as MapView
        map.setMultiTouchControls(true)
        roadManager = OSRMRoadManager(this, "MY_USER_AGENT")

        val startPoint = GeoPoint(48.13, -1.63)
        val endPoint = GeoPoint(48.4, -1.9)

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

        map.invalidate()
    }

    private fun displayToast(s:String){
        Toast.makeText(applicationContext, "$s Permission Granted",Toast.LENGTH_SHORT).show()
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
