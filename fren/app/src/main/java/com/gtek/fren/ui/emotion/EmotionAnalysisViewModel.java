package com.gtek.fren.ui.emotion;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.gtek.fren.ui.helper.EmotionBenchmark;
import com.gtek.fren.ui.helper.EmotionClassifier;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmotionAnalysisViewModel extends AndroidViewModel {
    private static final String TAG = "EmotionAnalysisViewModel";

    private final MutableLiveData<List<EmotionClassifier.EmotionResult>> _emotionResults = new MutableLiveData<>();
    public LiveData<List<EmotionClassifier.EmotionResult>> emotionResults = _emotionResults;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;

    private final MutableLiveData<Boolean> _isInitialized = new MutableLiveData<>(false);
    public LiveData<Boolean> isInitialized = _isInitialized;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private EmotionClassifier emotionClassifier;
    private final EmotionBenchmark benchmark;

    public void setEmotionResults(List<EmotionClassifier.EmotionResult> results) {
        _emotionResults.postValue(results);
    }

    public static class EmotionAnalysisViewModelFactory implements ViewModelProvider.Factory {
        private final Application application;
        private final EmotionBenchmark benchmark;

        public EmotionAnalysisViewModelFactory(Application application) {
            this.application = application;
            this.benchmark = new EmotionBenchmark();
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(EmotionAnalysisViewModel.class)) {
                return (T) new EmotionAnalysisViewModel(application, benchmark);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }

    public EmotionAnalysisViewModel(@NonNull Application application, EmotionBenchmark benchmark) {
        super(application);
        this.benchmark = benchmark;
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
                _error.postValue("Failed to initialize classifier: " + e.getMessage());
                _isInitialized.postValue(false);
            }
        });
    }

    public List<EmotionClassifier.EmotionResult> analyzeImage(Mat imageMat) {
        if (Boolean.FALSE.equals(_isInitialized.getValue())) {
            _error.postValue("System not initialized");
            return new ArrayList<>();
        }

        try {
            if (emotionClassifier != null) {
                benchmark.startDetection();
                List<EmotionClassifier.EmotionResult> results = emotionClassifier.classify(imageMat);
                benchmark.endDetection();

                if (results != null && !results.isEmpty()) {
                    _emotionResults.postValue(results);
                    return results;
                }
            }
        } catch (Exception e) {
            String errorMessage = "Error analyzing image: " + e.getMessage();
            Log.e(TAG, errorMessage);
            _error.postValue(errorMessage);
        }
        return new ArrayList<>();
    }

    public void logPerformanceMetrics() {
        benchmark.logMetrics();
    }

    public void resetBenchmark() {
        benchmark.reset();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cleanup();
    }

    public void cleanup() {
        executorService.shutdown();
        if (emotionClassifier != null) {
            emotionClassifier = null;
        }
        _emotionResults.postValue(new ArrayList<>());
        Log.d(TAG, "ViewModel cleared and resources released");
    }
}