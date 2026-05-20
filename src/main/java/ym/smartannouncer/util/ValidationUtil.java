package ym.smartannouncer.util;

import ym.smartannouncer.config.ConfigLoadException;

public final class ValidationUtil {
    private ValidationUtil() {
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new ConfigLoadException(message);
        }
    }
}
