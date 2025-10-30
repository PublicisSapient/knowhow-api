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
import java.util.stream.Collectors;

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
				.map(repo -> mapToRepositoryDTO(repo, repoOrder.getAndIncrement())).collect(Collectors.toList());

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