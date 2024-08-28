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
package com.ericsson.oss.adc.utils;

import com.ericsson.oss.adc.services.SftpService;
import com.jcraft.jsch.Session;

public class TestDependencyCheckSftp extends DependencyCheckSftp {

    boolean isConnected;

    TestDependencyCheckSftp(final SftpService sftpService, final String sftpHost, final int sftpPort, final String sftpUser,
            final String sftpPassword, final boolean isConnected) {
        super(sftpService, sftpHost, sftpPort, sftpUser, sftpPassword);
        this.isConnected = isConnected;
    }

    @Override
    protected boolean isJschConnected(final Session jschSession) {
        return this.isConnected;
    }


}
