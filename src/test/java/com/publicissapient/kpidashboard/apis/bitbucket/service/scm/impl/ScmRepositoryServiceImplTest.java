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

import com.publicissapient.kpidashboard.apis.bitbucket.dto.ScmConnectionMetaDataDTO;
import com.publicissapient.kpidashboard.common.model.scm.ScmBranch;
import com.publicissapient.kpidashboard.common.model.scm.ScmConnectionTraceLog;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import com.publicissapient.kpidashboard.common.repository.scm.ScmConnectionTraceLogRepository;
import com.publicissapient.kpidashboard.common.repository.scm.ScmReposRepository;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ScmRepositoryServiceImplTest {

	@Mock
	private ScmReposRepository scmReposRepository;

	@Mock
	private ScmConnectionTraceLogRepository scmConnectionTraceLogRepository;

	@InjectMocks
	private ScmRepositoryServiceImpl scmRepositoryService;

	private ObjectId connectionId;

	@Before
	public void setUp() {
		connectionId = new ObjectId();
	}

	@Test
	public void testGetScmRepositoryListByConnectionId_EmptyList() {
		when(scmReposRepository.findAllByConnectionId(connectionId)).thenReturn(Collections.emptyList());
		when(scmConnectionTraceLogRepository.findByConnectionId(connectionId.toString())).thenReturn(Optional.empty());
		ScmConnectionMetaDataDTO result = scmRepositoryService.getScmRepositoryListByConnectionId(connectionId);

		assertEquals(0, result.getRepositories().size());
	}

	@Test
	public void testGetScmRepositoryListByConnectionId_SingleRepo() {
		ScmBranch branch = ScmBranch.builder().name("main").lastUpdatedAt(1000L).build();
		ScmRepos repo = ScmRepos.builder().repositoryName("repo1").url("http://repo1.com").connectionId(connectionId)
				.lastUpdated(2000L).branchList(Arrays.asList(branch)).build();
		ScmConnectionTraceLog scmConnectionTraceLog = new ScmConnectionTraceLog();
		scmConnectionTraceLog.setConnectionId(connectionId.toString());
		scmConnectionTraceLog.setLastSyncTimeTimeStamp(2000L);
		scmConnectionTraceLog.setFetchSuccessful(true);
		scmConnectionTraceLog.setOnGoing(false);

		when(scmConnectionTraceLogRepository.findByConnectionId(connectionId.toString()))
				.thenReturn(Optional.of(scmConnectionTraceLog));
		when(scmReposRepository.findAllByConnectionId(connectionId)).thenReturn(Arrays.asList(repo));
		when(scmConnectionTraceLogRepository.findByConnectionId(connectionId.toString())).thenReturn(Optional.empty());

		ScmConnectionMetaDataDTO result = scmRepositoryService.getScmRepositoryListByConnectionId(connectionId);

		assertEquals(1, result.getRepositories().size());
		assertEquals("repo1", result.getRepositories().get(0).getRepositoryName());
		assertEquals(1, result.getRepositories().get(0).getOrder());
		assertEquals(1, result.getRepositories().get(0).getBranchList().size());
	}

	@Test
	public void testGetScmRepositoryListByConnectionId_SortedByLastUpdated() {
		ScmRepos repo1 = ScmRepos.builder().repositoryName("repo1").lastUpdated(1000L)
				.branchList(Collections.emptyList()).build();
		ScmRepos repo2 = ScmRepos.builder().repositoryName("repo2").lastUpdated(3000L)
				.branchList(Collections.emptyList()).build();

		when(scmConnectionTraceLogRepository.findByConnectionId(connectionId.toString())).thenReturn(Optional.empty());
		when(scmReposRepository.findAllByConnectionId(connectionId)).thenReturn(Arrays.asList(repo1, repo2));

		ScmConnectionMetaDataDTO result = scmRepositoryService.getScmRepositoryListByConnectionId(connectionId);

		assertEquals(2, result.getRepositories().size());
		assertEquals("repo2", result.getRepositories().get(0).getRepositoryName());
		assertEquals("repo1", result.getRepositories().get(1).getRepositoryName());
	}

}
