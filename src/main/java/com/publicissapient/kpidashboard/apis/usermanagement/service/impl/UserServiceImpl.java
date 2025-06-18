/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.apis.usermanagement.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.common.service.UserInfoService;
import com.publicissapient.kpidashboard.apis.usermanagement.service.UserService;
import com.publicissapient.kpidashboard.common.constant.AuthType;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;

import lombok.extern.slf4j.Slf4j;
import org.thymeleaf.util.StringUtils;

/**
 * Implementation of UserService for handling user operations
 */

@AllArgsConstructor
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserInfoService userInfoService;
    private static final String USER_NAME_CANNOT_NULL = "Username cannot be null or empty";
    private static final String DOMAIN_NAME = "@publicisgroupe.net";
    @Override
    public UserInfo saveUserInfo(String username) {

        if (StringUtils.isEmpty(username)) {
            log.error(USER_NAME_CANNOT_NULL);
            throw new IllegalArgumentException(USER_NAME_CANNOT_NULL);
        }
        log.info("Saving user information for username: {}", username);
        // Check if user already exists with SAML auth type
        UserInfo existingUser = userInfoService.getUserInfo(username, AuthType.SAML);

        if (!Objects.isNull(existingUser)) {
            log.info("User already exists with username: {} and authType: {}", username, AuthType.SAML);
            return existingUser;
        }

        // Create new user with SAML auth type
        UserInfo userInfo = new UserInfo();
        userInfo.setUsername(username);
        userInfo.setAuthType(AuthType.SAML);
        userInfo.setAuthorities(new ArrayList<>());
        userInfo.setEmailAddress(username.concat(DOMAIN_NAME));
        userInfo.setProjectsAccess(Collections.emptyList());
        
        log.info("Saving new user with username: {} and authType: {}", username, AuthType.SAML);
        return userInfoService.save(userInfo);
    }
}
