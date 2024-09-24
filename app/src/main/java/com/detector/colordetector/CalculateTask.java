package com.detector.colordetector;

public class CalculateTask implements Runnable {
    private ColorCalculator colorCalculator;

    public CalculateTask(ColorCalculator colorCalculator) {
        this.colorCalculator = colorCalculator;
    }

    @Override
    public void run() {
        colorCalculator.computeNewName();
    }
}
