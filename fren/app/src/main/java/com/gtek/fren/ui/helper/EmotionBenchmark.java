package com.gtek.fren.ui.helper;

import android.util.Log;

import java.util.Locale;

public class EmotionBenchmark {
    private static final String TAG = "EmotionBenchmark";

    private long totalProcessingTime = 0;
    private int frameCount = 0;
    private long detectionTime = 0;
    private long classificationTime = 0;
    private int successfulDetections = 0;
    private int failedDetections = 0;

    public void startDetection() {
        detectionTime = System.nanoTime();
    }

    public void endDetection() {
        long duration = System.nanoTime() - detectionTime;
        totalProcessingTime += duration;
        frameCount++;
    }

    public void recordClassificationTime(long start, long end) {
        classificationTime += (end - start);
    }

    public void recordSuccess() {
        successfulDetections++;
    }

    public void recordFailure() {
        failedDetections++;
    }

    public void logMetrics() {
        if (frameCount > 0) {
            double avgProcessingTime = totalProcessingTime / (double) frameCount / 1_000_000.0; // Convert to ms
            double avgClassificationTime = classificationTime / (double) frameCount / 1_000_000.0;
            double successRate = (successfulDetections * 100.0) / frameCount;

            Log.i(TAG, String.format(Locale.US,
                    "Performance Metrics:\n" +
                            "Average Processing Time: %.2f ms\n" +
                            "Average Classification Time: %.2f ms\n" +
                            "Frames Processed: %d\n" +
                            "Success Rate: %.2f%%\n" +
                            "Failed Detections: %d",
                    avgProcessingTime,
                    avgClassificationTime,
                    frameCount,
                    successRate,
                    failedDetections));
        }
    }

    public void reset() {
        totalProcessingTime = 0;
        frameCount = 0;
        detectionTime = 0;
        classificationTime = 0;
        successfulDetections = 0;
        failedDetections = 0;
    }
}
