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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.bitbucket.dto.ScmBranchDTO;
import com.publicissapient.kpidashboard.apis.bitbucket.dto.ScmConnectionMetaDataDTO;
import com.publicissapient.kpidashboard.apis.bitbucket.dto.ScmRepositoryDTO;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmRepositoryService;
import com.publicissapient.kpidashboard.common.model.scm.ScmBranch;
import com.publicissapient.kpidashboard.common.model.scm.ScmConnectionTraceLog;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import com.publicissapient.kpidashboard.common.repository.scm.ScmConnectionTraceLogRepository;
import com.publicissapient.kpidashboard.common.repository.scm.ScmReposRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service implementation for SCM repository operations. Provides methods to fetch repository
 * metadata and map SCM entities to DTOs.
 */
@Service
@RequiredArgsConstructor
public class ScmRepositoryServiceImpl implements ScmRepositoryService {

	private final ScmReposRepository scmReposRepository;
	private final ScmConnectionTraceLogRepository scmConnectionTraceLogRepository;

	/**
	 * Retrieves SCM repository metadata for a given connection ID. Includes execution status,
	 * timestamps, and a sorted list of repositories.
	 *
	 * @param connectionId The ObjectId of the SCM connection.
	 * @return ScmConnectionMetaDataDTO containing repository details and execution metadata.
	 */
	@Override
	public ScmConnectionMetaDataDTO getScmRepositoryListByConnectionId(ObjectId connectionId) {
		ScmConnectionMetaDataDTO scmConnectionMetaDataDTO = new ScmConnectionMetaDataDTO();
		Optional<ScmConnectionTraceLog> scmConnectionTraceLog =
				getScmConnectionTraceLogRepository(connectionId.toString());
		if (scmConnectionTraceLog.isPresent()) {
			scmConnectionMetaDataDTO.setExecutionSuccess(scmConnectionTraceLog.get().isFetchSuccessful());
			scmConnectionMetaDataDTO.setExecutionEndedAt(
					scmConnectionTraceLog.get().getLastSyncTimeTimeStamp());
			scmConnectionMetaDataDTO.setInitialExecutionOngoing(scmConnectionTraceLog.get().isOnGoing());
		}
		List<ScmRepos> scmReposList = scmReposRepository.findAllByConnectionId(connectionId);
		AtomicInteger repoOrder = new AtomicInteger(1);
		scmConnectionMetaDataDTO.setRepositories(
				scmReposList.stream()
						.sorted(
								Comparator.comparing(
										ScmRepos::getLastUpdated, Comparator.nullsLast(Comparator.reverseOrder())))
						.map(repo -> mapToRepositoryDTO(repo, repoOrder.getAndIncrement()))
						.toList());
		return scmConnectionMetaDataDTO;
	}

	/**
	 * Maps an ScmRepos entity to ScmRepositoryDTO, including sorted branches.
	 *
	 * @param scmRepo The SCM repository entity.
	 * @param order The display order of the repository.
	 * @return ScmRepositoryDTO representing the repository details.
	 */
	private ScmRepositoryDTO mapToRepositoryDTO(ScmRepos scmRepo, int order) {
		AtomicInteger branchOrder = new AtomicInteger(1);
		List<ScmBranchDTO> sortedBranches =
				scmRepo.getBranchList().stream()
						.sorted(
								Comparator.comparing(
										ScmBranch::getLastUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
						.map(branch -> mapToBranchDTO(branch, branchOrder.getAndIncrement()))
						.toList();
		return ScmRepositoryDTO.builder()
				.repositoryName(scmRepo.getRepositoryName())
				.repositoryUrl(scmRepo.getUrl())
				.connectionId(scmRepo.getConnectionId())
				.lastUpdatedTimestamp(scmRepo.getLastUpdated())
				.order(order)
				.branchList(sortedBranches)
				.build();
	}

	/**
	 * Retrieves SCM connection trace log for a given connection ID.
	 *
	 * @param connectionId The connection ID as a String.
	 * @return Optional containing ScmConnectionTraceLog if found.
	 */
	private Optional<ScmConnectionTraceLog> getScmConnectionTraceLogRepository(String connectionId) {
		return scmConnectionTraceLogRepository.findByConnectionId(connectionId);
	}

	/**
	 * Maps an ScmBranch entity to ScmBranchDTO.
	 *
	 * @param scmBranch The SCM branch entity.
	 * @param order The display order of the branch.
	 * @return ScmBranchDTO representing branch details.
	 */
	private ScmBranchDTO mapToBranchDTO(ScmBranch scmBranch, int order) {
		return ScmBranchDTO.builder()
				.branchName(scmBranch.getName())
				.lastUpdatedTimestamp(scmBranch.getLastUpdatedAt())
				.order(order)
				.build();
	}
}
