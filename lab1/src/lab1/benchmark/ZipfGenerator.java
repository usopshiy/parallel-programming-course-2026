package lab1.benchmark;

import java.util.Random;

public final class ZipfGenerator {
    public static final int SIZE = 1 << 20;
    public static final long SEED = 42;
    private static final int MAX_VALUE = 1023;
    private static final double EXPONENT = 1.15;

    private ZipfGenerator() {}

    public static long[] generate() {
        double[] probabilities = cumulativeProbabilities();
        Random random = new Random(SEED);
        long[] values = new long[SIZE];

        for (int i = 0; i < values.length; i++) {
            values[i] = findValue(probabilities, random.nextDouble());
        }
        return values;
    }

    private static double[] cumulativeProbabilities() {
        // Entry k - 1 stores the probability of a value at most k.
        double[] probabilities = new double[MAX_VALUE];
        double totalWeight = 0;

        for (int value = 1; value <= MAX_VALUE; value++) {
            totalWeight += 1.0 / StrictMath.pow(value, EXPONENT);
            probabilities[value - 1] = totalWeight;
        }
        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] /= totalWeight;
        }
        probabilities[MAX_VALUE - 1] = 1.0;
        return probabilities;
    }

    private static long findValue(double[] probabilities, double sample) {
        // Find the first cumulative probability greater than sample.
        int left = 0;
        int right = probabilities.length - 1;

        while (left < right) {
            int middle = (left + right) / 2;
            if (sample < probabilities[middle]) {
                right = middle;
            } else {
                left = middle + 1;
            }
        }
        return left + 1L;
    }
}
