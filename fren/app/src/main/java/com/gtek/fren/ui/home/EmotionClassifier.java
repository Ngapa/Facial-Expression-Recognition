package com.gtek.fren.ui.home;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.soloader.SoLoader;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.pytorch.executorch.EValue;
import org.pytorch.executorch.Module;
import org.pytorch.executorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmotionClassifier {

    private static final String TAG = "EmotionClassifier";
    private static final String MODEL_NAME = "40_kan_model.pte";
    private static final String[] EMOTION_CLASSES = {
            "angry", "disgust", "fear", "happy", "neutral", "sad", "surprise"
    };
    private final Module module;
    @SuppressLint("StaticFieldLeak")
    private static Context context;

    static {
        try {
            // Load OpenCV first
            if (!OpenCVLoader.initLocal()) {
                Log.e(TAG, "Failed to load OpenCV");
                throw new RuntimeException("Failed to load OpenCV");
            }

            // Load dependencies in correct order
            System.loadLibrary("c++_shared");
            System.loadLibrary("fbjni");

            // Use SoLoader from Facebook
            SoLoader.init(context, false);

            // Finally load executorch
            System.loadLibrary("executorch");

            Log.d(TAG, "All native libraries loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native libraries: " + e.getMessage());
            throw new RuntimeException("Failed to load native libraries", e);
        }
    }

    public EmotionClassifier(Context context) {
        this.context = context;
        // Ensure we have valid context
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        try {
            // Load model with proper error handling
            String modelPath = assetFilePath(context, MODEL_NAME);
            if (modelPath != null) {
                module = Module.load(modelPath);
                Log.d(TAG, "Model loaded successfully from: " + modelPath);
            } else {
                throw new RuntimeException("Error loading model file from assets");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading model: " + e.getMessage());
            throw new RuntimeException("Failed to load PyTorch model", e);
        }
    }

    private String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            Log.d(TAG, "Model found in internal storage: " + file.getAbsolutePath());
            return file.getAbsolutePath();
        }

        Log.d(TAG, "Copying model from assets to internal storage: " + file.getAbsolutePath());
        try (InputStream is = context.getAssets().open(assetName);
             OutputStream os = new FileOutputStream(file)) {
            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
        }
        Log.d(TAG, "Model copied successfully to internal storage");
        return file.getAbsolutePath();
    }

    public List<EmotionResult> classify(Mat faceImage) {
        if (faceImage == null || faceImage.empty()) {
            Log.w(TAG, "Received an empty or null face image. Returning empty result.");
            return new ArrayList<>();
        }
        try {
            // Convert Mat to tensor
            Tensor inputTensor = matToTensor(faceImage);

            // Run inference
            Tensor outputTensor = module.forward(EValue.from(inputTensor))[0].toTensor();
            float[] scores = outputTensor.getDataAsFloatArray();

            // Convert scores to EmotionResult objects
            List<EmotionResult> results = new ArrayList<>();
            for (int i = 0; i < scores.length; i++) {
                results.add(new EmotionResult(EMOTION_CLASSES[i], scores[i]));
            }

            // Sort by confidence descending
            Collections.sort(results, (a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
            return results;
        } catch (Exception e) {
            Log.e(TAG, "Error during classification: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private Tensor matToTensor(Mat mat) {
        try {
            // Convert Mat to float array
            int size = mat.rows() * mat.cols();
            float[] floatArray = new float[size];
            mat.get(0, 0, floatArray);

            // Create tensor of shape [1, 1, 48, 48] (adjust dimensions as needed)
            return Tensor.fromBlob(floatArray, new long[]{1, 1, 48, 48});
        } catch (Exception e) {
            Log.e(TAG, "Error converting Mat to Tensor: " + e.getMessage());
            throw new RuntimeException("Failed to convert Mat to Tensor", e);
        }
    }


    // Nested EmotionResult class
    public static class EmotionResult {
        private final String emotion;
        private final float confidence;

        public EmotionResult(String emotion, float confidence) {
            this.emotion = emotion;
            this.confidence = confidence;
        }

        public String getEmotion() {
            return emotion;
        }

        public float getConfidence() {
            return confidence;
        }

        @NonNull
        @Override
        public String toString() {
            return "EmotionResult{" +
                    "emotion='" + emotion + '\'' +
                    ", confidence=" + confidence +
                    '}';
        }
    }
}