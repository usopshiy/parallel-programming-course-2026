package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;

    private static final ThreadLocal<Worker> CURRENT = new ThreadLocal<>();
    private final List<Worker> workers = new ArrayList<>();
    private final List<SchedLock> locks = new ArrayList<>();
    private final Semaphore returned = new Semaphore(0);
    private final StringBuilder trace = new StringBuilder();
    private boolean started;
    private boolean aborted;

    private static final class RunAborted extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private final class Worker {
        final int id;
        final Semaphore turn = new Semaphore(0);
        final Thread thread;
        // The first shared operation must hand control to the scheduler too.
        boolean touched = true;
        boolean finished;
        BooleanSupplier waiting;
        Throwable failure;

        Worker(int id, Runnable body) {
            this.id = id;
            thread = new Thread(() -> {
                CURRENT.set(this);
                try {
                    body.run();
                } catch (RunAborted ignored) {
                    // The controller is stopping a suspended run.
                } catch (RuntimeException | Error e) {
                    failure = e;
                } finally {
                    finished = true;
                    CURRENT.remove();
                    returned.release();
                }
            }, "sched-" + (char) ('A' + id));
        }

        void pause() {
            touched = false;
            // Each handoff publishes all ordinary fields to the other side.
            returned.release();
            turn.acquireUninterruptibly();
            if (aborted) throw new RunAborted();
        }

        void record(String operation) {
            if (logging && !aborted) log.add((char) ('A' + id) + "  " + operation);
        }

        void rethrowFailure() {
            if (failure instanceof RuntimeException e) throw e;
            if (failure instanceof Error e) throw e;
        }
    }

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        if (started) throw new IllegalStateException("Cannot spawn after run has started");
        workers.add(new Worker(workers.size(), Objects.requireNonNull(body)));
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {
        Objects.requireNonNull(strategy);
        if (started) throw new IllegalStateException("Scheduler can only run once");
        started = true;
        int launched = 0;
        try {
            // Reach the first pause in registration order, without concurrent setup.
            for (Worker worker : workers) {
                worker.thread.start();
                launched++;
                returned.acquireUninterruptibly();
                worker.rethrowFailure();
            }
            int last = -1;
            while (true) {
                List<Integer> enabled = new ArrayList<>();
                boolean alive = false;
                for (Worker worker : workers) {
                    if (worker.finished) continue;
                    alive = true;
                    if (worker.waiting == null || worker.waiting.getAsBoolean()) {
                        enabled.add(worker.id);
                    }
                }
                if (!alive) break;
                if (enabled.isEmpty()) {
                    StringJoiner held = new StringJoiner(", ", "held={", "}");
                    for (SchedLock lock : locks) {
                        if (lock.owner >= 0) held.add(lock.name + ":" + (char) ('A' + lock.owner));
                    }
                    deadlock = held.toString();
                    break;
                }
                int next = strategy.pick(enabled, last);
                if (!enabled.contains(next)) {
                    throw new IllegalArgumentException("Strategy selected a disabled thread: " + next);
                }
                trace.append((char) ('A' + next));
                last = next;
                Worker worker = workers.get(next);
                worker.turn.release();
                returned.acquireUninterruptibly();
                worker.rethrowFailure();
            }
        } finally {
            aborted = true;
            // Stop one suspended worker at a time, including on strategy/body errors.
            boolean interrupted = false;
            for (int i = 0; i < launched; i++) {
                Worker worker = workers.get(i);
                if (!worker.finished) {
                    worker.turn.release();
                    returned.acquireUninterruptibly();
                }
                while (true) {
                    try {
                        worker.thread.join();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return trace.toString();
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        Worker worker = current();
        worker.record("window");
        worker.touched = true;
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        Objects.requireNonNull(cond);
        beforeAccess(cond);
        logOperation("await done");
    }

    private static Worker current() {
        Worker worker = CURRENT.get();
        if (worker == null) throw new IllegalStateException("Operation outside a scheduler worker");
        return worker;
    }

    static void beforeAccess() {
        beforeAccess(null);
    }

    static void beforeAccess(BooleanSupplier ready) {
        Worker worker = current();
        // Keep the predicate until selection: a free lock may be taken by another worker.
        worker.waiting = ready;
        if (worker.touched || (ready != null && !ready.getAsBoolean())) worker.pause();
        worker.waiting = null;
        worker.touched = true;
    }

    void registerLock(SchedLock lock) {
        if (started) throw new IllegalStateException("Cannot register a lock after run has started");
        locks.add(lock);
    }

    int currentThreadId() {
        Worker worker = current();
        if (worker.id >= workers.size() || workers.get(worker.id) != worker) {
            throw new IllegalStateException("Lock belongs to a different scheduler");
        }
        return worker.id;
    }

    boolean isAborted() {
        return aborted;
    }

    static void logOperation(String operation) {
        current().record(operation);
    }
}
