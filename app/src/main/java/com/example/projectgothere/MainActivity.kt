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


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        super.onCreate(savedInstanceState)
        Configuration.getInstance().setUserAgentValue("MyOwnUserAgent/1.0");
        setContentView(R.layout.activity_main)

  
        val map = findViewById<View>(R.id.map) as MapView
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
}