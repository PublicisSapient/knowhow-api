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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.rbac.AccessItem;
import com.publicissapient.kpidashboard.common.model.rbac.AccessNode;
import com.publicissapient.kpidashboard.common.model.rbac.ProjectsAccess;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.common.service.UserInfoService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.usermanagement.dto.response.UserResponseDTO;
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

    private final HierarchyLevelServiceImpl hierarchyLevelService;
    private static final String USER_NAME_CANNOT_NULL = "Username cannot be null or empty";
    private static final String DOMAIN_NAME = "@publicisgroupe.net";
    @Override
    public ServiceResponse saveUserInfo(String username) {

        if (StringUtils.isEmpty(username)) {
            log.error(USER_NAME_CANNOT_NULL);
            throw new IllegalArgumentException(USER_NAME_CANNOT_NULL);
        }
        log.info("Saving user information for username: {}", username);
        // Check if user already exists with SAML auth type
        UserInfo existingUser = userInfoService.getUserInfo(username, AuthType.SAML);
        UserInfo savedUserInfo;
        String responseMessage;

        if (!Objects.isNull(existingUser)) {
            log.info("User already exists with username: {} and authType: {}", username, AuthType.SAML);
            savedUserInfo = existingUser;
            responseMessage = "User already exists";
        } else {
            // Create new user with SAML auth type
            UserInfo userInfo = new UserInfo();
            userInfo.setUsername(username);
            userInfo.setAuthType(AuthType.SAML);
            userInfo.setAuthorities(new ArrayList<>());
            userInfo.setEmailAddress(username.concat(DOMAIN_NAME));

            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            List<String> roles = authentication.getAuthorities()
                    .stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());


            if(roles.contains(Constant.ROLE_SUPERADMIN))
                userInfo.setProjectsAccess(Collections.emptyList());
            else if(roles.contains(Constant.ROLE_PROJECT_ADMIN)){

                UserInfo fullUserDoc = userInfoService.getUserInfo(authentication.getName());
                List<ProjectsAccess> mappedProjects = fullUserDoc.getProjectsAccess().stream()
                        .map(projectsAccess ->
                        {
                            ProjectsAccess copy = new ProjectsAccess();
                            copy.setRole(Constant.ROLE_GUEST);

                            List<AccessNode> nodes = projectsAccess.getAccessNodes()
                                    .stream()
                                    .map(node -> {
                                        AccessNode newNode = new AccessNode();
                                        newNode.setAccessLevel(nextAccessLevel(node.getAccessLevel()));

                                        List<AccessItem> items = node.getAccessItems()
                                                .stream()
                                                .map(item -> {
                                                    AccessItem newItem = new AccessItem();
                                                    newItem.setItemId(item.getItemId());
                                                    newItem.setItemName(item.getItemName()); // assign Viewer
                                                    return newItem;
                                                })
                                                .collect(Collectors.toList());

                                        newNode.setAccessItems(items);
                                        return newNode;
                                    })
                                    .collect(Collectors.toList());

                            copy.setAccessNodes(nodes);
                            return copy;
                        }).collect(Collectors.toList());

                userInfo.setProjectsAccess(mappedProjects);
            }
            
            log.info("Saving new user with username: {} and authType: {}", username, AuthType.SAML);
            savedUserInfo = userInfoService.save(userInfo);
            responseMessage = "User information saved successfully";
        }
        
        // Create response DTO
        UserResponseDTO responseDTO = new UserResponseDTO();
        responseDTO.setUsername(savedUserInfo.getUsername());
        
        // Return service response with appropriate message
        return new ServiceResponse(true, responseMessage, responseDTO);
    }

    public String nextAccessLevel(String currentLevel) {
        List<HierarchyLevel> hierarchyLevels = hierarchyLevelService.getTopHierarchyLevels();
        int nextIndex = IntStream.range(0, hierarchyLevels.size())
                .filter(i -> hierarchyLevels.get(i).getHierarchyLevelId().equalsIgnoreCase(currentLevel))
                .findFirst()
                .orElse(-1);

        if (nextIndex == -1 || nextIndex >= hierarchyLevels.size() - 1) {
            return Constant.PROJECT.toLowerCase();
        }

        return hierarchyLevels.get(nextIndex + 1).getHierarchyLevelId();
    }
}
