package com.gtek.fren.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream

data class EmotionResult(
    val emotion: String,
    val confidence: Float
)

private fun ImageProxy.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    // Pastikan buffer tidak null dan memiliki data yang cukup
    yBuffer?.get(nv21, 0, ySize)
    vBuffer?.get(nv21, ySize, vSize)
    uBuffer?.get(nv21, ySize + vSize, uSize)

    return try {
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        ByteArrayOutputStream().use { outputStream ->
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, outputStream)
            val imageBytes = outputStream.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: throw IllegalStateException("Failed to decode bitmap")
        }
    } catch (e: Exception) {
        Log.e("ImageProxy", "Error converting to bitmap: ${e.message}")
        throw e
    }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "HomeViewModel"
    private val _emotionResults = MutableLiveData<List<EmotionResult>>()
    val emotionResults: LiveData<List<EmotionResult>> = _emotionResults

    private val _processingError = MutableLiveData<String>()
    val processingError: LiveData<String> = _processingError

    private var isDetecting = false
    private val emotionClassifier: EmotionClassifier = EmotionClassifier(application.applicationContext)

    fun startDetection() {
        isDetecting = true
        Log.d(tag, "Detection started")
    }

    fun stopDetection() {
        isDetecting = false
        _emotionResults.postValue(emptyList())
        Log.d(tag, "Detection stopped")
    }

    @OptIn(ExperimentalGetImage::class)
    fun processImageWithFaceDetection(
        imageProxy: ImageProxy,
        faceDetector: FaceDetector
    ) {
        if (!isDetecting) {
            imageProxy.close()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    Log.w(tag, "Skipping frame - no image available")
                    imageProxy.close()
                    return@launch
                }

                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                withContext(Dispatchers.Main) {
                    faceDetector.process(image)
                        .addOnSuccessListener { faces ->
                            if (faces.isEmpty()) {
                                Log.d(tag, "No faces detected")
                                _emotionResults.postValue(emptyList())
                                return@addOnSuccessListener
                            }

                            viewModelScope.launch(Dispatchers.Default) {
                                faces.forEach { face ->
                                    try {
                                        val bitmap = imageProxy.toBitmap()
                                        val mat = Mat()
                                        Utils.bitmapToMat(bitmap, mat)

                                        val boundingBox = face.boundingBox
                                        if (isValidBoundingBox(boundingBox, mat)) {
                                            val faceRegion = mat.submat(
                                                org.opencv.core.Rect(
                                                    boundingBox.left,
                                                    boundingBox.top,
                                                    boundingBox.width(),
                                                    boundingBox.height()
                                                )
                                            )

                                            val processedFace = preprocessFace(faceRegion)
                                            val emotions = emotionClassifier.classify(processedFace)
                                            val significantEmotions = emotions.filter { it.confidence > 0.3f }

                                            withContext(Dispatchers.Main) {
                                                _emotionResults.postValue(significantEmotions)
                                            }

                                            // Cleanup resources
                                            faceRegion.release()
                                            processedFace.release()
                                        } else {
                                            Log.w(tag, "Invalid face bounding box: $boundingBox")
                                        }
                                        mat.release()

                                    } catch (e: Exception) {
                                        Log.e(tag, "Error processing face: ${e.message}", e)
                                        withContext(Dispatchers.Main) {
                                            _processingError.postValue("Error processing face: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(tag, "Face detection failed: ${e.message}", e)
                            _processingError.postValue("Face detection failed: ${e.message}")
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }
            } catch (e: Exception) {
                Log.e(tag, "Image processing error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _processingError.postValue("Image processing error: ${e.message}")
                }
                imageProxy.close()
            }
        }
    }

    private fun isValidBoundingBox(boundingBox: Rect, mat: Mat): Boolean {
        return boundingBox.left >= 0 &&
                boundingBox.top >= 0 &&
                boundingBox.right <= mat.cols() &&
                boundingBox.bottom <= mat.rows()
    }

    private fun preprocessFace(faceMat: Mat): Mat {
        return Mat().also { processedMat ->
            try {
                // Resize to model input size
                Imgproc.resize(faceMat, processedMat, Size(48.0, 48.0))

                // Convert to grayscale
                Imgproc.cvtColor(processedMat, processedMat, Imgproc.COLOR_BGR2GRAY)

                // Normalize pixel values
                Core.normalize(processedMat, processedMat, 0.0, 1.0, Core.NORM_MINMAX)
            } catch (e: Exception) {
                Log.e(tag, "Error preprocessing face: ${e.message}", e)
                processedMat.release()
                throw e
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopDetection()
        Log.d(tag, "ViewModel cleared")
    }
}