package lab1;

import java.util.Locale;

import lab1.benchmark.Benchmark;
import lab1.benchmark.ZipfGenerator;
import lab1.collectors.BaselineCollector;

public final class Main {
    public static void main(String[] args) throws InterruptedException {
        long[] values = ZipfGenerator.generate();
        BaselineCollector collector = new BaselineCollector();
        double median = Benchmark.measure(collector, values, 1);

        System.err.printf(Locale.ROOT, "Median: %.3f million ops/sec%n", median / 1_000_000);
    }
}
