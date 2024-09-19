package com.example.colordetector;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
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
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.opencv.imgproc.Imgproc.COLOR_RGB2Lab;
import static org.opencv.imgproc.Imgproc.cvtColor;

public class MainActivity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mRgba;
    private Mat mCIELab;


    HashMap<String, List<Double>> colorSetCIE = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!InitOpenCV()) return;

        setContentView(R.layout.activity_main);

        AskCameraPermission();

        CreateColorSet();
    }

    private boolean InitOpenCV() {
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

    private void CreateColorSet() {
        try {
            AssetManager assetManager = getAssets();
            InputStream inputStream = assetManager.open("colorset.csv");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            reader.readLine(); // skip the first line
            String line;

            while ((line = reader.readLine()) != null) {
                String[] elt = line.split(";");
                colorSetCIE.put(elt[8], new ArrayList<>(Arrays.asList(Double.parseDouble(elt[4]), Double.parseDouble(elt[5]), Double.parseDouble(elt[6]))));
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("TAG MM", "The specified file was not found: " + e.getMessage());
        }
    }

    private void AskCameraPermission() {
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

    private void initializeCamera() {
        mOpenCvCameraView = findViewById(R.id.openCVCamera);
        mOpenCvCameraView.setVisibility(View.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
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
        mCIELab = new Mat();
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

        Rect blackRect = DrawRectangles();
        if (frameCounter >= FRAME_INTERVAL) {
            ProcessFrame(blackRect);
            frameCounter = 0;
        }
        Print(medianColorName);
        return mRgba;
    }

    private void ProcessFrame(Rect blackRect) {
        Rgba2CIELab(mRgba, mCIELab);
        Mat sub = mCIELab.submat(blackRect);
        Scalar medianColor = ComputeMedianCIE(sub);
        //Scalar averageColor = ComputeAverageCIE(sub);
        medianColorName = GetNameCIE(medianColor);
    }

    private String GetNameCIE(Scalar medianColor) {
        int min = Integer.MAX_VALUE;
        String name = "";
        for (String key : colorSetCIE.keySet()) {
            List<Double> color = colorSetCIE.get(key);
            int distance = (int) Math.sqrt(Math.pow(color.get(0) - medianColor.val[0], 2) + Math.pow(color.get(1) - medianColor.val[1], 2) + Math.pow(color.get(2) - medianColor.val[2], 2));
            if (distance < min) {
                min = distance;
                name = key;
            }
        }
        return name;
    }

    private String GetName(Scalar medianColor) {
        int min = Integer.MAX_VALUE;
        String name = "";
        for (String key : colorSetCIE.keySet()) {
            List<Double> color = colorSetCIE.get(key);
            int distance = (int) Math.sqrt(Math.pow(color.get(0) - medianColor.val[0], 2) + Math.pow(color.get(1) - medianColor.val[1], 2) + Math.pow(color.get(2) - medianColor.val[2], 2));
            if (distance < min) {
                min = distance;
                name = key;
            }
        }
        return name;
    }

    private void Rgba2CIELab(Mat mRgba, Mat mCIELab) {
        // Convert mRgba to floating-point type
        Mat mRgbaFloat = new Mat();
        mRgba.convertTo(mRgbaFloat, CvType.CV_32F);

        // Normalize mRgba to the range [0, 1]
        Core.normalize(mRgbaFloat, mRgbaFloat, 0, 1, Core.NORM_MINMAX);

        cvtColor(mRgbaFloat, mCIELab, COLOR_RGB2Lab);
    }


    private void Print(String name) {
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

    private Point GetCenterPoint() {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        return new Point(rows * 0.5, cols * 0.5);
    }

    private Rect DefineBlackRect(Point centerPoint) {
        int blackRectWidth = 300;
        int blackRectHeight = 300;
        return new Rect((int) (centerPoint.x - blackRectWidth * 0.5), (int) (centerPoint.y - blackRectHeight * 0.5), blackRectWidth, blackRectHeight);
    }

    private Rect DrawRectangles() {
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

    private Scalar ComputeMedianCIE(Mat roiMat) {
        List<Double> lValues = new ArrayList<>();
        List<Double> aValues = new ArrayList<>();
        List<Double> bValues = new ArrayList<>();

        for (int i = 0; i < roiMat.rows(); i++) {
            for (int j = 0; j < roiMat.cols(); j++) {
                double[] pixel = roiMat.get(i, j);
                if (pixel != null && IsBlackOrWhitePixel(pixel)) {
                    lValues.add(pixel[0]);
                    aValues.add(pixel[1]);
                    bValues.add(pixel[2]);
                }
            }
        }

        Collections.sort(lValues);
        Collections.sort(aValues);
        Collections.sort(bValues);

        double medianBlue = lValues.get(lValues.size() / 2);
        double medianGreen = aValues.get(aValues.size() / 2);
        double medianRed = bValues.get(bValues.size() / 2);

        return new Scalar(medianBlue, medianGreen, medianRed);
    }

    private Scalar ComputeMedian(Mat roiMat) {
        List<Double> blueValues = new ArrayList<>();
        List<Double> greenValues = new ArrayList<>();
        List<Double> redValues = new ArrayList<>();
        List<Double> alphaValues = new ArrayList<>();

        for (int i = 0; i < roiMat.rows(); i++) {
            for (int j = 0; j < roiMat.cols(); j++) {
                double[] pixel = roiMat.get(i, j);
                if (pixel != null && IsBlackOrWhitePixel(pixel)) {
                    blueValues.add(pixel[0]);
                    greenValues.add(pixel[1]);
                    redValues.add(pixel[2]);
                    alphaValues.add(pixel[3]);
                }
            }
        }

        Collections.sort(blueValues);
        Collections.sort(greenValues);
        Collections.sort(redValues);
        Collections.sort(alphaValues);

        double medianBlue = blueValues.get(blueValues.size() / 2);
        double medianGreen = greenValues.get(greenValues.size() / 2);
        double medianRed = redValues.get(redValues.size() / 2);
        double medianAlpha = alphaValues.get(alphaValues.size() / 2);

        return new Scalar(medianBlue, medianGreen, medianRed, medianAlpha);
    }

    private boolean IsBlackOrWhitePixel(double[] pixel) {
        return (pixel[0] != 250 || pixel[1] != 250 || pixel[2] != 250)
                && (pixel[0] != 0 || pixel[1] != 0 || pixel[2] != 0);
    }

}
