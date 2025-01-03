package com.gtek.fren.ui.home

import android.content.Context
import com.gtek.fren.executorch.EValue
import com.gtek.fren.executorch.Module
import com.gtek.fren.executorch.Tensor
import org.opencv.core.Mat
import java.io.File
import java.io.FileOutputStream

class EmotionClassifier(private val context: Context) {
    private val module: Module

    companion object {
        private const val MODEL_NAME = "40_kan_model.pte"
        val EMOTION_CLASSES = arrayOf(
            "angry", "disgust", "fear", "happy", "neutral", "sad", "surprise"
        )
    }

    init {
        module = Module.load(getModelPath(MODEL_NAME))
    }

    fun classify(faceImage: Mat): List<EmotionResult> {
        // Convert Mat to tensor
        val inputTensor = matToTensor(faceImage)

        // Run inference
        val outputTensor = module.forward(EValue.from(inputTensor))[0].toTensor()
        val scores = outputTensor.dataAsFloatArray

        // Convert scores to EmotionResult objects
        return scores.mapIndexed { index, score ->
            EmotionResult(
                emotion = EMOTION_CLASSES[index],
                confidence = score
            )
        }.sortedByDescending { it.confidence }
    }

    private fun matToTensor(mat: Mat): Tensor {
        // Convert Mat to float array
        val floatArray = FloatArray(mat.rows() * mat.cols())
        mat.get(0, 0, floatArray)

        // Create tensor of shape [1, 1, 48, 48] (adjust dimensions as needed)
        return Tensor.fromBlob(
            floatArray,
            longArrayOf(1, 1, 48, 48)
        )
    }

    private fun getModelPath(assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file.absolutePath
    }
}