"""Generate the throughput chart from saved CSV files. Requires matplotlib."""

import csv
from pathlib import Path
from statistics import median

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt


results = Path(__file__).resolve().parent.parent / "results"
fig, ax = plt.subplots(figsize=(9, 5.5))

for filename, label, color in [
    ("throughput.csv", "Этап 0: baseline", "#64748b"),
    ("mutex.csv", "Этап 1: общий лок", "#2563eb"),
    ("empty-lock.csv", "Этап 1: пустой лок", "#ea580c"),
    ("striped.csv", "Этап 2: 16 локов", "#16a34a"),
    ("thread-local.csv", "Этап 3: Thread-Local", "#9333ea"),
    ("double-buffer.csv", "Этап 4: двойной буфер", "#dc2626"),
]:
    measurements = {}
    with (results / filename).open() as source:
        for row in csv.DictReader(source):
            threads = int(row["threads"])
            speed = float(row["ops_per_second"])
            measurements.setdefault(threads, []).append(speed)

    threads = sorted(measurements)
    rates = [median(measurements[count]) / 1_000_000 for count in threads]
    ax.plot(threads, rates, marker="o", label=label, color=color, linewidth=2)

ax.set_title(
    "Коллектор метрик: этапы 0–4\n"
    "Apple M2 · Java 21 · медиана пяти замеров по 5 секунд"
)
ax.set_xlabel("Число потоков")
ax.set_ylabel("Производительность, млн оп/с (лог. шкала)")
ax.set_xticks([1, 2, 4, 8])
ax.set_yscale("log")
ax.set_yticks([10, 20, 50, 100, 200, 500, 1000], labels=["10", "20", "50", "100", "200", "500", "1000"])
ax.set_ylim(10, 1200)
ax.grid(True, which="major", alpha=0.25)
ax.legend()

fig.tight_layout()
fig.savefig(results / "throughput.png", dpi=180)
plt.close(fig)
