package com.example.myapplication


import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture
    private lateinit var imageView: ImageView
    private lateinit var captureButton: Button

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Log.e("Camera", "Camera permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        captureButton = findViewById(R.id.captureButton)

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        captureButton.setOnClickListener {
            takePhoto()
        }
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

    private fun takePhoto() {
        val photoFile = File(cacheDir, "temp_photo.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val rotatedBitmap = rotateBitmap(bitmap, 270f)
                    val contrast = calculateContrast(rotatedBitmap)
                    Log.d("Camera", "Calculated contrast: $contrast")
                    runOnUiThread {
                        imageView.setImageBitmap(rotatedBitmap)
                        Toast.makeText(this@MainActivity, "Контраст: ${String.format("%.2f", contrast)}", Toast.LENGTH_LONG).show()
                    }

                    detectFacesAndSave(rotatedBitmap, contrast)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("Camera", "Error taking photo", exception)
                }
            }
        )
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun calculateContrast(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val brightnessValues = pixels.map { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            (r + g + b) / 3.0
        }

        val mean = brightnessValues.average()
        val variance = brightnessValues.sumOf { (it - mean) * (it - mean) } / brightnessValues.size
        return kotlin.math.sqrt(variance)
    }

    private fun detectFacesAndSave(bitmap: Bitmap, contrast: Double) {
        if (contrast < 25.0) {
            Log.d("FaceDetection", "Контраст слишком низкий(${String.format("%.2f", contrast)}), фото не сохранено")
            runOnUiThread {
                Toast.makeText(this, "Контраст слишком низкий, фото не сохранено", Toast.LENGTH_LONG).show()
            }
            return
        }

        val image = InputImage.fromBitmap(bitmap, 0)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()

        val detector = FaceDetection.getClient(options)
        detector.process(image)
            .addOnSuccessListener { faces ->
                val faceDetected = faces.isNotEmpty()
                val timestamp = SimpleDateFormat("yyyy_MM_dd__HH:mm:ss", Locale.getDefault()).format(Date())
                val filename = if (faceDetected) "photo_with_face_$timestamp.jpg" else "photo_no_face_$timestamp.jpg"
                saveToInternalStorage(bitmap, filename)
                Log.d("FaceDetection", "Файл сохранён как: $filename")
                Toast.makeText(this@MainActivity, "Файл сохранён как: $filename", Toast.LENGTH_LONG).show()

            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "Ошибка при распознавании лица", e)
            }
    }

    private fun saveToInternalStorage(bitmap: Bitmap, filename: String) {
        val file = File(filesDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
