package com.example.projectgothere

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.example.projectgothere.databinding.ActivityListTripsBinding
import java.io.File

class ListTripsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityListTripsBinding
    private lateinit var rView: RecyclerView
    private val trips = arrayListOf<File>()
    private lateinit var dir: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListTripsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dir = File(intent.getStringExtra("rootDir")!!)
        rView = binding.recyclerView
        rView.layoutManager = LinearLayoutManager(this)
        rView.setHasFixedSize(true)

        getTrips()

        binding.backButton.setOnClickListener {
            finish()
        }
    }
    private fun getTrips() {
        if (dir.exists()) {
            dir.walkTopDown().forEach {
                if (it.parent == dir.absolutePath && it.isDirectory) {
                    trips.add(it)
                }
            }
        }
        var adapter = TripAdapter(this,trips.toTypedArray())
        rView.adapter = adapter
        adapter.setOnItemClickListener(object:TripAdapter.onItemClickListener{
            override fun onItemClick(position: Int) {
                val intent = Intent(this@ListTripsActivity,ViewPicturesActivity::class.java)
                intent.putExtra("Direct",trips[position].absolutePath)
                startActivity(intent)
            }
        })
    }
}