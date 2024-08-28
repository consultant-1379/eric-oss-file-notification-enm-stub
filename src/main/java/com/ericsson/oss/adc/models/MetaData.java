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

package com.ericsson.oss.adc.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.lang.Nullable;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetaData {

    private long id;

    @Nullable
    private String nodeName;

    @Nullable
    private String dataType;

    @Nullable
    private String nodeType;

    @Nullable
    private String fileLocation;

    public MetaData(FileNotificationDTO fileNotificationDTO,long id){
        this.id = id;
        this.nodeName=fileNotificationDTO.getNodeName();
        this.dataType=fileNotificationDTO.getDataType();
        this.nodeType=fileNotificationDTO.getNodeType();
        this.fileLocation=fileNotificationDTO.getFileLocation();
    }

    public MetaData(long id){
        this.id = id;
    }

}
