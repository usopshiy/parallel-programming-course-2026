package lab1.collectors;

public final class SnapshotUtils {
    private SnapshotUtils() {}

    public static Snapshot create(long[] buckets, long count, long sum, long min, long max) {
        long[] copy = buckets.clone();
        long p50 = percentile(copy, count, 0.50);
        long p99 = percentile(copy, count, 0.99);
        return new Snapshot(copy, count, sum, min, max, p50, p99);
    }

    private static long percentile(long[] buckets, long count, double fraction) {
        double threshold = count * fraction;
        long cumulative = 0;
        for (int i = 0; i < buckets.length; i++) {
            cumulative += buckets[i];
            if (cumulative >= threshold) {
                return i * 4L;
            }
        }
        return (buckets.length - 1) * 4L;
    }
}
