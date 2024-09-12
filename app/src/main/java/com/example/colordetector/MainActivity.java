package com.example.colordetector;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.Collections;
import java.util.List;

public class MainActivity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

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

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        FindColor();
        return mRgba;
    }

    private void FindColor(){
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int roi_size = 10;
        //get color at the center of the image
        //TODO : 1 - create ROI representing a little circle at the center of the image
        // 2 - draw the circle bold black
        // 3 - calculate the average of the color pixels under this circle
        double[] colorRGBA = mRgba.get(rows/2 , cols/2);
        Log.i("TAG MM", "Color size = " + colorRGBA[0] + " , G " + colorRGBA[1] + " , R " + colorRGBA[2]);
//
//        Mat centerRgba = mRgba.submat()
//        Mat centerHsv = new Mat();
//        Imgproc.cvtColor();

    }


}




























