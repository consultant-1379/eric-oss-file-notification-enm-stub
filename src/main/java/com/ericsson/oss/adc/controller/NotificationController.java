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

package com.ericsson.oss.adc.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ericsson.oss.adc.handler.FileNotificationHandler;
import com.ericsson.oss.adc.utils.Utilities;

import lombok.Data;

/**
 * Controller responsible for the creation of manual ROPs.
 */
@Data
@RestController
public class NotificationController {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationController.class);
    private static final String OK = "OK";
    private static final String NOK = "NOT_OK";
    private static final String LINUX_EOL = "\n";

    @Autowired
    private FileNotificationHandler fileNotificationHandler;

    @Value("${sftp.numberOfNodes.fileTrans}")
    private int numberOfNodesFileTrans;

    @Value("${sftp.numberOfNodes.fileTransEbs}")
    private int numberOfNodesFileTransEbs;

    @Value("${sftp.numberOfNodes.fileTransCore}")
    private int numberOfNodesFileTransCore;

    @Value("${sftp.numberOfNodes.pmEvent5g}")
    private int numberOfNodesPmEvent5G;

    @Value("${sftp.numberOfNodes.pmEvent4g:}")
    private int numberOfNodesPmEvent4G;

    @Value("${manualModeGenerate.backoffInMs}")
    private int generateBackoffInMs;

    @Value("${manualModeGenerate.retryCountMax}")
    private int generateRetryCountMax;
    /**
     * Will rename all currently uploaded files present on the sftp-server
     */
    @GetMapping("/generateRop")
    public ResponseEntity<String> generateRop() {
        LOG.info("------------------- MANUAL generate of ROPS -------------------");
        return generateRopManually(generateRetryCountMax, generateBackoffInMs);
    }

    public ResponseEntity<String> generateRopManually(final int manualRetryCountMax, final int manualBackoffInMs) {
        final String response = String.format(
                "MANUAL Rename and Sending of %d SFTP-FT Counter files and %d SFTP-FS EBS Counter files and %d SFTP-FT Core Counter files and %d 5GPmEvent files and %d 4GPmEvent files: Status = ",
                numberOfNodesFileTrans,
                numberOfNodesFileTransEbs,
                numberOfNodesFileTransCore,
                numberOfNodesPmEvent5G,
                numberOfNodesPmEvent4G);
        final String responseNok = response + NOK + LINUX_EOL;
        final String responseOk = response + OK + LINUX_EOL;

        LOG.info("MANUAL generate of ROPS triggered at '{}'", java.time.LocalDateTime.now());
        final long expectedNumberFiles = numberOfNodesFileTrans + numberOfNodesFileTransEbs + numberOfNodesFileTransCore + numberOfNodesPmEvent5G + numberOfNodesPmEvent4G;

        if (!fileNotificationHandler.getSftpService().sftpConnectionPresent()) {
            LOG.error("MANUAL generate of ROPS FAILED, SFTP connection is not present");
            return new ResponseEntity<>(responseNok, HttpStatus.I_AM_A_TEAPOT);
        }
        // First time configurable number of files should be generated from local files
        if (fileNotificationHandler.getMapUploadedFilePathToFileType().isEmpty()) {
            fileNotificationHandler.uploadConfigurableNumberOfFilesWithCurrentRopTime();

        // Otherwise symlinks should be updated to current ROP time
        } else {
            if (!waitForFilesToUpload(manualRetryCountMax, manualBackoffInMs)) {
                LOG.error("MANUAL generate of ROPS FAILED, All Files have not been uploaded");
                return new ResponseEntity<>(responseNok, HttpStatus.I_AM_A_TEAPOT);
            }

            if (!manualRename(expectedNumberFiles)) {
                LOG.error("MANUAL generate of ROPS FAILED, renaming failed.");
                return new ResponseEntity<>(responseNok, HttpStatus.I_AM_A_TEAPOT);
            }
        }

        LOG.info("MANUAL generate of ROPS PASSED at {}", java.time.LocalDateTime.now());
        return new ResponseEntity<>(responseOk, HttpStatus.OK);
    }

    private boolean manualRename(final long expectedNumberFiles) {
        LOG.info("MANUAL renaming of ROPS: STARTED");
        if (!fileNotificationHandler.renameAllMappedRemoteFiles()) {
            LOG.error("MANUAL renaming of ROPS: FAILED, rename returned false", java.time.LocalDateTime.now());
            return false;
        }
        final long actualNumberFilesRenamed = fileNotificationHandler.getNumSftpFilesRenamedPerRop().get();
        if (fileNotificationHandler.getNumSftpFilesRenamedPerRop().get() != expectedNumberFiles) {
            LOG.info("MANUAL renaming of ROPS: Expected {}, Actual Renamed {}", expectedNumberFiles,
                    actualNumberFilesRenamed);
            return true;
        }
        LOG.info("MANUAL renaming of ROPS: PASSED, Expected {}, Actual {}", expectedNumberFiles, actualNumberFilesRenamed);
        return true;
    }

    /**
     * Wait for files to upload.
     * This has a retry and backoff mechanism, to allow time for the ROPS to get uploaded if
     * manual trigger is received too soon after SFT_- Server is installed in APP Staging.
     *
     * @param manualRetryCountMax
     *            the manual retry count max
     * @param manualBackoffInMs
     *            the manual backoff in ms
     *
     * @return true, if successful
     */
    private boolean waitForFilesToUpload(final int manualRetryCountMax, final int manualBackoffInMs) {
        LOG.info("MANUAL generate of ROPS, Checked that all Files have been uploaded. Retry set to a max of {} with " + "backoff of {} : STARTED",
                manualRetryCountMax, manualBackoffInMs);
        final long then = System.currentTimeMillis();
        for (int i = 0; i < manualRetryCountMax; i++) {
            if (fileNotificationHandler.isAllFilesUploadedSuccessfully()) {
                LOG.info("MANUAL generate of ROPS, Checked that all Files have been uploaded : PASS");
                return true;
            }
            final long now = System.currentTimeMillis();

            LOG.error("MANUAL generate of ROPS FAILED, Retry {}/{} (with backoff {}), All Files have been uploaded, wait duration = {} mS", i + 1,
                    manualRetryCountMax, manualBackoffInMs, now - then);
            Utilities.waitaBit(generateBackoffInMs);
        }
        LOG.error("MANUAL generate of ROPS, Checked that all Files have been uploaded : FAILED, retries exhausted.");
        return false;
    }

}
