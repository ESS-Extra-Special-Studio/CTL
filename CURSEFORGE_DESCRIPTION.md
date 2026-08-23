Calm The Leaks

Companion mod for AllTheLeaks: it reads ATL’s leak output, adds context, and makes reports easier to understand and act on. ATL still does detection; CTL sits on top with judgement and explanation.

What It Does:

CTL intercepts ATL’s leak reports and evaluates them with context. It suppresses a lot of false alarms (stable chunk retention from claiming mods, startup noise, transitional false positives). Real issues still show up, with clearer notes on what might be going on and what to try next.

It also looks at your modpack in real time to guess which mods are involved, builds confidence over time, and only nudges toward fixes when the picture is reasonably clear. If Spark is installed, CTL can trigger profiling when a leak looks severe (configurable). Heap percentages you see in CTL come from the JVM.

How It Works:

CTL tracks what ATL reports but does not treat every line as an emergency. It watches patterns—is the count growing or stable? Does it line up with specific mods? It respects server phase (startup, warmup, live) so early noise is not treated like a live-world crisis, and it escalates when the pattern actually looks wrong.

When something looks like a real leak, it weighs mod conflicts, memory pressure, and trends so recommendations get sharper the longer it observes.

How To Use It:

Install alongside AllTheLeaks; CTL hooks ATL’s output automatically.

Diagnostics GUI: press F8 or run /ctl panel. Use Guide & how to use for help and a server snapshot; use Tracked leaks to scroll leaks and click a row for the full diagnosis. Ops can optionally turn on world leak memory from the guide tab, or open the narrow-down toolkit for snapshot/compare while you change the pack (restart between tests; see /ctl narrow).

By default /ctl status, /ctl leaks, and /ctl explain open that GUI instead of filling chat—set prefer_diagnostics_gui in config/calmtheleaks-common.toml if you want the old chat-first behaviour.

Commands:

/ctl panel — open the GUI
/ctl status, /ctl leaks, /ctl explain — status, list, and per-leak diagnosis (GUI by default)
/ctl narrow — narrow-down toolkit (snapshot, compare, mod list, etc.)

Everything else (thresholds, grace period, Spark, GUI vs chat) lives in config/calmtheleaks-common.toml.

Requires AllTheLeaks. If you use the panel, run the same CTL build on server and clients so the panel packets stay in sync.
