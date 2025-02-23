package com.gtek.fren.ui.helper;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class EmotionClassifier {

    private static final String TAG = "EmotionClassifier";
    public static final String MODEL_CNN_RESNET = "cnnresnet.tflite";
    public static final String MODEL_KAN_RESNET = "kanresnet.tflite";
    private static String currentModel = MODEL_CNN_RESNET;
    private static final String[] EMOTION_CLASSES = {
            "angry", "disgust", "fear", "happy", "neutral", "sad", "surprise"
    };
    private Interpreter interpreter;
    private static Context context;

    static {
        try {
            if (!OpenCVLoader.initLocal()) {
                Log.e(TAG, "Failed to load OpenCV");
                throw new RuntimeException("Failed to load OpenCV");
            }
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load OpenCV: " + e.getMessage());
            throw new RuntimeException("Failed to load OpenCV", e);
        }
    }

    public EmotionClassifier(Context context) {
        this.context = context;
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }

        try {
            // Load model default (CNN ResNet)
            loadModel(MODEL_CNN_RESNET);
        } catch (IOException e) {
            Log.e(TAG, "Error loading model: " + e.getMessage());
            throw new RuntimeException("Failed to load TFLite model", e);
        }
    }

    // Method untuk mengganti model
    public void switchModel(String modelName) throws IOException {
        if (!modelName.equals(currentModel)) {
            loadModel(modelName);
        }
    }

    // Method untuk mendapatkan nama model saat ini
    public String getCurrentModel() {
        return currentModel;
    }

    // Method untuk loading model
    private void loadModel(String modelName) throws IOException {
        String modelPath = assetFilePath(context, modelName);
        if (modelPath != null) {
            File modelFile = new File(modelPath);
            Interpreter.Options options = new Interpreter.Options();

            // Tutup interpreter yang ada jika sudah ada
            if (interpreter != null) {
                interpreter.close();
            }

            interpreter = new Interpreter(modelFile, options);

            Log.d(TAG, "Model loaded successfully");
            Log.d(TAG, "Model name: " + modelName);
            Log.d(TAG, "Model path: " + modelPath);
            Log.d(TAG, "Model file exists: " + modelFile.exists());
            Log.d(TAG, "Model file size: " + modelFile.length() + " bytes");

            // Verify model with dummy input
            float[][][][] dummyInput = new float[1][48][48][1];
            float[][] dummyOutput = new float[1][7];
            interpreter.run(dummyInput, dummyOutput);

            Log.d(TAG, "Model verification successful");
            currentModel = modelName;
        } else {
            throw new RuntimeException("Error loading model file from assets");
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
        try {
            if (faceImage == null || faceImage.empty()) {
                Log.e(TAG, "Invalid input image");
                return Collections.emptyList();
            }

            // Prepare input data
            float[][][][] inputArray = new float[1][48][48][1];
            for (int y = 0; y < 48; y++) {
                for (int x = 0; x < 48; x++) {
                    double[] pixel = faceImage.get(y, x);
                    if (pixel != null && pixel.length > 0) {
                        // Normalize pixel value (0-1)
                        inputArray[0][y][x][0] = (float) (pixel[0] / 255.0);
                    }
                }
            }

            // Prepare output array
            float[][] outputArray = new float[1][7];

            // Run inference
            interpreter.run(inputArray, outputArray);

            // Process results
            float[] scores = outputArray[0];
            float[] probs = softmax(scores);

            List<EmotionResult> results = new ArrayList<>();
            for (int i = 0; i < EMOTION_CLASSES.length; i++) {
                results.add(new EmotionResult(EMOTION_CLASSES[i], probs[i]));
            }
            return results;

        } catch (Exception e) {
            Log.e(TAG, "Classification error: " + e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private float[] softmax(float[] scores) {
        float maxScore = Float.NEGATIVE_INFINITY;
        for (float score : scores) {
            if (score > maxScore) {
                maxScore = score;
            }
        }

        float[] exps = new float[scores.length];
        float expSum = 0.0f;
        for (int i = 0; i < scores.length; i++) {
            exps[i] = (float) Math.exp(scores[i] - maxScore);
            expSum += exps[i];
        }

        float[] probabilities = new float[scores.length];
        if (expSum > 0) {
            for (int i = 0; i < scores.length; i++) {
                probabilities[i] = (exps[i] / expSum) * 100;
            }
        }

        Log.d(TAG, "Raw scores: " + Arrays.toString(scores));
        Log.d(TAG, "After softmax: " + Arrays.toString(probabilities));

        float sum = 0;
        for (float prob : probabilities) {
            sum += prob;
        }
        Log.d(TAG, "Total probability: " + sum + "%");

        return probabilities;
    }

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
            return String.format(Locale.US, "EmotionResult{emotion='%s', confidence=%.2f%%}",
                    emotion, confidence);
        }
    }
}