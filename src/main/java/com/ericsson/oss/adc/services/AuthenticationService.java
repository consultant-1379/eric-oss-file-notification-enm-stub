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

package com.ericsson.oss.adc.services;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Component
public class AuthenticationService {

    private boolean loggedIn;

    public MultiValueMap<String, String> authenticate() {
        final MultiValueMap<String, String> headers = new LinkedMultiValueMap<String, String>();
        headers.put("Set-Cookie", List.of("iPlanetDirectoryPro=TestCookie; Path=/; Secure; HttpOnly;"));
        loggedIn = true;
        return headers;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }
}
