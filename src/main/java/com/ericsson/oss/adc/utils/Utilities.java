/*******************************************************************************
 * COPYRIGHT Ericsson 2021
 *
 *
 *
 * The copyright to the computer program(s) herein is the property of
 *
 * Ericsson Inc. The programs may be used and/or copied only with written
 *
 * permission from Ericsson Inc. or in accordance with the terms and
 *
 * conditions stipulated in the agreement/contract under which the
 *
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.adc.utils;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.adc.enums.FileType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public final class Utilities {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utilities.class);
    public static final String UNIX_PATH_SEPARATOR = "/";

    /**
     * Convert path to use UNIX file separator value of {@value UNIX_PATH_SEPARATOR}.
     * @param path The path that needs to be converted.
     * @return String of the edited path.
     */
    public static String toUnixPathSeparator(final String path) {
        return FilenameUtils.separatorsToUnix(path);
    }

    /**
     * Gets file name from a path.
     * @param path The full path name with the file at the end.
     * @return String of the filename.
     */
    public static String getFileName(final String path) {
        return new File(toUnixPathSeparator(path)).getName();
    }

    public static Map<String, FileType> deepCopyMapStringToFileType(final Map<String, FileType> map) {
        final Gson gson = new Gson();
        final String jsonString = gson.toJson(map);
        final Type type = new TypeToken<Map<String, FileType>>(){}.getType();
        return gson.fromJson(jsonString, type);
    }

    public static void waitaBit(final long timeout) {
        try {
            TimeUnit.MILLISECONDS.sleep(timeout);
        } catch (final InterruptedException e) {
            LOGGER.error("Thread interrupted during check: {}", e.getMessage());
        }
    }
}
