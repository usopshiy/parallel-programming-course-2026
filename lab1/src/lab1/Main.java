package lab1;

import java.util.Locale;

import lab1.benchmark.Benchmark;
import lab1.benchmark.ConsistencyTest;
import lab1.benchmark.ZipfGenerator;
import lab1.collectors.BaselineCollector;
import lab1.collectors.EmptyLockCollector;
import lab1.collectors.MetricsCollector;
import lab1.collectors.MutexCollector;
import lab1.collectors.StripedCollector;
import lab1.collectors.ThreadLocalCollector;

public final class Main {
    public static void main(String[] args) throws InterruptedException {
        String name = args.length > 0 ? args[0] : "baseline";
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        if (threads < 1 || threads > Runtime.getRuntime().availableProcessors()) {
            throw new IllegalArgumentException("Thread count must be between 1 and the number of available processors");
        }
        if (name.equals("baseline") && threads != 1) {
            throw new IllegalArgumentException("Baseline requires one writer thread");
        }

        MetricsCollector collector = switch (name) {
            case "baseline" -> new BaselineCollector();
            case "mutex" -> new MutexCollector();
            case "empty-lock" -> new EmptyLockCollector();
            case "striped" -> new StripedCollector();
            case "thread-local" -> new ThreadLocalCollector();
            default -> throw new IllegalArgumentException("Unknown collector: " + name);
        };

        long[] values = ZipfGenerator.generate();
        if (args.length > 2 && args[2].equals("stress")) {
            ConsistencyTest.run(collector, values, threads);
            return;
        }
        double median = Benchmark.measure(collector, values, threads);

        System.err.printf(Locale.ROOT, "Median: %.3f million ops/sec%n", median / 1_000_000);
    }
}
