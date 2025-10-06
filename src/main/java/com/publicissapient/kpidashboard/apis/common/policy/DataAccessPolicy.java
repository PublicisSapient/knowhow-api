package com.publicissapient.kpidashboard.apis.common.policy;

import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;

import java.util.List;

public interface DataAccessPolicy {
    List<UserInfo> getAccessibleMembers(String userName);
}
