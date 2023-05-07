package com.example.projectgothere

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.DateFormat.getDateTimeInstance
import com.example.projectgothere.databinding.ActivityCameraBinding
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.projectgothere.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class CameraActivity : AppCompatActivity() {
    private lateinit var selectedImage: ImageView
    private lateinit var cameraBtn: Button
    private lateinit var galleryBtn: Button
    private lateinit var currentPhotoPath: String
    private lateinit var binding: ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedImage = findViewById(R.id.displayImageView)
        cameraBtn = findViewById(R.id.cameraBtn)
        galleryBtn = findViewById(R.id.galleryBtn)

        binding.cameraBtn.setOnClickListener{
            val cameraIntent = Intent(this, CameraActivity::class.java)
            startActivity(cameraIntent)
        }
        galleryBtn.setOnClickListener {
            val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(gallery, GALLERY_REQUEST_CODE)
        }
    }

    private fun askCameraPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERM_CODE
            )
        } else {
            dispatchTakePictureIntent()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERM_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent()
            } else {
                Toast.makeText(
                    this,
                    "Camera Permission is Required to Use camera.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                val f = File(currentPhotoPath)
                selectedImage.setImageURI(Uri.fromFile(f))
                Log.d("tag", "Absolute Url of Image is " + Uri.fromFile(f))
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                val contentUri = Uri.fromFile(f)
                mediaScanIntent.data = contentUri
                this.sendBroadcast(mediaScanIntent)
            }
        }
        if (requestCode == GALLERY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                val contentUri = data!!.data
                val timeStamp = getDateTimeInstance().format(Date())
                val imageFileName = "JPEG_" + timeStamp + "." + getFileExt(contentUri)
                Log.d("tag", "onActivityResult: Gallery Image Uri:  $imageFileName")
                selectedImage.setImageURI(contentUri)
            }
        }
    }


    private fun getFileExt(contentUri: Uri?): String? {
        val c = contentResolver
        val mime = MimeTypeMap.getSingleton()
        return mime.getExtensionFromMimeType(c.getType(contentUri!!))
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp = getDateTimeInstance().format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        //        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        val storageDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val image = File.createTempFile(
            imageFileName,  /* prefix */
            ".jpg",  /* suffix */
            storageDir /* directory */
        )

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.absolutePath
        return image
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            // Create the File where the photo should go
            var photoFile: File? = null
            try {
                photoFile = createImageFile()
            } catch (ex: IOException) {
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                val photoURI = FileProvider.getUriForFile(
                    this,
                    "com.example.projectgothere",
                    photoFile
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(
                    takePictureIntent,
                    CAMERA_REQUEST_CODE
                )
            }
        }
    }

    companion object {
        const val CAMERA_PERM_CODE = 101
        const val CAMERA_REQUEST_CODE = 102
        const val GALLERY_REQUEST_CODE = 105
    }
}