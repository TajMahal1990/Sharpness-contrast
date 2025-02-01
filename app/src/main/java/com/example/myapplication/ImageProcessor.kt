package com.example.myapplication


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Класс обработки изображений
object ImageProcessor {
    suspend fun processImage(photoFile: File, context: MainActivity): String? {
        Log.d("ImageProcessor", "Начало обработки изображения: ${photoFile.absolutePath}")

        if (!photoFile.exists()) {
            Log.e("ImageProcessor", "Файл не найден: ${photoFile.absolutePath}")
            return null
        }

        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        if (bitmap == null) {
            Log.e("ImageProcessor", "Не удалось декодировать изображение")
            return null
        }

        val rotatedBitmap = rotateBitmap(bitmap, 270f)
        val contrast = calculateContrast(rotatedBitmap)
        Log.d("ImageProcessor", "Контрастность изображения: $contrast")

        return detectFacesAndSave(rotatedBitmap, contrast, context)
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

    private suspend fun detectFacesAndSave(bitmap: Bitmap, contrast: Double, context: MainActivity): String? {
        if (contrast < 25.0) {
            Log.e("FaceDetection", "Контраст слишком низкий ($contrast), фото не сохранено")
            return null
        }

        val image = InputImage.fromBitmap(bitmap, 0)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()

        val detector = FaceDetection.getClient(options)

        return withContext(Dispatchers.IO) {
            try {
                val faces = Tasks.await(detector.process(image))
                val faceDetected = faces.isNotEmpty()
                val timestamp = SimpleDateFormat("yyyy_MM_dd__HH_mm_ss", Locale.getDefault()).format(Date())
                val filename = if (faceDetected) "photo_with_face_$timestamp.jpg" else "photo_no_face_$timestamp.jpg"

                saveToInternalStorage(bitmap, filename, context)
                Log.d("FaceDetection", "Файл сохранён: $filename")

                filename
            } catch (e: Exception) {
                Log.e("FaceDetection", "Ошибка распознавания лица", e)
                null
            }
        }
    }

    private fun saveToInternalStorage(bitmap: Bitmap, filename: String, context: MainActivity) {
        val file = File(context.filesDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        Log.d("ImageProcessor", "Файл сохранён во внутреннее хранилище: ${file.absolutePath}")
    }
}
