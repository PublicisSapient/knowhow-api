/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *    http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.githubaction.rest;

import java.util.List;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.githubaction.model.GithubActionRepoDTO;
import com.publicissapient.kpidashboard.apis.githubaction.model.GithubActionWorkflowsDTO;
import com.publicissapient.kpidashboard.apis.githubaction.service.GithubActionToolConfigServiceImpl;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

@RestController
@RequiredArgsConstructor
@Tag(name = "GitHub Action API", description = "APIs for GitHub Action Tool Configurations")
public class GithubActionController {

	private final GithubActionToolConfigServiceImpl githubActionToolConfigService;

	@PostMapping(
			value = "/githubAction/workflowName/{connectionId}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ServiceResponse getGithubActionWorkflows(
			@PathVariable String connectionId, @RequestBody GithubActionRepoDTO repoName) {
		ServiceResponse response;
		List<GithubActionWorkflowsDTO> workFlowList =
				githubActionToolConfigService.getGitHubWorkFlowList(
						connectionId, repoName.getRepositoryName());
		if (CollectionUtils.isEmpty(workFlowList)) {
			response = new ServiceResponse(false, "No workflow details found", null);
		} else {
			response = new ServiceResponse(true, "FETCHED_SUCCESSFULLY", workFlowList);
		}
		return response;
	}
}
