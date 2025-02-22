package com.gtek.fren.ui.helper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;
import androidx.lifecycle.MutableLiveData;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@OptIn(markerClass = ExperimentalGetImage.class)
public class ImageProcessor {

    private static final String TAG = "ImageProcessor";
    private static final float EMOTION_CONFIDENCE_THRESHOLD = 0.3f;
    private final ExecutorService executorService;
    private final EmotionClassifier emotionClassifier;
    private final MutableLiveData<List<EmotionClassifier.EmotionResult>> emotionResults;
    private final MutableLiveData<String> processingError;
    private boolean isDetecting = true;
    private final Object lock = new Object();
    private boolean isProcessing = false;
    private FaceDetector faceDetector;

    private Paint facePaint;
    private final ImageView overlayView;
    private static final long CLASSIFICATION_INTERVAL_MS = 500; // Jeda 500ms antar klasifikasi
    private long lastProcessingTime = 0;
    private final EmotionBenchmark benchmark;

    public ImageProcessor(ExecutorService executorService,
                          EmotionClassifier emotionClassifier,
                          MutableLiveData<List<EmotionClassifier.EmotionResult>> emotionResults,
                          MutableLiveData<String> processingError,
                          ImageView overlayView, EmotionBenchmark benchmark) {
        this.executorService = executorService;
        this.emotionClassifier = emotionClassifier;
        this.emotionResults = emotionResults;
        this.processingError = processingError;
        this.overlayView = overlayView;
        this.benchmark = new EmotionBenchmark();

        initializeFaceDetector();
        initializePaint();
    }

    private void initializePaint() {
        facePaint = new Paint();
        facePaint.setColor(Color.GREEN);
        facePaint.setStyle(Paint.Style.STROKE);
        facePaint.setStrokeWidth(3.0f);
    }

    private void initializeFaceDetector() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build();

        faceDetector = FaceDetection.getClient(options);
    }

    public void setIsDetecting(boolean isDetecting) {
        this.isDetecting = isDetecting;
    }


    @OptIn(markerClass = ExperimentalGetImage.class)
    public void processImageWithFaceDetection(final ImageProxy imageProxy) {
        if (!isDetecting) {
            imageProxy.close();
            return;
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessingTime < CLASSIFICATION_INTERVAL_MS) {
            imageProxy.close();
            return;
        }

        synchronized(lock) {
            if (isProcessing) {
                return;
            }
            isProcessing = true;
        }

        try {

            Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) {
                throw new IllegalArgumentException("Received null mediaImage");
            }

            InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());


            final int width = mediaImage.getWidth();
            final int height = mediaImage.getHeight();
            final Mat imageMat = imageToMat(mediaImage);

            if (imageMat == null) {
                throw new IllegalStateException("Failed to convert image to Mat");
            }


            faceDetector.process(inputImage)
                    .addOnSuccessListener(faces -> {
                        lastProcessingTime = System.currentTimeMillis();
                        if (!faces.isEmpty()) {

                            drawFacesOnOverlay(faces, width, height);
                            List<EmotionClassifier.EmotionResult> allEmotions = new ArrayList<>();

                            // Process each face and measure performance
                            for (Face face : faces) {
                                try {
                                    List<EmotionClassifier.EmotionResult> faceEmotions = processSingleFace(face, imageMat);
                                    if (faceEmotions != null && !faceEmotions.isEmpty()) {
                                        allEmotions.addAll(faceEmotions);
                                        // Record successful emotion detection
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error processing face", e);

                                }
                            }

                            emotionResults.postValue(allEmotions);

                        } else {
                            clearOverlay();
                            emotionResults.postValue(new ArrayList<>());
                        }

                    })
                    .addOnFailureListener(e -> {
                        String errorMessage = "Face detection failed: " + e.getMessage();
                        Log.e(TAG, errorMessage, e);
                        processingError.postValue(errorMessage);
                        clearOverlay();
                    })
                    .addOnCompleteListener(task -> {
                        if (!imageMat.empty()) {
                            imageMat.release();
                        }

                        Log.d(TAG, "Complete process image");
                        imageProxy.close();
                        synchronized(lock) {
                            isProcessing = false;
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            processingError.postValue("Error processing image: " + e.getMessage());
            imageProxy.close();
            synchronized(lock) {
                isProcessing = false;
            }
        }
    }


    private void drawFacesOnOverlay(List<Face> faces, int imageWidth, int imageHeight) {
        if (overlayView == null) return;

        // Pastikan overlayView memiliki dimensi valid
        if (overlayView.getWidth() <= 0 || overlayView.getHeight() <= 0) {
            Log.w(TAG, "OverlayView dimensions not ready yet");
            // Tunggu sampai view siap
            overlayView.post(() -> {
                if (overlayView.getWidth() > 0 && overlayView.getHeight() > 0) {
                    drawFacesOnOverlay(faces, imageWidth, imageHeight);
                }
            });
            return;
        }

        try {
            Bitmap overlay = Bitmap.createBitmap(
                    overlayView.getWidth(),
                    overlayView.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(overlay);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            float scaleX = (float) overlayView.getWidth() / imageWidth;
            float scaleY = (float) overlayView.getHeight() / imageHeight;

            for (Face face : faces) {
                Rect bounds = face.getBoundingBox();
                float left = bounds.left * scaleX;
                float top = bounds.top * scaleY;
                float right = bounds.right * scaleX;
                float bottom = bounds.bottom * scaleY;

                canvas.drawRect(left, top, right, bottom, facePaint);
            }

            overlayView.post(() -> overlayView.setImageBitmap(overlay));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error creating overlay bitmap: " + e.getMessage());
        }
    }

    private void clearOverlay() {
        if (overlayView == null) return;
        overlayView.post(() -> overlayView.setImageBitmap(null));
    }



    private Mat imageToMat(Image image) {
        try {
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            Mat yuv = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), CvType.CV_8UC1);
            yuv.put(0, 0, nv21);

            Mat rgb = new Mat();
            Imgproc.cvtColor(yuv, rgb, Imgproc.COLOR_YUV2RGB_NV21);
            yuv.release();

            return rgb;
        } catch (Exception e) {
            Log.e(TAG, "Error converting image to Mat", e);
            return null;
        }
    }

    private List<EmotionClassifier.EmotionResult> processSingleFace(Face face, Mat imageMat) {
        List<EmotionClassifier.EmotionResult> significantEmotions = new ArrayList<>();
        Mat faceRegion = null;
        Mat processedFace = null;

        try {
            Rect boundingBox = face.getBoundingBox();

            // Validasi dan perbaiki bounding box jika perlu
            int left = Math.max(0, boundingBox.left);
            int top = Math.max(0, boundingBox.top);
            int width = Math.min(boundingBox.width(), imageMat.width() - left);
            int height = Math.min(boundingBox.height(), imageMat.height() - top);

            // Buat Rect yang sudah divalidasi
            Rect validatedBox = new Rect(left, top, width, height);

            if (!isValidBoundingBox(validatedBox, imageMat)) {
                return significantEmotions;
            }

            org.opencv.core.Rect opencvRect = new org.opencv.core.Rect(
                    validatedBox.left,
                    validatedBox.top,
                    validatedBox.width(),
                    validatedBox.height()
            );

            faceRegion = new Mat(imageMat, opencvRect);
            processedFace = preprocessFace(faceRegion);

            List<EmotionClassifier.EmotionResult> emotions = emotionClassifier.classify(processedFace);
            for (EmotionClassifier.EmotionResult emotion : emotions) {
                if (emotion.getConfidence() > EMOTION_CONFIDENCE_THRESHOLD) {
                    significantEmotions.add(emotion);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing single face: " + e.getMessage(), e);
        } finally {
            if (faceRegion != null && !faceRegion.empty()) faceRegion.release();
            if (processedFace != null && !processedFace.empty()) processedFace.release();
        }

        return significantEmotions;
    }

    private boolean isValidBoundingBox(Rect boundingBox, Mat mat) {
        boolean isValid = boundingBox.left >= 0 &&
                boundingBox.top >= 0 &&
                boundingBox.right <= mat.width() &&
                boundingBox.bottom <= mat.height() &&
                boundingBox.width() > 0 &&
                boundingBox.height() > 0 &&
                boundingBox.width() <= mat.width() &&
                boundingBox.height() <= mat.height();

        if (!isValid) {
            Log.w(TAG, String.format("Invalid box dimensions - Image: %dx%d, Box: %s",
                    mat.width(), mat.height(), boundingBox.toString()));
        }

        return isValid;
    }

    private static final float[] EMOTION_NORM_MEAN = new float[] {0.485f}; // Sesuaikan dengan dataset emosi
    private static final float[] EMOTION_NORM_STD = new float[] {0.229f}; // Sesuaikan dengan dataset emosi

    private Mat preprocessFace(Mat faceRegion) {
        Mat grayFace = new Mat();
        Mat resizedFace = new Mat();
        Mat normalizedFace = new Mat();

        try {
            // Konversi ke grayscale terlebih dahulu
            if (faceRegion.channels() > 1) {
                Imgproc.cvtColor(faceRegion, grayFace, Imgproc.COLOR_RGB2GRAY);
            } else {
                faceRegion.copyTo(grayFace);
            }

            // Resize ke 48x48 sebelum normalisasi
            Size targetSize = new Size(48, 48);
            Imgproc.resize(grayFace, resizedFace, targetSize, 0, 0, Imgproc.INTER_AREA);

            // Konversi ke float32 dan normalisasi sekaligus
            resizedFace.convertTo(normalizedFace, CvType.CV_32F);
            Core.divide(normalizedFace, new Scalar(255.0), normalizedFace);

            // Debuggibg
            Log.d(TAG, String.format("Preprocessed face - Size: %dx%d, Type: %d",
                    normalizedFace.rows(), normalizedFace.cols(), normalizedFace.type()));

            return normalizedFace;
        } catch (Exception e) {
            Log.e(TAG, "Error in preprocessing: " + e.getMessage());
            throw e;
        }
    }


    @Override
    protected void finalize() throws Throwable {
        cleanup();
        super.finalize();
    }

    public void cleanup() {
        synchronized (lock) {
            if (faceDetector != null) {
                faceDetector.close();
                faceDetector = null;
            }
            isProcessing = false;
        }
    }
}