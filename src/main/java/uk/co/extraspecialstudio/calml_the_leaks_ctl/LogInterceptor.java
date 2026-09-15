package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/**
 * Intercepts log messages from ATL and processes them through CTL.
 * Uses a Log4j Filter to catch messages before they're printed.
 */
public class LogInterceptor {
    private static boolean initialized = false;
    
    public static void initialize() {
        if (initialized) return;
        
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        
        // Add a filter to intercept ATL messages
        Filter filter = new AbstractFilter() {
            @Override
            public Result filter(LogEvent event) {
                if (event == null || event.getMessage() == null) {
                    return Result.NEUTRAL;
                }
                
                String message = event.getMessage().getFormattedMessage();
                String loggerName = event.getLoggerName();
                
                // Strip [System] [CHAT] prefix if present (ATL outputs to chat which gets logged)
                String cleanMessage = message;
                if (message != null && message.contains("[CHAT]")) {
                    // Extract the actual message after [CHAT]
                    int chatIndex = message.indexOf("[CHAT]");
                    if (chatIndex >= 0 && chatIndex + 6 < message.length()) {
                        cleanMessage = message.substring(chatIndex + 6).trim();
                    }
                }
                
                // Check both logger name AND message content for ATL patterns
                // ATL might use different logger names or output through chat
                boolean isAtlMessage = false;
                
                if (loggerName != null && (loggerName.contains("AllTheLeaks") || 
                    loggerName.contains("alltheleaks") || 
                    loggerName.toLowerCase().contains("alltheleaks"))) {
                    isAtlMessage = true;
                }
                
                // Also check message content for ATL patterns (in case it uses a generic logger)
                // Check for "Listing leaks", "Level:", "Player:", "ChunkAccess:", etc.
                if (cleanMessage != null && (cleanMessage.contains("Memory Leaks detected") ||
                    cleanMessage.contains("Memory Leak") ||
                    cleanMessage.contains("Listing leaks") ||
                    cleanMessage.contains("Level:") ||
                    cleanMessage.contains("Player:") ||
                    cleanMessage.contains("ChunkAccess:") ||
                    (cleanMessage.contains("|") && cleanMessage.contains("ChunkAccess")) ||
                    (cleanMessage.contains("|") && cleanMessage.contains("LevelChunk")) ||
                    (cleanMessage.contains("-") && cleanMessage.contains("(") && cleanMessage.contains("):") && cleanMessage.matches(".*\\d+.*")))) {
                    isAtlMessage = true;
                }
                
                // Use clean message for processing
                if (isAtlMessage && cleanMessage != null) {
                    // Check if this message should be suppressed
                    if (AtlOutputInterceptor.interceptLogMessage(cleanMessage)) {
                        return Result.DENY; // Suppress the log message
                    }
                }
                
                return Result.NEUTRAL;
                
            }
        };
        
        // Add the filter to the root logger
        Logger rootLogger = ctx.getRootLogger();
        if (rootLogger instanceof org.apache.logging.log4j.core.Logger) {
            ((org.apache.logging.log4j.core.Logger) rootLogger).addFilter(filter);
        }
        
        initialized = true;
    }
}
