package lab1.collectors;

public interface MetricsCollector {
    /** Records a nonnegative latency in milliseconds. */
    void record(long value);

    Snapshot snapshot();
}
