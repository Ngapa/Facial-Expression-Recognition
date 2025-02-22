package com.gtek.fren.ui.helper;

import android.os.Debug;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EmotionBenchmark {
    private static final String TAG = "EnhancedEmotionBenchmark";

    // Performance metrics
    private long totalProcessingTime = 0;
    private long totalMemoryUsed = 0;
    private int frameCount = 0;
    private long detectionStartTime = 0;
    private final List<Long> processingTimes = new ArrayList<>();
    private final List<Long> memoryUsages = new ArrayList<>();

    // Accuracy metrics
    private int correctPredictions = 0;
    private int totalPredictions = 0;
    private final Map<String, Integer> confusionMatrix = new HashMap<>();

    // Resource usage metrics
    private long peakMemoryUsage = 0;
    private float cpuUsage = 0;
    private Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();

    public void startEvaluation() {
        detectionStartTime = System.nanoTime();
        recordMemoryUsage();

        // Reset values untuk pengukuran baru
        totalProcessingTime = 0;
        frameCount = 0;
        processingTimes.clear();
        memoryUsages.clear();
    }

    public void endEvaluation() {
        long duration = System.nanoTime() - detectionStartTime;
        processingTimes.add(duration);
        totalProcessingTime += duration;
        frameCount++;

        // Update memory usage
        recordMemoryUsage();

        // Update CPU usage dengan nilai aktual
        try {
            long cpuTimeEnd = Debug.threadCpuTimeNanos();
            cpuUsage = (float) cpuTimeEnd / (System.nanoTime() - detectionStartTime);
        } catch (Exception e) {
            Log.e(TAG, "Error measuring CPU usage: " + e.getMessage());
        }
    }

    private void recordMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        memoryUsages.add(usedMemory);

        // Update peak memory jika diperlukan
        peakMemoryUsage = Math.max(peakMemoryUsage, usedMemory);

        // Update total memory
        totalMemoryUsed = usedMemory;
    }

    private void updateCpuUsage() {
        try {
            // Simple CPU usage estimation based on process time
            long cpuTime = Debug.threadCpuTimeNanos();
            cpuUsage = (float) cpuTime / totalProcessingTime;
        } catch (Exception e) {
            Log.e(TAG, "Error measuring CPU usage: " + e.getMessage());
        }
    }

    public void logDetailedMetrics() {
        if (frameCount == 0) return;

        // Calculate performance metrics
        double avgProcessingTime = calculateAverage(processingTimes) / 1_000_000.0; // Convert to ms
        double stdDevProcessingTime = calculateStdDev(processingTimes) / 1_000_000.0;
        double avgMemoryUsage = calculateAverage(memoryUsages) / (1024.0 * 1024.0); // Convert to MB
        double accuracy = (correctPredictions * 100.0) / totalPredictions;

        // Log comprehensive metrics
        Log.i(TAG, String.format(Locale.US,
                "Performance Evaluation Report:\n\n" +
                        "1. Speed Metrics:\n" +
                        "   - Average Processing Time: %.2f ms\n" +
                        "   - Standard Deviation: %.2f ms\n" +
                        "   - Frames Processed: %d\n" +
                        "   - CPU Usage: %.2f%%\n\n" +
                        "2. Memory Metrics:\n" +
                        "   - Average Memory Usage: %.2f MB\n" +
                        "   - Peak Memory Usage: %.2f MB\n\n" +
                        "3. Accuracy Metrics:\n" +
                        "   - Overall Accuracy: %.2f%%\n" +
                        "   - Total Predictions: %d\n" +
                        "   - Correct Predictions: %d\n",
                avgProcessingTime,
                stdDevProcessingTime,
                frameCount,
                cpuUsage * 100,
                avgMemoryUsage,
                peakMemoryUsage / (1024.0 * 1024.0),
                accuracy,
                totalPredictions,
                correctPredictions
        ));

        logConfusionMatrix();
    }

    public BenchmarkMetrics getDetailedMetrics() {
        if (frameCount == 0) return new BenchmarkMetrics();

        double avgProcessingTime = calculateAverage(processingTimes) / 1_000_000.0; // Convert to ms
        double stdDevProcessingTime = calculateStdDev(processingTimes) / 1_000_000.0;
        double avgMemoryUsage = calculateAverage(memoryUsages) / (1024.0 * 1024.0); // Convert to MB
        double accuracy = totalPredictions > 0 ? (correctPredictions * 100.0) / totalPredictions : 0;

        return new BenchmarkMetrics(
                avgProcessingTime,
                stdDevProcessingTime,
                frameCount,
                cpuUsage * 100,
                avgMemoryUsage,
                peakMemoryUsage / (1024.0 * 1024.0),
                accuracy,
                totalPredictions,
                correctPredictions
        );
    }

    // Create a data class to hold metrics
    public static class BenchmarkMetrics {
        public final double avgProcessingTime;
        public final double stdDevProcessingTime;
        public final int framesProcessed;
        public final double cpuUsage;
        public final double avgMemoryUsage;
        public final double peakMemoryUsage;
        public final double accuracy;
        public final int totalPredictions;
        public final int correctPredictions;

        public BenchmarkMetrics() {
            this(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public BenchmarkMetrics(
                double avgProcessingTime,
                double stdDevProcessingTime,
                int framesProcessed,
                double cpuUsage,
                double avgMemoryUsage,
                double peakMemoryUsage,
                double accuracy,
                int totalPredictions,
                int correctPredictions) {
            this.avgProcessingTime = avgProcessingTime;
            this.stdDevProcessingTime = stdDevProcessingTime;
            this.framesProcessed = framesProcessed;
            this.cpuUsage = cpuUsage;
            this.avgMemoryUsage = avgMemoryUsage;
            this.peakMemoryUsage = peakMemoryUsage;
            this.accuracy = accuracy;
            this.totalPredictions = totalPredictions;
            this.correctPredictions = correctPredictions;
        }
    }

    private void logConfusionMatrix() {
        StringBuilder matrix = new StringBuilder("Confusion Matrix:\n");
        for (Map.Entry<String, Integer> entry : confusionMatrix.entrySet()) {
            matrix.append(String.format(Locale.US,
                    "   %s: %d occurrences\n",
                    entry.getKey(),
                    entry.getValue()
            ));
        }
        Log.i(TAG, matrix.toString());
    }

    private double calculateAverage(List<Long> values) {
        return values.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
    }

    private double calculateStdDev(List<Long> values) {
        double mean = calculateAverage(values);
        double sumSquaredDiff = values.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .sum();
        return Math.sqrt(sumSquaredDiff / values.size());
    }

    public void reset() {
        totalProcessingTime = 0;
        totalMemoryUsed = 0;
        frameCount = 0;
        detectionStartTime = 0;
        processingTimes.clear();
        memoryUsages.clear();
        correctPredictions = 0;
        totalPredictions = 0;
        confusionMatrix.clear();
        peakMemoryUsage = 0;
        cpuUsage = 0;
    }
}