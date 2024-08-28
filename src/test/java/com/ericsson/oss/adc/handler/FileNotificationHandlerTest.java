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

import static com.ericsson.oss.adc.enums.FileType.EVENT5G;
import static com.ericsson.oss.adc.enums.FileType.PMCOUNTER;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ActiveProfiles;

import com.ericsson.oss.adc.enums.FileType;
import com.ericsson.oss.adc.exceptions.NoSftpConnectionException;
import com.ericsson.oss.adc.models.SizedQueue;
import com.ericsson.oss.adc.services.SftpService;
import com.jcraft.jsch.SftpException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * The Class FileNotificationHandlerTest.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ActiveProfiles("test")
public class FileNotificationHandlerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileNotificationHandler.class);
    private static Map<String, FileType> configurableUploadedMapFilePathToFileTypeForTest = new HashMap<>();

    /**
     * Test that map is equal to the configurable number of nodes.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @Order(1)
    @DisplayName("Verify That Map Is Equal To The Configurable Number Of Nodes")
    public void testThatMapIsEqualToTheConfigurableNumberOfNodes() throws Exception {

        final FileNotificationHandler fileNotificationHandler = getFileNotificationHandlerForTest();
        final Map<String, FileType> map = new HashMap<>();

        final int numberOfNodes5gPmEvent = 10;
        final int numberOfNodesFileTrans = 10;
        map.put("/sftp/ericsson/pmic1/XML/LTE18dg2ERBS00010/A20210622.0245-0300_LTE18dg2ERBS00010.xml", PMCOUNTER);
        map.put("/sftp/ericsson/pmic1/XML/LTE18dg2ERBS00010/A20210622.0245-0300_LTE18dg2ERBS00010.xml.gz", PMCOUNTER);
        map.put("/sftp/ericsson/pmic1/XML/NR140gNodeBRadio00010/A20210622.0830-0845_NR140gNodeBRadio00010.xml", PMCOUNTER);
        map.put("/sftp/ericsson/pmic1/XML/NR140gNodeBRadio00010/A20210622.0830-0845_NR140gNodeBRadio00010.xml.gz", PMCOUNTER);
        map.put("/sftp/ericsson/pmic1/CELLTRACE/SubNetwork=ONRM_ROOT_MO_R,SubNetwork=5G,MeContext=NE00000650,ManagedElement=NE00000650/A20200824.1330+0900-1345+0900_SubNetwork=ONRM_ROOT_MO_R,SubNetwork=5G,MeContext=NE00000650,ManagedElement=NE00000650_celltracefile_CUCP0_1_1.gpb",
                EVENT5G);
        map.put("/sftp/ericsson/pmic1/CELLTRACE/SubNetwork=ONRM_ROOT_MO_R,SubNetwork=5G,MeContext=NE00000650,ManagedElement=NE00000650/A20200824.1330+0900-1345+0900_SubNetwork=ONRM_ROOT_MO_R,SubNetwork=5G,MeContext=NE00000650,ManagedElement=NE00000650_celltracefile_CUCP0_1_1.gpb.gz",
                EVENT5G);
        printMap(map, "INPUT");
        fileNotificationHandler.setMapUploadedBinFilePathToFileType(map);
        fileNotificationHandler.setNumberOfNodes5gPmEvent(numberOfNodes5gPmEvent);
        fileNotificationHandler.setNumberOfNodesFileTrans(numberOfNodesFileTrans);
        fileNotificationHandler.setSftpRemoteDirectory("/sftp/");
        fileNotificationHandler.setSftpPermissions("0755");
        fileNotificationHandler.uploadConfigurableNumberOfFilesWithCurrentRopTime();

        final Map<String, FileType> outMap = fileNotificationHandler.getMapUploadedFilePathToFileType();

        printMap(outMap, "OUTPUT");

        final AtomicInteger numPmCounter = new AtomicInteger(0);
        final AtomicInteger num5gEvent = new AtomicInteger(0);
        outMap.entrySet().forEach(entry -> {
            if (entry.getValue() == PMCOUNTER) {
                numPmCounter.getAndIncrement();
            }
            if (entry.getValue() == EVENT5G) {
                num5gEvent.getAndIncrement();
            }
        });

        Assertions.assertEquals(numberOfNodesFileTrans, numPmCounter.get());
        Assertions.assertEquals(numberOfNodes5gPmEvent, num5gEvent.get());
        configurableUploadedMapFilePathToFileTypeForTest.putAll(outMap);
    }

    /**
     * Test rename all mapped remote files.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @Order(2)
    @DisplayName("Verify That renameAllMappedRemoteFiles works OK")
    public void testRenameAllMappedRemoteFiles() throws Exception {

        final FileNotificationHandler fileNotificationHandler = getFileNotificationHandlerForTest();

        final int numberOfNodes5gPmEvent = 10;
        final int numberOfNodesFileTrans = 10;
        Assertions.assertFalse(configurableUploadedMapFilePathToFileTypeForTest.isEmpty(),
                "Expected 'configurableUploadedMapFilePathToFileTypeForTest' map to be setup by first test");
        printMap(configurableUploadedMapFilePathToFileTypeForTest, "INPUT");
        fileNotificationHandler.setMapUploadedFilePathToFileType(configurableUploadedMapFilePathToFileTypeForTest);
        fileNotificationHandler.setNumberOfNodes5gPmEvent(numberOfNodes5gPmEvent);
        fileNotificationHandler.setNumberOfNodesFileTrans(numberOfNodesFileTrans);
        fileNotificationHandler.setSftpRemoteDirectory("/sftp/");
        fileNotificationHandler.setSftpPermissions("0755");
        final boolean result = fileNotificationHandler.renameAllMappedRemoteFiles();

        Assertions.assertTrue(result);
        final Map<String, FileType> outMap = fileNotificationHandler.getMapUploadedFilePathToFileType();

        printMap(outMap, "OUTPUT");

        final AtomicInteger numPmCounter = new AtomicInteger(0);
        final AtomicInteger num5gEvent = new AtomicInteger(0);
        outMap.entrySet().forEach(entry -> {
            if (entry.getValue() == PMCOUNTER) {
                numPmCounter.getAndIncrement();
            }
            if (entry.getValue() == EVENT5G) {
                num5gEvent.getAndIncrement();
            }
        });

        Assertions.assertEquals(numberOfNodesFileTrans, numPmCounter.get());
        Assertions.assertEquals(numberOfNodes5gPmEvent, num5gEvent.get());

    }

    /**
     * Test update file path with new utc gmt date time.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @Order(3)
    @DisplayName("Verify That updateFilePathWithNewDateTime works OK with UTC/GMT Time zone")
    public void testUpdateFilePathWithNewUtcGmtDateTime() throws Exception {
        final FileNotificationHandler fileNotificationHandler = getFileNotificationHandlerForTest();

        final LocalDateTime testTime = LocalDateTime.now(ZoneId.of("UTC")).withYear(2022).withDayOfYear(01).withDayOfMonth(01).withHour(0)
                .withMinute(16);

        final String expectedRemoteFilePath = "/sftp/ericsson/pmic1/CELLTRACE/SubNetwork=ONRM_ROOT_MO_R,SubNetwork=5G,MeContext=NE00000650,ManagedElement=NE00000003/A20220101.0000+0000-0015+0000_SubNetwork=ONRM_ROOT_MO_R,SubNetwork=5G,MeContext=NE00000650,ManagedElement=NE00000003_celltracefile_CUCP0_1_1.gpb.gz";
        final String remoteFilePath = "/sftp/ericsson/pmic1/CELLTRACE/SubNetwork=ONRM_ROOT_MO_R,SubNetwork=5G,MeContext=NE00000650,ManagedElement=NE00000003/A20220412.1600+0100-1615+0100_SubNetwork=ONRM_ROOT_MO_R,SubNetwork=5G,MeContext=NE00000650,ManagedElement=NE00000003_celltracefile_CUCP0_1_1.gpb.gz";
        final String actualRemoteFilePath = fileNotificationHandler.updateFilePathWithNewDateTime(remoteFilePath, testTime);
        LOGGER.info("expectedRemoteFilePath = {}", expectedRemoteFilePath);
        LOGGER.info("  actualRemoteFilePath = {}", actualRemoteFilePath);

        Assertions.assertEquals(expectedRemoteFilePath, actualRemoteFilePath);

    }

    /**
     * Test update file path with new ist date time.
     * Note: IF underlying system is on IST, then the expected will be offset +0100
     * If underlying system is on GMT,, then the expected will be offset +0000
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @Order(4)
    @DisplayName("Verify That updateFilePathWithNewDateTime works OK with IST Time zone")
    public void testUpdateFilePathWithNewIstDateTime() throws Exception {
        final FileNotificationHandler fileNotificationHandler = getFileNotificationHandlerForTest();

        final ZoneId ireZoneId = ZoneId.of("Europe/Dublin");
        final LocalDateTime testTime = LocalDateTime.now().withYear(2022).withDayOfYear(100).withDayOfMonth(01).withHour(0)
                .withMinute(16);
        String expectedRemoteFilePath = "/sftp/ericsson/pmic1/CELLTRACE/SubNetwork=ONRM_ROOT_MO_R,SubNetwork=5G,MeContext=NE00000650,ManagedElement=NE00000003/A20220401.0000+0100-0015+0100_SubNetwork=ONRM_ROOT_MO_R,SubNetwork=5G,MeContext=NE00000650,ManagedElement=NE00000003_celltracefile_CUCP0_1_1.gpb.gz";
        if (testRunningOnSystemWithGmtTimeZone(testTime)) {
            expectedRemoteFilePath = "/sftp/ericsson/pmic1/CELLTRACE/SubNetwork=ONRM_ROOT_MO_R,SubNetwork=5G,MeContext=NE00000650,ManagedElement=NE00000003/A20220401.0000+0000-0015+0000_SubNetwork=ONRM_ROOT_MO_R,SubNetwork=5G,MeContext=NE00000650,ManagedElement=NE00000003_celltracefile_CUCP0_1_1.gpb.gz";
        }
        final String remoteFilePath = "/sftp/ericsson/pmic1/CELLTRACE/SubNetwork=ONRM_ROOT_MO_R,SubNetwork=5G,MeContext=NE00000650,ManagedElement=NE00000003/A20220412.1600+0100-1615+0100_SubNetwork=ONRM_ROOT_MO_R,SubNetwork=5G,MeContext=NE00000650,ManagedElement=NE00000003_celltracefile_CUCP0_1_1.gpb.gz";
        final String actualRemoteFilePath = fileNotificationHandler.updateFilePathWithNewDateTime(remoteFilePath, testTime);
        LOGGER.info("expectedRemoteFilePath = {}", expectedRemoteFilePath);
        LOGGER.info("  actualRemoteFilePath = {}", actualRemoteFilePath);

        Assertions.assertEquals(expectedRemoteFilePath, actualRemoteFilePath);

    }

    private boolean testRunningOnSystemWithGmtTimeZone(final LocalDateTime testTime) {
        final ZoneOffset zoneOffset = testTime.atZone(ZoneId.systemDefault()).getOffset();
        final String offset = (zoneOffset.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)).replace(":", "");
        if (offset.equalsIgnoreCase("Z")) { // UTC, GMT Zone is offset of 00:00, represented by 'Z'
            return true;
        }
        return false;
    }

    /**
     * Test files to upload file set pm counter.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @Order(5)
    @DisplayName("Verify All XML Files in 'files-to-upload' set")
    public void testFilesToUploadFileSetPmCounter() throws Exception {

        final FileNotificationHandler fileNotificationHandler = getFileNotificationHandlerForTest();

        final String[] fileExtn = { ".xml", ".xml.gz" };
        final Map<String, FileType> map = getInputMap(fileExtn, FileType.PMCOUNTER);
        final int numberOfNodesFileTrans = 19;

        printMap(map, "INPUT");
        fileNotificationHandler.setMapUploadedBinFilePathToFileType(map);
        fileNotificationHandler.setNumberOfNodesFileTrans(numberOfNodesFileTrans);
        fileNotificationHandler.setSftpPermissions("0755");
        fileNotificationHandler.uploadConfigurableNumberOfFilesWithCurrentRopTime();

        final Map<String, FileType> outMap = fileNotificationHandler.getMapUploadedFilePathToFileType();

        printMap(outMap, "OUTPUT");

        Assertions.assertEquals(numberOfNodesFileTrans, outMap.size());
    }

    /**
     * Test files to upload file set 5 G pm events.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @Order(6)
    @DisplayName("Verify All 5G PmEvents Files in 'files-to-upload' set")
    public void testFilesToUploadFileSet5GPmEvents() throws Exception {

        final FileNotificationHandler fileNotificationHandler = getFileNotificationHandlerForTest();

        final String[] fileExtn = { ".gpb", ".gpb.gz" };
        final Map<String, FileType> map = getInputMap(fileExtn, FileType.EVENT5G);
        final int numberOfNodes5gPmEvent = 19;

        printMap(map, "INPUT");
        fileNotificationHandler.setMapUploadedBinFilePathToFileType(map);
        fileNotificationHandler.setNumberOfNodes5gPmEvent(numberOfNodes5gPmEvent);
        fileNotificationHandler.setSftpRemoteDirectory("/sftp/");
        fileNotificationHandler.setSftpPermissions("0755");
        fileNotificationHandler.uploadConfigurableNumberOfFilesWithCurrentRopTime();

        final Map<String, FileType> outMap = fileNotificationHandler.getMapUploadedFilePathToFileType();

        printMap(outMap, "OUTPUT");

        Assertions.assertEquals(numberOfNodes5gPmEvent, outMap.size());
    }

    /**
     * Test files to upload file set 4 G pm events.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @Order(7)
    @DisplayName("Verify All 4G PmEvents Files in 'files-to-upload' set")
    public void testFilesToUploadFileSet4GPmEvents() throws Exception {

        final FileNotificationHandler fileNotificationHandler = getFileNotificationHandlerForTest();
        final String[] fileExtn = { ".bin", ".bin.gz" };
        final Map<String, FileType> map = getInputMap(fileExtn, FileType.EVENT4G);
        final int numberOfNodes4gPmEvent = 4;

        printMap(map, "INPUT");
        fileNotificationHandler.setMapUploadedBinFilePathToFileType(map);
        fileNotificationHandler.setNumberOfNodes4gPmEvent(numberOfNodes4gPmEvent);
        fileNotificationHandler.setSftpRemoteDirectory("/sftp/");
        fileNotificationHandler.setSftpPermissions("0755");
        fileNotificationHandler.uploadConfigurableNumberOfFilesWithCurrentRopTime();

        final Map<String, FileType> outMap = fileNotificationHandler.getMapUploadedFilePathToFileType();

        printMap(outMap, "OUTPUT");

        Assertions.assertEquals(numberOfNodes4gPmEvent, outMap.size());
    }


    /**
     * Just to see of generator.nextInt(MAX) will select all possible values... it will not
     * So use the sequential loop counter.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @Order(8)
    public void testRandom() throws Exception {
        final int MAX = 20;
        final Random generator = new Random();
        final List<Integer> list = new ArrayList<>();
        for (int i = 0; i < MAX; i++) {
            final int g = generator.nextInt(MAX);
            list.add(g);
        }
        Collections.sort(list);
        LOGGER.info("Random (all numbers not used): {}", list);

        final String[] array = { "one", "two", "three", "four", "five" };
        final List<String> outList = new ArrayList<>();
        int c = 0;
        for (int i = 0; i < MAX; i++) {
            if (c >= array.length) {
                c = 0;
            }
            outList.add(array[c++]);
        }
        LOGGER.info("Sequential (all numbers used) {}", outList);

    }

    public FileNotificationHandler getFileNotificationHandler(final int numberOfNodesSftpFt, final int numberOfNodes4gPmEvent, final int numberOfNodes5gPmEvent) throws Exception {

        final FileNotificationHandler fileNotificationHandler = getFileNotificationHandlerForTest();

        final String[] fileExtn = { ".xml", ".xml.gz" };
        final Map<String, FileType> map = getInputMap(fileExtn, FileType.PMCOUNTER);

        final String[] fileExtn4G = { ".bin", ".bin.gz" };
        map.putAll(getInputMap(fileExtn4G, FileType.EVENT4G));

        final String[] fileExtn5G = { ".gpb", ".gpb.gz" };
        map.putAll(getInputMap(fileExtn5G, FileType.EVENT5G));


        printMap(map, "INPUT");
        fileNotificationHandler.setMapUploadedBinFilePathToFileType(map);
        fileNotificationHandler.setNumberOfNodesFileTrans(numberOfNodesSftpFt);
        fileNotificationHandler.setNumberOfNodes4gPmEvent(numberOfNodes4gPmEvent);
        fileNotificationHandler.setNumberOfNodes5gPmEvent(numberOfNodes5gPmEvent);
        fileNotificationHandler.setSftpPermissions("0755");
        fileNotificationHandler.uploadConfigurableNumberOfFilesWithCurrentRopTime();

        final Map<String, FileType> outMap = fileNotificationHandler.getMapUploadedFilePathToFileType();

        printMap(outMap, "OUTPUT");

        final int total = numberOfNodesSftpFt + numberOfNodes4gPmEvent + numberOfNodes5gPmEvent;
        Assertions.assertEquals(total, outMap.size());
        return fileNotificationHandler;
    }

    private Map<String, FileType> getInputMap(final String[] fileExtn, final FileType fileType) throws IOException {
        final Map<String, FileType> map = new HashMap<>();

        final Path path = new File("./files-to-upload").toPath();
        final List<Path> xmlPath = Files.walk(Paths.get(path.toUri())).filter(p -> !Files.isDirectory(p))
                .filter(f -> isFileType(f.toString(), fileExtn)).collect(Collectors.toList());
        LOGGER.info("Current Dir  = {} ", path.toAbsolutePath().toString());
        for (final Path xmlFile : xmlPath) {
            LOGGER.info("Current File is   = {} ", xmlFile.toString());
            map.put(xmlFile.toString(), fileType);
        }
        return map;
    }

    private static boolean isFileType(final String file, final String[] fileExtn) {
        boolean result = false;

        for (final String fileExtension : fileExtn) {
            if (file.endsWith(fileExtension)) {
                result = true;
                break;
            }
        }
        return result;
    }
    private FileNotificationHandler getFileNotificationHandlerForTest() throws SftpException, NoSftpConnectionException {
        final SftpService sftpService = mock(SftpService.class);
        when(sftpService.sftpConnectionPresent()).thenReturn(true);
        Mockito.doNothing().when(sftpService).mkdirRemotePath(anyString(), anyString());
        Mockito.doNothing().when(sftpService).cdToRemotePath(anyString());
        Mockito.when(sftpService.upload(anyString(), anyString(), anyString())).thenReturn(true);
        Mockito.when(sftpService.symlink(anyString(), anyString(), anyString())).thenReturn(SymlinkResult.SUCCESS);
        Mockito.when(sftpService.symlink(Mockito.isNull(), anyString(), anyString())).thenReturn(SymlinkResult.SUCCESS);

        final FileHandler fileHandler = mock(FileHandler.class);
        Mockito.when(fileHandler.getAvailableFiles()).thenReturn(new ArrayList<>());
        Mockito.when(fileHandler.getIdCounter()).thenReturn(new AtomicLong());

        final FileNotificationHandler fileNotificationHandler = new FileNotificationHandler(new SimpleMeterRegistry());
        fileNotificationHandler.setSftpService(sftpService);
        fileNotificationHandler.setFileHandler(fileHandler);
        fileNotificationHandler.setFilesWindow(new SizedQueue<>(15, 60));
        fileNotificationHandler.initMisc();
        return fileNotificationHandler;
    }
    private void printMap(final Map<String, FileType> map, final String name) {
        LOGGER.info("---------------- MAP : {} ------------------------", name);
        map.entrySet().forEach(entry -> {
            LOGGER.info("{} : {}", entry.getKey(), entry.getValue());
        });
        LOGGER.info("---------------- END ------------------------");
    }
}
