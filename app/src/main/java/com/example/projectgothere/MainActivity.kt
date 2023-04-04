package com.example.projectgothere

import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.Manifest
import android.widget.Toast
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions


class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks{
    private lateinit var map : MapView
    override fun onCreate(savedInstanceState: Bundle?) {
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        super.onCreate(savedInstanceState)
        Configuration.getInstance().setUserAgentValue("MyOwnUserAgent/1.0")
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

        val startPoint = GeoPoint(48.13, -1.63)
        val mapController = map.controller
        mapController.setZoom(9)
        mapController.setCenter(startPoint)

        val startMarker = Marker(map)
        startMarker.position = startPoint
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.overlays.add(startMarker)
        map.invalidate()
    }

    private fun displayToast(s:String){
        Toast.makeText(applicationContext, s + " Permission Granted",Toast.LENGTH_SHORT).show()
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