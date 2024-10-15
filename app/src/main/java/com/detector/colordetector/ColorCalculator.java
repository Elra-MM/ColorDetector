package com.detector.colordetector;

import android.content.res.AssetManager;
import android.util.Log;
import android.widget.TextView;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.opencv.imgproc.Imgproc.COLOR_RGB2Lab;
import static org.opencv.imgproc.Imgproc.cvtColor;

/// This class calculate the median color of the Mat each frame and then calculate the average of
/// all the medians to get the final color's name
/// To do that, it convert the color from RGB to CIELab and then find the closest color in the
// colorset.csv file and take its name

public class ColorCalculator {
    private List<Scalar> mediansColor;
    private final String TAG = "ColorCalculator";

    private final String SETNAME = "colorset.csv";
    public static final int ENGLISH = 7;
    public static final int FRENCH = 8;
    private final Mat mCIELab;
    private HashMap<String, List<Double>> colorSetCIE = new HashMap<>();
    private boolean isRunning;
    private final TextView textView;

    protected ColorCalculator(AssetManager assets, TextView textView) {
        mCIELab = new Mat();
        mediansColor = new ArrayList<>();
        colorSetCIE = createColorSet(assets);
        this.textView = textView;
    }

    protected void computeNewMedian(Mat newRgba) {
        if (newRgba == null || newRgba.empty()) {
            Log.e(TAG, "New frame is null or empty.");
            return;
        }
        isRunning = true;
        mediansColor.add(computeMedian(newRgba));
    }

    protected void computeNewName() {
        if (!isRunning)
        {
            return;
        }

        String medianName = getNameCIE(computeAverage(mediansColor));
        mediansColor.clear();
        textView.post(() -> textView.setText(medianName));
    }

    private Scalar computeAverage(List<Scalar> scalars) {
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
            InputStream inputStream = assets.open(SETNAME);
            int lang = Locale.getDefault().getLanguage().equals("fr") ? FRENCH : ENGLISH;
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            reader.readLine(); // skip the first line
            String line;

            while ((line = reader.readLine()) != null) {
                String[] elt = line.split(";");
                set.put(elt[lang], new ArrayList<>(Arrays.asList(Double.parseDouble(elt[4]), Double.parseDouble(elt[5]), Double.parseDouble(elt[6]))));
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
                if (pixel != null) {
                    lValues.add(pixel[0]);
                    aValues.add(pixel[1]);
                    bValues.add(pixel[2]);
                }
            }
        }

        Collections.sort(lValues);
        Collections.sort(aValues);
        Collections.sort(bValues);

        if (lValues.isEmpty() || aValues.isEmpty() || bValues.isEmpty()) 
        {
            Log.w(TAG, "Empty frame, all pixels were black|white|null !");
            return new Scalar(0, 0, 0);
        }
        double medianL = lValues.get(lValues.size() / 2);
        double medianA = aValues.get(aValues.size() / 2);
        double medianB = bValues.get(bValues.size() / 2);

        return new Scalar(medianL, medianA, medianB);
    }

    private String getNameCIE(Scalar medianColor) {
        String name = "";

        //Compute the distance between the median color and all the colors in the colorset in parallel
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<DistanceResult>> distances = new ArrayList<>();
        for (String colorName : colorSetCIE.keySet()) {
            List<Double> color = colorSetCIE.get(colorName);
            distances.add(executor.submit(
                    () -> new DistanceResult(colorName, getDistanceCIE2000(medianColor, new Scalar(color.get(0), color.get(1), color.get(2))))
            ));
        }

        //Get the min distances and return the color name associated
        try {
            DistanceResult result = distances.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .min(Comparator.comparingDouble(o -> o.distance)).orElseThrow();
            name = result.colorName;
        } catch (Exception e) {
            Log.e(TAG, "Error while calculating the distance", e);
        }

        executor.shutdown();
        return name;
    }

    private record DistanceResult(String colorName, double distance) {}

    private final double k_L = 1.0, k_C = 1.0, k_H = 1.0;
    private final double deg360InRad = toRadians(360.0);
    private final double deg180InRad = toRadians(180.0);
    private final double pow25To7 = Math.pow(25, 7);

    private double getDistanceCIE2000(Scalar color1, Scalar color2) {
        double l1 = color1.val[0];
        double a1 = color1.val[1];
        double b1 = color1.val[2];
        double l2 = color2.val[0];
        double a2 = color2.val[1];
        double b2 = color2.val[2];

        double C1 = Math.sqrt((a1 * a1) + (b1 * b1));
        double C2 = Math.sqrt((a2 * a2) + (b2 * b2));

        double meanC = (C1 + C2) * 0.5;

        double meanCpow7 = Math.pow(meanC, 7);
        double G = 0.5 * (1 - Math.sqrt(meanCpow7 / (meanCpow7 + pow25To7)));

        double a1Prime = (1.0 + G) * a1;
        double a2Prime = (1.0 + G) * a2;

        double c1Prime = Math.sqrt((a1Prime * a1Prime) + (b1 * b1));
        double c2Prime = Math.sqrt((a2Prime * a2Prime) + (b2 * b2));

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

        double deltaLPrime = l2 - l1;

        double deltaCPrime = c2Prime - c1Prime;

        double deltahPrime;
        double CPrimeProduct = c1Prime * c2Prime;
        if (CPrimeProduct == 0)
            deltahPrime = 0;
        else {
            deltahPrime = hPrime2 - hPrime1;
            if (deltahPrime < -deg180InRad)
                deltahPrime += deg360InRad;
            else if (deltahPrime > deg180InRad)
                deltahPrime -= deg360InRad;
        }

        double deltaHPrime = 2.0 * Math.sqrt(CPrimeProduct) *
                Math.sin(deltahPrime * 0.5);

        double lMeanPrime = (l1 + l2) * 0.5;

        double cMeanPrime = (c1Prime + c2Prime) * 0.5;

        double hMeanPrime;
        double hPrimeSum = hPrime1 + hPrime2;
        if (c1Prime * c2Prime == 0) {
            hMeanPrime = hPrimeSum;
        } else {
            if (Math.abs(hPrime1 - hPrime2) <= deg180InRad)
                hMeanPrime = hPrimeSum * 0.5;
            else {
                if (hPrimeSum < deg360InRad)
                    hMeanPrime = (hPrimeSum + deg360InRad) * 0.5;
                else
                    hMeanPrime = (hPrimeSum - deg360InRad) * 0.5;
            }
        }

        double T = 1.0 - (0.17 * Math.cos(hMeanPrime - toRadians(30.0))) +
                (0.24 * Math.cos(2.0 * hMeanPrime)) +
                (0.32 * Math.cos((3.0 * hMeanPrime) + toRadians(6.0))) -
                (0.20 * Math.cos((4.0 * hMeanPrime) - toRadians(63.0)));

        double deltaTheta = toRadians(30.0) *
                Math.exp(-Math.pow((hMeanPrime - toRadians(275.0)) / toRadians(25.0), 2.0));

        double R_C = 2.0 * Math.sqrt(Math.pow(cMeanPrime, 7.0) /
                (Math.pow(cMeanPrime, 7.0) + pow25To7));

        double S_L = 1 + ((0.015 * Math.pow(lMeanPrime - 50.0, 2.0)) /
                Math.sqrt(20 + Math.pow(lMeanPrime - 50.0, 2.0)));

        double S_C = 1 + (0.045 * cMeanPrime);

        double S_H = 1 + (0.015 * cMeanPrime * T);

        double R_T = (-Math.sin(2.0 * deltaTheta)) * R_C;

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
