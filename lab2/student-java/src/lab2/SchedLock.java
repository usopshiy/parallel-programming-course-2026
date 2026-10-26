package lab2;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    final String name;
    int owner = -1;
    private final Scheduler scheduler;

    /** Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s. */
    public SchedLock(Scheduler s, String name) {
        this.name = name;
        scheduler = s;
        s.registerLock(this);
    }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем. */
    public void lock() {
        int id = scheduler.currentThreadId();
        Scheduler.beforeAccess(() -> owner == -1);
        owner = id;
        Scheduler.logOperation("lock " + name);
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны. */
    public void unlock() {
        // A finally block may release a lock while an aborted worker unwinds its stack.
        if (scheduler.isAborted()) return;
        if (owner != scheduler.currentThreadId()) {
            throw new IllegalMonitorStateException("Current thread does not own lock " + name);
        }
        Scheduler.beforeAccess();
        owner = -1;
        Scheduler.logOperation("unlock " + name);
    }
}
