package lab1.benchmark;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;

import lab1.collectors.MetricsCollector;
import lab1.collectors.Snapshot;

public final class ConsistencyTest {
    private static final int SNAPSHOTS = 10_000;

    private static final class StopFlag {
        volatile boolean stopped;
    }

    private ConsistencyTest() {}

    public static void run(MetricsCollector collector, long[] values, int threadCount)
            throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(threadCount);
        StopFlag stop = new StopFlag();
        long[] operations = new long[threadCount];
        Thread[] workers = new Thread[threadCount];

        for (int k = 0; k < threadCount; k++) {
            final int workerIndex = k;
            workers[k] = new Thread(() -> {
                int valueIndex = (int) ((workerIndex * 1000L) % values.length);
                collector.record(values[valueIndex]);
                long localCount = 1;
                // Start reading snapshots after every writer has recorded a value.
                ready.countDown();

                while (!stop.stopped) {
                    valueIndex++;
                    if (valueIndex == values.length) {
                        valueIndex = 0;
                    }
                    collector.record(values[valueIndex]);
                    localCount++;
                }
                operations[workerIndex] = localCount;
            });
            workers[k].start();
        }

        int less = 0;
        int greater = 0;
        try {
            ready.await();
            for (int i = 0; i < SNAPSHOTS; i++) {
                Snapshot snapshot = collector.snapshot();
                long bucketCount = 0;
                for (long bucket : snapshot.buckets()) {
                    bucketCount += bucket;
                }
                if (bucketCount < snapshot.count()) {
                    less++;
                } else if (bucketCount > snapshot.count()) {
                    greater++;
                }
            }
        } finally {
            stop.stopped = true;
            for (Thread worker : workers) {
                worker.join();
            }
        }

        long totalOperations = 0;
        for (long count : operations) {
            totalOperations += count;
        }
        long finalCount = collector.snapshot().count();
        double inconsistentPercent = (less + greater) * 100.0 / SNAPSHOTS;

        System.out.println("threads,snapshots,inconsistent_percent,less,greater,operations,final_count,count_difference");
        System.out.printf(Locale.ROOT, "%d,%d,%.2f,%d,%d,%d,%d,%d%n",
                threadCount, SNAPSHOTS, inconsistentPercent, less, greater,
                totalOperations, finalCount, finalCount - totalOperations);
    }
}
