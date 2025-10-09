package com.publicissapient.kpidashboard.apis.common.policy.impl;

import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.common.model.rbac.AccessItem;
import com.publicissapient.kpidashboard.common.model.rbac.AccessNode;
import com.publicissapient.kpidashboard.common.model.rbac.ProjectsAccess;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import com.publicissapient.kpidashboard.common.repository.rbac.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
public class ProjectAdminDataAccessPolicyTest {
    @InjectMocks
    private ProjectAdminDataAccessPolicy policy;

    @Mock
    private UserInfoRepository userRepository;
    String userName;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userName ="tempName";
    }

  /*  @Test
    void shouldReturnAllUsers() {

        String userName ="tempName";
        UserInfo fullUserDoc = null;
        when(userRepository.findByUsername(userName)).thenReturn(fullUserDoc);
        // given
        UserInfo userInfo = new UserInfo();
        userInfo.setUsername("user1");
        List<UserInfo> allUsers = List.of(userInfo, new UserInfo());
        when(userRepository.findAll()).thenReturn(allUsers);

        // when
        List<UserInfo> result = policy.getAccessibleMembers(Constant.ROLE_SUPERADMIN);

        // then
        assertEquals(2, result.size());
        assertEquals("user1", result.get(0).getUsername());
        verify(userRepository, times(1)).findAll();
    }*/

    @Test
    void shouldReturnEmptyListIfNoUsers() {
        // given
        when(userRepository.findByUsername(userName)).thenReturn(null);

        // when
        List<UserInfo> result = policy.getAccessibleMembers(userName);

        // then
        assertEquals(0, result.size());
        verify(userRepository, times(1)).findByUsername(userName);
    }

    @Test
    void shouldReturnEmptyListIfNoProjectIsAvailable() {

        UserInfo userInfo = new UserInfo();
        userInfo.setUsername(userName);
        userInfo.setProjectsAccess(new ArrayList<>());
        // given
        when(userRepository.findByUsername(userName)).thenReturn(userInfo);

        // when
        List<UserInfo> result = policy.getAccessibleMembers(userName);

        // then
        assertEquals(0, result.size());
        verify(userRepository, times(1)).findByUsername(userName);
    }

    @Test
    void shouldReturnEmptyListIfNoProjectAdminRoleIsAvailable() {

        ProjectsAccess access = new ProjectsAccess();
        access.setRole(Constant.ROLE_GUEST);
        access.setAccessNodes(new ArrayList<>());

        UserInfo userInfo = new UserInfo();
        userInfo.setUsername(userName);
        userInfo.setProjectsAccess(List.of(access));
        // given
        when(userRepository.findByUsername(userName)).thenReturn(userInfo);

        // when
        List<UserInfo> result = policy.getAccessibleMembers(userName);

        // then
        assertEquals(0, result.size());
        verify(userRepository, times(1)).findByUsername(userName);
    }

    @Test
    void shouldReturnUserList() {

        AccessItem accessItem = new AccessItem();
        accessItem.setItemId("tempItemId1");
        AccessItem accessItem2 = new AccessItem();
        accessItem2.setItemId("tempItemId2");
        AccessNode accessNode = new AccessNode();
        accessNode.setAccessLevel("project");
        accessNode.setAccessItems(List.of(accessItem,accessItem2));
        ProjectsAccess access = new ProjectsAccess();
        access.setRole(Constant.ROLE_PROJECT_ADMIN);
        access.setAccessNodes(List.of(accessNode));

        UserInfo userInfo = new UserInfo();
        userInfo.setUsername(userName);
        userInfo.setProjectsAccess(List.of(access));
        // given
        when(userRepository.findByUsername(userName)).thenReturn(userInfo);
        List<UserInfo> userInfoList = new ArrayList<>();
        userInfoList.add(userInfo);
        List<String> items = accessNode.getAccessItems().stream().map(AccessItem::getItemId).toList();;
        when(userRepository.findUsersByItemIds(items)).thenReturn(userInfoList);

        // when
        List<UserInfo> result = policy.getAccessibleMembers(userName);

        // then
        assertEquals(1, result.size());
        verify(userRepository, times(1)).findByUsername(userName);
    }
}

