package lab1.collectors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class DoubleBufferCollector implements MetricsCollector {
    private static final int NOWHERE = -1;

    private static final class ThreadBuffers {
        final long[][] buckets = new long[2][256];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = new long[2];
        final AtomicInteger inside = new AtomicInteger(NOWHERE);
    }

    private final boolean checkActive;
    private volatile int active;
    private final Object snapLock = new Object();
    private final List<ThreadBuffers> allStates = new ArrayList<>();

    private final long[] globalBuckets = new long[256];
    private long globalCount;
    private long globalSum;
    private long globalMin = Long.MAX_VALUE;
    private long globalMax;

    private final ThreadLocal<ThreadBuffers> myState = ThreadLocal.withInitial(() -> {
        ThreadBuffers state = new ThreadBuffers();
        synchronized (snapLock) {
            allStates.add(state);
        }
        return state;
    });

    public DoubleBufferCollector() {
        this(true);
    }

    // Disable the check only for the experiment without step 3.
    public DoubleBufferCollector(boolean checkActive) {
        this.checkActive = checkActive;
    }

    @Override
    public void record(long value) {
        ThreadBuffers state = myState.get();
        int buffer;
        while (true) {
            buffer = active;
            state.inside.set(buffer);
            if (!checkActive || active == buffer) {
                break;
            }
            state.inside.setRelease(NOWHERE);
        }

        int bucket = (int) Math.min(value / 4, 255);
        state.buckets[buffer][bucket]++;
        state.count[buffer]++;
        state.sum[buffer] += value;
        state.min[buffer] = Math.min(state.min[buffer], value);
        state.max[buffer] = Math.max(state.max[buffer], value);

        state.inside.setRelease(NOWHERE);
    }

    @Override
    public Snapshot snapshot() {
        synchronized (snapLock) {
            int old = active;
            active = 1 - old;

            for (ThreadBuffers state : allStates) {
                while (state.inside.get() == old) {
                    Thread.onSpinWait();
                }

                for (int i = 0; i < globalBuckets.length; i++) {
                    globalBuckets[i] += state.buckets[old][i];
                }
                globalCount += state.count[old];
                globalSum += state.sum[old];
                globalMin = Math.min(globalMin, state.min[old]);
                globalMax = Math.max(globalMax, state.max[old]);

                Arrays.fill(state.buckets[old], 0);
                state.count[old] = 0;
                state.sum[old] = 0;
                state.min[old] = Long.MAX_VALUE;
                state.max[old] = 0;
            }

            return SnapshotUtils.create(globalBuckets, globalCount, globalSum, globalMin, globalMax);
        }
    }
}
