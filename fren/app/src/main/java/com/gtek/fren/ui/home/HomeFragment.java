package com.gtek.fren.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.gtek.fren.databinding.FragmentHomeBinding;

import org.opencv.android.OpenCVLoader;

import java.util.Arrays;
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
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        // Sembunyikan elemen kamera secara default
        binding.viewFinder.setVisibility(View.GONE);
        binding.errorView.setVisibility(View.GONE);
        binding.rvEmotions.setVisibility(View.GONE);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        cameraExecutor = Executors.newSingleThreadExecutor();

        homeViewModel.emotionResults.observe(getViewLifecycleOwner(), emotionResults -> {
            setupUI();
            Log.d(TAG, "Emotion results: " + emotionResults);
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


//    private void setupObservers() {
//        // Observe errors
//        homeViewModel.processingError.observe(getViewLifecycleOwner(), error -> {
//            if (error != null && !error.isEmpty()) {
//                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
//            }
//        });
//
//        // Observe emotion results
//        homeViewModel.emotionResults.observe(getViewLifecycleOwner(), emotions -> {
//            if (emotions != null) {
//                emotionAdapter.submitList(emotions);
//            }
//        });
//    }

//    private void initializeViewModel() {
//        try {
//            homeViewModel = new ViewModelProvider(this, new ViewModelProvider.Factory() {
//                @NonNull
//                @Override
//                public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
//                    try {
//                        return (T) new HomeViewModel(requireActivity().getApplication());
//                    } catch (Exception e) {
//                        throw new RuntimeException("Failed to create ViewModel", e);
//                    }
//                }
//            }).get(HomeViewModel.class);
//            setupObservers();
//        } catch (Exception e) {
//            Log.e(TAG, "Failed to initialize ViewModel: " + e.getMessage());
//            showError("Failed to initialize. Please restart the app");
//        }
//    }

    private void setupUI() {
        try {
            Log.d(TAG, "Setting up UI components");
            // Hide camera preview and error view initially
            binding.viewFinder.setVisibility(View.GONE);
            binding.errorView.setVisibility(View.GONE);

            // Setup RecyclerView
            binding.rvEmotions.setLayoutManager(
                    new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            );
            if (emotionAdapter == null) {
                emotionAdapter = new EmotionAdapter();
            }
            binding.rvEmotions.setAdapter(emotionAdapter);

            setupButtons();
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
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalyzer.setAnalyzer(cameraExecutor, this::processImage);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera: " + e.getMessage(), e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
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
                showError("Please wait for current processing to complete");
            }
        });

        // Set initial button states
        binding.btnStart.setEnabled(true);
        binding.btnStop.setEnabled(false);
        binding.btnSwitchCamera.setEnabled(false);
    }

    private void startDetection() {
        isDetecting = true;
        updateButtonStates();

        // Tampilkan elemen kamera dan daftar emosi saat tombol Start ditekan
        binding.viewFinder.setVisibility(View.VISIBLE);
        binding.rvEmotions.setVisibility(View.VISIBLE);
        binding.errorView.setVisibility(View.GONE);

        startCamera();
        homeViewModel.startDetection();
        Log.d(TAG, "Detection started");
    }

    private void stopDetection() {
        isDetecting = false;
        updateButtonStates();

        // Sembunyikan elemen kamera dan daftar emosi saat tombol Stop ditekan
        binding.viewFinder.setVisibility(View.GONE);
        binding.rvEmotions.setVisibility(View.GONE);

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        homeViewModel.stopDetection();
        Log.d(TAG, "Detection stopped");
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
        String[] permissions = {
                Manifest.permission.CAMERA
        };

        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Arrays.toString(permissions));
        }
    }


    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImage(ImageProxy imageProxy) {
        if (!isDetecting || isProcessingImage || imageProxy == null || imageProxy.getImage() == null) {
            if (imageProxy != null) {
                imageProxy.close();
            }
            return;
        }

        isProcessingImage = true;

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);
        try {
            homeViewModel.processImageWithFaceDetection(imageProxy, detector);
        } catch (Exception e) {
            Log.e(TAG, "Error processing image: " + e.getMessage(), e);
            imageProxy.close();
        } finally {
            isProcessingImage = false;
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


//    private void retryCamera() {
//        if (isAdded()) {
//            requireActivity().runOnUiThread(() -> {
//                new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                    if (isAdded() && !isDetecting) {
//                        startCamera();
//                    }
//                }, 1000);
//            });
//        }
//    }

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
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            cameraExecutor.shutdown();
            try {
                if (!cameraExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    cameraExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cameraExecutor.shutdownNow();
            }
        }
        binding = null;
    }

}
