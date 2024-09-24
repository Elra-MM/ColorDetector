package com.example.colordetector;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Collections;
import java.util.List;

import static org.opencv.imgproc.Imgproc.cvtColor;

public class MainActivity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mRgba;
    private ColorCalculator colorCalculator;

    private final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!initOpenCV()) return;

        setContentView(R.layout.activity_main);

        askCameraPermission();

        colorCalculator = new ColorCalculator(getAssets());
    }

    private boolean initOpenCV() {
        if (OpenCVLoader.initLocal())
        {
            Log.i("Main", "OpenCV init success");
            return true;
        }
        else {
            Log.e("Main", "OpenCV init failed");
            (Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG)).show();
            return false;
        }
    }

    private void askCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            if (isCameraAvailable()) {
                initializeCamera();
            } else {
                Toast.makeText(this, "It seems that your device does not support camera (or it is locked). Application will be closed.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private boolean isCameraAvailable() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return true;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isCameraAvailable()) {
                    initializeCamera();
                } else {
                    Toast.makeText(this, "It seems that your device does not support camera (or it is locked). Application will be closed.", Toast.LENGTH_LONG).show();
                    finish();
                }
            } else {
                Toast.makeText(this, "Camera permission is required to use this app", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initializeCamera() {
        mOpenCvCameraView = findViewById(R.id.openCVCamera);
        mOpenCvCameraView.setVisibility(View.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.enableView();
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
    }

    @Override
    public void onCameraViewStopped() {
    }

    private int frameCounter = 0;
    private static final int FRAME_INTERVAL = 30; // Call the method every 30 frames
    String medianColorName = "";

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        frameCounter++;
        mRgba = inputFrame.rgba();
        colorCalculator.setNewFrame(mRgba);

        Rect blackRect = drawRectangles();

        if (frameCounter >= FRAME_INTERVAL) {
            medianColorName = colorCalculator.getMedianName(blackRect);
            frameCounter = 0;
        }
        drawText(medianColorName);
        return mRgba;
    }

    private void drawText(String name) {
        // Create a bitmap from the Mat object
        Bitmap bitmap = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mRgba, bitmap);

        // Create a canvas to draw on the bitmap
        Canvas canvas = new Canvas(bitmap);

        // Set up the paint with desired attributes
        Paint paint = new Paint();
        paint.setColor(android.graphics.Color.BLACK);
        paint.setTextSize(80);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setAntiAlias(true);

        // Calculate the size of the text
        android.graphics.Rect textBounds = new android.graphics.Rect();
        paint.getTextBounds(name, 0, name.length(), textBounds);

        // Calculate the position for the text and background
        float x = (bitmap.getWidth() - textBounds.width()) / 2;
        float y = textBounds.height() + 20; // 20 pixels padding from the top

        // Add padding to the rectangle
        int padding = 20;
        float rectLeft = x - padding;
        float rectTop = y - textBounds.height() - padding;
        float rectRight = x + textBounds.width() + padding;
        float rectBottom = y + padding;

        // Draw a white rectangle behind the text
        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(android.graphics.Color.WHITE);
        backgroundPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, backgroundPaint);

        // Draw the text on the canvas
        canvas.drawText(name, x, y, paint);

        // Convert the bitmap back to a Mat object
        Utils.bitmapToMat(bitmap, mRgba);
    }


    private Rect drawRectangles() {
        int frameWidth = mRgba.cols();
        int frameHeight = mRgba.rows();

        // Define the size of the rectangles relative to the frame size
        int blackRectWidth = frameWidth / 4;
        int blackRectHeight = frameHeight / 4;
        int whiteRectWidth = blackRectWidth + 50;
        int whiteRectHeight = blackRectHeight + 50;
        int thickness = 25;

        Point centerPoint = new Point(frameWidth * 0.5, frameHeight * 0.5);

        // Update the black rectangle based on the new size
        Rect blackRect = new Rect((int) (centerPoint.x - blackRectWidth * 0.5), (int) (centerPoint.y - blackRectHeight * 0.5), blackRectWidth, blackRectHeight);

        // Define the white rectangle based on the black rectangle
        Rect whiteRect = new Rect((int) (centerPoint.x - whiteRectWidth * 0.5), (int) (centerPoint.y - whiteRectHeight * 0.5), whiteRectWidth, whiteRectHeight);

        // Draw the rectangles on the frame
        Imgproc.rectangle(mRgba, whiteRect, new Scalar(255, 255, 255), thickness);
        Imgproc.rectangle(mRgba, blackRect, new Scalar(0, 0, 0), thickness);
        return blackRect;
    }




}
