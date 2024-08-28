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

package com.ericsson.oss.adc.controller.enmfls;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ericsson.oss.adc.enums.DataType;
import com.ericsson.oss.adc.models.FileNotificationDTO;
import com.ericsson.oss.adc.models.MetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ericsson.oss.adc.CoreApplication;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { CoreApplication.class, EnmFlsRestController.class })
public class EnmFlsRestControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private EnmFlsRestController enmFlsRC;

    private MockMvc mvc;

    @BeforeEach
    public void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void sample() throws Exception {
        mvc.perform(get("/v1/sample").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                .andExpect(content().string("Sample response"));
    }

    @Test
    public void dataTypeFilterTest() {
        FileNotificationDTO pm_celltraceDTO = new FileNotificationDTO(
                "4Gevent",
                DataType.PM_CELLTRACE.toString(),
                "RadioNode",
                "Location");
        FileNotificationDTO pm_celltrace_duDTO = new FileNotificationDTO(
                "5Gevent",
                DataType.PM_CELLTRACE_DU.toString(),
                "RadioNode",
                "Location");
        FileNotificationDTO pm_celltrace_cuupDTO = new FileNotificationDTO(
                "5Gevent",
                DataType.PM_CELLTRACE_CUUP.toString(),
                "RadioNode",
                "Location");
        FileNotificationDTO pm_celltrace_cucpDTO = new FileNotificationDTO(
                "5Gevent",
                DataType.PM_CELLTRACE_CUCP.toString(),
                "RadioNode",
                "Location");
        FileNotificationDTO pm_counter_ranDTO = new FileNotificationDTO(
                "pmCounter",
                DataType.PM_STATISTICAL.toString(),
                "PCC",
                "Location");
        MetaData nullDataTypeMeta = new MetaData();
        enmFlsRC = new EnmFlsRestController();
        assertFalse(enmFlsRC.isFilterMatch("PM_CELLTRACE", "RadioNode", nullDataTypeMeta));

        MetaData pm_celltrace = new MetaData(pm_celltraceDTO, 1);
        assertFalse(enmFlsRC.isFilterMatch("PM_STATISTICAL", "RadioNode", pm_celltrace));
        assertTrue(enmFlsRC.isFilterMatch("PM_CELLTRACE", "RadioNode", pm_celltrace));
        assertFalse(enmFlsRC.isFilterMatch("PM_CELLTRACE_CUUP", "RadioNode", pm_celltrace));
        assertFalse(enmFlsRC.isFilterMatch("PM_CELLTRACE_*", "RadioNode", pm_celltrace));
        assertTrue(enmFlsRC.isFilterMatch("*_CELLTRACE", "RadioNode", pm_celltrace));

        MetaData pm_celltrace_du = new MetaData(pm_celltrace_duDTO, 2);
        assertFalse(enmFlsRC.isFilterMatch("PM_STATISTICAL", "RadioNode", pm_celltrace_du));
        assertFalse(enmFlsRC.isFilterMatch("PM_CELLTRACE", "RadioNode", pm_celltrace_du));
        assertFalse(enmFlsRC.isFilterMatch("PM_CELLTRACE_CUUP", "RadioNode", pm_celltrace_du));
        assertTrue(enmFlsRC.isFilterMatch("PM_CELLTRACE_*", "RadioNode", pm_celltrace_du));
        assertTrue(enmFlsRC.isFilterMatch("PM_*", "RadioNode", pm_celltrace_du));
        assertTrue(enmFlsRC.isFilterMatch("*_CELLTRACE_*", "RadioNode", pm_celltrace_du));
        assertFalse(enmFlsRC.isFilterMatch("*_CELLTRACE", "RadioNode", pm_celltrace_du));

        MetaData pm_celltrace_cuup = new MetaData(pm_celltrace_cuupDTO, 3);
        assertFalse(enmFlsRC.isFilterMatch("PM_STATISTICAL", "RadioNode", pm_celltrace_cuup));
        assertFalse(enmFlsRC.isFilterMatch("PM_CELLTRACE", "RadioNode", pm_celltrace_cuup));
        assertTrue(enmFlsRC.isFilterMatch("PM_CELLTRACE_CUUP", "RadioNode", pm_celltrace_cuup));
        assertTrue(enmFlsRC.isFilterMatch("PM_CELLTRACE_*", "RadioNode", pm_celltrace_cuup));
        assertTrue(enmFlsRC.isFilterMatch("PM_CELLTRACE*", "RadioNode", pm_celltrace_cuup));

        MetaData pm_celltrace_cucp = new MetaData(pm_celltrace_cucpDTO, 4);
        assertFalse(enmFlsRC.isFilterMatch("PM_STATISTICAL", "RadioNode", pm_celltrace_cucp));
        assertFalse(enmFlsRC.isFilterMatch("PM_CELLTRACE", "RadioNode", pm_celltrace_cucp));
        assertFalse(enmFlsRC.isFilterMatch("PM_CELLTRACE_CUUCP", "RadioNode", pm_celltrace_cucp));
        assertTrue(enmFlsRC.isFilterMatch("PM_CELLTRACE*", "RadioNode", pm_celltrace_cucp));
        assertTrue(enmFlsRC.isFilterMatch("PM_CELLTRACE_*", "RadioNode", pm_celltrace_cucp));
        assertFalse(enmFlsRC.isFilterMatch("PM_CELLTRACE_*", "PCC", pm_celltrace_cucp));
        assertTrue(enmFlsRC.isFilterMatch("PM_CELLTRACE_*", null, pm_celltrace_cucp));
        assertTrue(enmFlsRC.isFilterMatch("*", "RadioNode", pm_celltrace_cucp));
        assertFalse(enmFlsRC.isFilterMatch("Dummy*", "RadioNode", pm_celltrace_cucp));

        MetaData pm_counter_ran = new MetaData(pm_counter_ranDTO, 5);
        assertTrue(enmFlsRC.isFilterMatch("PM_STATISTICAL", "PCC", pm_counter_ran));
        assertFalse(enmFlsRC.isFilterMatch("PM_STATISTICAL", "RadioNode", pm_counter_ran));
        assertTrue(enmFlsRC.isFilterMatch("PM_STATISTICAL", null, pm_counter_ran));
        assertFalse(enmFlsRC.isFilterMatch("PM_CELLTRACE", null, pm_counter_ran));
        assertFalse(enmFlsRC.isFilterMatch("PM_CELLTRACE", "PCC", pm_counter_ran));
    }

}
