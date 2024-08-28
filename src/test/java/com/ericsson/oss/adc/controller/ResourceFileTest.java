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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class ResourceFileTest {


   @Test
   void testgetLocalDownloadFile() throws IOException {
       ResourceFile resourceFile= new ResourceFile();
       Assertions.assertNotNull(resourceFile.getLocalDownloadFile("PMFileFinal.xml.gz"));


   }
}
