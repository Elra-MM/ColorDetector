package com.example.colordetector;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
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

        Point centerPoint = GetCenterPoint();


        Rect blackRect = DefineBlackRect(centerPoint);

        DrawRectangles(centerPoint, blackRect);

        Scalar medianColor = ComputeMedian(mRgba.submat(blackRect));
        Log.i("TAG MM", "Median color value: " + medianColor);

        return mRgba;
    }

    private Point GetCenterPoint(){
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        return new Point(rows * 0.5, cols * 0.5);
    }

    private Rect DefineBlackRect(Point centerPoint){
        int blackRectWidth = 100;
        int blackRectHeight = 100;
        return new Rect((int) (centerPoint.x - blackRectWidth / 2), (int) (centerPoint.y - blackRectHeight / 2), blackRectWidth, blackRectHeight);
    }

    private void DrawRectangles(Point centerPoint, Rect blackRect){
        int whiteRectWidth = blackRect.width + 10;
        int whiteRectHeight = blackRect.height + 10;

        Rect whiteRect = new Rect((int) (centerPoint.x - whiteRectWidth / 2), (int) (centerPoint.y - whiteRectHeight / 2), whiteRectWidth, whiteRectHeight);

        Imgproc.rectangle(mRgba, whiteRect, new Scalar(255, 255, 255), 5);
        Imgproc.rectangle(mRgba, blackRect, new Scalar(0, 0, 0), 5);
    }

    private Scalar ComputeMedian(Mat roiMat){
        List<Double> blueValues = new ArrayList<>();
        List<Double> greenValues = new ArrayList<>();
        List<Double> redValues = new ArrayList<>();
        List<Double> alphaValues = new ArrayList<>();

        for (int i = 0; i < roiMat.rows(); i++) {
            for (int j = 0; j < roiMat.cols(); j++) {
                double[] pixel = roiMat.get(i, j);
                if (pixel != null && !IsBlackOrWhitePixel(pixel)) {
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

    private boolean IsBlackOrWhitePixel(double[] pixel){
        return (pixel[0] == 250 && pixel[1] == 250 && pixel[2] == 250)
                || (pixel[0] == 0 && pixel[1] == 0 && pixel[2] == 0);
    }
    
}
