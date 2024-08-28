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
package com.ericsson.oss.adc.config;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SftpServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SftpServer.class);
    private static final int DEFAULT_TIMEOUT_MS = 1000;

    public static ChannelSftp getSftpConnection(final String sftpUser, final String sftpHost, final int sftpPort, final String sftpPassword) {
        LOGGER.info("SFTP: Connecting to '{}@{}:{}' ", sftpUser, sftpHost, sftpPort);
        try {
            final Session jschSession = connect(sftpUser, sftpHost, sftpPort, sftpPassword, DEFAULT_TIMEOUT_MS);
            final ChannelSftp channelSftp = (ChannelSftp) jschSession.openChannel("sftp");
            channelSftp.connect();
            return channelSftp;
        } catch (final JSchException jSchException) {
            LOGGER.error("{}: Error connecting to '{}@{}:{}'", jSchException.getMessage(), sftpUser, sftpHost, sftpPort);
            LOGGER.error("Stacktrace: ", jSchException);
        }
        return null;
    }

    public static Session connect(final String sftpUser, final String sftpHost, final int sftpPort, final String sftpPassword, final int timeoutInMs)
            throws JSchException {

        final JSch jsch = new JSch();
        final Session jschSession = jsch.getSession(sftpUser, sftpHost, sftpPort);
        jschSession.setPassword(sftpPassword);
        jschSession.setConfig("StrictHostKeyChecking", "no");
        jschSession.connect(timeoutInMs);
        return jschSession;
    }
}
