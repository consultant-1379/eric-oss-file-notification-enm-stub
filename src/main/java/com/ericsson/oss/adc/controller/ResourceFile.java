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

import com.ericsson.oss.adc.services.SftpService;
import io.swagger.annotations.ApiParam;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collection;

import static com.ericsson.oss.adc.utils.Utilities.toUnixPathSeparator;

@RestController
public class ResourceFile {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceFile.class);

    @Autowired
    SftpService sftpService;

    String localPath = "local-files";
    String localPMFile = "A20220510.0930+0100-0945+0100_SubNetwork=NR254gNodeBRadio00016,MeContext=NR254gNodeBRadio00016,ManagedElement=NR254gNodeBRadio00016_statsfile.xml.gz";


    @RequestMapping("/getcENMPMfile")
    public ResponseEntity<ByteArrayResource>  getLocalDownloadFile(@ApiParam(value = "Get file name") @Valid @RequestParam(value = "fileName",
            required = false) String fileName) throws IOException {
        File finalFile = null;
        String editedLocalPath = toUnixPathSeparator(localPath);
        LOG.info("editedLocalPathl path '{}'", editedLocalPath);

        if (!Paths.get(editedLocalPath).toFile().exists()) {
            LOG.error("Error uploading files: Local path '{}' does not exist", editedLocalPath);
        } else{
            LOG.info(" files: Local path '{}'", Paths.get(editedLocalPath));
        }

        final Collection<File> localFiles = FileUtils.listFiles(new File(editedLocalPath), null, true);
        for (File file : localFiles) {
            if (file.getName().contains(fileName) || file.getName().contains("PMFileFinal") ||
                    file.getName().contains(localPMFile)) {
                finalFile = file;
            }
        }

        InputStream inputStream = new FileInputStream(finalFile);
        byte[] bytes = IOUtils.toByteArray(inputStream);
        ByteArrayResource byteArrayResource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + fileName)
                .header(HttpHeaders.CONTENT_TYPE, "application/octect-stream")
                .body(byteArrayResource);

    }
}
