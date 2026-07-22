package com.apply.kmcg;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Properties;

final class BuildInfo {
    private static final String LAST_UPDATED = loadLastUpdated();

    private BuildInfo() {
    }

    static String getLastUpdated() {
        return LAST_UPDATED;
    }

    private static String loadLastUpdated() {
        try (InputStream in = BuildInfo.class.getResourceAsStream("build-info.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String value = props.getProperty("lastUpdated", "").trim();
                if (!value.isEmpty() && !value.contains("${")) {
                    return value;
                }
            }
        } catch (IOException ignored) {
        }
        return LocalDate.now().toString();
    }
}
