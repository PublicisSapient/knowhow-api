package com.publicissapient.kpidashboard.apis.common.policy.impl;

import com.publicissapient.kpidashboard.apis.common.policy.DataAccessPolicy;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import com.publicissapient.kpidashboard.common.repository.rbac.UserInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("ROLE_SUPERADMIN")
public class SuperAdminDataAccessPolicy implements DataAccessPolicy {
    @Autowired
    private UserInfoRepository userInfoRepository;
    @Override
    public List<UserInfo> getAccessibleMembers(String userName) {
        return userInfoRepository.findAll();
    }
}
