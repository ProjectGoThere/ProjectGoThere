package com.example.projectgothere

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.projectgothere.databinding.ActivityCameraBinding
import com.example.projectgothere.databinding.ActivityMainBinding

class CameraActivity : AppCompatActivity(){

    private lateinit var imageView: ImageView
    private lateinit var button : Button
    private lateinit var binding: ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        binding = ActivityCameraBinding.inflate(layoutInflater)

        imageView = findViewById(R.id.image_view)
        button = findViewById(R.id.open_button)

        if (ContextCompat.checkSelfPermission(this,
            Manifest.permission.CAMERA)!= PackageManager.PERMISSION_GRANTED)

    }


}