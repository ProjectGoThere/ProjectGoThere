package com.example.projectgothere

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.viewpager.widget.ViewPager
import com.example.projectgothere.databinding.ActivityViewPicturesBinding
import java.io.File

class ViewPicturesActivity : AppCompatActivity() {
    private var viewPager: ViewPager? = null
    private val images = arrayListOf<Uri>()
    private lateinit var dir:File
    private var pictureAdapter:PictureAdapter? = null
    private lateinit var binding: ActivityViewPicturesBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewPicturesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dir = File(intent.getStringExtra("Direct")!!)
        getImages()
        viewPager = binding.viewPager
        pictureAdapter = PictureAdapter(this, images.toTypedArray())
        viewPager!!.adapter = pictureAdapter
        binding.tripTitle.text = dir.name
        binding.back.setOnClickListener{
            finish()
        }
    }

    private fun getImages() {
        if (dir.exists()) {
            dir.walkTopDown().forEach {
                if (it.isFile && it.absolutePath.endsWith(".jpeg")) {
                    val uri = FileProvider.getUriForFile(
                        this,
                        BuildConfig.APPLICATION_ID + ".provider",
                        it.absoluteFile
                    )
                    images.add(uri)
                }
            }
        }
    }
}