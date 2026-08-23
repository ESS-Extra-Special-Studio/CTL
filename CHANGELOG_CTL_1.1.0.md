# Calm The Leaks 1.1.0

## Major Fixes

### Fixed Count Display Showing 0
- **Critical Fix**: Changed all display locations to use `state.currentCount` instead of `signature.count`
- The count now correctly shows the current leak count instead of the initial count from when the leak was first detected
- Fixed in `/ctl explain`, `/ctl status`, and all other display commands

### Fixed Confidence Percentage Not Showing
- Fixed confidence percentage not appearing in `/ctl explain` output
- The check was incorrectly matching the word "confidence" in uncertainty notes, causing the percentage to be skipped
- Now only skips if the uncertainty note actually contains a confidence percentage (e.g., "Confidence: 50%")

### Fixed Duplicate Text in Explain Output
- Removed duplicate "Related mods" list that appeared both in the conflict warning and at the bottom
- Removed duplicate confidence percentage display
- Cleaned up output formatting for better readability

### Fixed Baseline Leaks Showing in Explain
- Added filtering to `/ctl explain` to only show leaks with `SUSPICIOUS_TREND` or `CONFIRMED_LEAK` verdicts
- Baseline retention and transitional noise leaks are now properly filtered out
- Users will only see leaks that actually need attention

### Fixed Verdict Engine Using Stale Count Data
- Updated `VerdictEngine.evaluate()` to use `state.currentCount` instead of `signature.count` throughout
- Verdicts now use the current leak count, ensuring accurate evaluation
- Prevents incorrect verdicts when leak counts change over time

## New Features

### Death Detangler Integration
- Added `DeathDetanglerIntegration` class for communication with Death Detangler mod
- CTL now tracks when Death Detangler is active and handling player clone cleanups
- Adjusts leak analysis accordingly - doesn't blame Death Detangler if it's actively managing leaks
- Shows Death Detangler's cleanup count in leak reports

### Confidence Hints System
- Added `confidenceHints` field to `LeakAnalysis` to provide explicit suggestions for increasing confidence
- Displays hints like "Run /atl force_refresh X more time(s) to increase confidence"
- Helps users understand how to improve CTL's analysis accuracy
- Hints are displayed with a 💡 icon in the explain output

### Improved Spark Profiling
- Spark profiling now only triggers for `CONFIRMED_LEAK` verdicts or `HIGH` severity leaks
- Prevents unnecessary profiling for small or baseline leaks
- Added `isSparkAlreadyRunning()` check to prevent starting multiple Spark sessions
- Spark profiling runs silently in the background (no chat messages)

## Improvements

### Leak Analysis
- Modified `LeakAnalyser.findRelatedMods` to exclude Death Detangler from conflict reports if it's active and managing leaks
- Only reports Death Detangler as a conflict if it's not actively managing the leak or if the leak is significant despite its efforts
- Improved confidence calculation to increase more gradually (30% base + 5% per observation)

### Severity Calculation
- Adjusted severity calculation to require multiple observations before setting to `HIGH`
- First detection is `LOW` or `MEDIUM`, only becomes `HIGH` with 2+ reports and 2+ growth events, or 5+ reports with 70%+ growth events
- More stable severity reporting

### Verdict Logic
- Updated verdict logic to return `CONFIRMED_LEAK` if there are multiple growth events (2+ growth events and 2+ reports, or 5+ reports with 70%+ growth events)
- Even if Death Detangler is active, confirmed leak patterns still trigger warnings
- Ensures ATL warnings appear when there's a confirmed leak pattern

### Output Formatting
- Removed duplicate confidence information from uncertainty notes
- Confidence is now shown once at the bottom with proper color coding
- Confidence hints are displayed separately with clear icons
- Related mods list only shows if not already in the conflict warning

## Bug Fixes

- Fixed compilation errors related to variable scope in `LeakAnalyser`
- Fixed missing import for `LeakAnalysis` in `SparkProfilerManager`
- Fixed config reference (`Config.enableLogNotifications` → `Config.logDebugDetails`)
- Improved error handling for reflection-based operations

## Code Quality

- Removed debug comments and unnecessary fluff
- Improved code organization and documentation
- Better error handling throughout

## Technical Details

- **State Tracking**: Now properly tracks `currentCount` vs `previousCount` for accurate trend analysis
- **Event Filtering**: Improved filtering logic to distinguish between baseline retention and actual leaks
- **Inter-Mod Communication**: Uses reflection-based API for communication with Death Detangler

## Compatibility

- **Minecraft**: 1.20.1
- **Forge**: 47.4.0 and later (all 47.4.x versions)
- **AllTheLeaks**: Compatible with all versions
- **Death Detangler**: Enhanced integration for better diagnostics
- **Spark**: Optional integration for memory profiling
