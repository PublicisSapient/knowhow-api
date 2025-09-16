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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmUserService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.repotools.service.RepoToolsConfigServiceImpl;
import com.publicissapient.kpidashboard.common.model.scm.User;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ScmUserServiceImpl implements ScmUserService {

	private final RepoToolsConfigServiceImpl repoToolsConfigService;
	private final ScmKpiHelperService scmKpiHelperService;
	private final ObjectMapper objectMapper;
	private final CustomApiConfig customApiConfig;

	@Override
	public JsonNode getScmToolUsersMailList(String projectConfigId) {
		if (customApiConfig.isRepoToolEnabled()) {
			return repoToolsConfigService.getProjectRepoToolMembers(projectConfigId);
		} else {
			List<User> scmUsers = scmKpiHelperService.getScmUser(new ObjectId(projectConfigId));
			Set<String> userIdentifiers = scmUsers.stream()
					.map(user -> user.getEmail() != null ? user.getEmail() : user.getUsername())
					.collect(Collectors.toSet());
			return objectMapper.valueToTree(userIdentifiers);
		}
	}
}
