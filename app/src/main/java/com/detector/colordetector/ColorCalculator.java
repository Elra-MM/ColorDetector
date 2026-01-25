package com.detector.colordetector;

import android.content.res.AssetManager;
import android.util.Log;
import android.widget.TextView;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.opencv.imgproc.Imgproc.COLOR_HSV2RGB;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2HSV;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2Lab;
import static org.opencv.imgproc.Imgproc.cvtColor;

/**
 * This class calculates the median color of the Mat each frame and then calculates the average of
 * all the medians to get the final color's name.
 * It converts the color from RGB to CIELab and then finds the closest color in the colorset CSV file.
 */
public class ColorCalculator {
    private static final String TAG = "ColorCalculator";
    private static final String SETNAME = "colorsetCut.csv";
    public static final int ENGLISH = 7;
    public static final int FRENCH = 8;

    private final List<Scalar> mediansColor = Collections.synchronizedList(new ArrayList<>());
    private final Mat mCIELab = new Mat();
    private final Mat mNormalized = new Mat();
    private final Mat mHSV = new Mat();
    private final HashMap<String, List<Double>> colorSetCIE;
    private final TextView textView;
    private final ExecutorService distanceExecutor;
    
    private volatile boolean isRunning = false;

    protected ColorCalculator(AssetManager assets, TextView textView) {
        this.colorSetCIE = createColorSet(assets);
        this.textView = textView;
        this.distanceExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    protected void computeNewMedian(Mat newRgba) {
        if (newRgba == null || newRgba.empty()) {
            Log.e(TAG, "New frame is null or empty.");
            return;
        }
        isRunning = true;
        
        // Process the frame and add its median to the list
        mediansColor.add(computeMedian(newRgba));
    }

    private Scalar computeMedian(Mat rgba) {
        // Enhance color (Saturation)
        cvtColor(rgba, mHSV, COLOR_RGB2HSV);
        List<Mat> hsvChannels = new ArrayList<>();
        Core.split(mHSV, hsvChannels);
        Core.multiply(hsvChannels.get(1), new Scalar(2), hsvChannels.get(1)); // Increase saturation
        Core.merge(hsvChannels, mHSV);
        cvtColor(mHSV, rgba, COLOR_HSV2RGB);

        // Normalize to [0, 1] for CIELab conversion
        rgba.convertTo(mNormalized, CvType.CV_32F, 1.0 / 255.0);
        
        // Convert to CIELab
        cvtColor(mNormalized, mCIELab, COLOR_RGB2Lab);

        return computeMedianCIE(mCIELab);
    }

    protected void computeNewName() {
        if (!isRunning || mediansColor.isEmpty()) {
            return;
        }

        List<Scalar> copy;
        synchronized (mediansColor) {
            copy = new ArrayList<>(mediansColor);
            mediansColor.clear();
        }

        Scalar averageColor = computeAverage(copy);
        String medianName = getNameCIE(averageColor);
        
        textView.post(() -> textView.setText(medianName));
    }

    private Scalar computeAverage(List<Scalar> scalars) {
        if (scalars == null || scalars.isEmpty()) {
            return new Scalar(0, 0, 0);
        }
        double L = scalars.stream().mapToDouble(scalar -> scalar.val[0]).average().orElse(0);
        double A = scalars.stream().mapToDouble(scalar -> scalar.val[1]).average().orElse(0);
        double B = scalars.stream().mapToDouble(scalar -> scalar.val[2]).average().orElse(0);

        return new Scalar(L, A, B);
    }

    private HashMap<String, List<Double>> createColorSet(AssetManager assets) {
        HashMap<String, List<Double>> set = new HashMap<>();
        try (InputStream inputStream = assets.open(SETNAME);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            int lang = Locale.getDefault().getLanguage().equals("fr") ? FRENCH : ENGLISH;
            reader.readLine(); // skip header
            String line;

            while ((line = reader.readLine()) != null) {
                String[] elt = line.split(";");
                if (elt.length > Math.max(lang, 6)) {
                    set.put(elt[lang], Arrays.asList(
                            Double.parseDouble(elt[4]), 
                            Double.parseDouble(elt[5]), 
                            Double.parseDouble(elt[6])
                    ));
                }
            }
        } catch (IOException | NumberFormatException e) {
            Log.e(TAG, "Error loading color set: " + e.getMessage());
        }
        return set;
    }

    private Scalar computeMedianCIE(Mat roiMat) {
        int totalPixels = roiMat.rows() * roiMat.cols();
        if (totalPixels == 0) return new Scalar(0, 0, 0);

        float[] lValues = new float[totalPixels];
        float[] aValues = new float[totalPixels];
        float[] bValues = new float[totalPixels];

        int index = 0;
        for (int i = 0; i < roiMat.rows(); i++) {
            for (int j = 0; j < roiMat.cols(); j++) {
                double[] pixel = roiMat.get(i, j);
                if (pixel != null) {
                    lValues[index] = (float) pixel[0];
                    aValues[index] = (float) pixel[1];
                    bValues[index] = (float) pixel[2];
                    index++;
                }
            }
        }

        if (index == 0) return new Scalar(0, 0, 0);

        Arrays.sort(lValues, 0, index);
        Arrays.sort(aValues, 0, index);
        Arrays.sort(bValues, 0, index);

        return new Scalar(lValues[index / 2], aValues[index / 2], bValues[index / 2]);
    }

    private String getNameCIE(Scalar medianColor) {
        List<Future<DistanceResult>> futures = new ArrayList<>();
        for (String colorName : colorSetCIE.keySet()) {
            List<Double> colorValues = colorSetCIE.get(colorName);
            futures.add(distanceExecutor.submit(() -> {
                double distance = getDistanceCIE2000(medianColor, new Scalar(colorValues.get(0), colorValues.get(1), colorValues.get(2)));
                return new DistanceResult(colorName, distance);
            }));
        }

        DistanceResult bestMatch = null;
        for (Future<DistanceResult> future : futures) {
            try {
                DistanceResult result = future.get();
                if (bestMatch == null || result.distance < bestMatch.distance) {
                    bestMatch = result;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error calculating distance", e);
            }
        }

        return bestMatch != null ? bestMatch.colorName : "";
    }

    public void shutdown() {
        distanceExecutor.shutdown();
    }

    private record DistanceResult(String colorName, double distance) {}

    // CIE2000 Constants
    private final double k_L = 1.0, k_C = 1.0, k_H = 1.0;
    private final double deg360InRad = Math.toRadians(360.0);
    private final double deg180InRad = Math.toRadians(180.0);
    private final double pow25To7 = Math.pow(25, 7);

    private double getDistanceCIE2000(Scalar color1, Scalar color2) {
        double l1 = color1.val[0], a1 = color1.val[1], b1 = color1.val[2];
        double l2 = color2.val[0], a2 = color2.val[1], b2 = color2.val[2];

        double C1 = Math.sqrt(a1 * a1 + b1 * b1);
        double C2 = Math.sqrt(a2 * a2 + b2 * b2);
        double meanC = (C1 + C2) * 0.5;

        double meanCpow7 = Math.pow(meanC, 7);
        double G = 0.5 * (1 - Math.sqrt(meanCpow7 / (meanCpow7 + pow25To7)));

        double a1P = (1.0 + G) * a1;
        double a2P = (1.0 + G) * a2;

        double c1P = Math.sqrt(a1P * a1P + b1 * b1);
        double c2P = Math.sqrt(a2P * a2P + b2 * b2);

        double h1P = (b1 == 0 && a1P == 0) ? 0.0 : Math.atan2(b1, a1P);
        if (h1P < 0) h1P += deg360InRad;

        double h2P = (b2 == 0 && a2P == 0) ? 0.0 : Math.atan2(b2, a2P);
        if (h2P < 0) h2P += deg360InRad;

        double dL = l2 - l1;
        double dC = c2P - c1P;
        double dhP;
        double CPProd = c1P * c2P;
        if (CPProd == 0) dhP = 0;
        else {
            dhP = h2P - h1P;
            if (dhP < -deg180InRad) dhP += deg360InRad;
            else if (dhP > deg180InRad) dhP -= deg360InRad;
        }

        double dH = 2.0 * Math.sqrt(CPProd) * Math.sin(dhP * 0.5);
        double lMP = (l1 + l2) * 0.5;
        double cMP = (c1P + c2P) * 0.5;

        double hMP;
        if (CPProd == 0) hMP = h1P + h2P;
        else {
            if (Math.abs(h1P - h2P) <= deg180InRad) hMP = (h1P + h2P) * 0.5;
            else hMP = (h1P + h2P + (h1P + h2P < deg360InRad ? deg360InRad : -deg360InRad)) * 0.5;
        }

        double T = 1.0 - 0.17 * Math.cos(hMP - Math.toRadians(30.0))
                   + 0.24 * Math.cos(2.0 * hMP)
                   + 0.32 * Math.cos(3.0 * hMP + Math.toRadians(6.0))
                   - 0.20 * Math.cos(4.0 * hMP - Math.toRadians(63.0));

        double dTheta = Math.toRadians(30.0) * Math.exp(-Math.pow((hMP - Math.toRadians(275.0)) / Math.toRadians(25.0), 2.0));
        double RC = 2.0 * Math.sqrt(Math.pow(cMP, 7.0) / (Math.pow(cMP, 7.0) + pow25To7));
        double SL = 1 + (0.015 * Math.pow(lMP - 50.0, 2.0)) / Math.sqrt(20 + Math.pow(lMP - 50.0, 2.0));
        double SC = 1 + 0.045 * cMP;
        double SH = 1 + 0.015 * cMP * T;
        double RT = -Math.sin(2.0 * dTheta) * RC;

        return Math.sqrt(Math.pow(dL / (k_L * SL), 2.0)
                       + Math.pow(dC / (k_C * SC), 2.0)
                       + Math.pow(dH / (k_H * SH), 2.0)
                       + RT * (dC / (k_C * SC)) * (dH / (k_H * SH)));
    }
}
