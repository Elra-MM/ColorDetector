package com.example.colordetector;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.opencsv.CSVReader;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.opencv.imgproc.Imgproc.COLOR_RGB2Lab;
import static org.opencv.imgproc.Imgproc.cvtColor;

public class MainActivity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mRgba;
    private Mat mCIELab;


    HashMap<String, List<Double>> colorSet = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (OpenCVLoader.initLocal())
            Log.i("Main", "OpenCV init success");
        else {
            Log.e("Main", "OpenCV init failed");
            (Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG)).show();
            return;
        }

        setContentView(R.layout.activity_main);

        mOpenCvCameraView = findViewById(R.id.openCVCamera);
        mOpenCvCameraView.setVisibility(View.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        try {
            AssetManager assetManager = getAssets();
            InputStream inputStream = assetManager.open("colorset.csv");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            reader.readLine(); // skip the first line
            String line;

            while ((line = reader.readLine()) != null) {
                String[] elt = line.split(";");
                colorSet.put(elt[8], new ArrayList<>(Arrays.asList(Double.parseDouble(elt[4]), Double.parseDouble(elt[5]), Double.parseDouble(elt[6]))));
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("TAG MM", "The specified file was not found: " + e.getMessage());
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

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();

        Point centerPoint = GetCenterPoint();

        Rect blackRect = DefineBlackRect(centerPoint);

        DrawRectangles(centerPoint, blackRect);

        Rgba2CIELab(mRgba, mCIELab);

        Scalar medianColor = ComputeMedianCIE(mCIELab.submat(blackRect));
        PrintMedian(medianColor);
        String medianColorName = GetName(medianColor);

        return mRgba;
    }

    private String GetName(Scalar medianColor) {
        int min = Integer.MAX_VALUE;
        String name = "";
        for (String key : colorSet.keySet()) {
            List<Double> color = colorSet.get(key);
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


    private void PrintMedian(Scalar medianColor) {
        @SuppressLint("DefaultLocale") String medianColorText = String.format("Median Color: [%.2f, %.2f, %.2f]",
                medianColor.val[0], medianColor.val[1],
                medianColor.val[2]);

        Point textPosition = new Point(0, mRgba.rows() / 6);

        // Draw the text on the Mat object
        Imgproc.putText(mRgba, medianColorText, textPosition, Imgproc.FONT_HERSHEY_SIMPLEX,
                2.0, new Scalar(0, 0, 0), 5);
    }

    private Point GetCenterPoint() {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        return new Point(rows * 0.5, cols * 0.5);
    }

    private Rect DefineBlackRect(Point centerPoint) {
        int blackRectWidth = 100;
        int blackRectHeight = 100;
        return new Rect((int) (centerPoint.x - blackRectWidth / 2), (int) (centerPoint.y - blackRectHeight / 2), blackRectWidth, blackRectHeight);
    }

    private void DrawRectangles(Point centerPoint, Rect blackRect) {
        int whiteRectWidth = blackRect.width + 10;
        int whiteRectHeight = blackRect.height + 10;

        Rect whiteRect = new Rect((int) (centerPoint.x - whiteRectWidth / 2), (int) (centerPoint.y - whiteRectHeight / 2), whiteRectWidth, whiteRectHeight);

        Imgproc.rectangle(mRgba, whiteRect, new Scalar(255, 255, 255), 5);
        Imgproc.rectangle(mRgba, blackRect, new Scalar(0, 0, 0), 5);
    }

    private Scalar ComputeMedianCIE(Mat roiMat) {
        List<Double> lValues = new ArrayList<>();
        List<Double> aValues = new ArrayList<>();
        List<Double> bValues = new ArrayList<>();

        for (int i = 0; i < roiMat.rows(); i++) {
            for (int j = 0; j < roiMat.cols(); j++) {
                double[] pixel = roiMat.get(i, j);
                if (pixel != null ) {
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

    private boolean IsBlackOrWhitePixel(double[] pixel) {
        return (pixel[0] == 250 && pixel[1] == 250 && pixel[2] == 250)
                || (pixel[0] == 0 && pixel[1] == 0 && pixel[2] == 0);
    }

}
