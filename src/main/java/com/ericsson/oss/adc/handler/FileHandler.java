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

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores the list of files that are ready for sending, and counts their flsId.
 */
@Component
@Data
public class FileHandler {

    private AtomicLong idCounter = new AtomicLong(System.currentTimeMillis());

    private final ArrayList<Object> availableFiles = new ArrayList<>();

}
