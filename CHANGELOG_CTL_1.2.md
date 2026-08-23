Calm The Leaks 1.2

New Features

In-Game Diagnostics GUI
- The previous release had no in-game GUI for CTL. 1.2 adds the full diagnostics experience: open with F8 or /ctl panel, guide and tracked-leak views, per-leak detail screens, and panel access to optional world memory and the narrow-down toolkit.

Narrow-Down Toolkit (Modpack Troubleshooting)
- Commands under /ctl narrow plus a GUI for snapshot/compare workflows while you change the modpack (restart between tests).
- Primary store file is config/calmtheleaks_narrow.json; older installs may migrate from a legacy on-disk name automatically.
- User-facing and internal naming no longer uses the word bisect except in the BisectHosting disclaimer line.

World Leak Memory (Optional)
- Ops enable or disable saving leak history in the world from the CTL panel; nothing is written under the save until they turn it on.
- Config option allow_world_leak_memory defaults true so the panel can offer the feature; set false only to remove that option on a server.

Networking
- Panel data uses protocol id ctl-8; run the same CTL jar on dedicated server and clients so the panel stays in sync.

Compatibility
- Minecraft: 1.20.1
- Forge: 47.4.x (as declared in the mod metadata)
- AllTheLeaks: required companion mod
- Spark: optional
- Output jar for this release: calmtheleaks-1.2.jar (mod_version in gradle.properties)
