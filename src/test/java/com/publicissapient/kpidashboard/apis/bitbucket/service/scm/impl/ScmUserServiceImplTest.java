/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.repotools.service.RepoToolsConfigServiceImpl;
import com.publicissapient.kpidashboard.common.model.scm.User;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ScmUserServiceImplTest {

    @Mock
    private RepoToolsConfigServiceImpl repoToolsConfigService;

    @Mock
    private ScmKpiHelperService scmKpiHelperService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CustomApiConfig customApiConfig;

    @InjectMocks
    private ScmUserServiceImpl scmUserService;

    private static final String PROJECT_CONFIG_ID = "507f1f77bcf86cd799439011";
    private ObjectMapper realObjectMapper;

    private User userWithEmail;
    private User userWithoutEmail;
    private User userWithEmptyEmail;
    private User userWithUsername;
    private JsonNode expectedRepoToolResult;

    @Before
    public void setUp() {
        realObjectMapper = new ObjectMapper();

        setupTestUsers();

        expectedRepoToolResult = realObjectMapper.createArrayNode()
                .add("user1@example.com")
                .add("user2@example.com");
    }

    private void setupTestUsers() {
        userWithEmail = new User();
        userWithEmail.setEmail("user1@example.com");
        userWithEmail.setUsername("user1");

        userWithoutEmail = new User();
        userWithoutEmail.setEmail(null);
        userWithoutEmail.setUsername("user2");

        userWithEmptyEmail = new User();
        userWithEmptyEmail.setEmail("");
        userWithEmptyEmail.setUsername("user3");

        userWithUsername = new User();
        userWithUsername.setEmail(null);
        userWithUsername.setUsername("user1");
    }

    @Test
    public void testGetScmUsersRepoToolEnabledSuccess() {
        when(customApiConfig.isRepoToolEnabled()).thenReturn(true);
        when(repoToolsConfigService.getProjectRepoToolMembers(PROJECT_CONFIG_ID))
                .thenReturn(expectedRepoToolResult);

        JsonNode result = scmUserService.getScmToolUsersMailList(PROJECT_CONFIG_ID);

        assertNotNull(result);
        assertEquals(expectedRepoToolResult, result);
        verify(customApiConfig).isRepoToolEnabled();
        verify(repoToolsConfigService).getProjectRepoToolMembers(PROJECT_CONFIG_ID);
        verifyNoInteractions(scmKpiHelperService, objectMapper);
    }

    @Test
    public void testGetScmUsersRepoDisabled() {
        User user2 = new User();
        user2.setEmail("user2@example.com");
        user2.setUsername("user2");

        List<User> scmUsers = Arrays.asList(userWithEmail, user2);
        List<String> expectedIdentifiers = Arrays.asList("user1@example.com", "user2@example.com");
        JsonNode expectedResult = realObjectMapper.valueToTree(expectedIdentifiers);

        when(customApiConfig.isRepoToolEnabled()).thenReturn(false);
        when(scmKpiHelperService.getScmUser(any(ObjectId.class))).thenReturn(scmUsers);
        when(objectMapper.valueToTree(expectedIdentifiers)).thenReturn(expectedResult);

        JsonNode result = scmUserService.getScmToolUsersMailList(PROJECT_CONFIG_ID);

        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(customApiConfig).isRepoToolEnabled();
        verify(scmKpiHelperService).getScmUser(any(ObjectId.class));
        verify(objectMapper).valueToTree(expectedIdentifiers);
        verifyNoInteractions(repoToolsConfigService);
    }

    @Test
    public void testGetScmUsersRepoDisabledName() {
        List<User> scmUsers = Arrays.asList(userWithUsername, userWithoutEmail);
        List<String> expectedIdentifiers = Arrays.asList("user1", "user2");
        JsonNode expectedResult = realObjectMapper.valueToTree(expectedIdentifiers);

        when(customApiConfig.isRepoToolEnabled()).thenReturn(false);
        when(scmKpiHelperService.getScmUser(any(ObjectId.class))).thenReturn(scmUsers);
        when(objectMapper.valueToTree(expectedIdentifiers)).thenReturn(expectedResult);

        JsonNode result = scmUserService.getScmToolUsersMailList(PROJECT_CONFIG_ID);

        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(customApiConfig).isRepoToolEnabled();
        verify(scmKpiHelperService).getScmUser(any(ObjectId.class));
        verify(objectMapper).valueToTree(expectedIdentifiers);
    }

    @Test
    public void testGetScmUsersRepoDisabledEmpty() {
        List<User> emptyScmUsers = Collections.emptyList();
        List<String> expectedIdentifiers = Collections.emptyList();
        JsonNode expectedResult = realObjectMapper.valueToTree(expectedIdentifiers);

        when(customApiConfig.isRepoToolEnabled()).thenReturn(false);
        when(scmKpiHelperService.getScmUser(any(ObjectId.class))).thenReturn(emptyScmUsers);
        when(objectMapper.valueToTree(expectedIdentifiers)).thenReturn(expectedResult);

        JsonNode result = scmUserService.getScmToolUsersMailList(PROJECT_CONFIG_ID);

        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(customApiConfig).isRepoToolEnabled();
        verify(scmKpiHelperService).getScmUser(any(ObjectId.class));
        verify(objectMapper).valueToTree(expectedIdentifiers);
    }
}