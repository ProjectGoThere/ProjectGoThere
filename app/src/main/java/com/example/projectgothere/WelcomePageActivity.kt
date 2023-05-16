package com.example.projectgothere

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.projectgothere.databinding.ActivityWelcomePageBinding


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