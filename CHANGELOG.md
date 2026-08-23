# Changelog

## 1.2.4

Update by: Extra_Special_K

Requires **ExtraSpecialCore (ESC) 2.0.0+** and **ExtraSpecialHub (ESH) 1.0.0+** (ES Library ships with ESC).

Added:

- **ES Hub entry** — open diagnostics from **F9 → UTILITY → Calm The Leaks** (`/ctl panel` still works).

Changed:

- **F8 hotkey removed** — CTL lives under Extra Special Hub only.
- Requires **ESC 2.0.x** dependency range (`[2.0.0,3.0)`).

---

## 1.2.3

- **ES Hub** — open diagnostics from F9 → UTILITY → Calm The Leaks (F8 removed; `/ctl panel` still works).
- Depends on ES Library + ES Hub.
- **ESS domain swap** — Java packages moved from `uk.creatopia.unbound.calml_the_leaks_ctl…` to `uk.co.extraspecialstudio.calml_the_leaks_ctl…`.
- Requires **ExtraSpecialCore 1.3.0+** (ESC domain / UI packages under `uk.co.extraspecialstudio`).

## 1.2.2

- Completed ESC anchor layout on the F8 diagnostics panel, leak detail screen, and narrow-down toolkit — tabs, scroll regions, and footers resize correctly at any GUI scale.
- Guide and tracked-leak tab bodies use ESC title bar / footer reserves instead of hard-coded pixel bands.

## 1.2.1

- Integrated ExtraSpecialCore as a required dependency for shared GUI scaffolding.
- Diagnostics panel wiring uses ESC panel/button helpers.

## 1.2

- In-game diagnostics GUI (F8 / `/ctl panel`), narrow-down toolkit, optional world leak memory, panel protocol `ctl-8`.
