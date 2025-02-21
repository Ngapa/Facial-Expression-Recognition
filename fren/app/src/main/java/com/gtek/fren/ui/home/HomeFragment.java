package com.gtek.fren.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.gtek.fren.databinding.FragmentHomeBinding;
import com.gtek.fren.ui.helper.EmotionAdapter;
import com.gtek.fren.ui.helper.ImageProcessor;

import org.opencv.android.OpenCVLoader;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private ExecutorService cameraExecutor;
    private boolean isDetecting = false;
    private CameraSelector cameraFacing = CameraSelector.DEFAULT_FRONT_CAMERA;
    private static final String TAG = "HomeFragment";
    private HomeViewModel homeViewModel;
    private EmotionAdapter emotionAdapter;
    private ProcessCameraProvider cameraProvider;
    private boolean isProcessingImage = false;
    private boolean isUiSetup = false;
    private ImageProcessor imageProcessor;
    private Handler benchmarkHandler;
    private final Runnable benchmarkRunnable = new Runnable() {
        @Override
        public void run() {
            if (homeViewModel != null && homeViewModel.isProcessorReady()) {
                homeViewModel.logPerformanceMetrics();
                homeViewModel.resetBenchmark();
            }
            // Schedule next run only if Fragment is still active
            if (isAdded() && !isDetached()) {
                benchmarkHandler.postDelayed(this, 500);
            }
        }
    };


    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(getContext(), "Camera permission is required", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraExecutor = Executors.newSingleThreadExecutor();
        emotionAdapter = new EmotionAdapter(); // Initialize adapter early
        initializeOpenCV();
    }

    private void initializeOpenCV() {
        try {
            if (!OpenCVLoader.initLocal()) {
                Log.e(TAG, "OpenCV initialization failed");
                Toast.makeText(requireContext(),
                        "Failed to initialize OpenCV",
                        Toast.LENGTH_LONG).show();
            } else {
                Log.d(TAG, "OpenCV initialization successful");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing OpenCV: " + e.getMessage());
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        HomeViewModel.HomeViewModelFactory factory = new HomeViewModel.HomeViewModelFactory(requireActivity().getApplication());
        homeViewModel = new ViewModelProvider(this, factory).get(HomeViewModel.class);
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        // Make sure overlay is above PreviewView but initially invisible
        binding.faceOverlay.setVisibility(View.GONE);
        binding.viewFinder.setVisibility(View.GONE);
        binding.errorView.setVisibility(View.GONE);
        binding.rvEmotions.setVisibility(View.GONE);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        benchmarkHandler = new Handler(Looper.getMainLooper());
        cameraExecutor = Executors.newSingleThreadExecutor();

        binding.faceOverlay.post(() -> {
            ViewGroup.LayoutParams params = binding.faceOverlay.getLayoutParams();
            if (params.width <= 0) {
                params.width = binding.viewFinder.getWidth();
            }
            if (params.height <= 0) {
                params.height = binding.viewFinder.getHeight();
            }
            binding.faceOverlay.setLayoutParams(params);
        });

        binding.viewFinder.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            ViewGroup.LayoutParams params = binding.faceOverlay.getLayoutParams();
            params.width = right - left;
            params.height = bottom - top;
            binding.faceOverlay.setLayoutParams(params);
        });
        homeViewModel.isInitialized.observe(getViewLifecycleOwner(), isInitialized -> {
            if (isInitialized) {
                homeViewModel.initializeImageProcessor(binding.faceOverlay);
                startBenchmarkLogging();
                Log.d(TAG, "ImageProcessor initialized");
            }
        });
        homeViewModel.emotionResults.observe(getViewLifecycleOwner(), emotionResults -> {
            if (!isUiSetup) {
                setupUI();
                isUiSetup = true;
            }
            Log.d(TAG, "Emotion results: " + emotionResults);
            if (emotionResults != null && !emotionResults.isEmpty()) {
                emotionAdapter.submitList(emotionResults);
                emotionAdapter.notifyDataSetChanged();
            }
        });

        homeViewModel.processingError.observe(getViewLifecycleOwner(), error -> {
            showError(error);
            showCameraError(error);
            Log.e(TAG, "Processing error: " + error);
            Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
        });

        checkAndRequestPermissions();
        setupButtons();
    }

    private void startBenchmarkLogging() {
        // Remove any existing callbacks
        benchmarkHandler.removeCallbacks(benchmarkRunnable);
        // Start new benchmark logging cycle
        benchmarkHandler.postDelayed(benchmarkRunnable, 30000);
    }


    private void setupUI() {
        try {
            Log.d(TAG, "Setting up UI components");

            // Setup RecyclerView
            binding.rvEmotions.setLayoutManager(
                    new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            );
            if (emotionAdapter == null) {
                emotionAdapter = new EmotionAdapter();
            }
            binding.rvEmotions.setAdapter(emotionAdapter);
        } catch (Exception e) {
            Log.e(TAG, "Error in setupUI: " + e.getMessage());
            showError("Error setting up UI");
        }
    }
    private void switchCamera() {
        Log.d(TAG, "Current camera: " + (cameraFacing == CameraSelector.DEFAULT_FRONT_CAMERA ? "Front" : "Back"));
        if (cameraFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
            if (hasBackCamera()) {
                cameraFacing = CameraSelector.DEFAULT_BACK_CAMERA;
                Log.d(TAG, "Switching to Back Camera");
            } else {
                Toast.makeText(requireContext(), "Back camera not available", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Back camera not available");
                return;
            }
        } else {
            if (hasFrontCamera()) {
                cameraFacing = CameraSelector.DEFAULT_FRONT_CAMERA;
                Log.d(TAG, "Switching to Front Camera");
            } else {
                Toast.makeText(requireContext(), "Front camera not available", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Front camera not available");
                return;
            }
        }
        startCamera();
    }

    private void startCamera() {
        if (!isAdded() || getContext() == null) {
            return;
        }

        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
            } catch (Exception e) {
                Log.e(TAG, "Error unbinding camera uses cases", e);
            }
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera: " + e.getMessage(), e);
                showError("Failed to start camera. Please try again.");
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            return;
        }

        Preview preview = new Preview.Builder()
                .setTargetRotation(binding.viewFinder.getDisplay().getRotation())
                .setTargetResolution(new Size(640, 480))
                .build();

        ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetRotation(binding.viewFinder.getDisplay().getRotation())
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build();

        preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());
        imageAnalyzer.setAnalyzer(cameraExecutor, this::processImage);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this,
                    cameraFacing,
                    preview,
                    imageAnalyzer
            );
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
            showError("Camera initialization failed. Please restart the app.");
            stopDetection();
        }
    }

    private void setupButtons() {
        binding.btnStart.setOnClickListener(v -> {
            if (!isDetecting) {
                if (ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startDetection();
                } else {
                    checkAndRequestPermissions();
                }
            }
        });

        binding.btnStop.setOnClickListener(v -> {
            if (isDetecting) {
                stopDetection();
            }
        });

        binding.btnSwitchCamera.setOnClickListener(v -> {
            if (!isProcessingImage && isDetecting) {
                switchCamera();
            } else if (!isDetecting) {
                showError("Please start camera first");
            } else {
                switchCamera();
            }
        });

        // Set initial button states
        binding.btnStart.setEnabled(true);
        binding.btnStop.setEnabled(false);
        binding.btnSwitchCamera.setEnabled(false);
    }

    private void startDetection() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            checkAndRequestPermissions();
            return;
        }

        if (Boolean.FALSE.equals(homeViewModel.isInitialized.getValue())) {
            showError("Please wait for initialization to complete");
            return;
        }

        if (cameraExecutor == null || cameraExecutor.isShutdown()) {
            cameraExecutor = Executors.newSingleThreadExecutor();
        }

        isDetecting = true;
        updateButtonStates();
        binding.viewFinder.setVisibility(View.VISIBLE);
        binding.faceOverlay.setVisibility(View.VISIBLE);
        binding.rvEmotions.setVisibility(View.VISIBLE);
        binding.errorView.setVisibility(View.GONE);
        startCamera();
        homeViewModel.startDetection();
    }
    private void stopDetection() {
        isDetecting = false;
        updateButtonStates();

        // Sembunyikan elemen kamera dan daftar emosi saat tombol Stop ditekan
        binding.viewFinder.setVisibility(View.GONE);
        binding.faceOverlay.setVisibility(View.GONE);
        binding.rvEmotions.setVisibility(View.GONE);

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        homeViewModel.stopDetection();
        Log.d(TAG, "Detection stopped");
        shutdownCameraExecutor();
    }

    private void setCameraButtonStates(boolean startEnabled, boolean stopEnabled, boolean switchEnabled) {
        if (isAdded() && getContext() != null) {
            requireActivity().runOnUiThread(() -> {
                binding.btnStart.setEnabled(startEnabled);
                binding.btnStop.setEnabled(stopEnabled);
                binding.btnSwitchCamera.setEnabled(switchEnabled);
            });
        }
    }


    private void updateButtonStates() {
        setCameraButtonStates(!isDetecting, isDetecting, isDetecting);
    }


    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }


    private final Object processingLock = new Object();
    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImage(ImageProxy imageProxy) {
        if (!isDetecting) {
            imageProxy.close();
            return;
        }

        synchronized (processingLock) {
            if (isProcessingImage) {
                imageProxy.close();
                return;
            }
            isProcessingImage = true;
        }

        try {
            if (homeViewModel != null) {
                // Pass the image to ViewModel and wait for processing to complete
                Task<Void> task = homeViewModel.processImageWithFaceDetection(imageProxy);
                task.addOnCompleteListener(completeTask -> {
                    synchronized (processingLock) {
                        isProcessingImage = false;
                    }
                    // No need to close imageProxy here, it's handled in ViewModel
                    if (!completeTask.isSuccessful()) {
                        Exception e = completeTask.getException();
                        Log.e(TAG, "Error processing image: " + e.getMessage(), e);
                        showError("Error processing image. Please restart detection.");
                        stopDetection();
                    }
                });
            } else {
                synchronized (processingLock) {
                    isProcessingImage = false;
                }
                imageProxy.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image: " + e.getMessage(), e);
            showError("Error processing image. Please restart detection.");
            synchronized (processingLock) {
                isProcessingImage = false;
            }
            imageProxy.close();
            stopDetection();
        }
    }

    private boolean hasBackCamera() {
        return requireContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    private boolean hasFrontCamera() {
        return requireContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }

    private void showError(String message) {
        if (isAdded()) {
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            );
        }
    }

    private void showCameraError(String message) {
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                binding.viewFinder.setVisibility(View.GONE);
                binding.errorView.setVisibility(View.VISIBLE);
                binding.errorView.setText(message);
            });
            Log.e(TAG, message);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Don't automatically start camera on resume
        if (isDetecting && binding != null) {
            startCamera();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isDetecting) {
            stopDetection();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (benchmarkHandler != null) {
            benchmarkHandler.removeCallbacks(benchmarkRunnable);
        }
        if (isDetecting) {
            stopDetection();
        }

        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
            } catch (Exception e) {
                Log.e(TAG, "Error unbinding camera", e);
            }
        }

        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            try {
                cameraExecutor.shutdown();
                if (!cameraExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                    cameraExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cameraExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        binding = null;
    }

    private void shutdownCameraExecutor() {
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        benchmarkHandler = null;
        if (homeViewModel != null) {
            homeViewModel.cleanup(); // Add cleanup method in ViewModel
        }
    }
}

