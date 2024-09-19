package com.example.colordetector;

import android.content.res.AssetManager;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;

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

public class ColorCalculator {

    private final List<Mat> mRgbaList;
    private final String TAG = "ColorCalculator";

    private Mat mCIELab;
    HashMap<String, List<Double>> colorSetCIE = new HashMap<>();

    public ColorCalculator(AssetManager assets) {
        mRgbaList = new ArrayList<>();
        mCIELab = new Mat();
        createColorSet(assets);
    }

    public void setNewFrame(Mat mRgba) {
        mRgbaList.add(mRgba);
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

    protected String getMedianName(Rect blackRect){
        rgba2CIELab(mRgbaList.get(mRgbaList.size()-1), mCIELab); //TODO maybe don't take only the last rgba
        Mat sub = mCIELab.submat(blackRect);
        Scalar medianColor = computeMedianCIE(sub);
        return getNameCIE(medianColor);
    }

    private void rgba2CIELab(Mat mRgba, Mat mCIELab) {
        // Convert mRgba to floating-point type
        Mat mRgbaFloat = new Mat();
        mRgba.convertTo(mRgbaFloat, CvType.CV_32F);

        // Normalize mRgba to the range [0, 1]
        Core.normalize(mRgbaFloat, mRgbaFloat, 0, 1, Core.NORM_MINMAX);

        cvtColor(mRgbaFloat, mCIELab, COLOR_RGB2Lab);
    }

    private Scalar computeMedianCIE(Mat roiMat) {
        List<Double> lValues = new ArrayList<>();
        List<Double> aValues = new ArrayList<>();
        List<Double> bValues = new ArrayList<>();

        for (int i = 0; i < roiMat.rows(); i++) {
            for (int j = 0; j < roiMat.cols(); j++) {
                double[] pixel = roiMat.get(i, j);
                if (pixel != null && isBlackOrWhitePixel(pixel)) {
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
    private boolean isBlackOrWhitePixel(double[] pixel) {
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

}
