package com.publicissapient.kpidashboard.apis.common.service.impl;

import com.publicissapient.kpidashboard.apis.common.policy.DataAccessPolicy;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DataAccessService {

    private final Map<String, DataAccessPolicy> policies;

    public DataAccessService(Map<String, DataAccessPolicy> policies) {
        this.policies = policies;
    }

    public List<UserInfo> getMembersForUser(String role,String user) {
        DataAccessPolicy policy = policies.get(role);

        if (policy == null) {
            throw new IllegalArgumentException("No policy defined for role: " + user);
        }

        return policy.getAccessibleMembers(user);
    }
}
