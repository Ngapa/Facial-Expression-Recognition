// HomeFragment.kt
package com.gtek.fren.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.gtek.fren.databinding.FragmentHomeBinding
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.opencv.android.OpenCVLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors



class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var isDetecting = false
    private var cameraFacing = CameraSelector.DEFAULT_FRONT_CAMERA
    private val TAG = "HomeFragment"
    private val homeViewModel: HomeViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
    }

    private val emotionAdapter = EmotionAdapter()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize OpenCV
        try {
            val isOpenCVInitialized = OpenCVLoader.initLocal()
            if (!isOpenCVInitialized) {
                Log.e(TAG, "OpenCV initialization failed")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OpenCV: ${e.message}")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")
        setupUI()
        setupObservers()
        checkCameraPermission()
    }

    private fun setupObservers() {
        homeViewModel.emotionResults.observe(viewLifecycleOwner) { emotions ->
            emotions?.let {
                emotionAdapter.submitList(emotions as MutableList<EmotionResult>?)
            }
        }
    }

    private fun checkCameraPermission() {
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Camera permission granted: $hasPermission")

        if (hasPermission) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupUI() {
        Log.d(TAG, "Setting up UI components")
        binding.rvEmotions.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = emotionAdapter
        }

        binding.btnStart.setOnClickListener {
            isDetecting = true
            Log.d(TAG, "Detection started")
            homeViewModel.startDetection()
        }

        binding.btnStop.setOnClickListener {
            isDetecting = false
            Log.d(TAG, "Detection stopped")
            homeViewModel.stopDetection()
        }

        binding.btnSwitchCamera.setOnClickListener {
            Log.d(TAG, "Switching camera")
            switchCamera()
        }
    }

    private fun switchCamera() {
        Log.d(TAG, "Current camera: ${if (cameraFacing == CameraSelector.DEFAULT_FRONT_CAMERA) "Front" else "Back"}")
        cameraFacing = if (cameraFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
            if (hasBackCamera()) {
                Log.d(TAG, "Switching to Back Camera")
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                Toast.makeText(requireContext(), "Back camera not available", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Back camera not available")
                return
            }
        } else {
            if (hasFrontCamera()) {
                Log.d(TAG, "Switching to Front Camera")
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                Toast.makeText(requireContext(), "Front camera not available", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Front camera not available")
                return
            }
        }
        startCamera()
    }

    private fun startCamera() {
        Log.d(TAG, "Starting camera")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            Log.d(TAG, "Camera provider initialized")

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.viewFinder.surfaceProvider
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (isDetecting) {
                            Log.d(TAG, "Processing image for detection")
                            processImage(imageProxy)
                        } else {
                            imageProxy.close()
                            Log.d(TAG, "Image processing skipped")
                        }
                    }
                }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraFacing,
                    preview,
                    imageAnalyzer
                )
                Log.d(TAG, "Camera bound to lifecycle")
            } catch (e: Exception) {
                Log.e(TAG, "Error binding camera", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // Tambahkan fungsi untuk mengecek ketersediaan kamera
    private fun hasBackCamera(): Boolean {
        return requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    private fun hasFrontCamera(): Boolean {
        return requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }

    private fun processImage(imageProxy: ImageProxy) {
        // Setup MLKit face detector
        Log.d(TAG, "Processing image with ML Kit Face Detector")
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()

        val detector = FaceDetection.getClient(options)

        // Process image and detect faces
        homeViewModel.processImageWithFaceDetection(
            imageProxy,
            detector
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}