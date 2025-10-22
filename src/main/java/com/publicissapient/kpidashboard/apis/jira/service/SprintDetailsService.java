package com.publicissapient.kpidashboard.apis.jira.service;

import java.util.List;

import org.bson.types.ObjectId;

import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;

public interface SprintDetailsService {

	List<SprintDetails> getSprintDetails(String basicProjectConfigId);

	List<SprintDetails> getSprintDetailsByIds(List<String> sprintIds);

	List<SprintDetails> findByBasicProjectConfigIdInByCompletedDateDesc(
			List<ObjectId> basicProjectConfigIds, int limit);
}
