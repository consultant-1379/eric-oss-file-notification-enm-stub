/*******************************************************************************
 * COPYRIGHT Ericsson 2022
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
package com.ericsson.oss.adc.enums;

/**
 * The Enum FileEndings.
 */
public enum FileEndings {
    GPB_GZ(".gpb.gz"), GPB(".gpb"), BIN(".bin"), BIN_GZ(".bin.gz"), XML(".xml"), XML_GZ(".xml.gz");

    private String fileEnding;

    /**
     * Instantiates a new file endings.
     *
     * @param fileEnding
     *            the file ending
     */
    FileEndings(final String fileEnding) {
        this.fileEnding = fileEnding;
    }

    public String getFileEnding() {
        return fileEnding;
    }
}
