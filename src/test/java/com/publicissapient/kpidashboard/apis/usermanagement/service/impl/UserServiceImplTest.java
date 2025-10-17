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

import com.publicissapient.kpidashboard.apis.auth.service.AuthenticationService;
import com.publicissapient.kpidashboard.apis.common.service.UserInfoService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.usermanagement.dto.response.UserResponseDTO;
import com.publicissapient.kpidashboard.common.constant.AuthType;
import com.publicissapient.kpidashboard.common.model.rbac.AccessItem;
import com.publicissapient.kpidashboard.common.model.rbac.AccessNode;
import com.publicissapient.kpidashboard.common.model.rbac.ProjectsAccess;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserInfoService userInfoService;

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    HierarchyLevelServiceImpl hierarchyLevelService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp(){
        authentication = Mockito.mock(Authentication.class);
        securityContext = Mockito.mock(SecurityContext.class);
    }

    @Test
    void testSaveUserInfo_NewUserForProjectAdmin() {
        String username = "testUser";
        SecurityContextHolder.setContext(securityContext);

        when(userInfoService.getUserInfo(anyString(), eq(AuthType.SAML))).thenReturn(null);

        ProjectsAccess access = getProjectAccess("project");

        List<GrantedAuthority> authorities = List.of(
                (GrantedAuthority) () -> Constant.ROLE_PROJECT_ADMIN
        );
        when(userInfoService.getAuthorities(any())).thenReturn(authorities);
        UserInfo projectAdminUserInfo = new UserInfo();
        projectAdminUserInfo.setUsername("ProjectAdmin");
        projectAdminUserInfo.setProjectsAccess(List.of(access));

        when(userInfoService.getUserInfo(authenticationService.getLoggedInUser())).thenReturn(projectAdminUserInfo);

        UserInfo savedUserInfo = new UserInfo();
        savedUserInfo.setUsername(username);
        savedUserInfo.setAuthType(AuthType.SAML);
        savedUserInfo.setAuthorities(new ArrayList<>());
        savedUserInfo.setEmailAddress("");
        savedUserInfo.setProjectsAccess(List.of(access));


        when(userInfoService.save(any())).thenReturn(savedUserInfo);

        // Act
        ServiceResponse result = userService.saveUserInfo(username);

        // Assert
        assertNotNull(result);
        assertEquals(true, result.getSuccess());
        assertEquals("User information saved successfully", result.getMessage());
        UserResponseDTO responseDTO = (UserResponseDTO) result.getData();
        assertEquals(username, responseDTO.getUsername());
        verify(userInfoService).save(any(UserInfo.class));
    }

    @Test
    void testSaveUserInfo_NewUserForOtherUser() {
        String username = "testUser";
        SecurityContextHolder.setContext(securityContext);
        when(userInfoService.getUserInfo(anyString(), eq(AuthType.SAML))).thenReturn(null);

        ProjectsAccess access = getProjectAccess("project");

        UserInfo projectAdminUserInfo = new UserInfo();
        projectAdminUserInfo.setUsername("ProjectAdmin");
        projectAdminUserInfo.setProjectsAccess(List.of(access));

        UserInfo savedUserInfo = new UserInfo();
        savedUserInfo.setUsername(username);
        savedUserInfo.setAuthType(AuthType.SAML);
        savedUserInfo.setAuthorities(new ArrayList<>());
        savedUserInfo.setEmailAddress("");
        savedUserInfo.setProjectsAccess(Collections.emptyList());

        when(userInfoService.save(any(UserInfo.class))).thenReturn(savedUserInfo);

        // Act
        ServiceResponse result = userService.saveUserInfo(username);

        // Assert
        assertNotNull(result);
        assertEquals(true, result.getSuccess());
        assertEquals("User information saved successfully", result.getMessage());
        UserResponseDTO responseDTO = (UserResponseDTO) result.getData();
        assertEquals(username, responseDTO.getUsername());
        verify(userInfoService).save(any(UserInfo.class));
    }

    ProjectsAccess getProjectAccess(String accessLevel){
        AccessItem accessItem = new AccessItem();
        accessItem.setItemId("tempItemId1");
        AccessItem accessItem2 = new AccessItem();
        accessItem2.setItemId("tempItemId2");
        AccessNode accessNode = new AccessNode();
        accessNode.setAccessLevel(accessLevel);
        accessNode.setAccessItems(List.of(accessItem,accessItem2));
        ProjectsAccess access = new ProjectsAccess();
        access.setRole(Constant.ROLE_PROJECT_ADMIN);
        access.setAccessNodes(List.of(accessNode));
        return access;
    }

    @Test
    void testSaveUserInfo_NewUserForProjectAdmin1() {
        String username = "testUser";
        SecurityContextHolder.setContext(securityContext);
        when(userInfoService.getUserInfo(anyString(), eq(AuthType.SAML))).thenReturn(null);
        ProjectsAccess access = getProjectAccess("acc");

        UserInfo projectAdminUserInfo = new UserInfo();
        projectAdminUserInfo.setUsername("ProjectAdmin");
        projectAdminUserInfo.setProjectsAccess(List.of(access));

        UserInfo savedUserInfo = new UserInfo();
        savedUserInfo.setUsername(username);
        savedUserInfo.setAuthType(AuthType.SAML);
        savedUserInfo.setAuthorities(new ArrayList<>());
        savedUserInfo.setEmailAddress("");
        savedUserInfo.setProjectsAccess(Collections.emptyList());

        when(userInfoService.save(any(UserInfo.class))).thenReturn(savedUserInfo);

        // Act
        ServiceResponse result = userService.saveUserInfo(username);

        // Assert
        assertNotNull(result);
        assertEquals(true, result.getSuccess());
        assertEquals("User information saved successfully", result.getMessage());
        UserResponseDTO responseDTO = (UserResponseDTO) result.getData();
        assertEquals(username, responseDTO.getUsername());
        verify(userInfoService).save(any(UserInfo.class));
    }

    @Test
    void testSaveUserInfo_NewUserForProjectAdminForAccess() {
        String username = "testUser";
        SecurityContextHolder.setContext(securityContext);
        when(userInfoService.getUserInfo(anyString(), eq(AuthType.SAML))).thenReturn(null);
        ProjectsAccess access = getProjectAccess("port");

        UserInfo projectAdminUserInfo = new UserInfo();
        projectAdminUserInfo.setUsername("ProjectAdmin");
        projectAdminUserInfo.setProjectsAccess(List.of(access));

        UserInfo savedUserInfo = new UserInfo();
        savedUserInfo.setUsername(username);
        savedUserInfo.setAuthType(AuthType.SAML);
        savedUserInfo.setAuthorities(new ArrayList<>());
        savedUserInfo.setEmailAddress("");
        savedUserInfo.setProjectsAccess(Collections.emptyList());

        when(userInfoService.save(any(UserInfo.class))).thenReturn(savedUserInfo);

        // Act
        ServiceResponse result = userService.saveUserInfo(username);

        // Assert
        assertNotNull(result);
        assertEquals(true, result.getSuccess());
        assertEquals("User information saved successfully", result.getMessage());
        UserResponseDTO responseDTO = (UserResponseDTO) result.getData();
        assertEquals(username, responseDTO.getUsername());
        verify(userInfoService).save(any(UserInfo.class));
    }



    @Test
    void testSaveUserInfo_NewUserForSuperAdmin() {
        // Arrange
        String username = "testUser";
        SecurityContextHolder.setContext(securityContext);
        when(userInfoService.getUserInfo(anyString(), eq(AuthType.SAML))).thenReturn(null);
        List<GrantedAuthority> authorities = List.of(
                (GrantedAuthority) () -> Constant.ROLE_SUPERADMIN
        );
        when(userInfoService.getAuthorities(any())).thenReturn(authorities);
        UserInfo savedUserInfo = new UserInfo();
        savedUserInfo.setUsername(username);
        savedUserInfo.setAuthType(AuthType.SAML);
        savedUserInfo.setAuthorities(new ArrayList<>());
        savedUserInfo.setEmailAddress("");
        savedUserInfo.setProjectsAccess(Collections.emptyList());
        
        when(userInfoService.save(any(UserInfo.class))).thenReturn(savedUserInfo);

        // Act
        ServiceResponse result = userService.saveUserInfo(username);
        
        // Assert
        assertNotNull(result);
        assertEquals(true, result.getSuccess());
        assertEquals("User information saved successfully", result.getMessage());
        UserResponseDTO responseDTO = (UserResponseDTO) result.getData();
        assertEquals(username, responseDTO.getUsername());
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
        ServiceResponse result = userService.saveUserInfo(username);
        
        // Assert
        assertNotNull(result);
        assertEquals(true, result.getSuccess());
        assertEquals("User already exists", result.getMessage());
        UserResponseDTO responseDTO = (UserResponseDTO) result.getData();
        assertEquals(username, responseDTO.getUsername());
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
