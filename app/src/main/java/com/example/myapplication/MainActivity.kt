package com.example.myapplication
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var photoDatabase: PhotoDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoDatabase = PhotoDatabaseHelper(this)

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 100)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        schedulePhotoCapture()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
            cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun schedulePhotoCapture() {
        handler.postDelayed({
            takePhoto()
            schedulePhotoCapture()
        }, TimeUnit.SECONDS.toMillis(10))
    }

    private fun takePhoto() {
        val photoFile = File(cacheDir, "temp_photo.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        Log.d("Camera", "Запуск процесса съёмки")
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d("Camera", "Фото успешно сохранено во временный файл: ${photoFile.absolutePath}")

                    lifecycleScope.launch {
                        val filename = ImageProcessor.processImage(photoFile, this@MainActivity)
                        if (filename != null) {
                            photoDatabase.addPhoto(filename)
                            Log.d("Database", "Фото добавлено в базу: $filename")
                            val allPhotos = photoDatabase.getAllPhotos()
                            Log.d("Database", "Текущий список фото в базе: ${allPhotos.joinToString()}")
                        } else {
                            Log.e("Database", "Ошибка обработки фото перед добавлением в базу")
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("Camera", "Ошибка при съёмке фото", exception)
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        handler.removeCallbacksAndMessages(null)
    }
}
