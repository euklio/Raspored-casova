# Raspored časova / School Timetable Generator

Java desktop application that generates weekly timetables for primary schools,
secondary schools and faculties. Built as Jovan Marković's final-year project in 2026.
The user interface is in Serbian.

## Try the Windows demo

Open this repository's **Releases** section and download
**RasporedCasova-1.0.0-windows-x64.zip** from the release assets.

1. Extract the entire ZIP into a folder.
2. Open **RasporedCasova.exe** inside the extracted `RasporedCasova` folder.
3. Open the **JSON uvoz** tab.
4. For a small, populated example, click **Uvezi JSON fajl** and select the
   included `example.json` next to the executable.
5. Click **Napravi raspored**. Switch between class, teacher and whole-school views.

The Windows x64 demo includes Java and JavaFX. No separate Java installation,
account, API key or internet connection is needed to use the local demo.
Keep the `app` and `runtime` folders beside the executable.
The GitHub-generated **Source code (zip)** is source code, not the runnable demo.

## Features

- Manual entry and JSON import of classes, teachers, availability and teaching assignments.
- Scheduling rules for teacher conflicts, shifts, daily subject limits and block lessons.
- Backtracking CSP solver with MRV/LCV heuristics and forward checking.
- Local improvement using simulated annealing and a tabu list.
- User-selectable priorities for timetable quality.
- Class, teacher and whole-school views, with manually locked lessons.
- Background generation to keep the JavaFX interface responsive.
- Explanatory feedback for invalid input, search limits and unmet preferences.

## How it works

Teaching assignments are converted to lesson units and candidate time slots.
The solver searches for a timetable satisfying mandatory constraints, then tries
to improve preferences such as teacher gaps. It uses bit masks for occupancy checks.
An improved result is not a proof of global optimality. A search timeout also does
not prove that no feasible timetable exists.

## Build from source on Windows

Requirements: Windows x64, JDK 17 or later, and PowerShell. The `lib/` directory
contains the Windows JavaFX 17.0.15 libraries used by this project.

From this folder, run:

```powershell
.\compile.bat
java --module-path lib --add-modules javafx.controls,javafx.graphics -cp out rasporedcasova.App
```

To create the portable Windows package, run:

```powershell
.\build-demo.ps1
```

The ZIP and SHA-256 checksum are written to `dist/`. The build packages the source
code, not pre-existing `out/` files. It retains temporary build folders under
`.build/` for inspection. The package is platform-specific; macOS and Linux need
their own JavaFX libraries and builds.

## Project structure

- `src/rasporedcasova/model/`: teachers, lessons, assignments and school rules.
- `src/rasporedcasova/motor/`: input validation, scheduling state and solver.
- `src/rasporedcasova/json/`: JSON reader.
- `src/rasporedcasova/ui/`: JavaFX interface and styling.
- `tests/`: sample inputs and data-generation helpers; not a unit-test suite.
- `example.json`: small populated demonstration input.

## Limitations

Generation time depends on input size, constraints and the selected priority.
Large examples may take considerably longer than the small demo. The project is
an educational prototype, not a production school-management system.

## Author

Jovan Marković — [GitHub](https://github.com/euklio)

Third-party runtime components retain their own licenses; see
`THIRD-PARTY-NOTICES.md` and the bundled runtime's `legal/` directory.

