package com.gtek.fren.ui.home;

import android.app.Application;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.gtek.fren.ui.helper.EmotionBenchmark;
import com.gtek.fren.ui.helper.EmotionClassifier;
import com.gtek.fren.ui.helper.ImageProcessor;

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

    private final MutableLiveData<Boolean> _isInitialized = new MutableLiveData<>(false);
    public LiveData<Boolean> isInitialized = _isInitialized;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private ImageProcessor imageProcessor;
    private EmotionClassifier emotionClassifier;
    private final EmotionBenchmark benchmark;


    public static class HomeViewModelFactory implements ViewModelProvider.Factory {
        private final Application application;
        private final EmotionBenchmark benchmark;

        public HomeViewModelFactory(Application application) {
            this.application = application;
            this.benchmark = new EmotionBenchmark(); // Create benchmark instance here
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(HomeViewModel.class)) {
                return (T) new HomeViewModel(application, benchmark);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
    public HomeViewModel(@NonNull Application application, EmotionBenchmark benchmark) {
        super(application);
        this.benchmark = new EmotionBenchmark();
        initializeEmotionClassifier(application);
    }

    private void initializeEmotionClassifier(Application application) {
        executorService.execute(() -> {
            try {
                emotionClassifier = new EmotionClassifier(application.getApplicationContext());
                _isInitialized.postValue(true);
                Log.d(TAG, "EmotionClassifier initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize EmotionClassifier: " + e.getMessage());
                _processingError.postValue("Failed to initialize classifier: " + e.getMessage());
                _isInitialized.postValue(false);
            }
        });
    }

    public void initializeImageProcessor(ImageView overlayView) {
        if (emotionClassifier != null && overlayView != null) {
            imageProcessor = new ImageProcessor(
                    executorService,
                    emotionClassifier,
                    _emotionResults,
                    _processingError,
                    overlayView,
                    benchmark
            );
            Log.d(TAG, "ImageProcessor initialized with overlay view");
        } else {
            Log.e(TAG, "Cannot initialize ImageProcessor: emotionClassifier or overlayView is null");
        }
    }

    public void startDetection() {
        if (imageProcessor != null) {
            imageProcessor.setIsDetecting(true);
            _emotionResults.postValue(new ArrayList<>());
            Log.d(TAG, "Detection started");
        } else {
            Log.e(TAG, "Cannot start detection: ImageProcessor is null");
        }
    }

    public void stopDetection() {
        if (imageProcessor != null) {
            imageProcessor.setIsDetecting(false);
            _emotionResults.postValue(new ArrayList<>());
            Log.d(TAG, "Detection stopped");
        }
    }

    public Task<Void> processImageWithFaceDetection(final ImageProxy imageProxy) {
        if (Boolean.FALSE.equals(_isInitialized.getValue())) {
            Log.e(TAG, "Cannot process image: System not yet initialized");
            return Tasks.forException(new IllegalStateException("System not initialized"));
        }

        if (imageProcessor != null) {
            return Tasks.call(() -> {
                imageProcessor.processImageWithFaceDetection(imageProxy);
                return null;
            });
        } else {
            Log.e(TAG, "Cannot process image: ImageProcessor is null");
            _processingError.postValue("System not properly initialized");
            return Tasks.forException(new IllegalStateException("ImageProcessor is null"));
        }
    }

    public void logPerformanceMetrics() {
        if (imageProcessor != null) {
            imageProcessor.logPerformanceMetrics();
        }
    }

    public void resetBenchmark() {
        if (imageProcessor != null) {
            imageProcessor.resetBenchmark();
        }
    }

    // Tambahkan method untuk mengecek status ImageProcessor
    public boolean isProcessorReady() {
        return imageProcessor != null;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cleanup();
        executorService.shutdown();
        Log.d(TAG, "ViewModel cleared");
    }

    public void cleanup() {
        stopDetection();
        if (imageProcessor != null) {
            imageProcessor.cleanup();
            imageProcessor = null;
        }
        if (emotionClassifier != null) {
            emotionClassifier = null;
        }
    }
}