package com.limelight;

import com.liblime.UnityMessager;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

public class LimeLog {
    private static final Logger LOGGER = Logger.getLogger("com.liblime.log");

    public static void info(String msg) {
        LOGGER.info(msg);
    }

    public static void warning(String msg) {
        LOGGER.warning(msg);
    }

    public static void severe(String msg) {
        LOGGER.severe(msg);
    }

    private static final boolean DEBUGING = false;
    private static final boolean VERBOSE = false;

    public static void todo(String msg) {
        UnityMessager.Debug("TODO:" + msg);
    }

    public static void debug(String msg) {
        if (DEBUGING)
            LOGGER.warning(msg);
    }

    //TDOO: add a verbose level
    public static void verbose(String msg) {
        if (VERBOSE)
            LOGGER.info("verbose:" + msg);
    }

    public static void temp(String msg) {
        LOGGER.severe("xxxx:" + msg);
    }

    public static void setFileHandler(String fileName) throws IOException {
        LOGGER.addHandler(new FileHandler(fileName));
    }
}
