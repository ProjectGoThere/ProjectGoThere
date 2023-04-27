package com.example.projectgothere

import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.ImageView
import org.osmdroid.bonuspack.R
import org.osmdroid.bonuspack.location.POI
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow


/**
 * A customized InfoWindow handling POIs.
 * We inherit from MarkerInfoWindow as it already provides most of what we want.
 * And we just add support for a "more info" button.
 *
 * @author M.Kergall
 */
class POIInfoWindow(mapView: MapView?) :
    MarkerInfoWindow(R.layout.bonuspack_bubble, mapView) {
    private var mSelectedPOI: POI? = null

    init {
        val btn = mView.findViewById<View>(R.id.bubble_moreinfo) as Button
        btn.setOnClickListener { view ->
            if (mSelectedPOI!!.mUrl != null) {
                val myIntent = Intent(
                    Intent.ACTION_VIEW, Uri.parse(
                        mSelectedPOI!!.mUrl
                    )
                )
                view.context.startActivity(myIntent)
            }
        }
    }

    override fun onOpen(item: Any) {
        val marker = item as Marker
        mSelectedPOI = marker.relatedObject as POI
        super.onOpen(item)

        //Fetch the thumbnail in background
        if (mSelectedPOI!!.mThumbnailPath != null) {
            val imageView = mView.findViewById<View>(R.id.bubble_image) as ImageView
            mSelectedPOI!!.fetchThumbnailOnThread(imageView)
        }

        //Show or hide "more info" button:
        if (mSelectedPOI!!.mUrl != null) mView.findViewById<View>(R.id.bubble_moreinfo).visibility =
            View.VISIBLE else mView.findViewById<View>(R.id.bubble_moreinfo).visibility =
            View.GONE
    }
}