package com.gtek.fren.ui.emotionanalysis;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.gtek.fren.databinding.FragmentEmotionAnalysisBinding;
import com.gtek.fren.ui.helper.EmotionAdapter;
import com.gtek.fren.ui.helper.EmotionBenchmark;
import com.gtek.fren.ui.helper.EmotionClassifier;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;


public class EmotionAnalysisFragment extends Fragment {

    private FragmentEmotionAnalysisBinding binding;
    private EmotionAnalysisViewModel viewModel;
    private Uri photoUri;
    private static final String TAG = "EmotionAnalysis";
    private Handler benchmarkHandler;
    EmotionBenchmark emotionBenchmark;


    private EmotionAdapter emotionAdapter;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {
                        launchCamera();
                    }
                } else {
                    Toast.makeText(requireContext(), "Permission required for camera", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == -1 && result.getData() != null) { // RESULT_OK = -1
                    Uri selectedImage = result.getData().getData();
                    try {
                        displaySelectedImage(selectedImage);
                        binding.analyzeButton.setEnabled(true);
                    } catch (IOException e) {
                        Toast.makeText(requireContext(), "Error loading image", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == -1) { // RESULT_OK = -1
                    try {
                        displaySelectedImage(photoUri);
                        binding.analyzeButton.setEnabled(true);
                    } catch (IOException e) {
                        Toast.makeText(requireContext(), "Error capturing image", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        emotionAdapter = new EmotionAdapter();
        emotionBenchmark = new EmotionBenchmark();
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        EmotionAnalysisViewModel.EmotionAnalysisViewModelFactory factory =
                new EmotionAnalysisViewModel.EmotionAnalysisViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(this, factory).get(EmotionAnalysisViewModel.class);
        binding = FragmentEmotionAnalysisBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupRecyclerView();
        benchmarkHandler = new Handler(Looper.getMainLooper());
        setupViews();
        observeViewModel();
    }

    private void setupViews() {
        binding.analyzeButton.setEnabled(false);

        binding.uploadButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);

            if (emotionBenchmark != null) {
                emotionBenchmark.reset();
            }

            //TODO
            //Clear resultList when uploading a new image
        });

        binding.cameraButton.setOnClickListener(v -> checkCameraPermission());

        binding.analyzeButton.setOnClickListener(v -> analyzeImage());
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        binding.resultList.setLayoutManager(layoutManager);
        binding.resultList.setAdapter(emotionAdapter);

        binding.resultList.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                      oldLeft, oldTop, oldRight, oldBottom) -> {
            Log.d(TAG, "RecyclerView dimensions: " + (right-left) + "x" + (bottom-top));
        });
    }

    private void observeViewModel() {
        viewModel.emotionResults.observe(getViewLifecycleOwner(), emotionResults -> {
            Log.d(TAG, "Received emotion results: " + (emotionResults != null ? emotionResults.size() : "null"));
            if (emotionResults != null && !emotionResults.isEmpty()) {
                binding.resultList.setVisibility(View.VISIBLE);
                emotionAdapter.submitList(new ArrayList<>(emotionResults));
                Log.d(TAG, "Submitted to adapter: " + emotionResults.size() + " items");
            } else {
                binding.resultList.setVisibility(View.GONE);
                Log.d(TAG, "No results to display");
            }
        });

        viewModel.benchmarkMetrics.observe(getViewLifecycleOwner(), metrics -> {
            if (metrics != null) {
                updateBenchmarkDisplay(metrics);
            }
        });

        // Observe errors
        viewModel.error.observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                showError(error);
            }
        });
    }

    private void updateBenchmarkDisplay(EmotionBenchmark.BenchmarkMetrics metrics) {
        // Processing Time Display
        binding.processingTimeText.setText(String.format(Locale.US,
                "Average: %.2f ms\n" +
                        "Standard Deviation: ±%.2f ms\n" +
                        "Total Frames: %d",
                metrics.avgProcessingTime,
                metrics.stdDevProcessingTime,
                metrics.framesProcessed));

        // Memory Usage Display
        binding.memoryUsageText.setText(String.format(Locale.US,
                "Current: %.2f MB\n" +
                        "Peak: %.2f MB\n" +
                        "Average: %.2f MB",
                metrics.avgMemoryUsage,
                metrics.peakMemoryUsage,
                metrics.avgMemoryUsage));

        // CPU Usage Display
        binding.cpuUsageText.setText(String.format(Locale.US,
                "Current Usage: %.2f%%\n" +
                        "Threads: %d",
                metrics.cpuUsage,
                Thread.activeCount()));

        // Accuracy Display
        binding.accuracyText.setText(String.format(Locale.US,
                "Overall: %.2f%%\n" +
                        "Correct/Total: %d/%d\n" +
                        "Error Rate: %.2f%%",
                metrics.accuracy,
                metrics.correctPredictions,
                metrics.totalPredictions,
                100 - metrics.accuracy));
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            launchCamera();
        }
    }

    private void launchCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            File photoFile = createImageFile();
            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(requireContext(),
                        "com.gtek.fren.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                cameraLauncher.launch(takePictureIntent);
            }
        }
    }

    private File createImageFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireActivity().getExternalFilesDir(null);
        try {
            return File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException e) {
            return null;
        }
    }

    private void displaySelectedImage(Uri imageUri) throws IOException {
        InputStream inputStream = requireActivity().getContentResolver().openInputStream(imageUri);
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

        // Get image orientation
        int rotation = getImageRotation(imageUri);
        if (rotation != 0) {
            bitmap = rotateBitmap(bitmap, rotation);
        }

        binding.imagePreview.setImageBitmap(bitmap);
        photoUri = imageUri;
    }

    private void analyzeImage() {
        try {

            Log.d(TAG, "Starting image analysis");
            InputStream inputStream = requireActivity().getContentResolver().openInputStream(photoUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                showError("Failed to load image");
                return;
            }
            viewModel.getBenchmark().startEvaluation();
            // Handle rotation
            int rotation = getImageRotation(photoUri);
            if (rotation != 0) {
                bitmap = rotateBitmap(bitmap, rotation);
                Log.d(TAG, "Image rotated by " + rotation + " degrees");
            }

            // Convert bitmap to Mat
            Mat imageMat = new Mat();
            Utils.bitmapToMat(bitmap, imageMat);

            if (imageMat.empty()) {
                showError("Failed to convert image");
                return;
            }
            Log.d(TAG, "imageMat size: " + imageMat.width() + "x" + imageMat.height() + ", channels: " + imageMat.channels());

            // Convert to grayscale
            Mat grayMat = new Mat();
            if (imageMat.channels() == 4) {
                Imgproc.cvtColor(imageMat, grayMat, Imgproc.COLOR_BGRA2GRAY);
            } else if (imageMat.channels() == 3) {
                Imgproc.cvtColor(imageMat, grayMat, Imgproc.COLOR_BGR2GRAY);
            } else if (imageMat.channels() == 1) {
                imageMat.copyTo(grayMat);
            } else {
                showError("Unsupported image format");
                imageMat.release();
                return;
            }

            if (grayMat.empty()) {
                imageMat.release();
                showError("Failed to convert to grayscale");
                return;
            }
            Log.d(TAG, "grayMat size: " + grayMat.width() + "x" + grayMat.height() + ", channels: " + grayMat.channels());

            // Detect faces
            InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
            FaceDetector detector = FaceDetection.getClient(
                    new FaceDetectorOptions.Builder()
                            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                            .build()
            );

            Bitmap finalBitmap = bitmap;
            detector.process(inputImage)
                    .addOnSuccessListener(faces -> {
                        if (faces.isEmpty()) {
                            showNoFacesDetected();
                            imageMat.release();
                            grayMat.release();
                            viewModel.getBenchmark().endEvaluation();
                            return;
                        }

                        List<EmotionClassifier.EmotionResult> allResults = new ArrayList<>();

                        // Create mutable bitmap for drawing
                        Bitmap mutableBitmap = finalBitmap.copy(Bitmap.Config.ARGB_8888, true);
                        Canvas canvas = new Canvas(mutableBitmap);
                        Paint paint = new Paint();
                        paint.setColor(Color.GREEN);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(5);

                        Paint textPaint = new Paint();
                        textPaint.setColor(Color.WHITE);
                        textPaint.setTextSize(100); // Perbesar ukuran font (dari 50 menjadi 80)
                        textPaint.setStyle(Paint.Style.FILL);
                        textPaint.setShadowLayer(5.0f, 0f, 0f, Color.BLACK);

                        for (Face face : faces) {
                            android.graphics.Rect bounds = face.getBoundingBox();
                            viewModel.logPerformanceMetrics();

                            // Ensure bounds are within image dimensions
                            bounds.left = Math.max(0, bounds.left);
                            bounds.top = Math.max(0, bounds.top);
                            bounds.right = Math.min(bounds.right, grayMat.width());
                            bounds.bottom = Math.min(bounds.bottom, grayMat.height());

                            if (bounds.width() <= 0 || bounds.height() <= 0) {
                                continue;
                            }

                            try {
                                org.opencv.core.Rect faceRect = new org.opencv.core.Rect(
                                        bounds.left, bounds.top, bounds.width(), bounds.height());

                                // Check if the rect is within the image bounds
                                if (faceRect.x < 0 || faceRect.y < 0 ||
                                        faceRect.x + faceRect.width > grayMat.width() ||
                                        faceRect.y + faceRect.height > grayMat.height()) {
                                    continue;
                                }

                                Mat faceROI = grayMat.submat(faceRect);
                                Mat faceMat = new Mat();
                                Imgproc.resize(faceROI, faceMat, new Size(48, 48));

                                List<EmotionClassifier.EmotionResult> emotions = viewModel.analyzeImage(faceMat);

                                // Draw rectangle and emotion
                                canvas.drawRect(bounds, paint);
                                if (emotions != null && !emotions.isEmpty()) {
                                    // Urutkan emotions berdasarkan confidence
                                    Collections.sort(emotions, (e1, e2) ->
                                            Float.compare(e2.getConfidence(), e1.getConfidence()));

                                    EmotionClassifier.EmotionResult topEmotion = emotions.get(0);
                                    String emotionText = String.format(Locale.getDefault(),
                                            "%s: %.1f%%",
                                            topEmotion.getEmotion().toUpperCase(), // Tambahkan toUpperCase()
                                            topEmotion.getConfidence());

                                    // Posisikan teks lebih jauh dari bounding box
                                    canvas.drawText(emotionText,
                                            bounds.left,
                                            bounds.top - 30, // Ubah dari -10 ke -30 untuk jarak lebih jauh
                                            textPaint);

                                    allResults = new ArrayList<>(emotions);
                                    viewModel.setEmotionResults(allResults);


                                    // Debug log
                                    Log.d(TAG, "Added emotion to results: " + topEmotion.getEmotion() +
                                            " with confidence: " + topEmotion.getConfidence());
                                }
                                faceROI.release();
                                faceMat.release();
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing face: " + e.getMessage());
                            }
                        }

                        binding.imagePreview.setImageBitmap(mutableBitmap);
                        imageMat.release();
                        grayMat.release();
                        viewModel.getBenchmark().endEvaluation();
                        viewModel.logPerformanceMetrics();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Face detection failed: " + e.getMessage());
                        showError("Face detection failed: " + e.getMessage());
                        imageMat.release();
                        grayMat.release();
                        viewModel.getBenchmark().endEvaluation();
                    });

        } catch (IOException e) {
            Log.e(TAG, "IO Error: " + e.getMessage());
            showError("IO Error: " + e.getMessage());
            viewModel.getBenchmark().endEvaluation();
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
            showError("Error: " + e.getMessage());
            viewModel.getBenchmark().endEvaluation();
        }
    }

    private void showNoFacesDetected() {
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(),
                    "No faces detected in the image",
                    Toast.LENGTH_SHORT).show();

        });
    }

    // Helper method to show error messages
    private void showError(String message) {
        if (isAdded()) {
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            );
        }
    }

    private int getImageRotation(Uri imageUri) {
        try {
            ExifInterface exif = new ExifInterface(Objects.requireNonNull(requireActivity().getContentResolver().openInputStream(imageUri)));
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting image rotation: " + e.getMessage());
            return 0;
        }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        if (degrees == 0) return bitmap;

        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up RecyclerView
        if (binding != null) {
            binding.resultList.setAdapter(null);
        }
        binding = null;
    }
}