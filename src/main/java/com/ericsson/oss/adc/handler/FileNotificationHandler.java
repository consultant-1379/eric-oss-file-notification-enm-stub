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

package com.ericsson.oss.adc.handler;

import static com.ericsson.oss.adc.utils.Utilities.UNIX_PATH_SEPARATOR;
import static com.ericsson.oss.adc.utils.Utilities.getFileName;
import static com.ericsson.oss.adc.utils.Utilities.toUnixPathSeparator;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.ericsson.oss.adc.enums.DataType;
import com.ericsson.oss.adc.enums.FileEndings;
import com.ericsson.oss.adc.enums.FileFormats;
import com.ericsson.oss.adc.enums.FileType;
import com.ericsson.oss.adc.exceptions.FileHandlingException;
import com.ericsson.oss.adc.exceptions.NoSftpConnectionException;
import com.ericsson.oss.adc.models.FileNotificationDTO;
import com.ericsson.oss.adc.models.MetaData;
import com.ericsson.oss.adc.models.SizedQueue;
import com.ericsson.oss.adc.services.SftpService;
import com.ericsson.oss.adc.utils.DependencyCheckSftp;
import com.ericsson.oss.adc.utils.Utilities;
import com.jcraft.jsch.SftpException;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * The Class FileNotificationHandler.
 */
@Component
public class FileNotificationHandler {

    private static final String DOT_GZ = ".gz";

    private static final String RENAME = "RENAME";

    private static final String STARTUP = "STARTUP";

    private static final Logger LOG = LoggerFactory.getLogger(FileNotificationHandler.class);

    private static final String NAMING_DELIMITER = "_";

    private static final String FULL_STOP = ".";

    private static final String UNDER_SCORE = "_";

    private final AtomicLong numSftpFilesRenamedPerRop;

    private final AtomicLong numSftpFilesUploadedPerRop;

    private final AtomicLong numSftpFilesRenamedEvent4gPerRop;

    private final AtomicLong numSftpFilesRenamedEvent5gPerRop;

    private final AtomicLong numSftpFilesRenamedPmCounterPerRop;

    private final AtomicLong numSftpFilesRenamedPmCounterEbsPerRop;

    private final AtomicLong numSftpFilesRenamedPmCounterCorePerRop;

    private final AtomicLong timeMsSftpFilesRenamedPerRop;

    private final AtomicLong timeMsSftpFilesUploadedPerRop;

    // A map of FileType enums to their respective DataType.
    // Used in the notification sent to kafka
    private final Map<FileType, DataType> mapFileTypeToDataType = new HashMap<>();

    // A map of FileType enums to their respective sftp file uploaded per rop
    // metric.
    // Used in prometheus to know how many sftp files were uploaded for a FileType
    private final Map<FileType, AtomicLong> mapFileTypeToSftpFilesRenamedPerRopMetric = new HashMap<>();

    // A map of the symlinked file paths to their respective FileType enums
    // Used in keeping track of what files are uploaded (eventually used for the
    // deletion of those files)
    // and used for kafka notifications too
    private Map<String, FileType> mapUploadedFilePathToFileType = new HashMap<>();

    // A map of the symlinked file paths to their respective actual files in the bin
    // directory
    // Used to keep a track of what actual files are used for the symlinks
    private final Map<String, String> mapUploadedFilePathToBinFilePath = new HashMap<>();

    // A map of the actual file paths to their respective FileType enums
    // Used to keep a track of the File types for the actual files in the bin
    // directory
    private Map<String, FileType> mapUploadedBinFilePathToFileType = new HashMap<>();

    // A map of FileType enums to the number of nodes that need to be generated for
    // them
    // Used to keep a track of the number of nodes needed per file type,
    // used in the initial generation of files (symlinks)
    private final Map<FileType, Integer> mapFileTypeToNodeCount = new HashMap<>();

    // Window of files that stores maps of files (mapUploadedFilePathToFileType)
    // to be stored for retention purposes
    private SizedQueue<Map<String, FileType>> filesWindow;

    @Autowired
    private SftpService sftpService;

    public SftpService getSftpService() {
        return sftpService;
    }

    @Autowired
    private DependencyCheckSftp dependencyCheckSftp;

    @Autowired
    private FileHandler fileHandler;

    @Value("${sftp.remote.directory}")
    private String sftpRemoteDirectory;

    @Value("${sftp.permissions}")
    private String sftpPermissions;

    @Value("${sftp.user}")
    private String sftpUser;

    @Value("${sftp.numberOfNodes.pmEvent5g}")
    private int numberOfNodes5gPmEvent;

    @Value("${sftp.numberOfNodes.pmEvent4g}")
    private int numberOfNodes4gPmEvent;

    @Value("${sftp.numberOfNodes.fileTrans}")
    private int numberOfNodesFileTrans;

    @Value("${sftp.numberOfNodes.fileTransEbs}")
    private int numberOfNodesFileTransEbs;

    @Value("${sftp.numberOfNodes.fileTransCore}")
    private int numberOfNodesFileTransCore;

    @Value("${sftp.ropPeriodMinutes}")
    private int ropPeriodMinutes;

    @Value("${sftp.retentionPeriodMinutes}")
    private int retentionPeriodMinutes;

    @Value("${sftp.connection.timeoutInMs}")
    private int connectionTimeoutInMs;

    @Value("${sftp.connection.retryCountMax.atStartup}")
    private int connectionRetryCountAtStartupMax;

    @Value("${sftp.connection.retryCountMax.running}")
    private int connectionRetryCountMax;

    private String lastFormattedLocalDateTime;

    private final String binSubDirectory = "bin/";

    private int expectedTotalNumberOfFiles;

    private Pattern pattern = Pattern.compile("NodeType=(\\w+)");

    /**
     * Constructor to build the metrics.
     *
     * @param meterRegistry
     *            The meter registry
     */
    public FileNotificationHandler(final MeterRegistry meterRegistry) {
        numSftpFilesRenamedPerRop = meterRegistry.gauge(
                "eric.oss.file.notification.enm.stub:sftp.files.renamed.per.rop",
                new AtomicLong(0));
        numSftpFilesUploadedPerRop = meterRegistry.gauge(
                "eric.oss.file.notification.enm.stub:sftp.files.uploaded.per.rop",
                new AtomicLong(0));
        numSftpFilesRenamedEvent4gPerRop = meterRegistry.gauge(
                "eric.oss.file.notification.enm.stub:sftp.files.renamed.event4g.per.rop",
                new AtomicLong(0));
        numSftpFilesRenamedEvent5gPerRop = meterRegistry.gauge(
                "eric.oss.file.notification.enm.stub:sftp.files.renamed.event5g.per.rop",
                new AtomicLong(0));
        numSftpFilesRenamedPmCounterPerRop = meterRegistry.gauge(
                "eric.oss.file.notification.enm.stub:sftp.files.renamed.sftp_filetrans.per.rop",
                new AtomicLong(0));
        numSftpFilesRenamedPmCounterEbsPerRop = meterRegistry.gauge(
                "eric.oss.file.notification.enm.stub:sftp.files.renamed.sftp_filetrans_ebs.per.rop",
                new AtomicLong(0));
        numSftpFilesRenamedPmCounterCorePerRop = meterRegistry.gauge(
                "eric.oss.file.notification.enm.stub:sftp.files.renamed.sftp_filetrans_core.per.rop",
                new AtomicLong(0));
        timeMsSftpFilesUploadedPerRop = meterRegistry.gauge(
                "eric.oss.file.notification.enm.stub:sftp.files.uploaded.per.rop.time",
                new AtomicLong(0));
        timeMsSftpFilesRenamedPerRop = meterRegistry.gauge(
                "eric.oss.file.notification.enm.stub:sftp.files.renamed.per.rop.time",
                new AtomicLong(0));
    }

    /**
     * Initialises required miscellaneous material
     */
    @Order(0)
    @EventListener(ApplicationReadyEvent.class)
    public void initMisc() {
        filesWindow = new SizedQueue<>(ropPeriodMinutes, retentionPeriodMinutes);

        mapFileTypeToNodeCount.put(FileType.PMCOUNTER, numberOfNodesFileTrans);
        mapFileTypeToNodeCount.put(FileType.PMCOUNTER_EBS, numberOfNodesFileTransEbs);
        mapFileTypeToNodeCount.put(FileType.PMCOUNTER_CORE, numberOfNodesFileTransCore);
        mapFileTypeToNodeCount.put(FileType.EVENT4G, numberOfNodes4gPmEvent);
        mapFileTypeToNodeCount.put(FileType.EVENT5G, numberOfNodes5gPmEvent);
        expectedTotalNumberOfFiles = mapFileTypeToNodeCount.values().stream().reduce(0, Integer::sum);

        mapFileTypeToDataType.put(FileType.PMCOUNTER, DataType.PM_STATISTICAL);
        mapFileTypeToDataType.put(FileType.PMCOUNTER_EBS, DataType.PM_STATISTICAL);
        mapFileTypeToDataType.put(FileType.PMCOUNTER_CORE, DataType.PM_STATISTICAL);
        mapFileTypeToDataType.put(FileType.EVENT4G, DataType.PM_CELLTRACE);
        mapFileTypeToDataType.put(FileType.EVENT5G, DataType.PM_CELLTRACE_CUUP);

        mapFileTypeToSftpFilesRenamedPerRopMetric.put(FileType.PMCOUNTER, numSftpFilesRenamedPmCounterPerRop);
        mapFileTypeToSftpFilesRenamedPerRopMetric.put(FileType.PMCOUNTER_EBS, numSftpFilesRenamedPmCounterEbsPerRop);
        mapFileTypeToSftpFilesRenamedPerRopMetric.put(FileType.PMCOUNTER_CORE, numSftpFilesRenamedPmCounterCorePerRop);
        mapFileTypeToSftpFilesRenamedPerRopMetric.put(FileType.EVENT4G, numSftpFilesRenamedEvent4gPerRop);
        mapFileTypeToSftpFilesRenamedPerRopMetric.put(FileType.EVENT5G, numSftpFilesRenamedEvent5gPerRop);
    }

    /**
     * Runs at startup to upload files to the provided sftpRemoteDirectory and renames them.
     *
     * @throws NoSftpConnectionException
     *             the sftp exception
     * @throws NoSftpConnectionException
     *             the no sftp connection exception
     */
    @Order(1)
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void startup() throws SftpException, NoSftpConnectionException {
        LOG.info("APPLICATION HAS STARTED - 2");
        LOG.info("Config: {}", readFromValuesYaml());
        dependencyCheckSftp.deploySftp(connectionTimeoutInMs, connectionRetryCountAtStartupMax);
        if (!sftpService.sftpConnectionPresent()) {
            logStpServerError(STARTUP);
            return;
        }
        uploadLocalFilesToSftpServer(STARTUP);
    }

    /**
     * Renames the remoteFilePaths in the object instance's fileMap by prepending formatted
     * LocalDateTime{@value #NAMING_DELIMITER} to the filename. If already present, it is updated.
     *
     * @return true, if successful
     */
    public boolean renameAllMappedRemoteFiles() {
        if (!sftpService.sftpConnectionPresent()) {
            LOG.error("{}: No SFTP Server connection found ", RENAME);
            dependencyCheckSftp.deploySftp(connectionTimeoutInMs, connectionRetryCountMax);
            if (!sftpService.sftpConnectionPresent()) {
                logStpServerError(RENAME);
                return false;
            }
        }

        // First time configurable number of files should be generated from local files
        if (!isAllFilesUploadedSuccessfully()) {
            uploadConfigurableNumberOfFilesWithCurrentRopTime();
        }
        timeMsSftpFilesRenamedPerRop.set(0);
        numSftpFilesRenamedPerRop.set(0);
        mapFileTypeToSftpFilesRenamedPerRopMetric.values().forEach(metric -> metric.set(0));
        if (isAllFilesUploadedSuccessfully()) {
            final Instant startTime = Instant.now();
            final LocalDateTime now = LocalDateTime.now();
            final Map<String, FileType> shallowCopyMap = new HashMap<>(mapUploadedFilePathToFileType);
            lastFormattedLocalDateTime = "Not_Set";
            for (final Map.Entry<String, FileType> entry : shallowCopyMap.entrySet()) {
                String newFilePath;
                try {
                    newFilePath = updateFilePathWithNewDateTime(entry.getKey(), now);
                } catch (final FileHandlingException e) {
                    LOG.error("{} ERROR: Unable to update file '{}' with current ROP time for timestamp '{}':'{}'",
                            RENAME, entry.getKey(), now, e.getMessage());
                    continue;
                }

                final SymlinkResult symlinkResult = sftpService.symlink(mapUploadedFilePathToBinFilePath.get(entry.getKey()), newFilePath, sftpPermissions);
                if (symlinkResult == SymlinkResult.FAILED) {
                    return false;
                }
                final FileType fileType = entry.getValue();
                mapFileTypeToSftpFilesRenamedPerRopMetric.get(fileType).incrementAndGet();
                numSftpFilesRenamedPerRop.incrementAndGet();
                if (symlinkResult == SymlinkResult.EXIST) {
                    continue;
                }
                mapUploadedFilePathToBinFilePath.put(newFilePath,
                        mapUploadedFilePathToBinFilePath.remove(entry.getKey()));
                mapUploadedFilePathToFileType.put(newFilePath, mapUploadedFilePathToFileType.remove(entry.getKey()));
                storeNewFilePath(fileType, newFilePath);
            }

            // maintain window of files in the retention period and delete files that are
            // outside the window
            final Map<String, FileType> toBeDeleted = filesWindow.addWithRemove(
                    Utilities.deepCopyMapStringToFileType(mapUploadedFilePathToFileType));
            if (toBeDeleted != null) {
                cleanupFiles(toBeDeleted);
            }
            timeMsSftpFilesRenamedPerRop.set(Duration.between(startTime, Instant.now()).toMillis());
            LOG.info("{}: Updated File DateTime to '{}' for {} Files took {} ms", RENAME, lastFormattedLocalDateTime,
                    numSftpFilesRenamedPerRop.get(),
                    timeMsSftpFilesRenamedPerRop.get());
            return true;
        } else {
            LOG.error("{} ERROR: Expected number of files incorrect, files will not be renamed", RENAME);
        }
        return false;

    }

    /**
     * Update file path with new date time.
     *
     * @param remoteFilePath
     *            the remote file path
     * @param time
     *            the time
     *
     * @return the string
     *
     * @throws FileHandlingException
     */
    public String updateFilePathWithNewDateTime(final String remoteFilePath, final LocalDateTime time) throws FileHandlingException {
        final String remoteFileName = getFileName(remoteFilePath);
        final char preDateChar = remoteFileName.charAt(0);
        if (!isValidFile(remoteFilePath, remoteFileName)) {
            throw new FileHandlingException("Invalid File " + remoteFilePath);
        }
        final String parentDirectory = String.valueOf(Paths.get(remoteFilePath).getParent());
        final String remoteFileNameAfterFirstUnscore = getRemoteFilename(remoteFileName);
        final String formattedLocalDateTime = getFormattedLocalDateTime(preDateChar, time);
        final String newRemoteFilePath = toUnixPathSeparator(parentDirectory) + UNIX_PATH_SEPARATOR + formattedLocalDateTime + NAMING_DELIMITER
                + remoteFileNameAfterFirstUnscore;
        if (LOG.isTraceEnabled()) {
            LOG.trace("UPDATE: ----------------------------------------------------------------");
            LOG.trace("UPDATE: remoteFilePath (before)         '{}'", remoteFilePath);
            LOG.trace("UPDATE: remoteFileName (before)         '{}'", remoteFileName);
            LOG.trace("UPDATE: parentDirectory                 '{}'", parentDirectory);
            LOG.trace("UPDATE: formattedLocalDateTime          '{}'", formattedLocalDateTime);
            LOG.trace("UPDATE: remoteFileNameAfterFirstUnscore '{}'", remoteFileNameAfterFirstUnscore);
            LOG.trace("UPDATE: newRemoteFilePath (after)       '{}'", newRemoteFilePath);
            LOG.trace("UPDATE: ----------------------------------------------------------------");
        }
        if (remoteFileNameAfterFirstUnscore == null) {
            LOG.error("UPDATE ERROR: Unable to update FilePath With New Date Time; remoteFileNameAfterFirstUnscore = '{}'",
                    remoteFileNameAfterFirstUnscore);
            throw new FileHandlingException("Unable to update FilePath With New Date Time; {} " + remoteFilePath);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("UPDATE:\nNew path for '{}' " + "\nwill be      '{}'", remoteFilePath, newRemoteFilePath);
        }
        lastFormattedLocalDateTime = formattedLocalDateTime;

        return newRemoteFilePath;
    }

    public boolean isAllFilesUploadedSuccessfully() {
        LOG.info("Actual number of files uploaded {}, expected number of files {} ", mapUploadedFilePathToFileType.size(),
                expectedTotalNumberOfFiles);
        return mapUploadedFilePathToFileType.size() == expectedTotalNumberOfFiles;
    }

    /**
     * Upload configurable number of files with current rop time.
     */
    public boolean uploadConfigurableNumberOfFilesWithCurrentRopTime() {
        final Instant startTime = Instant.now();
        LOG.info("UPLOAD: Initial file generation sequence started");
        final Map<String, FileType> filteredMapFilePathToFileType = new HashMap<>(mapUploadedBinFilePathToFileType.size());
        int localTotalUploaded = 0;

        for (final FileType fileType : FileType.values()) {
            if (fileType == FileType.UNKNOWN) {
                continue;
            }
            LOG.info("Generating {} nodes (files) for {}", mapFileTypeToNodeCount.get(fileType), fileType);
            filteredMapFilePathToFileType.clear();
            for (final Map.Entry<String, FileType> entry : mapUploadedBinFilePathToFileType.entrySet()) {
                // Aggregating files of the same file type
                if (entry.getValue().equals(fileType)) {
                    filteredMapFilePathToFileType.put(entry.getKey(), entry.getValue());
                }
            }

            if (filteredMapFilePathToFileType.isEmpty()) {
                LOG.warn("No files present in bin directory for file type '{}'. Skipping...", fileType);
                continue;
            }

            int fileNumberToUpload = 0;
            int localNumUploaded = 0;
            for (int i = 1; i <= mapFileTypeToNodeCount.get(fileType); i++) {
                final Object[] filePaths = filteredMapFilePathToFileType.keySet().toArray();
                if (fileNumberToUpload >= filePaths.length) { // round-robin to iterate through files
                    fileNumberToUpload = 0;
                }
                final String filepath = (String) filePaths[fileNumberToUpload++];
                String newFilePath;
                try {
                    newFilePath = renameFilePathWithNewNodeNameAndDateTime(filepath, LocalDateTime.now(), i)
                            .replace(binSubDirectory, ""); // in actual dir now and not the bin
                } catch (final FileHandlingException e) {
                    LOG.error("UPLOAD: Unable to rename file '{}' with new node name and datetime: {}", filepath, e.getMessage());
                    return false;
                }

                final SymlinkResult symlinkResult = sftpService.symlink(filepath, newFilePath, sftpPermissions); // these links will not be removed
                if (symlinkResult == SymlinkResult.FAILED) {
                    return false;
                }
                if (symlinkResult == SymlinkResult.EXIST) {
                    continue;
                }
                mapUploadedFilePathToBinFilePath.put(newFilePath, filepath);
                mapUploadedFilePathToFileType.put(newFilePath, filteredMapFilePathToFileType.get(filepath));
                storeNewFilePath(fileType, newFilePath);
                numSftpFilesUploadedPerRop.incrementAndGet();
                mapFileTypeToSftpFilesRenamedPerRopMetric.get(fileType).incrementAndGet();
                localNumUploaded++;
                localTotalUploaded++;
            }
            LOG.info("UPLOAD: Uploaded {} {} Files ", localNumUploaded, fileType);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("UPLOAD: mapUploadedFilePathToFileType {} ", mapUploadedFilePathToFileType);
        }
        timeMsSftpFilesUploadedPerRop.set(Duration.between(startTime, Instant.now()).toMillis());
        LOG.info("UPLOAD: Uploaded {} Files (total) in {} mS", localTotalUploaded, timeMsSftpFilesUploadedPerRop.get());
        return true;
    }

    private void cleanupFiles(final Map<String, FileType> map) {
        map.keySet().forEach(filePath -> sftpService.rm(filePath));
    }

    private void storeNewFilePath(final FileType fileType, final String newFilePath) {
        if (mapFileTypeToDataType.get(fileType) != null) {
            final FileNotificationDTO fileNotificationDTO =
                constructFileNotificationDto(newFilePath, mapFileTypeToDataType.get(fileType).toString());
            synchronized(fileHandler){
                fileHandler.getAvailableFiles().add(new MetaData(fileNotificationDTO,fileHandler.getIdCounter().incrementAndGet()));
            }
        }
    }

    private boolean uploadLocalFilesToSftpServer(final String callingLocation) throws SftpException, NoSftpConnectionException {
        LOG.info("{}: Upload local files to SFTP server", callingLocation);
        if (sftpRemoteDirectory != null) { // default is $HOME, if one provided move to that instead
            LOG.info("{}: Creating remote directory structure '{}' with permissions '{}'", callingLocation, sftpRemoteDirectory, sftpPermissions);
            sftpService.mkdirRemotePath(sftpRemoteDirectory, sftpPermissions);
            sftpService.cdToRemotePath(sftpRemoteDirectory);
        } else {
            LOG.error("{}: ERROR Creating remote directory structure '{}' with permissions '{}'."
                    + " sftpRemoteDirectory is NULL, No Files will be transferred. ", callingLocation, sftpRemoteDirectory, sftpPermissions);
            return false;
        }
        final String localPath = "files-to-upload";
        LOG.info("{}: Transferring files from '{}' to '{}'", callingLocation, localPath, sftpRemoteDirectory);
        return uploadLocalFilesToRemoteBin(localPath, sftpRemoteDirectory, sftpPermissions);
    }

    private FileNotificationDTO constructFileNotificationDto(String path, final String dataType) {
        path = toUnixPathSeparator(path);
        Matcher matcher = pattern.matcher(path);
        String nodeType = matcher.find() ? matcher.group(1) : "RadioNode";
        return new FileNotificationDTO(
                path.split(UNIX_PATH_SEPARATOR)[path.split(UNIX_PATH_SEPARATOR).length - 2],
                dataType,
                nodeType,
                path,
                "this-field-should-be-ignored-in-the-listener" // extra field to be ignored in the listener
        );
    }

    private void logStpServerError(final String callingLocation) {
        LOG.error("ERROR : Cannot initialize Connection to SFTP Server at {}, cannot transfer and rename files "
                + "Check if SFTP server exists/works/is connectable. " + "No files will be uploaded or renamed", callingLocation);
    }

    private boolean isValidFile(final String remoteFilePath, final String remoteFileName) {
        if (!isPathNameValid(remoteFilePath)) {
            final String template = " <something>/<valid_type>/<nodename>/<filename>";
            LOG.error("RENAME UPDATE : Invalid File Path, \n\t expecting: '{}', \n\t received: '{}'", template, remoteFilePath);
            return false;
        }

        if (!isFilenameValid(remoteFileName)) {
            final String template = " A<something>.<something>_<something>.<valid FileFormat>";
            LOG.error("RENAME UPDATE: Invalid File Format, \n\t expecting: '{}', \n\t received: '{}'", template, remoteFileName);
            return false;
        }
        return true;
    }

    private String renameFilePathWithNewNodeNameAndDateTime(final String remoteFilePath, final LocalDateTime time, final int nodeIndex)
            throws FileHandlingException {
        final String remoteFileName = getFileName(remoteFilePath);
        final char preDateChar = remoteFileName.charAt(0);
        if (!isValidFile(remoteFilePath, remoteFileName)) {
            throw new FileHandlingException("Invalid File " + remoteFilePath);
        }
        final String parentDirectory = toUnixPathSeparator(String.valueOf(Paths.get(remoteFilePath).getParent()));
        final String parentDirectoryPrefix = parentDirectory.substring(0, parentDirectory.lastIndexOf(UNIX_PATH_SEPARATOR)) ;
        final String currentNodename = parentDirectory.substring(parentDirectory.lastIndexOf(UNIX_PATH_SEPARATOR) + 1);
        final String currentNodenamePrefix = getNodeNamePrefix(currentNodename);
        final String formattedLocalDateTime = getFormattedLocalDateTime(preDateChar, time);
        final String formattedNodeIndex = String.format("%04d", nodeIndex);
        final String newNodename = currentNodenamePrefix + formattedNodeIndex;

        String currentFileFormat = "not_set"; // either (example) .gpb.gz or .gpb
        String currentFileNameEnding = ""; // extract '_celltracefile_CUCP0_1_1' for example
        boolean compressed = false; // filename end with '.gz'
        if (remoteFileName.endsWith(DOT_GZ)) {
            compressed = true;
            final int gzIndex = remoteFileName.lastIndexOf(DOT_GZ);
            final int fileFormatIndex = remoteFileName.lastIndexOf(FULL_STOP, gzIndex - 1);
            currentFileFormat = remoteFileName.substring(fileFormatIndex, gzIndex);
            final int beginIndex = remoteFileName.indexOf(UNDER_SCORE) + 1 + currentNodename.length();
            currentFileNameEnding = remoteFileName.substring(beginIndex, fileFormatIndex);
        } else {
            compressed = false;
            final int fileFormatIndex = remoteFileName.lastIndexOf(FULL_STOP);
            currentFileFormat = remoteFileName.substring(fileFormatIndex);
            final int beginIndex = remoteFileName.indexOf(UNDER_SCORE) + 1 + currentNodename.length();
            currentFileNameEnding = remoteFileName.substring(beginIndex, fileFormatIndex);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("RENAME: ----------------------------------------------------------------");
            LOG.trace("RENAME: remoteFileName         '{}'", remoteFileName);
            LOG.trace("RENAME: parentDirectory        '{}'", parentDirectory);
            LOG.trace("RENAME: parentDirectoryPrefix  '{}'", parentDirectoryPrefix);
            LOG.trace("RENAME: FormattedLocalDateTime '{}'", formattedLocalDateTime);
            LOG.trace("RENAME: currentNodename        '{}'", currentNodename);
            LOG.trace("RENAME: currentNodenamePrefix  '{}'", currentNodenamePrefix);
            LOG.trace("RENAME: nodeNameEnding         '{}'", formattedNodeIndex);
            LOG.trace("RENAME: newNodename            '{}'", newNodename);
            LOG.trace("RENAME: currentFileNameEnding  '{}'", currentFileNameEnding);
            LOG.trace("RENAME: currentFileFormat      '{}'", currentFileFormat);
            LOG.trace("RENAME: compressed             '{}'", compressed);
            LOG.trace("RENAME: ----------------------------------------------------------------");
        }

        final String newRemoteFilePath = toUnixPathSeparator(parentDirectoryPrefix)
                + UNIX_PATH_SEPARATOR
                + newNodename
                + UNIX_PATH_SEPARATOR
                + formattedLocalDateTime
                + NAMING_DELIMITER
                + newNodename
                + currentFileNameEnding + currentFileFormat + (compressed ? DOT_GZ : "");

        if (LOG.isDebugEnabled()) {
            LOG.info("RENAME:\nNew path for '{}' " + "\nwill be      '{}'", remoteFilePath, newRemoteFilePath);
        }
        return newRemoteFilePath;
    }

    /**
     * Check for valid format of FileName;
     * Keep it basic.
     * A20200824.1330+0900-1345+0900_SubNetwork=ONRM_ROOT_MO_R_celltracefile_CUCP0_1_1.gpb.gz
     * A<something>.<something> _<something> .<valid FileFormat>
     *
     *
     * @param remoteFileName
     *
     * @return
     */
    private boolean isFilenameValid(final String remoteFileName) {
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("RENAME UPDATE: Validating File name: '{}'", remoteFileName);
            }

            if (remoteFileName.contains(NAMING_DELIMITER)) {
                final String ADateTime = remoteFileName.split(NAMING_DELIMITER, 2)[0];
                if (ADateTime.contains(FULL_STOP)) {
                    final String ADateStartTime = ADateTime.split("\\.")[0];
                    if (ADateStartTime.matches("[A-B][0-9]*([:print:]*)")) {
                        final String croppedRemainder = remoteFileName.split(NAMING_DELIMITER, 2)[1];
                        if (croppedRemainder.contains(FULL_STOP)) {
                            for (final FileEndings validFileFormat : FileEndings.values()) {
                                if (croppedRemainder.endsWith(validFileFormat.getFileEnding())) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (final Exception exception) {
            LOG.error("RENAME UPDATE: Failed to validate File name: '{}'", remoteFileName, exception);
        }
        return false;
    }

    /**
     * Check valid path:
     * Example .../pmic1/XML
     * /NR140gNodeBRadio00010/A20210622.0830-0845_NR140gNodeBRadio00010.xml.gz
     * <something>/<valid_type>/<nodename> /<filename>
     *
     * @param remoteFilePath
     * @return
     */
    private boolean isPathNameValid(final String remoteFilePath) {
        try {
            LOG.debug("RENAME UPDATE: Validating File Path: {}", remoteFilePath);
            if (remoteFilePath.contains(UNIX_PATH_SEPARATOR)) {
                final String[] pathParts = remoteFilePath.split(UNIX_PATH_SEPARATOR);
                /*
                 * EXAMPLE:
                 *                  0      1       2    3        4               5
                 * remoteFilePath=/sftp/ericsson/pmic1/XML/LTE18dg2ERBS00010/A20210622.0245-0300_LTE18dg2ERBS00010.xml
                 *
                 * Interested in validation 'XML' 'LTE18dg2ERBS00010' and filename
                 *
                 * so split breaks this up into arrays
                 * pathParts (matching remoteFilePath-1 example) has length of 6
                 *
                 * int p1 = 5
                 * int p2 = 4
                 * int p3 = 3
                 *
                 * so pathParts[p1] is always the filename
                 * pathParts[p2] is the node name
                 * pathParts[p2] is the FileFormat
                 */
                if (pathParts.length >= 3) {
                    final int p1 = pathParts.length - 1;
                    final int p2 = pathParts.length - 2;
                    final int p3 = pathParts.length - 3;
                    if (pathParts[p1].length() > 0) { // filename will be checked separately
                        if (pathParts[p2].length() > 0) {
                            for (final FileFormats validFormat : FileFormats.values()) {
                                if (pathParts[p3].equalsIgnoreCase(validFormat.toString())) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (final Exception exception) {
            LOG.error("RENAME: Failed to validate File Path: '{}'", remoteFilePath, exception);
        }
        return false;
    }

    private String getRemoteFilename(final String remoteFileName) {
        if (remoteFileName.contains(NAMING_DELIMITER)) {
            final String[] arr = remoteFileName.split(NAMING_DELIMITER, 2);
            if (arr.length >= 2) {
                return arr[1];
            }
        }
        return null;
    }

    private String getNodeNamePrefix(final String nodeName) {
        return nodeName.substring(0, nodeName.length() - 4);
    }

    private LocalDateTime getNewTime(LocalDateTime time) {
        // changes the time to be in 15 min intervals
        final int minute = time.getMinute();
        if (minute >= 45) {
            time = time.plusHours(1L).withMinute(0);
        } else {
            time = time.withMinute(minute < 30 ? minute < 15 ? 15 : 30 : 45);
        }
        return time;
    }

    private String getFormattedLocalDateTime(final char firstChar, final LocalDateTime time) {
        // return time with format "{firstChar}yyyyMMdd.HHmm-<HHmm + 15 mins>"
        final ZoneOffset zoneOffset = time.atZone(ZoneId.systemDefault()).getOffset();
        String offset = (zoneOffset.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)).replace(":", "");
        if (offset.equalsIgnoreCase("Z")) { // UTC, GMT Zone is offset of 00:00, represented by 'Z'
            offset = "+0000";
        }
        final LocalDateTime startTime = getNewTime(time.minusMinutes(30));
        final LocalDateTime endTime = getNewTime(startTime.plusMinutes(1));

        return firstChar + startTime.format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmm"))
                + offset
                + "-"
                + endTime.format(DateTimeFormatter.ofPattern("HHmm"))
                + offset;
    }

    /**
     * Uploads all the files and directories from local path to the remote path and
     * sets the permissions of the files.
     * Adds to the instance hashmap the key value pairs of remoteBinFilePath ->
     * fileType for the files uploaded.
     *
     * This bin is then used to create symlinks for files per ROP, essentially
     * simulating file generation.
     *
     * @param localPath   The local path from which the files are to be uploaded.
     * @param remotePath  The remote destination which is absolute or relative to
     *                    the current remote
     *                    directory (user home).
     * @param permissions The new permission pattern. This may be modified by a
     *                    current mask before being applied.
     * @return true, if successful
     */
    private boolean uploadLocalFilesToRemoteBin(final String localPath, String remotePath, final String permissions) {
        final String editedLocalPath = toUnixPathSeparator(localPath);
        remotePath = toUnixPathSeparator(remotePath);
        if (!remotePath.endsWith(UNIX_PATH_SEPARATOR)) {
            remotePath += UNIX_PATH_SEPARATOR;
        }

        final String editedRemotePath = remotePath + binSubDirectory;
        LOG.info("Uploading local files from '{}' to remote bin directory '{}'", editedLocalPath, editedRemotePath);
        if (!Paths.get(editedLocalPath).toFile().exists()) {
            LOG.error("Error uploading files: Local path '{}' does not exist", editedLocalPath);
            return false;
        }

        final Collection<File> localFiles = FileUtils.listFiles(new File(editedLocalPath), null, true);
        for (final File file : localFiles) {
            final String localFilePath = toUnixPathSeparator(file.getAbsolutePath());
            final String remoteFilePath = editedRemotePath
                    + toUnixPathSeparator(file.getPath().replace(editedLocalPath, ""));
            if (!sftpService.upload(localFilePath, remoteFilePath, permissions)) {
                LOG.info("Error uploading local files from '{}' to remote bin directory '{}'", editedLocalPath,
                        editedRemotePath);
                return false;
            }
            mapUploadedBinFilePathToFileType.put(remoteFilePath, getFileType(localFilePath));
        }
        LOG.info("Successfully uploaded local files from '{}' to remote bin directory '{}'", editedLocalPath, editedRemotePath);
        return true;
    }

    private FileType getFileType(final String fullPath) {
        FileType type;
        if (fullPath.contains(".bin")) {
            type = FileType.EVENT4G;
        } else if (fullPath.contains(".gpb")) {
            type = FileType.EVENT5G;
        } else if (fullPath.contains(".xml")) {
            if (fullPath.contains("NodeType=PCC") || fullPath.contains("NodeType=PCG")) {
                type = FileType.PMCOUNTER_CORE;
            } else if (fullPath.contains("osscounterfile")) {
                type = FileType.PMCOUNTER_EBS;
            } else {
                type = FileType.PMCOUNTER;
            }
        } else {
            type = FileType.UNKNOWN;
        }
        return type;
    }

    private String readFromValuesYaml() {
        return "FileNotificationHandler 'Values.Yaml'[sftpRemoteDirectory=" + sftpRemoteDirectory
                + ", sftpPermissions=" + sftpPermissions
                + ", baseDirectory=" + sftpRemoteDirectory
                + ", numberOfNodes5gPmEvent=" + numberOfNodes5gPmEvent
                + ", numberOfNodes4gPmEvent=" + numberOfNodes4gPmEvent
                + ", numberOfNodesFileTrans=" + numberOfNodesFileTrans
                + ", numberOfNodesFileTransEbs=" + numberOfNodesFileTransEbs
                + ", numberOfNodesFileTransCore=" + numberOfNodesFileTransCore
                + ", connectionTimeoutInMs=" + connectionTimeoutInMs
                + ", connectionRetryCountAtStartupMax=" + connectionRetryCountAtStartupMax
                + ", connectionRetryCountMax=" + connectionRetryCountMax + "]";
    }

    // Public for test From here
    public Map<String, FileType> getMapUploadedBinFilePathToFileType() {
        return mapUploadedBinFilePathToFileType;
    }

    public void setMapUploadedBinFilePathToFileType(final Map<String, FileType> map) {
        this.mapUploadedBinFilePathToFileType = map;
    }

    public void setSftpPermissions(final String sftpPermissions) {
        this.sftpPermissions = sftpPermissions;
    }

    public void setSftpRemoteDirectory(final String sftpRemoteDirectory) {
        this.sftpRemoteDirectory = sftpRemoteDirectory;
    }

    public Map<String, FileType> getMapUploadedFilePathToFileType() {
        return mapUploadedFilePathToFileType;
    }

    public void setMapUploadedFilePathToFileType(final Map<String, FileType> mapUploadedFilePathToFileType) {
        this.mapUploadedFilePathToFileType = mapUploadedFilePathToFileType;
    }

    public void setSftpService(final SftpService sftpService) {
        this.sftpService = sftpService;
    }

    public SizedQueue<Map<String, FileType>> getFilesWindow() {
        return filesWindow;
    }

    public void setFilesWindow(final SizedQueue<Map<String, FileType>> filesWindow) {
        this.filesWindow = filesWindow;
    }

    public void setNumberOfNodes5gPmEvent(final int numberOfNodes5gPmEvent) {
        this.numberOfNodes5gPmEvent = numberOfNodes5gPmEvent;
        mapFileTypeToNodeCount.put(FileType.EVENT5G, numberOfNodes5gPmEvent);
        expectedTotalNumberOfFiles = mapFileTypeToNodeCount.values().stream().reduce(0, Integer::sum);
    }

    public void setNumberOfNodes4gPmEvent(final int numberOfNodes4gPmEvent) {
        this.numberOfNodes4gPmEvent = numberOfNodes4gPmEvent;
        mapFileTypeToNodeCount.put(FileType.EVENT4G, numberOfNodes4gPmEvent);
        expectedTotalNumberOfFiles = mapFileTypeToNodeCount.values().stream().reduce(0, Integer::sum);
    }

    public void setNumberOfNodesFileTrans(final int numberOfNodesFileTrans) {
        this.numberOfNodesFileTrans = numberOfNodesFileTrans;
        mapFileTypeToNodeCount.put(FileType.PMCOUNTER, numberOfNodesFileTrans);
        expectedTotalNumberOfFiles = mapFileTypeToNodeCount.values().stream().reduce(0, Integer::sum);
    }

    public void setNumberOfNodesFileTransEbs(final int numberOfNodesFileTransEbs) {
        this.numberOfNodesFileTransEbs = numberOfNodesFileTransEbs;
        mapFileTypeToNodeCount.put(FileType.PMCOUNTER_EBS, numberOfNodesFileTransEbs);
        expectedTotalNumberOfFiles = mapFileTypeToNodeCount.values().stream().reduce(0, Integer::sum);
    }

    public void setNumberOfNodesFileTransCore(final int numberOfNodesFileTransCore) {
        this.numberOfNodesFileTransCore = numberOfNodesFileTransCore;
        mapFileTypeToNodeCount.put(FileType.PMCOUNTER_CORE, numberOfNodesFileTransCore);
        expectedTotalNumberOfFiles = mapFileTypeToNodeCount.values().stream().reduce(0, Integer::sum);
    }

    public AtomicLong getNumSftpFilesRenamedPerRop() {
        return numSftpFilesRenamedPerRop;
    }

    public void setFileHandler(final FileHandler fileHandler) {
        this.fileHandler = fileHandler;
    }
}
