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
package com.ericsson.oss.adc.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.ericsson.oss.adc.enums.FileType;
import com.ericsson.oss.adc.handler.FileNotificationHandler;
import com.ericsson.oss.adc.handler.FileNotificationHandlerTest;

class NotificationControllerTest {
    NotificationController nc = new NotificationController();
    @BeforeAll
    static void setUpBeforeClass() throws Exception {
    }

    @AfterAll
    static void tearDownAfterClass() throws Exception {
    }

    @BeforeEach
    void setUp() throws Exception {
    }

    @AfterEach
    void tearDown() throws Exception {
    }

    @Test
    void test_generateRop_happyCase() throws Exception {
        final NotificationController nc = new NotificationController();

        final int numberOfNodesSftpFT = 19;
        final int numberOfNodes4gPmEvent = 4;
        final int numberOfNodes5gPmEvent = 19;
        final FileNotificationHandlerTest fht = new FileNotificationHandlerTest();
        final FileNotificationHandler fh = fht.getFileNotificationHandler(numberOfNodesSftpFT, numberOfNodes4gPmEvent, numberOfNodes5gPmEvent);
        fh.initMisc();

        nc.setFileNotificationHandler(fh);
        nc.setNumberOfNodesFileTrans(numberOfNodesSftpFT);
        nc.setNumberOfNodesPmEvent4G(numberOfNodes4gPmEvent);
        nc.setNumberOfNodesPmEvent5G(numberOfNodes5gPmEvent);

        final int generateTimeoutInMs = 1000;
        final int generateRetryCountMax = 5;
        final ResponseEntity<String> resp = nc.generateRopManually(generateRetryCountMax, generateTimeoutInMs);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void test_generateRop_NotOk_upload() throws Exception {
        final NotificationController nc = new NotificationController();

        final int numberOfNodesSftpFT = 19;
        final int numberOfNodes4gPmEvent = 4;
        final int numberOfNodes5gPmEvent = 19;
        final FileNotificationHandlerTest fht = new FileNotificationHandlerTest();
        final FileNotificationHandler fh = fht.getFileNotificationHandler(numberOfNodesSftpFT, numberOfNodes4gPmEvent, numberOfNodes5gPmEvent);
        fh.initMisc();

        nc.setFileNotificationHandler(fh);
        nc.setNumberOfNodesFileTrans(numberOfNodesSftpFT);
        nc.setNumberOfNodesPmEvent4G(numberOfNodes4gPmEvent);
        nc.setNumberOfNodesPmEvent5G(numberOfNodes5gPmEvent);

        fh.getMapUploadedFilePathToFileType().put("filePath", FileType.EVENT5G);
        final int generateTimeoutInMs = 1000;

        final int generateRetryCountMax = 5;
        final ResponseEntity<String> resp = nc.generateRopManually(generateRetryCountMax, generateTimeoutInMs);
        assertEquals(HttpStatus.I_AM_A_TEAPOT, resp.getStatusCode());
    }

    @Test
    void test_generateRop_NotOk_rename() throws Exception {
        final NotificationController nc = new NotificationController();

        final int numberOfNodesSftpFT = 19;
        final int numberOfNodes4gPmEvent = 4;
        final int numberOfNodes5gPmEvent = 19;
        final FileNotificationHandlerTest fht = new FileNotificationHandlerTest();
        final FileNotificationHandler fh = fht.getFileNotificationHandler(numberOfNodesSftpFT, numberOfNodes4gPmEvent, numberOfNodes5gPmEvent);
        fh.initMisc();

        final FileNotificationHandler fhMock = spy(fh);
        doReturn(false).when(fhMock).renameAllMappedRemoteFiles();
        doReturn(true).when(fhMock).isAllFilesUploadedSuccessfully();
        fhMock.getMapUploadedFilePathToFileType().put("filePath", FileType.EVENT5G);

        nc.setFileNotificationHandler(fhMock);
        nc.setNumberOfNodesFileTrans(numberOfNodesSftpFT);
        nc.setNumberOfNodesPmEvent4G(numberOfNodes4gPmEvent);
        nc.setNumberOfNodesPmEvent5G(numberOfNodes5gPmEvent);

        final int generateTimeoutInMs = 1000;
        final int generateRetryCountMax = 5;
        final ResponseEntity<String> resp = nc.generateRopManually(generateRetryCountMax, generateTimeoutInMs);
        assertEquals(HttpStatus.I_AM_A_TEAPOT, resp.getStatusCode());
    }
}
