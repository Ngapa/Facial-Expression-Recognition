package com.gtek.fren.ui.home;

import android.content.Context;
import android.util.Log;

import org.opencv.core.Mat;
import org.pytorch.executorch.EValue;
import org.pytorch.executorch.Module;
import org.pytorch.executorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class EmotionClassifier<EmotionResult> {

    private static final String MODEL_NAME = "40_kan_model.pte";
    private static final String[] EMOTION_CLASSES = {
            "angry", "disgust", "fear", "happy", "neutral", "sad", "surprise"
    };

    private final Module module;
    private final Context context;

    public EmotionClassifier(Context context) {
        this.context = context;
        try {
            module = Module.load(getModelPath(MODEL_NAME));
        } catch (Exception e) {
            Log.e("EmotionClassifier", "Error loading model: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public List<EmotionResult> classify(Mat faceImage) {
        // Convert Mat to tensor
        Tensor inputTensor = matToTensor(faceImage);

        // Run inference
        Tensor outputTensor = module.forward(EValue.from(inputTensor))[0].toTensor();
        float[] scores = outputTensor.getDataAsFloatArray();

        // Convert scores to EmotionResult objects
        List<EmotionResult> results = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            boolean add = results.add(new EmotionResult(EMOTION_CLASSES[i], scores[i]));
        }

        // Sort by confidence descending
        results.sort(Comparator.comparingDouble(EmotionResult::getConfidence).reversed());
        return results;
    }

    private Tensor matToTensor(Mat mat) {
        // Convert Mat to float array
        int size = mat.rows() * mat.cols();
        float[] floatArray = new float[size];
        mat.get(0, 0, floatArray);

        // Create tensor of shape [1, 1, 48, 48] (adjust dimensions as needed)
        return Tensor.fromBlob(floatArray, new long[]{1, 1, 48, 48});
    }

    private String getModelPath(String assetName) {
        File file = new File(context.getFilesDir(), assetName);
        if (!file.exists()) {
            try (InputStream input = context.getAssets().open(assetName);
                 FileOutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = input.read(buffer)) > 0) {
                    output.write(buffer, 0, length);
                }
            } catch (Exception e) {
                Log.e("EmotionClassifier", "Error copying model file: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }
        return file.getAbsolutePath();
    }
}
