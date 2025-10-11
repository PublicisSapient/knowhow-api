package com.publicissapient.kpidashboard.apis.jira.service;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepositoryCustom;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SprintDetailsServiceImpl implements SprintDetailsService {

	private final SprintRepository sprintRepository;

	private final SprintRepositoryCustom sprintRepositoryCustom;

	@Override
	public List<SprintDetails> getSprintDetails(String basicProjectConfigId) {
		return sprintRepository.findByBasicProjectConfigId(new ObjectId(basicProjectConfigId));
	}

	@Override
	public List<SprintDetails> getSprintDetailsByIds(List<String> sprintIds) {
		return sprintRepository.findBySprintIDIn(sprintIds);
	}

	@Override
	public List<SprintDetails> findByBasicProjectConfigIdInByCompletedDateDesc(List<ObjectId> basicProjectConfigIds,
			int limit) {
		return sprintRepositoryCustom.findByBasicProjectConfigIdInOrderByCompletedDateDesc(basicProjectConfigIds, limit);
	}
}
