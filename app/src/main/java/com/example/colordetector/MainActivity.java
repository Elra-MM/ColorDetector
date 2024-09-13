package com.example.colordetector;

import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends CameraActivity implements View.OnTouchListener, CameraBridgeViewBase.CvCameraViewListener2 {

    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mRgba;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (OpenCVLoader.initLocal())
            Log.i("Main" , "OpenCV init success");
        else{
            Log.e("Main", "OpenCV init failed");
            (Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG)).show();
            return;
        }

        setContentView(R.layout.activity_main);

        mOpenCvCameraView = findViewById(R.id.openCVCamera);
        mOpenCvCameraView.setVisibility(View.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (mOpenCvCameraView != null)
        {
            mOpenCvCameraView.enableView();
            mOpenCvCameraView.setOnTouchListener(this);
        }
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
        mRgba = new Mat(height, width, CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        //FindColor(inputFrame);
        return inputFrame.rgba();
    }

    public boolean onTouch(View v, MotionEvent event){

        int cols = mRgba.cols();
        int rows = mRgba.rows();

        Point center = new Point(rows * 0.5, cols * 0.5);
        double[] colorRGBA = mRgba.get((int) center.x, (int) center.y);
        Log.i("TAG MM", "Color size = " + colorRGBA[0] + " , G " + colorRGBA[1] + " , R " + colorRGBA[2]);

        Imgproc.circle(mRgba, center, 50, new Scalar(0, 0, 0), 5); // black circle
        Imgproc.circle(mRgba, center, 55, new Scalar(255, 255, 255), 2); // white circle

        // Create a mask for the circle
        Mat mask = Mat.zeros(mRgba.size(), mRgba.type());
        Imgproc.circle(mask, center, 50, new Scalar(255, 255, 255), -1); // filled white circle

        // Extract the pixels within the circle using the mask
        Mat circlePixels = new Mat();
        mRgba.copyTo(circlePixels, mask);

        // Calculate the median of these pixels
        List<Double> blueValues = new ArrayList<>();
        List<Double> greenValues = new ArrayList<>();
        List<Double> redValues = new ArrayList<>();
        List<Double> alphaValues = new ArrayList<>();

        for (int i = 0; i < circlePixels.rows(); i++) {
            for (int j = 0; j < circlePixels.cols(); j++) {
                double[] pixel = circlePixels.get(i, j);
                if (pixel != null) {
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

        Scalar medianColor = new Scalar(medianBlue, medianGreen, medianRed, medianAlpha);
        Log.i("TAG MM", "Median color value: " + medianColor);
        return false; // don't need subsequent touch events
    }


}




























