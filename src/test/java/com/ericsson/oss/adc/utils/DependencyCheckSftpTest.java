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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.adc.config.SftpServer;
import com.ericsson.oss.adc.services.SftpService;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class DependencyCheckSftpTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyCheckSftpTest.class);
    private final String sftpHost = "sftp-server";
    private final int sftpPort = 9023;
    private final String sftpUser = "demo";
    private final String sftpPassword = "demo";
    private final int connectionTimeoutInMs = 1;
    private final int connectionRetryCountMax = 1;

    @Test
    void test_DependencyCheckSftpReturnsFalseWithNoConnection() {
        try (MockedStatic<SftpServer> sftpServer = Mockito.mockStatic(SftpServer.class)) {
            when(SftpServer.getSftpConnection(sftpUser, sftpHost, sftpPort, sftpPassword)).thenReturn(null);
            final SftpService sftpService = new SftpService(new SimpleMeterRegistry());
            final DependencyCheckSftp dependencyCheckSftp = new DependencyCheckSftp(sftpService, sftpHost, sftpPort, sftpUser, sftpPassword);
            final boolean result = dependencyCheckSftp.deploySftp(connectionTimeoutInMs, connectionRetryCountMax);
            assertFalse(result);
        }

    }

    @Test
    void test_DependencyCheckSftpReturnsTrueWithGoodConnection() throws JSchException {
        try (MockedStatic<SftpServer> sftpServer = Mockito.mockStatic(SftpServer.class)) {
            when(SftpServer.getSftpConnection(sftpUser, sftpHost, sftpPort, sftpPassword)).thenReturn(new com.jcraft.jsch.ChannelSftp());
            when(SftpServer.connect(sftpUser, sftpHost, sftpPort, sftpPassword, connectionTimeoutInMs)).thenReturn(getSession());
            final SftpService sftpService = mock(SftpService.class);
            Mockito.doReturn(true).when(sftpService).sftpConnectionPresent();
            final TestDependencyCheckSftp dependencyCheckSftp = new TestDependencyCheckSftp(sftpService, sftpHost, sftpPort, sftpUser, sftpPassword,
                    true);
            final boolean result = dependencyCheckSftp.deploySftp(connectionTimeoutInMs, connectionRetryCountMax);
            assertTrue(result);
        }
    }

    @Test
    void test_sftpServerDeployedReturnsTrueWithGoodConnection() throws JSchException {
        try (MockedStatic<SftpServer> sftpServer = Mockito.mockStatic(SftpServer.class)) {
            when(SftpServer.getSftpConnection(sftpUser, sftpHost, sftpPort, sftpPassword)).thenReturn(new com.jcraft.jsch.ChannelSftp());
            when(SftpServer.connect(sftpUser, sftpHost, sftpPort, sftpPassword, connectionTimeoutInMs)).thenReturn(getSession());
            final TestDependencyCheckSftp dependencyCheckSftp = new TestDependencyCheckSftp(null, sftpHost, sftpPort, sftpUser, sftpPassword,
                    true);
            final boolean result = dependencyCheckSftp.sftpServerDeployed(connectionTimeoutInMs, connectionRetryCountMax);
            assertTrue(result);
        }
    }

    @Test
    void test_sftpServerDeployedReturnsFalseWithBadConnection() throws JSchException {
        try (MockedStatic<SftpServer> sftpServer = Mockito.mockStatic(SftpServer.class)) {
            when(SftpServer.getSftpConnection(sftpUser, sftpHost, sftpPort, sftpPassword)).thenReturn(new com.jcraft.jsch.ChannelSftp());
            when(SftpServer.connect(sftpUser, sftpHost, sftpPort, sftpPassword, connectionTimeoutInMs)).thenReturn(getSession());
            final DependencyCheckSftp dependencyCheckSftp = new DependencyCheckSftp(null, sftpHost, sftpPort, sftpUser, sftpPassword);
            final boolean result = dependencyCheckSftp.sftpServerDeployed(connectionTimeoutInMs, connectionRetryCountMax);
            assertFalse(result);
        }
    }

    @Test
    void test_SftpServerConnectThrowsExceptionWithBadConnection(){
        try {
            SftpServer.connect(sftpUser, sftpHost, sftpPort, sftpPassword, connectionTimeoutInMs);
            fail("Test Should Throw Exception");
        }
        catch (final JSchException jSchException) {
        }
    }

    private Session getSession() throws JSchException {
        final JSch jsch = new JSch();
        final Session jschSession = jsch.getSession(sftpUser, sftpHost, sftpPort);
        jschSession.setPassword(sftpPassword);
        jschSession.setConfig("StrictHostKeyChecking", "no");
        return jschSession;
    }
}
