import json
from pathlib import Path

DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]
path = Path("tests/os_1_4_18_odeljenja.json")
data = json.loads(path.read_text(encoding="utf-8"))

def teacher(name, assignments):
    return {
        "name": name,
        "available_days": list(DAYS),
        "assignments": assignments,
    }

kept = []
english = []
for item in data["teachers"]:
    subjects = {a["subject"] for a in item["assignments"]}
    if subjects == {"Engleski"}:
        english.extend(item["assignments"])
    else:
        kept.append(item)

morning = [a for a in english if a["class"].split("-")[0] in ("1", "3")]
afternoon = [a for a in english if a["class"].split("-")[0] in ("2", "4")]
morning.sort(key=lambda a: a["class"])
afternoon.sort(key=lambda a: a["class"])

def split(rows, left_name, right_name):
    mid = (len(rows) + 1) // 2
    return teacher(left_name, rows[:mid]), teacher(right_name, rows[mid:])

eng_teachers = [
    *split(morning, "Ivana Jovanović", "Jelena Marković"),
    *split(afternoon, "Milica Đorđević", "Ana Kovačević"),
]

# Keep classroom + religion, replace english block.
religion = [item for item in kept if {a["subject"] for a in item["assignments"]} == {"Veronauka"}]
classroom = [item for item in kept if {a["subject"] for a in item["assignments"]} != {"Veronauka"}]
data["teachers"] = classroom + eng_teachers + religion
data["comment"] = (
    "Osnovna 1–4, 18 odeljenja (5+4+4+5). Razredna nastava: školski dan 4–5 časova od 1. časa. "
    "Učiteljica drži svoje odeljenje od 1. časa, bez pauze. Engleski i veronauka su na kraju dana. "
    "1. i 3. prepodne, 2. i 4. popodne, smene se rotiraju."
)
path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("teachers", len(data["teachers"]))
for item in data["teachers"]:
    hours = sum(a["per_week"] for a in item["assignments"])
    subj = next(iter({a["subject"] for a in item["assignments"]}))
    print(f"{hours:2d} {item['name']} {subj} n={len(item['assignments'])}")
