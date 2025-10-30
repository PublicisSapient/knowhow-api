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

import com.publicissapient.kpidashboard.apis.bitbucket.dto.ScmBranchDTO;
import com.publicissapient.kpidashboard.apis.bitbucket.dto.ScmRepositoryDTO;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmRepositoryService;
import com.publicissapient.kpidashboard.common.model.scm.ScmBranch;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import com.publicissapient.kpidashboard.common.repository.scm.ScmReposRepository;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ScmRepositoryServiceImpl implements ScmRepositoryService {

	private final ScmReposRepository scmReposRepository;

	public ScmRepositoryServiceImpl(ScmReposRepository scmReposRepository) {
		this.scmReposRepository = scmReposRepository;
	}

	@Override
	public List<ScmRepositoryDTO> getScmRepositoryListByConnectionId(ObjectId connectionId) {
		List<ScmRepos> scmReposList = scmReposRepository.findAllByConnectionId(connectionId);
		AtomicInteger repoOrder = new AtomicInteger(1);
		return scmReposList.stream()
				.sorted(Comparator.comparing(ScmRepos::getLastUpdated, Comparator.nullsLast(Comparator.reverseOrder())))
				.map(repo -> mapToRepositoryDTO(repo, repoOrder.getAndIncrement())).toList();

	}

	@Override
	public boolean triggerScmReposFetcher(String connectionId) {
		return false;
	}

	private ScmRepositoryDTO mapToRepositoryDTO(ScmRepos scmRepo, int order) {
		AtomicInteger branchOrder = new AtomicInteger(1);
		List<ScmBranchDTO> sortedBranches = scmRepo.getBranchList().stream()
				.sorted(Comparator.comparing(ScmBranch::getLastUpdatedAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.map(branch -> mapToBranchDTO(branch, branchOrder.getAndIncrement())).toList();
		return ScmRepositoryDTO.builder().repositoryName(scmRepo.getRepositoryName()).repositoryUrl(scmRepo.getUrl())
				.connectionId(scmRepo.getConnectionId()).lastUpdatedTimestamp(scmRepo.getLastUpdated()).order(order)
				.branchList(sortedBranches).build();

	}

	private ScmBranchDTO mapToBranchDTO(ScmBranch scmBranch, int order) {
		return ScmBranchDTO.builder().branchName(scmBranch.getName()).lastUpdatedTimestamp(scmBranch.getLastUpdatedAt())
				.order(order).build();
	}
}