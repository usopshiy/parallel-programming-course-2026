package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.get(0);
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String trace;
        private int position;

        public Replay(String trace) {
            this.trace = Objects.requireNonNull(trace);
        }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() { position = 0; }

        public int pick(List<Integer> enabled, int last) {
            if (position >= trace.length()) {
                throw new IllegalArgumentException("Replay trace ended before decision " + (position + 1));
            }
            char thread = trace.charAt(position);
            int id = thread - 'A';
            if (!enabled.contains(id)) {
                throw new IllegalArgumentException("Replay thread " + thread
                        + " is not enabled at decision " + (position + 1));
            }
            position++;
            return id;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final Random rnd;

        public RandomPick(long seed) {
            rnd = new Random(seed);
        }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(rnd.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private static final class Choice {
            final List<Integer> options;
            int selected;

            Choice(List<Integer> options) {
                this.options = List.copyOf(options);
            }
        }

        private final List<Choice> path = new ArrayList<>();
        private final int bound;
        private int position;
        private int preemptions;

        public Dfs(int bound) {
            if (bound < 0) throw new IllegalArgumentException("Preemption bound must be non-negative");
            this.bound = bound;
        }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() {
            position = 0;
            preemptions = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            boolean canContinue = enabled.contains(last);
            // Replay the saved prefix, then take the first option at each new branch.
            if (position == path.size()) {
                List<Integer> options = canContinue && preemptions == bound
                        ? List.of(last) : enabled;
                path.add(new Choice(options));
            }
            Choice choice = path.get(position++);
            int next = choice.options.get(choice.selected);
            // Switching after a block or completion does not count as preemption.
            if (canContinue && next != last) preemptions++;
            return next;
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            // A deadlock can end a run before the previous path's depth.
            path.subList(position, path.size()).clear();
            while (!path.isEmpty()) {
                Choice choice = path.get(path.size() - 1);
                if (choice.selected + 1 < choice.options.size()) {
                    choice.selected++;
                    return true;
                }
                path.remove(path.size() - 1);
            }
            return false;
        }
    }
}
