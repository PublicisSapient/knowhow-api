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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.bitbucket.dto.ScmRepositoryDTO;
import com.publicissapient.kpidashboard.common.model.scm.ScmBranch;
import com.publicissapient.kpidashboard.common.model.scm.ScmRepos;
import com.publicissapient.kpidashboard.common.repository.scm.ScmReposRepository;

@RunWith(MockitoJUnitRunner.class)
public class ScmRepositoryServiceImplTest {

	@Mock
	private ScmReposRepository scmReposRepository;

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

		List<ScmRepositoryDTO> result = scmRepositoryService.getScmRepositoryListByConnectionId(connectionId);

		assertEquals(0, result.size());
	}

	@Test
	public void testGetScmRepositoryListByConnectionId_SingleRepo() {
		ScmBranch branch = ScmBranch.builder().name("main").lastUpdatedAt(1000L).build();
		ScmRepos repo = ScmRepos.builder()
				.repositoryName("repo1")
				.url("http://repo1.com")
				.connectionId(connectionId)
				.lastUpdated(2000L)
				.branchList(Arrays.asList(branch))
				.build();

		when(scmReposRepository.findAllByConnectionId(connectionId)).thenReturn(Arrays.asList(repo));

		List<ScmRepositoryDTO> result = scmRepositoryService.getScmRepositoryListByConnectionId(connectionId);

		assertEquals(1, result.size());
		assertEquals("repo1", result.get(0).getRepositoryName());
		assertEquals(1, result.get(0).getOrder());
		assertEquals(1, result.get(0).getBranchList().size());
	}

	@Test
	public void testGetScmRepositoryListByConnectionId_SortedByLastUpdated() {
		ScmRepos repo1 = ScmRepos.builder()
				.repositoryName("repo1")
				.lastUpdated(1000L)
				.branchList(Collections.emptyList())
				.build();
		ScmRepos repo2 = ScmRepos.builder()
				.repositoryName("repo2")
				.lastUpdated(3000L)
				.branchList(Collections.emptyList())
				.build();

		when(scmReposRepository.findAllByConnectionId(connectionId)).thenReturn(Arrays.asList(repo1, repo2));

		List<ScmRepositoryDTO> result = scmRepositoryService.getScmRepositoryListByConnectionId(connectionId);

		assertEquals(2, result.size());
		assertEquals("repo2", result.get(0).getRepositoryName());
		assertEquals("repo1", result.get(1).getRepositoryName());
	}

	@Test
	public void testTriggerScmReposFetcher() {
		assertEquals(false, scmRepositoryService.triggerScmReposFetcher("connectionId"));
	}
}
