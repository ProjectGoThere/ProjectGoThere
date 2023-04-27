package com.example.projectgothere

import android.view.View
import android.widget.Button
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow


/**
 * A customized InfoWindow handling "itinerary" points (start, destination and via-points).
 * We inherit from MarkerInfoWindow as it already provides most of what we want.
 * And we just add support for a "remove" button.
 *
 * @author M.Kergall
 */
class WaypointInfoWindow(layoutResId: Int, mapView: MapView?) :
    MarkerInfoWindow(layoutResId, mapView) {
    var mSelectedPoint = 0

    init {
        val btnDelete = mView.findViewById<View>(R.id.bubble_delete) as Button
        btnDelete.setOnClickListener { view -> //Call the removePoint method on MapActivity.
            //TODO: find a cleaner way to do that!
            val mapActivity: MainActivity = view.context as MainActivity
            mapActivity.removePoint(mSelectedPoint)
            close()
        }
    }

    override fun onOpen(item: Any) {
        val eItem = item as Marker
        mSelectedPoint = eItem.relatedObject as Int
        super.onOpen(item)
    }
}