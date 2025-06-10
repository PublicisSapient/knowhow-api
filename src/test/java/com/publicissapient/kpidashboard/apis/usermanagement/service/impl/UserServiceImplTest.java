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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.common.service.UserInfoService;
import com.publicissapient.kpidashboard.common.constant.AuthType;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserInfoService userInfoService;

    @InjectMocks
    private UserServiceImpl userService;



    @Test
    void testSaveUserInfo_NewUser() {
        // Arrange
        String username = "testUser";
        
        when(userInfoService.getUserInfo(anyString(), eq(AuthType.SAML))).thenReturn(null);
        
        UserInfo savedUserInfo = new UserInfo();
        savedUserInfo.setUsername(username);
        savedUserInfo.setAuthType(AuthType.SAML);
        savedUserInfo.setAuthorities(new ArrayList<>());
        savedUserInfo.setEmailAddress("");
        savedUserInfo.setProjectsAccess(Collections.emptyList());
        
        when(userInfoService.save(any(UserInfo.class))).thenReturn(savedUserInfo);
        
        // Act
        UserInfo result = userService.saveUserInfo(username);
        
        // Assert
        assertNotNull(result);
        assertEquals(username, result.getUsername());
        assertEquals(AuthType.SAML, result.getAuthType());
        verify(userInfoService).save(any(UserInfo.class));
    }
    
    @Test
    void testSaveUserInfo_ExistingUser() {
        // Arrange
        String username = "existingUser";
        
        UserInfo existingUserInfo = new UserInfo();
        existingUserInfo.setUsername(username);
        existingUserInfo.setAuthType(AuthType.SAML);
        
        when(userInfoService.getUserInfo(anyString(), eq(AuthType.SAML))).thenReturn(existingUserInfo);
        
        // Act
        UserInfo result = userService.saveUserInfo(username);
        
        // Assert
        assertNotNull(result);
        assertEquals(username, result.getUsername());
        assertEquals(existingUserInfo, result);
    }
    
    @Test
    void testSaveUserInfo_NullUsername() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> userService.saveUserInfo(null));
    }
    
    @Test
    void testSaveUserInfo_EmptyUsername() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> userService.saveUserInfo(""));
    }
}
