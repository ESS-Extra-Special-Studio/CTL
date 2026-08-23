# Calm The Leaks — 1.2 Hotfix (Forge)

Small release for **Minecraft 1.20.1 / Forge**. Same gameplay as 1.2; fixes **dedicated server** compatibility.

## Fixes

- **Dedicated server compatibility:** Fixed packet handling so common/network classes no longer hard-reference client-only GUI classes on server startup.
- **Client panel open path:** Added a client-only panel opener (`S2CPanelClientHandler`) and routed S2C panel handling through a server-safe indirection so dedicated servers can load/register networking cleanly.

**File:** `calmtheleaks-1.2 Hotfix (Forge)-forge.jar` (version string matches `mod_version` in `gradle.properties`.)
