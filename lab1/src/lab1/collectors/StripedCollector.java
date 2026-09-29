package lab1.collectors;

import java.util.concurrent.atomic.AtomicLong;

public final class StripedCollector implements MetricsCollector {
    private static final int STRIPES = 16;

    private final Object[] locks = new Object[STRIPES];
    private final long[] buckets = new long[256];
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong sum = new AtomicLong();
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong();

    public StripedCollector() {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    @Override
    public void record(long value) {
        int bucket = (int) Math.min(value / 4, 255);
        synchronized (locks[bucket % STRIPES]) {
            buckets[bucket]++;
        }

        count.incrementAndGet();
        sum.addAndGet(value);

        long currentMin = min.get();
        while (value < currentMin && !min.compareAndSet(currentMin, value)) {
            currentMin = min.get();
        }

        long currentMax = max.get();
        while (value > currentMax && !max.compareAndSet(currentMax, value)) {
            currentMax = max.get();
        }
    }

    @Override
    public Snapshot snapshot() {
        long[] copy = new long[256];
        for (int stripe = 0; stripe < STRIPES; stripe++) {
            synchronized (locks[stripe]) {
                for (int bucket = stripe; bucket < buckets.length; bucket += STRIPES) {
                    copy[bucket] = buckets[bucket];
                }
            }
        }
        // Each field is safe to read, but the snapshot is not a single consistent view.
        return SnapshotUtils.create(copy, count.get(), sum.get(), min.get(), max.get());
    }
}
