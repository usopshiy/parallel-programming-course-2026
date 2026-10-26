package lab1.benchmark;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

import lab1.collectors.MetricsCollector;

public final class Benchmark {
    private static final int RUN_SECONDS = 5;
    private static final int REPETITIONS = 5;

    private static final class StopFlag {
        volatile boolean stopped;
    }

    private Benchmark() {}

    public static double measure(MetricsCollector collector, long[] values, int threadCount)
            throws InterruptedException {
        System.err.println("Warmup: 5 seconds");
        run(collector, values, threadCount);

        double[] results = new double[REPETITIONS];
        System.out.println("threads,run,ops_per_second");
        for (int i = 0; i < results.length; i++) {
            System.err.printf("Measurement %d/%d: 5 seconds%n", i + 1, REPETITIONS);
            results[i] = run(collector, values, threadCount);
            System.out.printf(Locale.ROOT, "%d,%d,%.3f%n", threadCount, i + 1, results[i]);
        }

        System.err.println("Snapshot count: " + collector.snapshot().count());
        Arrays.sort(results);
        return results[results.length / 2];
    }

    private static double run(MetricsCollector collector, long[] values, int threadCount)
            throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        StopFlag stop = new StopFlag();
        long[] operations = new long[threadCount];
        Thread[] workers = new Thread[threadCount];

        for (int k = 0; k < threadCount; k++) {
            final int workerIndex = k;
            workers[k] = new Thread(() -> {
                long localCount = 0;
                int valueIndex = (int) ((workerIndex * 1000L) % values.length);

                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                while (!stop.stopped) {
                    collector.record(values[valueIndex]);
                    localCount++;
                    valueIndex++;
                    if (valueIndex == values.length) {
                        valueIndex = 0;
                    }
                }
                operations[workerIndex] = localCount;
            });
            workers[k].start();
        }

        long elapsedNanos;
        try {
            ready.await();
            long startNanos = System.nanoTime();
            start.countDown();
            Thread.sleep(RUN_SECONDS * 1000L);
            stop.stopped = true;
            elapsedNanos = System.nanoTime() - startNanos;
        } finally {
            stop.stopped = true;
            start.countDown();
            for (Thread worker : workers) {
                worker.join();
            }
        }

        long totalOperations = 0;
        for (long count : operations) {
            totalOperations += count;
        }
        return totalOperations * 1_000_000_000.0 / elapsedNanos;
    }
}
