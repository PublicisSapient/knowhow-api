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

    public List<UserInfo> getMembersForUser(List<String> providedRole,String user) {

        for (Map.Entry<String, DataAccessPolicy> entry : policies.entrySet()) {
            String role = entry.getKey();
            if (providedRole.contains(role)) {
                DataAccessPolicy policy = entry.getValue();
                return policy.getAccessibleMembers(user);
            }
        }
        throw new IllegalArgumentException("No policy defined for role: " + user);

    }
}
