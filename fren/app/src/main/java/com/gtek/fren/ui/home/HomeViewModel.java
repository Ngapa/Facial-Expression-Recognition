package com.gtek.fren.ui.home;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.mlkit.vision.face.FaceDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeViewModel extends AndroidViewModel {

    private static final String TAG = "HomeViewModel";

    private final MutableLiveData<List<EmotionClassifier.EmotionResult>> _emotionResults = new MutableLiveData<>();
    public LiveData<List<EmotionClassifier.EmotionResult>> emotionResults = _emotionResults;

    private final MutableLiveData<String> _processingError = new MutableLiveData<>();
    public LiveData<String> processingError = _processingError;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private ImageProcessor imageProcessor;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        initializeEmotionClassifier(application);
    }

    private void initializeEmotionClassifier(Application application) {
        executorService.execute(() -> {
            try {
                EmotionClassifier emotionClassifier = new EmotionClassifier(application.getApplicationContext());
                imageProcessor = new ImageProcessor(executorService, emotionClassifier, _emotionResults, _processingError);
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize EmotionClassifier: " + e.getMessage());
                _processingError.postValue("Failed to initialize classifier: " + e.getMessage());
            }
        });
    }

    public void startDetection() {
        if (imageProcessor != null) {
            imageProcessor.setIsDetecting(true);
            Log.d(TAG, "Detection started");
        }
    }

    public void stopDetection() {
        if (imageProcessor != null) {
            imageProcessor.setIsDetecting(false);
            _emotionResults.postValue(new ArrayList<>());
            Log.d(TAG, "Detection stopped");
        }
    }

    public void processImageWithFaceDetection(final ImageProxy imageProxy, final FaceDetector faceDetector) {
        if (imageProcessor != null) {
            imageProcessor.processImageWithFaceDetection(imageProxy, faceDetector);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopDetection();
        executorService.shutdown();
        Log.d(TAG, "ViewModel cleared");
    }
}