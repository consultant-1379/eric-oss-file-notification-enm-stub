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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class FilterParamsTest {

    @Test
    public void test_getFilterParamsDataType() {
        String filter = "dataType==PM_STATISTICAL;id=gt=0";
        FilterParams filterParams = new FilterParams(filter);
        assertEquals("PM_STATISTICAL", filterParams.getDataType());
        assertEquals(null, filterParams.getNodeType());
        assertEquals("0", filterParams.getId());
    }

    @Test
    public void test_getFilterParamsDataTypeAndNodeType() {
        String filter = "dataType==PM_STATISTICAL;nodeType==RadioNode;id=gt=43";
        FilterParams filterParams = new FilterParams(filter);
        assertEquals("PM_STATISTICAL", filterParams.getDataType());
        assertEquals("RadioNode", filterParams.getNodeType());
        assertEquals("43", filterParams.getId());
    }
}
