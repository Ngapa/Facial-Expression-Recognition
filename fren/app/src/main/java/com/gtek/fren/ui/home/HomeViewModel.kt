package com.gtek.fren.ui.home

import android.app.Application
import android.graphics.Bitmap
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

data class EmotionResult(
    val emotion: String,
    val confidence: Float
)

class HomeViewModel<EmotionResult>(application: Application) : AndroidViewModel(application) {
    private val _emotionResults = MutableLiveData<List<EmotionResult>>()
    val emotionResults: LiveData<List<EmotionResult>> = _emotionResults

    private val _processingError = MutableLiveData<String>()
    val processingError: LiveData<String> = _processingError

    private var isDetecting = false
    private val emotionClassifier: EmotionClassifier = EmotionClassifier(application.applicationContext)

    fun startDetection() {
        isDetecting = true
    }

    fun stopDetection() {
        isDetecting = false
        _emotionResults.postValue(emptyList())
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
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    withContext(Dispatchers.Main) {
                        faceDetector.process(image)
                            .addOnSuccessListener { faces ->
                                if (faces.isEmpty()) {
                                    _emotionResults.postValue(emptyList())
                                    return@addOnSuccessListener
                                }

                                viewModelScope.launch(Dispatchers.Default) {
                                    faces.forEach { face ->
                                        try {
                                            // Convert image to OpenCV Mat
                                            val bitmap = imageProxy.toBitmap()
                                            val mat = Mat()
                                            Utils.bitmapToMat(bitmap, mat)

                                            // Convert Android Rect to OpenCV Rect
                                            val opencvRect = org.opencv.core.Rect(
                                                face.boundingBox.left,
                                                face.boundingBox.top,
                                                face.boundingBox.width(),
                                                face.boundingBox.height()
                                            )
                                            if (face.boundingBox.left >= 0 &&
                                                face.boundingBox.top >= 0 &&
                                                face.boundingBox.right <= mat.cols() &&
                                                face.boundingBox.bottom <= mat.rows()) {

                                                val opencvRect = org.opencv.core.Rect(
                                                    face.boundingBox.left,
                                                    face.boundingBox.top,
                                                    face.boundingBox.width(),
                                                    face.boundingBox.height()
                                                )

                                                val faceRegion = mat.submat(opencvRect)

                                                // Preprocess and classify
                                                val processedFace = preprocessFace(faceRegion)
                                                val emotions = emotionClassifier.classify(processedFace)

                                                // Filter emotions with confidence > 30%
                                                val significantEmotions = emotions.filter { it.confidence > 0.3f }
                                                withContext(Dispatchers.Main) {
                                                    _emotionResults.postValue(significantEmotions as List<EmotionResult>)
                                                }

                                                // Cleanup
                                                faceRegion.release()
                                                mat.release()
                                                processedFace.release()
                                            } else {
                                                Log.w("FaceDetection", "Face bounding box outside image bounds")
                                            }

                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                _processingError.postValue("Error processing face: ${e.message}")
                                            }
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                _processingError.postValue("Face detection failed: ${e.message}")
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _processingError.postValue("Image processing error: ${e.message}")
                }
                imageProxy.close()
            }
        }
    }

    private fun preprocessFace(faceMat: Mat): Mat {
        val processedMat = Mat()

        // Resize to model input size (adjust size as needed)
        Imgproc.resize(faceMat, processedMat, Size(48.0, 48.0))

        // Convert to grayscale
        Imgproc.cvtColor(processedMat, processedMat, Imgproc.COLOR_BGR2GRAY)

        // Normalize pixel values
        Core.normalize(processedMat, processedMat, 0.0, 1.0, Core.NORM_MINMAX)

        return processedMat
    }

    override fun onCleared() {
        super.onCleared()
        stopDetection()
    }
}