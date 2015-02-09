package com.byclosure.webcat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggerHelper {
    private static final Level DEFAULT_LOG_LEVEL = Level.INFO;

    private static boolean debug;
    private static final List<Logger> loggers = new ArrayList<Logger>();

    public static Logger getLogger(String className) {
        final Logger logger = Logger.getLogger(className);
        logger.setUseParentHandlers(false);

        final ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(DEFAULT_LOG_LEVEL);
        logger.addHandler(handler);

        if(isDebug()) {
            setDebugOn(logger);
        }

        loggers.add(logger);

        return logger;
    }

    private static void setDebugOn(Logger logger) {
        logger.setLevel(Level.FINE);

        for(Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.FINE);
        }
    }

    private static boolean isDebug() {
        return debug;
    }

    public static void setDebugOn() {
        debug = true;

        for(Logger logger : loggers) {
            setDebugOn(logger);
        }
    }
}
