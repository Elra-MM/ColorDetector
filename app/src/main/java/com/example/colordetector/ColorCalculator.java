package com.example.colordetector;

import android.content.res.AssetManager;
import android.nfc.Tag;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
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

import static org.opencv.imgproc.Imgproc.COLOR_RGB2HSV_FULL;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2Lab;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2XYZ;
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
        Mat normalizedRgba = new Mat();
        newRgba.convertTo(normalizedRgba, CvType.CV_32F, 1.0 / 255.0);
        rgba2CIELab(normalizedRgba, mCIELab);
        return computeMedianCIE(mCIELab);
    }

    private HashMap<String, List<Double>> createColorSet(AssetManager assets) {
        HashMap<String, List<Double>> set = new HashMap<>();
        try {
            InputStream inputStream = assets.open("colorset.csv");
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
        List<Double> c = new ArrayList<>();

        Log.i(TAG, "MM : medianColor =" + medianColor.val[0] + " " + medianColor.val[1] + " " + medianColor.val[2]);
        //TODO : can use stream() and colorSetCIE.entrySet()
        for (String key : colorSetCIE.keySet()) {
            List<Double> color = colorSetCIE.get(key);
            double distance = getDistanceCIE2000(medianColor, new Scalar(color.get(0), color.get(1), color.get(2)));
            if (distance < min) {
                min = distance;
                name = key;
                c = color;
            }
        }
        Log.i(TAG, "MM : name selected = " + name +" color selected =" + c.get(0) + " " + c.get(1) + " " + c.get(2));
        return name;
    }

    //TODO : refactor this method for perf
    private double getDistanceCIE2000(Scalar color1, Scalar color2) {
        double l1 = color1.val[0];
        double a1 = color1.val[1];
        double b1 = color1.val[2];
        double l2 = color2.val[0];
        double a2 = color2.val[1];
        double b2 = color2.val[2];

        double k_L = 1.0, k_C = 1.0, k_H = 1.0;
        double deg360InRad = toRadians(360.0);
        double deg180InRad = toRadians(180.0);
        double pow25To7 = 6103515625.0; /* Math.pow(25, 7) */

        /*
         * Step 1
         */
        /* Equation 2 */
        double C1 = Math.sqrt((a1 * a1) + (b1 * b1));
        double C2 = Math.sqrt((a2 * a2) + (b2 * b2));
        /* Equation 3 */
        double barC = (C1 + C2) / 2.0;
        /* Equation 4 */
        double G = 0.5 * (1 - Math.sqrt(Math.pow(barC, 7) / (Math.pow(barC, 7) + pow25To7)));
        /* Equation 5 */
        double a1Prime = (1.0 + G) * a1;
        double a2Prime = (1.0 + G) * a2;
        /* Equation 6 */
        double CPrime1 = Math.sqrt((a1Prime * a1Prime) + (b1 * b1));
        double CPrime2 = Math.sqrt((a2Prime * a2Prime) + (b2 * b2));
        /* Equation 7 */
        double hPrime1;
        if (b1 == 0 && a1Prime == 0)
            hPrime1 = 0.0;
        else {
            hPrime1 = Math.atan2(b1, a1Prime);
            /*
             * This must be converted to a hue angle in degrees between 0
             * and 360 by addition of 2􏰏 to negative hue angles.
             */
            if (hPrime1 < 0)
                hPrime1 += deg360InRad;
        }
        double hPrime2;
        if (b2 == 0 && a2Prime == 0)
            hPrime2 = 0.0;
        else {
            hPrime2 = Math.atan2(b2, a2Prime);
            /*
             * This must be converted to a hue angle in degrees between 0
             * and 360 by addition of 2􏰏 to negative hue angles.
             */
            if (hPrime2 < 0)
                hPrime2 += deg360InRad;
        }

        /*
         * Step 2
         */
        /* Equation 8 */
        double deltaLPrime = l2 - l1;
        /* Equation 9 */
        double deltaCPrime = CPrime2 - CPrime1;
        /* Equation 10 */
        double deltahPrime;
        double CPrimeProduct = CPrime1 * CPrime2;
        if (CPrimeProduct == 0)
            deltahPrime = 0;
        else {
            /* Avoid the fabs() call */
            deltahPrime = hPrime2 - hPrime1;
            if (deltahPrime < -deg180InRad)
                deltahPrime += deg360InRad;
            else if (deltahPrime > deg180InRad)
                deltahPrime -= deg360InRad;
        }
        /* Equation 11 */
        double deltaHPrime = 2.0 * Math.sqrt(CPrimeProduct) *
                Math.sin(deltahPrime / 2.0);

        /*
         * Step 3
         */
        /* Equation 12 */
        double barLPrime = (l1 + l2) / 2.0;
        /* Equation 13 */
        double barCPrime = (CPrime1 + CPrime2) / 2.0;
        /* Equation 14 */
        double barhPrime, hPrimeSum = hPrime1 + hPrime2;
        if (CPrime1 * CPrime2 == 0) {
            barhPrime = hPrimeSum;
        } else {
            if (Math.abs(hPrime1 - hPrime2) <= deg180InRad)
                barhPrime = hPrimeSum / 2.0;
            else {
                if (hPrimeSum < deg360InRad)
                    barhPrime = (hPrimeSum + deg360InRad) / 2.0;
                else
                    barhPrime = (hPrimeSum - deg360InRad) / 2.0;
            }
        }
        /* Equation 15 */
        double T = 1.0 - (0.17 * Math.cos(barhPrime - toRadians(30.0))) +
                (0.24 * Math.cos(2.0 * barhPrime)) +
                (0.32 * Math.cos((3.0 * barhPrime) + toRadians(6.0))) -
                (0.20 * Math.cos((4.0 * barhPrime) - toRadians(63.0)));
        /* Equation 16 */
        double deltaTheta = toRadians(30.0) *
                Math.exp(-Math.pow((barhPrime - toRadians(275.0)) / toRadians(25.0), 2.0));
        /* Equation 17 */
        double R_C = 2.0 * Math.sqrt(Math.pow(barCPrime, 7.0) /
                (Math.pow(barCPrime, 7.0) + pow25To7));
        /* Equation 18 */
        double S_L = 1 + ((0.015 * Math.pow(barLPrime - 50.0, 2.0)) /
                Math.sqrt(20 + Math.pow(barLPrime - 50.0, 2.0)));
        /* Equation 19 */
        double S_C = 1 + (0.045 * barCPrime);
        /* Equation 20 */
        double S_H = 1 + (0.015 * barCPrime * T);
        /* Equation 21 */
        double R_T = (-Math.sin(2.0 * deltaTheta)) * R_C;

        /* Equation 22 */
        return Math.sqrt(
                Math.pow(deltaLPrime / (k_L * S_L), 2.0) +
                        Math.pow(deltaCPrime / (k_C * S_C), 2.0) +
                        Math.pow(deltaHPrime / (k_H * S_H), 2.0) +
                        (R_T * (deltaCPrime / (k_C * S_C)) * (deltaHPrime / (k_H * S_H))));
    }
    private double toRadians(double degrees) {
        return degrees * Math.PI / 180;
    }
    public static double toDegrees(double radians) {
        return radians * 180 / Math.PI;
    }
}
