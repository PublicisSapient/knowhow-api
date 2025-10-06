package com.publicissapient.kpidashboard.apis.common.policy.impl;

import com.publicissapient.kpidashboard.apis.common.policy.DataAccessPolicy;
import com.publicissapient.kpidashboard.common.model.rbac.AccessItem;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;
import com.publicissapient.kpidashboard.common.repository.rbac.UserInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component("ROLE_PROJECT_ADMIN")
public class AdminDataAccessPolicy implements DataAccessPolicy {
    @Autowired
    private UserInfoRepository userInfoRepository;
    @Override
    public List<UserInfo> getAccessibleMembers(String userName) {
        UserInfo fullUserDoc = userInfoRepository.findByUsername(userName);
        if (fullUserDoc == null || fullUserDoc.getProjectsAccess().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> accessibleItemIds = fullUserDoc.getProjectsAccess()
                .stream()
                .flatMap(p -> p.getAccessNodes().stream())
                .flatMap(n -> n.getAccessItems().stream())
                .map(AccessItem::getItemId)
                .toList();

        if (accessibleItemIds.isEmpty()) {
            return Collections.emptyList();
        }

        return userInfoRepository.findUsersByItemIds(accessibleItemIds);
    }
}
