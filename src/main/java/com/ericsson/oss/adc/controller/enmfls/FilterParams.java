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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;

@Getter
class FilterParams {
    private static final Pattern filterPatternDataType = Pattern.compile("dataType==(.*?);");
    private static final Pattern filterPatternNodeType = Pattern.compile("nodeType==(.*?);");
    private static final Pattern filterPatternId = Pattern.compile("id=gt=(\\d+)");
    private String dataType;
    private String nodeType;
    private String id;

    public FilterParams(final String filter) {
        final Matcher matcherDataType = filterPatternDataType.matcher(filter);
        final Matcher matcherNodeType = filterPatternNodeType.matcher(filter);
        final Matcher matcherId = filterPatternId.matcher(filter);

        if (matcherDataType.find()) {
            dataType = matcherDataType.group(1);
        }
        if (matcherNodeType.find()) {
            nodeType = matcherNodeType.group(1);
        }
        if (matcherId.find()) {
            id = matcherId.group(1);
        }
    }
}
