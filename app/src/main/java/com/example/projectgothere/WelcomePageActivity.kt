package com.example.projectgothere

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.example.projectgothere.databinding.ActivityWelcomePageBinding
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions

class WelcomePageActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWelcomePageBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomePageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.welcomeClick.setOnClickListener{
            super.finish()
        }
    }
}