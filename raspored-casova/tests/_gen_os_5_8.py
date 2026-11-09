# Rebuild 5-8 samples: one subject per teacher, paired class lines.
from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path

DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]

CURRICULUM = {
    5: [
        ("Srpski", 5, 1),
        ("Matematika", 4, 1),
        ("Engleski", 2, 1),
        ("Nemački", 2, 1),
        ("Istorija", 1, 1),
        ("Geografija", 1, 1),
        ("Biologija", 2, 1),
        ("Informatika", 1, 1),
        ("Fizičko", 3, 1),
        ("Likovno", 2, 2),
        ("Muzičko", 2, 1),
        ("Tehničko", 2, 2),
        ("Veronauka", 1, 1),
    ],
    6: [
        ("Srpski", 4, 1),
        ("Matematika", 4, 1),
        ("Engleski", 2, 1),
        ("Nemački", 2, 1),
        ("Istorija", 2, 1),
        ("Geografija", 2, 1),
        ("Biologija", 2, 1),
        ("Fizika", 2, 1),
        ("Informatika", 1, 1),
        ("Fizičko", 3, 1),
        ("Likovno", 1, 1),
        ("Muzičko", 1, 1),
        ("Tehničko", 2, 2),
        ("Veronauka", 1, 1),
    ],
    7: [
        ("Srpski", 4, 1),
        ("Matematika", 4, 1),
        ("Engleski", 2, 1),
        ("Nemački", 2, 1),
        ("Istorija", 2, 1),
        ("Geografija", 2, 1),
        ("Biologija", 2, 1),
        ("Fizika", 2, 1),
        ("Hemija", 2, 1),
        ("Informatika", 1, 1),
        ("Fizičko", 3, 1),
        ("Likovno", 1, 1),
        ("Muzičko", 1, 1),
        ("Tehničko", 2, 2),
        ("Veronauka", 1, 1),
    ],
    8: [
        ("Srpski", 4, 1),
        ("Matematika", 4, 1),
        ("Engleski", 2, 1),
        ("Nemački", 2, 1),
        ("Istorija", 2, 1),
        ("Geografija", 2, 1),
        ("Biologija", 2, 1),
        ("Fizika", 2, 1),
        ("Hemija", 2, 1),
        ("Informatika", 1, 1),
        ("Fizičko", 3, 1),
        ("Likovno", 1, 1),
        ("Muzičko", 1, 1),
        ("Tehničko", 2, 2),
        ("Veronauka", 1, 1),
    ],
}

NAMES = {
    "Srpski": ["Ana Petrović", "Marko Ilić", "Marija Kovačević", "Jelena Nikolić", "Nikola Janković"],
    "Matematika": ["Stefan Popović", "Nataša Pavlović", "Ivana Jovanović", "Miloš Avramović", "Branko Simić"],
    "Engleski": ["Milica Đorđević", "Nikola Kostić"],
    "Nemački": ["Jovana Savić", "Milan Obradović"],
    "Biologija": ["Ivana Šević", "Petar Lukić"],
    "Fizičko": ["Ana Ristić", "Dragana Mitić", "Aleksandar Đukić"],
    "Informatika": ["Vladimir Todorović"],
    "Veronauka": ["Boško Pavlović"],
    "Tehničko": ["Mina Stojanović", "Tamara Vučić"],
    "Hemija": ["Lazar Minić"],
    "Fizika": ["Sofija Anđelković", "Luka Radović"],
    "Likovno": ["Vesna Simić", "Igor Marković"],
    "Istorija": ["Nenad Ćirić", "Dejan Savić"],
    "Muzičko": ["Sanja Vuković", "Goran Stanković"],
    "Geografija": ["Maja Radosavljević", "Dušan Jelić"],
}

SUBJECT_ORDER = [
    "Srpski",
    "Matematika",
    "Engleski",
    "Nemački",
    "Fizičko",
    "Likovno",
    "Muzičko",
    "Istorija",
    "Geografija",
    "Biologija",
    "Fizika",
    "Hemija",
    "Informatika",
    "Tehničko",
    "Veronauka",
]


def shift_of(section: str) -> str:
    grade = int(section.split("-")[0])
    return "prepodne" if grade in (5, 7) else "popodne"


def make_classes(counts: dict[int, int]) -> list[str]:
    classes = []
    for grade in (5, 6, 7, 8):
        for index in range(1, counts[grade] + 1):
            classes.append(f"{grade}-{index}")
    return classes


def jobs_for(classes: list[str], subject: str) -> list[dict]:
    jobs = []
    for section in classes:
        grade = int(section.split("-")[0])
        for name, weekly, max_per_day in CURRICULUM[grade]:
            if name == subject:
                jobs.append(
                    {
                        "class": section,
                        "subject": subject,
                        "per_week": weekly,
                        "max_per_day": max_per_day,
                    }
                )
    return jobs


def suffix(section: str) -> str:
    return section.split("-", 1)[1]


def hours_of(chunk: list[dict]) -> int:
    return sum(job["per_week"] for job in chunk)


def morning_hours(section: str, weekly: int) -> int:
    return weekly if shift_of(section) == "prepodne" else 0


def pack_subject(jobs: list[dict], subject: str) -> list[list[dict]]:
    if not jobs:
        return []
    by_line: dict[str, list[dict]] = defaultdict(list)
    for job in jobs:
        by_line[suffix(job["class"])].append(job)
    lines = [sorted(by_line[tag], key=lambda job: job["class"]) for tag in sorted(by_line)]

    # One teacher per ~16h+ class line. Shorter lines share a teacher up to 24h.
    # Do not glue two Serbian/Math lines: 17+17 exceeds 24 and 3-way splits
    # wreck shift balance (14/8) and flood Ishod with pause warnings.
    line_hours = [hours_of(line) for line in lines]
    total = sum(line_hours)
    heavy = sum(1 for weekly in line_hours if weekly >= 14)
    bin_count = max(1, (total + 23) // 24, heavy)
    # Split school-wide 1h subjects so one person is not in all 16 classes.
    if bin_count == 1 and len(jobs) > 10 and total >= 20:
        bin_count = min(2, len(lines))

    teachers: list[list[dict]] = [[] for _ in range(bin_count)]
    loads = [0] * bin_count
    ordered = sorted(lines, key=lambda line: -hours_of(line))
    for line in ordered:
        weekly = hours_of(line)
        best = None
        best_key = None
        for index, load in enumerate(loads):
            if load + weekly > 24:
                continue
            morn = sum(
                morning_hours(job["class"], job["per_week"]) for job in teachers[index] + line
            )
            after = load + weekly - morn
            key = (load, abs(morn - after), index)
            if best_key is None or key < best_key:
                best_key = key
                best = index
        if best is None:
            teachers.append(list(line))
            loads.append(weekly)
        else:
            teachers[best].extend(line)
            loads[best] += weekly
    return [group for group in teachers if group]


def build_school(counts: dict[int, int], comment: str) -> dict:
    classes = make_classes(counts)
    teachers = []
    used_names: set[str] = set()
    for subject in SUBJECT_ORDER:
        bins = pack_subject(jobs_for(classes, subject), subject)
        pool = [name for name in NAMES.get(subject, []) if name not in used_names]
        extra = 1
        for index, bin_jobs in enumerate(bins):
            if index < len(pool):
                name = pool[index]
            else:
                name = f"Nastavnik {subject} {extra}"
                extra += 1
            used_names.add(name)
            teachers.append(
                {
                    "name": name,
                    "available_days": list(DAYS),
                    "assignments": sorted(bin_jobs, key=lambda job: job["class"]),
                }
            )
    return {
        "comment": comment,
        "school_type": "5-8",
        "periods_per_day": 7,
        "shift_rotation": True,
        "classes": classes,
        "class_shifts": {section: shift_of(section) for section in classes},
        "teachers": teachers,
        "locked_lessons": [],
    }


def teacher_hours(teacher: dict) -> tuple[int, int, int]:
    morn = sum(
        item["per_week"]
        for item in teacher["assignments"]
        if int(item["class"].split("-")[0]) in (5, 7)
    )
    total = sum(item["per_week"] for item in teacher["assignments"])
    return total, morn, total - morn


def summarize(data: dict) -> str:
    mixed = 0
    diffs = []
    by_subject: dict[str, list[int]] = defaultdict(list)
    for teacher in data["teachers"]:
        subjects = {item["subject"] for item in teacher["assignments"]}
        if len(subjects) > 1:
            mixed += 1
        total, morn, after = teacher_hours(teacher)
        subject = next(iter(subjects))
        diffs.append((teacher["name"], subject, morn, after, abs(morn - after), total))
        by_subject[subject].append(total)
    hours = [row[5] for row in diffs]
    worst = max(diffs, key=lambda row: row[4])
    lines = [
        f"classes={len(data['classes'])} teachers={len(data['teachers'])} mixed={mixed} "
        f"min={min(hours)} max={max(hours)} avg={sum(hours) / len(hours):.1f} "
        f"worst_diff={worst[4]} ({worst[0]} {worst[1]} {worst[2]}/{worst[3]})"
    ]
    for subject in SUBJECT_ORDER:
        loads = by_subject.get(subject)
        if not loads:
            continue
        lines.append(
            f"  {subject}: n={len(loads)} hours={sorted(loads, reverse=True)} "
            f"avg={sum(loads) / len(loads):.1f}"
        )
    return "\n".join(lines)


def main() -> None:
    root = Path(__file__).resolve().parent
    samples = [
        (
            "os_5_8_16_odeljenja.json",
            {5: 4, 6: 4, 7: 4, 8: 4},
            "5–8, 16 odeljenja (4+4+4+4). Školski dan 5–7 časova od 1. časa. "
            "Tehničko je spojeni blok; Likovno u 5. razredu je spojeni blok. "
            "Svaki nastavnik predaje samo jedan predmet, po liniji odeljenja (5-k/6-k/7-k/8-k). "
            "Smene se rotiraju.",
        ),
    ]
    for filename, counts, comment in samples:
        data = build_school(counts, comment)
        (root / filename).write_text(
            json.dumps(data, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(filename)
        print(summarize(data).encode("ascii", "replace").decode("ascii"))


if __name__ == "__main__":
    main()
