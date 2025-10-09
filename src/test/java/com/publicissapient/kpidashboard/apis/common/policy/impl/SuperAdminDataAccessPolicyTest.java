package com.publicissapient.kpidashboard.apis.common.policy.impl;

import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import com.publicissapient.kpidashboard.common.repository.rbac.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SuperAdminDataAccessPolicyTest {

    @InjectMocks
    private SuperAdminDataAccessPolicy policy;

    @Mock
    private UserInfoRepository userRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldReturnAllUsers() {
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
    }

    @Test
    void shouldReturnEmptyListIfNoUsers() {
        // given
        when(userRepository.findAll()).thenReturn(List.of());

        // when
        List<UserInfo> result = policy.getAccessibleMembers(Constant.ROLE_SUPERADMIN);

        // then
        assertEquals(0, result.size());
        verify(userRepository, times(1)).findAll();
    }
}