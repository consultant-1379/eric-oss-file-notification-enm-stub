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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ericsson.oss.adc.config.SftpServer;
import com.ericsson.oss.adc.services.SftpService;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * The Class DependencyCheckSftp.
 * This class will force the STUB to wait for the SFTP-Server to come on line.
 * It retries connection until it succeeds.
 *
 */
@Component
public class DependencyCheckSftp {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyCheckSftp.class);

    @Autowired
    private SftpService sftpService;

    @Value("${sftp.host}")
    private String sftpHost;

    @Value("${sftp.port}")
    private int sftpPort;

    @Value("${sftp.user}")
    private String sftpUser;

    @Value("${sftp.password}")
    private String sftpPassword;

    /**
     * Instantiates a new dependency check sftp.
     */
    public DependencyCheckSftp() {
        // No Args constructor for spring.
    }

    /**
     * Instantiates a new dependency check sftp.
     *
     * @param sftpService
     *            the sftp service
     * @param sftpHost
     *            the sftp host
     * @param sftpPort
     *            the sftp port
     * @param sftpUser
     *            the sftp user
     * @param sftpPassword
     *            the sftp password
     */
    public DependencyCheckSftp(final SftpService sftpService, final String sftpHost, final int sftpPort, final String sftpUser,
                               final String sftpPassword) {
        super();
        this.sftpHost = sftpHost;
        this.sftpPort = sftpPort;
        this.sftpUser = sftpUser;
        this.sftpPassword = sftpPassword;
        this.sftpService = sftpService;
    }

    /**
     * Deploy sftp.
     * Connect to the SFTP Server and make that instance available to the SFTP Service for use elsewhere.
     *
     * @param connectionTimeoutInMs
     *            the connection timeout in ms
     * @param connectionRetryCountMax
     *            the connection retry count max
     *
     * @return true, if successful
     */
    public boolean deploySftp(final int connectionTimeoutInMs, final int connectionRetryCountMax) {
        if (sftpServerDeployed(connectionTimeoutInMs, connectionRetryCountMax)) {
            LOGGER.info("Dependency Check: SFTP Server is available, trying to establish a connection.");
            final com.jcraft.jsch.ChannelSftp sftpConnection = SftpServer.getSftpConnection(sftpUser, sftpHost, sftpPort, sftpPassword);
            if (sftpConnection == null) {
                LOGGER.info("Dependency Check: ERROR with SFTP Server: SFTP Server seems to be available but cannot connect;  sftpConnection = {}",
                        sftpConnection);
                return false;
            }
            sftpService.setSftpConnection(sftpConnection);
        }
        return checkConnected();
    }

    /**
     * Sftp server deployed.
     * Check if the SFTP Server is deployed, by attempting to connect.
     * Retry a configurable number of times.
     *
     * @param connectionTimeoutInMs
     *            the connection timeout in ms
     * @param connectionRetryCountMax
     *            the connection retry count max
     *
     * @return true, if successful
     */
    public boolean sftpServerDeployed(final int connectionTimeoutInMs, final int connectionRetryCountMax) {
        LOGGER.info("Check SFTP Server Deployed:  Check connection using '{}@{}:{}'", sftpUser, sftpHost,
                sftpPort);
        Session jschSession = null;
        boolean isReachable = false;
        for (int i = 0; i < connectionRetryCountMax; i++) {
            try {
                jschSession = SftpServer.connect(sftpUser, sftpHost, sftpPort, sftpPassword, connectionTimeoutInMs);
                isReachable = jschSession == null ? false : isJschConnected(jschSession);
                if (isReachable) {
                    LOGGER.info("Check SFTP Server Deployed: Sftp Server is reachable");
                    jschSession = removeSession(jschSession);
                    return true;
                }
            } catch (final JSchException jSchException) {
                LOGGER.error("Check SFTP Server Deployed, ERROR: {}: Error Connecting to '{}@{}:{}'", jSchException.getMessage(), sftpUser,
                        sftpHost, sftpPort);
                if ((i + 1 == connectionRetryCountMax) || (i == 0)) {
                    LOGGER.error("Check SFTP Server Deployed, ERROR: Stacktrace: ", jSchException);
                }
            } finally {
                jschSession = removeSession(jschSession);
            }
            LOGGER.info("Check SFTP Server Deployed: Retry {}/{}, Sftp Server isReachable = {}", i + 1, connectionRetryCountMax, isReachable);
            Utilities.waitaBit(1000);
        }
        return isReachable;
    }

    /**
     * Checks if is jsch connected.
     *
     * @param jschSession
     *            the jsch session
     *
     * @return true, if is jsch connected
     */
    protected boolean isJschConnected(final Session jschSession) {
        return jschSession.isConnected();
    }

    private Session removeSession(Session jschSession) {
        if (jschSession != null && isJschConnected(jschSession)) {
            jschSession.disconnect();
            jschSession = null;
        }
        return jschSession;
    }

    private boolean checkConnected() {
        if (sftpService.sftpConnectionPresent()) {
            LOGGER.info("Dependency Check: SFTP Server is available and connection can be established");
            return true;
        } else {
            LOGGER.error("Dependency Check: ERROR with SFTP Server, no connection can be established");
            return false;
        }
    }
}
