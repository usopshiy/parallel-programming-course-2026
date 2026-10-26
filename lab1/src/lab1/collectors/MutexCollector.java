package lab1.collectors;

public final class MutexCollector implements MetricsCollector {
    private final BaselineCollector collector = new BaselineCollector();

    @Override
    public synchronized void record(long value) {
        collector.record(value);
    }

    @Override
    public synchronized Snapshot snapshot() {
        return collector.snapshot();
    }
}
