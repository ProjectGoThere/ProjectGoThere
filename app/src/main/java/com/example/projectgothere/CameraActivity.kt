package com.example.projectgothere

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.icu.text.DateFormat.getDateTimeInstance
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import androidx.lifecycle.lifecycleScope
import com.example.projectgothere.databinding.ActivityCameraBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.DateFormat.getDateTimeInstance
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : AppCompatActivity() {
    private val takeImageResult = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
        if (isSuccess) {
            latestTmpUri?.let { uri ->
                previewImage.setImageURI(uri)
            }
        }
    }

    private val selectImageFromGalleryResult = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            previewImage.setImageURI(uri)
        }
    }

    private var latestTmpUri: Uri? = null
    private val previewImage by lazy { viewBinding.imageView }

    private lateinit var viewBinding: ActivityCameraBinding
    private var isReadPermissionGranted = false
    private var isWritePermissionGranted = false
    private var isCameraPermissionGranted = false
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ permissions ->
            isCameraPermissionGranted = permissions[Manifest.permission.CAMERA] ?: isCameraPermissionGranted
            isReadPermissionGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: isReadPermissionGranted
            isWritePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: isWritePermissionGranted

        }

        requestPermission()

        viewBinding.captureBtn.setOnClickListener {
            takeImage()
        }
        viewBinding.galleryBtn.setOnClickListener{
            selectImageFromGallery()
        }
        viewBinding.addGallery.setOnClickListener{
            saveToGallery()
        }
        viewBinding.back.setOnClickListener{
            finish()
        }

    }
    private fun takeImage() {
        lifecycleScope.launch {
            getTmpFileUri().let { uri ->
                latestTmpUri = uri
                takeImageResult.launch(uri)
            }
        }
    }

    private fun selectImageFromGallery() = selectImageFromGalleryResult.launch("image/*")

    private fun getTmpFileUri(): Uri {
        val tmpFile = File.createTempFile("tmp_image_file", ".jpeg", cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }

        return FileProvider.getUriForFile(applicationContext, "${BuildConfig.APPLICATION_ID}.provider", tmpFile)
    }

    private fun requestPermission(){
        val isCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val isReadPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val isWritePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val minSdkLevel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        isCameraPermissionGranted = isCameraPermission
        isReadPermissionGranted = isReadPermission
        isWritePermissionGranted = isWritePermission || minSdkLevel

        val permissionRequest = mutableListOf<String>()
        if (!isCameraPermissionGranted) permissionRequest.add(Manifest.permission.CAMERA)
        if (!isWritePermissionGranted) permissionRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (!isReadPermissionGranted) permissionRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)

        if (permissionRequest.isNotEmpty()) permissionLauncher.launch(permissionRequest.toTypedArray())
    }

    private fun saveToGallery() {
        val bitmapDrawable = viewBinding.imageView.drawable as BitmapDrawable
        val bitmap = bitmapDrawable.bitmap
        var outputStream: FileOutputStream? = null
        val curDir = intent.getStringExtra("currentDir")
        val dir = File(curDir)
        val date = Calendar.getInstance().time
        val sdf = SimpleDateFormat(FILENAME_FORMAT,Locale.US)
        val fdate = sdf.format(date)
        val filename = String.format("%s.jpeg", fdate)
        val outFile = File(dir, filename)
        Log.d(TAG,"$dir, $filename")
        try {
            outputStream = FileOutputStream(outFile)
        } catch (e: java.lang.Exception) {
            Toast.makeText(this, "Image Failed to Save",Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        try {
            outputStream?.flush()
        } catch (e: java.lang.Exception) {
            Toast.makeText(this, "Image Failed to Save",Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
        try {
            outputStream?.close()
        } catch (e: java.lang.Exception) {
            Toast.makeText(this, "Image Failed to Save",Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
        Toast.makeText(this,"Image saved",Toast.LENGTH_SHORT).show()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd HH:mm:ss"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}