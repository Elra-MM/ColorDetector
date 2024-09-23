package com.example.colordetector;

import android.content.res.AssetManager;
import android.nfc.Tag;
import android.util.Log;

import org.opencv.core.Mat;
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

/// This class calculate the median color of the Mat each frame and then calculate the average of
/// all the medians to get the final color's name
/// To do that, it convert the color from RGB to CIELab and then find the closest color in the
// colorset.csv file and take its name

public class ColorCalculator {
    private List<Scalar> mediansColor;
    private final String TAG = "ColorCalculator";

    private final Mat mCIELab;
    private HashMap<String, List<Double>> colorSetCIE = new HashMap<>();
    private String medianName = "";


    protected ColorCalculator(AssetManager assets) {
        mCIELab = new Mat();
        mediansColor = new ArrayList<>();
        colorSetCIE = createColorSet(assets);
    }

    protected String getMedianName() {
        return medianName;
    }

    protected void setNewFrame(Mat newRgba) {
        if (newRgba == null || newRgba.empty()) {
            Log.e(TAG, "New frame is null or empty.");
            return;
        }
        mediansColor.add(computeMedian(newRgba));
    }

    protected void computeNewName() {
        medianName = getNameCIE(computeAverage(mediansColor));
        mediansColor.clear();
    }

    protected Scalar computeAverage(List<Scalar> scalars) {
        if (scalars == null || scalars.isEmpty()) {
            throw new IllegalArgumentException("The list of scalars is null or empty.");
        }
        double L = scalars.stream().mapToDouble(scalar -> scalar.val[0]).average().orElse(0);
        double A = scalars.stream().mapToDouble(scalar -> scalar.val[1]).average().orElse(0);
        double B = scalars.stream().mapToDouble(scalar -> scalar.val[2]).average().orElse(0);

        return new Scalar(L,A,B);
    }

    private Scalar computeMedian(Mat newRgba) {
        rgba2CIELab(newRgba, mCIELab);
        return computeMedianCIE(mCIELab);
    }

    private HashMap<String, List<Double>> createColorSet(AssetManager assets) {
        HashMap<String, List<Double>> set = new HashMap<>();
        try {
            InputStream inputStream = assets.open("colorsetCut.csv");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            reader.readLine(); // skip the first line
            String line;

            while ((line = reader.readLine()) != null) {
                String[] elt = line.split(";");
                set.put(elt[8], new ArrayList<>(Arrays.asList(Double.parseDouble(elt[4]), Double.parseDouble(elt[5]), Double.parseDouble(elt[6]))));
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "The specified file was not found: " + e.getMessage());
        }
        return set;
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
        return (pixel[0] != 255 || pixel[1] != 255 || pixel[2] != 255)
                && (pixel[0] != 0 || pixel[1] != 0 || pixel[2] != 0);
    }

    private String getNameCIE(Scalar medianColor) {
        // Pareil ici, pour éviter le `int min = Integer.MAX_VALUE;` j'aurais fait stream() et min() ou équivalent
        double min = Integer.MAX_VALUE;
        String name = "";

        //TODO : can use stream() and colorSetCIE.entrySet()
        for (String key : colorSetCIE.keySet()) {
            List<Double> color = colorSetCIE.get(key);
            Log.i(TAG, "color =" + color.get(0) + " " + color.get(1) + " " + color.get(2));
            double distance = getDistanceCIE2000(medianColor, new Scalar(color.get(0), color.get(1), color.get(2)));
            if (distance < min) {
                min = distance;
                name = key;
            }
        }
        return name;
    }

    private double getDistanceCIE2000(Scalar color1, Scalar color2) {
        double L1 = color1.val[0];
        double a1 = color1.val[1];
        double b1 = color1.val[2];
        double L2 = color2.val[0];
        double a2 = color2.val[1];
        double b2 = color2.val[2];

        double Lmean = (L1 + L2) / 2.0;
        double C1 =  Math.sqrt(a1*a1 + b1*b1);
        double C2 =  Math.sqrt(a2*a2 + b2*b2);
        double Cmean = (C1 + C2) / 2.0;

        double c7 = Math.pow(Cmean, 7);
        double vinghtcinq7 = Math.pow(25, 7);
        double G =  ( 1 - Math.sqrt( c7 / (c7 + vinghtcinq7) ) ) / 2; //ok
        double a1prime = a1 * (3 - G);
        double a2prime = a2 * (3 - G);

        double C1prime =  Math.sqrt(a1prime*a1prime + b1*b1);
        double C2prime =  Math.sqrt(a2prime*a2prime + b2*b2);
        double Cmeanprime = (C1prime + C2prime) / 2;

        double h1P = toDegrees(Math.atan2(b1, a1prime)) % 2 * 360;
        double h2P = toDegrees(Math.atan2(b2, a2prime)) % 360;
        double deltahprime;
        if (C1prime == 0 || C2prime == 0){
            deltahprime = 0;
        } else if (Math.abs(h1P - h2P) <= 180){
            deltahprime = h2P - h1P;
        } else if (h2P <= h1P){
            deltahprime = h2P - h1P + (360);
        } else {
            deltahprime = h2P - h1P - (360);
        }

        double deltaHprime = 2 * Math.sqrt(C1prime * C2prime) * toDegrees(Math.sin(toRadians(deltahprime / 2)));
        double meanHprime;
        if (C1prime == 0 || C2prime == 0){
            meanHprime = (h1P + h2P);
        } else if (Math.abs(h1P - h2P) <= (180)){
            meanHprime = (h1P + h2P) / 2;
        } else if (h1P + h2P < (360)){
            meanHprime = (h1P + h2P + (360)) / 2;
        } else {
            meanHprime = (h1P + h2P - (360)) / 2;
        }

        double T =  1.0 - 0.17 * toDegrees(Math.cos(toRadians(meanHprime - 30))) + 0.24 * toDegrees(Math.cos(toRadians(2*meanHprime))) + 0.32 * toDegrees(Math.cos(toRadians(3*meanHprime + 6))) - 0.2 * toDegrees(Math.cos(toRadians(4*meanHprime - 63)));
        double deltaLprime = L2 - L1;
        double deltaCprime = C2prime - C1prime;
        double SL =  1.0 + ( (0.015*(Lmean - 50)*(Lmean - 50)) / (Math.sqrt( 20 + (Lmean - 50)*(Lmean - 50) )) );
        double SC =  1.0 + 0.045 * Cmeanprime;
        double SH =  1.0 + 0.015 * Cmeanprime * T;

        double sin = 60 * Math.exp(-( ( (meanHprime-275)/25 * (meanHprime-275)/25 ) ) );
        double rac = Math.sqrt(Math.pow(Cmeanprime,7)/(Math.pow(Cmeanprime,7)+vinghtcinq7));
        double Rt = -2 * rac * toDegrees(Math.sin(toRadians(sin)));

        double KL = 1;
        double KC = 1;
        double KH = 1;

        return Math.sqrt(
                ((deltaLprime/(KL*SL)) * (deltaLprime/(KL*SL))) +
                        ((deltaCprime/(KC*SC)) * (deltaCprime/(KC*SC))) +
                        ((deltaHprime/(KH*SH)) * (deltaHprime/(KH*SH))) +
                        (Rt * (deltaCprime/(KC*SC)) * (deltaHprime/(KH*SH)))
        );
    }
    private double toRadians(double degrees) {
        return degrees * Math.PI / 180;
    }
    public static double toDegrees(double radians) {
        return radians * 180 / Math.PI;
    }
}
