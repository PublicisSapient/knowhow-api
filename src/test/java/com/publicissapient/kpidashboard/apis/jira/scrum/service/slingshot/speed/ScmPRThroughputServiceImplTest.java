package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ProjectFilter;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;

@RunWith(MockitoJUnitRunner.class)
public class ScmPRThroughputServiceImplTest {

	@InjectMocks private ScmPRThroughputServiceImpl scmPRThroughputService;

	@Mock private ConfigHelperService configHelperService;

	@Mock private KpiHelperService kpiHelperService;

	@Mock private ScmKpiHelperService scmKpiHelperService;

	private KpiRequest kpiRequest;
	private Node projectNode;
	private List<ScmCommits> scmCommitsList;
	private List<Assignee> assigneeList;

	@Before
	public void setUp() {
		kpiRequest = new KpiRequest();
		List<KpiElement> kpiElements = new ArrayList<>();
		kpiElements.add(new KpiElement());
		kpiRequest.setKpiList(kpiElements);

		projectNode = createProjectNode();
		scmCommitsList = createScmCommits();
		assigneeList = createAssignees();
	}

	@Test
	public void testGetQualifierType() {
		assertThat(scmPRThroughputService.getQualifierType(), equalTo(KPICode.PR_THROUGHPUT.name()));
	}

	@Test
	public void testCalculateKPIMetrics() {
		Map<String, Object> testMap = new HashMap<>();
		assertThat(scmPRThroughputService.calculateKPIMetrics(testMap), equalTo(null));
	}

	@Test
	public void testCalculateThresholdValue() {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setThresholdValueKPI157("5.0");
		Double threshold = scmPRThroughputService.calculateThresholdValue(fieldMapping);
		assertNotNull(threshold);
	}

	@Test
	public void testFetchKPIDataFromDb() {
		List<Node> leafNodeList = Arrays.asList(projectNode);

		when(scmKpiHelperService.getCommitDetails(any(ObjectId.class), any()))
				.thenReturn(scmCommitsList);
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any(ObjectId.class)))
				.thenReturn(assigneeList);

		Map<String, Object> result =
				scmPRThroughputService.fetchKPIDataFromDb(leafNodeList, null, null, kpiRequest);

		assertNotNull(result);
		assertThat(result.containsKey("assigneeSet"), equalTo(true));
		assertThat(result.containsKey("commitList"), equalTo(true));
	}

	private Node createProjectNode() {
		Node node = new Node();
		node.setId("project123");
		node.setName("Test Project");

		ProjectFilter projectFilter =
				new ProjectFilter("project123", "Test Project", new ObjectId("6335363749794a18e8a4479b"));

		node.setProjectFilter(projectFilter);
		return node;
	}

	private List<ScmCommits> createScmCommits() {
		List<ScmCommits> commits = new ArrayList<>();

		ScmCommits commit1 = new ScmCommits();
		commit1.setProcessorItemId(new ObjectId());
		commit1.setRevisionNumber("rev1");
		commit1.setAuthor("test.user@example.com");
		commit1.setCommitTimestamp(System.currentTimeMillis());
		commit1.setIsMergeCommit(true);
		commits.add(commit1);

		ScmCommits commit2 = new ScmCommits();
		commit2.setProcessorItemId(new ObjectId());
		commit2.setRevisionNumber("rev2");
		commit2.setAuthor("another.user@example.com");
		commit2.setCommitTimestamp(System.currentTimeMillis());
		commit2.setIsMergeCommit(false);
		commits.add(commit2);

		return commits;
	}

	private List<Assignee> createAssignees() {
		List<Assignee> assignees = new ArrayList<>();

		Set<String> emails1 = new HashSet<>();
		emails1.add("test.user@example.com");
		Assignee assignee1 = new Assignee("testuser", "Test User", emails1);
		assignees.add(assignee1);

		Set<String> emails2 = new HashSet<>();
		emails2.add("another.user@example.com");
		Assignee assignee2 = new Assignee("anotheruser", "Another User", emails2);
		assignees.add(assignee2);

		return assignees;
	}
}
