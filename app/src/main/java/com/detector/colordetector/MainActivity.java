package com.detector.colordetector;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "MainActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    
    private CameraBridgeViewBase mOpenCvCameraView;
    private ColorCalculator colorCalculator;
    private DrawingUtils drawingUtils;
    private Window window;
    
    private ExecutorService computeExecutor;
    private ScheduledExecutorService nameUpdateExecutor;
    private final AtomicBoolean isProcessingFrame = new AtomicBoolean(false);

    private ScaleGestureDetector scaleGestureDetector;
    private float zoomFactor = 1.0f;
    private float maxZoom = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initOpenCV();

        setContentView(R.layout.activity_main);
        window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        colorCalculator = new ColorCalculator(getAssets(), findViewById(R.id.color_txt));
        drawingUtils = new DrawingUtils();

        computeExecutor = Executors.newSingleThreadExecutor();
        startNameUpdateTimer();

        View privacyPolicyButton = findViewById(R.id.button_privacy_policy);
        privacyPolicyButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PrivacyPolicyActivity.class);
            startActivity(intent);
        });

        setupZoom();
        askCameraPermission();
    }

    private void setupZoom() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                zoomFactor *= detector.getScaleFactor();
                zoomFactor = Math.max(1.0f, Math.min(zoomFactor, maxZoom));
                
                if (mOpenCvCameraView instanceof ZoomableCameraView) {
                    ((ZoomableCameraView) mOpenCvCameraView).setZoom(zoomFactor);
                }
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (scaleGestureDetector != null) {
            scaleGestureDetector.onTouchEvent(event);
        }
        return super.onTouchEvent(event);
    }

    private void startNameUpdateTimer() {
        if (nameUpdateExecutor != null && !nameUpdateExecutor.isShutdown()) {
            return;
        }
        nameUpdateExecutor = Executors.newSingleThreadScheduledExecutor();
        nameUpdateExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (colorCalculator != null) {
                    colorCalculator.computeNewName();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in scheduled name update", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void initOpenCV() {
        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV initialized successfully");
        } else {
            Log.e(TAG, "OpenCV initialization failed");
            showPopup("opencv_init_failed");
        }
    }

    private void askCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            initializeCamera();
        }
    }

    private void fetchMaxZoom() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    Float maxZoomValue = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                    if (maxZoomValue != null) {
                        this.maxZoom = maxZoomValue;
                    }
                    break;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access exception", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeCamera();
            } else {
                showPopup("camera_not_granted");
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void initializeCamera() {
        mOpenCvCameraView = findViewById(R.id.openCVCamera);
        mOpenCvCameraView.setCameraPermissionGranted();
        mOpenCvCameraView.disableFpsMeter();
        mOpenCvCameraView.setVisibility(View.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        fetchMaxZoom();
        mOpenCvCameraView.enableView();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
        if (nameUpdateExecutor != null) {
            nameUpdateExecutor.shutdown();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mOpenCvCameraView != null && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            mOpenCvCameraView.enableView();
        }
        startNameUpdateTimer();
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
        if (nameUpdateExecutor != null) {
            nameUpdateExecutor.shutdownNow();
        }
        if (computeExecutor != null) {
            computeExecutor.shutdownNow();
        }
        if (colorCalculator != null) {
            colorCalculator.shutdown();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
    }

    @Override
    public void onCameraViewStopped() {
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat mRgba = inputFrame.rgba();

        int squareSize = drawingUtils.getDetectionSquareSize(mRgba);
        Rect detectionSquare = drawingUtils.getDetectionSquare(mRgba, squareSize);
        
        // Use the new dashed circle instead of squares
        drawingUtils.drawDetectionCircle(mRgba, squareSize);

        if (isProcessingFrame.compareAndSet(false, true)) {
            final Mat submatCopy = mRgba.submat(detectionSquare).clone();
            computeExecutor.execute(() -> {
                try {
                    colorCalculator.computeNewMedian(submatCopy);
                } finally {
                    submatCopy.release();
                    isProcessingFrame.set(false);
                }
            });
        }

        return mRgba;
    }

    private void showPopup(String msgKey) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.popup);

        int resId = getResources().getIdentifier(msgKey, "string", getPackageName());
        String msg = resId != 0 ? getString(resId) : msgKey;
        
        TextView tv = dialog.findViewById(R.id.popup_txt);
        if (tv != null) tv.setText(msg);

        View btn = dialog.findViewById(R.id.popup_btn);
        if (btn != null) {
            btn.setOnClickListener(v -> {
                dialog.dismiss();
                finish();
            });
        }

        dialog.show();
    }
}
