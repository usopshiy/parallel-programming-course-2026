package lab1.collectors;

/** Single writer, no synchronization. Take snapshots after writing finishes. */
public final class BaselineCollector implements MetricsCollector {
    private final long[] buckets = new long[256];
    private long count;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max;

    @Override
    public void record(long value) {
        int bucket = (int) Math.min(value / 4, 255);
        buckets[bucket]++;

        count++;
        sum += value;
        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    @Override
    public Snapshot snapshot() {
        return SnapshotUtils.create(buckets, count, sum, min, max);
    }
}
