package com.example.colordetector;

import android.content.res.AssetManager;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
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

/// This class calculate the median color of the Mat each frame and then calculate the average of
// all the medians to get the final color's name
// To do that, it convert the color from RGB to CIELab and then find the closest color in the
// colorset.csv file and take its name

public class ColorCalculator {

    private List<Scalar> mediansColor;
    private final String TAG = "ColorCalculator";

    private final Mat mCIELab;
    HashMap<String, List<Double>> colorSetCIE = new HashMap<>();
    private String medianName = "";


    protected ColorCalculator(AssetManager assets) {
        mCIELab = new Mat();
        mediansColor = new ArrayList<>();
        createColorSet(assets);
    }

    protected String getMedianName() {
        return medianName;
    }

    protected void setNewFrame(Mat newRgba) {
        if (newRgba == null || newRgba.empty()) {
            Log.e(TAG, "New frame is null or empty.");
            return;
        }
        mediansColor.add(calculateMedian(newRgba));
    }

    protected void computeNewName() {
        medianName = getNameCIE(calculateAverage(mediansColor));
        mediansColor.clear();
    }

    protected Scalar calculateAverage(List<Scalar> scalars) {
        if (scalars == null || scalars.isEmpty()) {
            throw new IllegalArgumentException("The list of scalars is null or empty.");
        }

        double sumL = 0, sumA = 0, sumB = 0;
        for (Scalar scalar : scalars) {
            sumL += scalar.val[0];
            sumA += scalar.val[1];
            sumB += scalar.val[2];
        }

        int count = scalars.size();
        return new Scalar(sumL / count, sumA / count, sumB / count);
    }

    private Scalar calculateMedian(Mat newRgba) {
        rgba2CIELab(newRgba, mCIELab);
        return computeMedianCIE(mCIELab);
    }

    private void createColorSet(AssetManager assets) {
        try {
            InputStream inputStream = assets.open("colorset.csv");
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
            Log.e(TAG, "The specified file was not found: " + e.getMessage());
        }
    }

    private void rgba2CIELab(Mat mRgba, Mat mCIELab) {
        if (mRgba.empty()) {
            Log.e(TAG, "Input matrix is empty, cannot convert to CIELab.");
            return;
        }
        cvtColor(mRgba, mCIELab, COLOR_RGB2Lab);
    }

    private Scalar computeMedianCIE(Mat roiMat) {
        List<Double> lValues = new ArrayList<>();
        List<Double> aValues = new ArrayList<>();
        List<Double> bValues = new ArrayList<>();

        for (int i = 0; i < roiMat.rows(); i++) {
            for (int j = 0; j < roiMat.cols(); j++) {
                double[] pixel = roiMat.get(i, j);
                if (pixel != null && isNotBlackOrWhitePixel(pixel)) {
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

    private boolean isNotBlackOrWhitePixel(double[] pixel) {
        return (pixel[0] != 250 || pixel[1] != 250 || pixel[2] != 250)
                && (pixel[0] != 0 || pixel[1] != 0 || pixel[2] != 0);
    }

    private String getNameCIE(Scalar medianColor) {
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
//    private Mat FinishCalculateAverageMat() {
//        if (sumMat.empty()) {
//            Log.e(TAG, "Sum matrix is empty, cannot calculate average.");
//            return new Mat();
//        }
//        Mat averageMat = new Mat();
//        Core.divide(sumMat, Scalar.all(nbFrames), averageMat);
//        return averageMat;
//    }
}
