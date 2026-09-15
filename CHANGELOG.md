Calm The Leaks 1.2.5
Changed:
Extra Special Hub registration is optional — CTL no longer hard-crashes if the hub JAR is missing; /ctl panel still works solo.
Config comments regrouped for clearer threshold / prescription sections (keys unchanged).

# Changelog

## 1.2.4

- Requires **ExtraSpecialCore (ESC) 2.0.0** or newer (`[2.0.0,3.0)`), plus **ES Library (ESL) 1.0.0** and **ES Hub (ESH) 1.0.0**.
- **ES Hub** â€” open diagnostics from F9 â†’ UTILITY â†’ Calm The Leaks (F8 removed; `/ctl panel` still works).

## 1.2.3

- **ESS domain swap** â€” Java packages moved from `uk.creatopia.unbound.calml_the_leaks_ctlâ€¦` to `uk.co.extraspecialstudio.calml_the_leaks_ctlâ€¦`.
- Requires **ExtraSpecialCore 1.3.0+** (ESC domain / UI packages under `uk.co.extraspecialstudio`).
- NeoForge: F8 panel keybind listens on the game bus (opens diagnostics in-world); panel text colors use opaque ARGB so guide/detail text stays readable on 1.21.

## 1.2.2

- Completed ESC anchor layout on the F8 diagnostics panel, leak detail screen, and narrow-down toolkit â€” tabs, scroll regions, and footers resize correctly at any GUI scale.
- Dedicated server compatibility for panel networking; S2C panel channel registered on both sides.

## 1.2.1

- Integrated ExtraSpecialCore as a required dependency for shared GUI scaffolding.
- Diagnostics panel wiring uses ESC panel/button helpers.

## 1.2

- In-game diagnostics GUI (F8 / `/ctl panel`), narrow-down toolkit, optional world leak memory, panel protocol `ctl-8`.

