/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.kpiintegration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketServiceKanbanR;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.HierachyLevelFactory;
import com.publicissapient.kpidashboard.apis.data.KpiMasterDataFactory;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsServiceKanbanR;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsServiceR;
import com.publicissapient.kpidashboard.apis.jira.service.JiraServiceKanbanR;
import com.publicissapient.kpidashboard.apis.jira.service.JiraServiceR;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.sonar.service.SonarServiceKanbanR;
import com.publicissapient.kpidashboard.apis.sonar.service.SonarServiceR;
import com.publicissapient.kpidashboard.apis.zephyr.service.ZephyrService;
import com.publicissapient.kpidashboard.apis.zephyr.service.ZephyrServiceKanban;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.repository.application.KpiMasterRepository;
import com.publicissapient.kpidashboard.common.repository.application.OrganizationHierarchyRepository;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelService;

import jakarta.ws.rs.BadRequestException;

/**
 * @author kunkambl
 */
@RunWith(MockitoJUnitRunner.class)
public class KpiIntegrationServiceImplTest {
	@Mock private KpiMasterRepository kpiMasterRepository;
	@Mock private JiraServiceR jiraService;
	@Mock private SonarServiceR sonarService;
	@Mock private ZephyrService zephyrService;
	@Mock private JenkinsServiceR jenkinsService;
	@Mock private JiraServiceKanbanR jiraServiceKanbanR;
	@Mock private SonarServiceKanbanR sonarServiceKanbanR;
	@Mock private ZephyrServiceKanban zephyrServiceKanban;
	@Mock private JenkinsServiceKanbanR jenkinsServiceKanbanR;
	@Mock private BitBucketServiceKanbanR bitBucketServiceKanbanR;
	@Mock private HierarchyLevelService hierarchyLevelService;
	@Mock private OrganizationHierarchyRepository organizationHierarchyRepository;

	@InjectMocks private KpiIntegrationServiceImpl kpiIntegrationService;

	private final KpiMasterDataFactory kpiMasterDataFactory = KpiMasterDataFactory.newInstance();

	private KpiRequest kpiRequest;
	private KpiElement kpiElement1;
	private KpiElement kpiElement2;
	private List<String> kpiIdList = List.of("kpi14", "kpi70", "kpi27", "kpi8");

	private KpiRequest kanbanKpiRequest;
	private List<KpiMaster> kanbanKpiMastersList;
	private List<KpiElement> kanbanKpiElements;

	@Before
	public void setup() {
		kanbanKpiRequest = createKanbanKpiRequest();
		kanbanKpiMastersList = createKpiMasterList();
		kanbanKpiElements = createMockKpiElements();
		kpiRequest = new KpiRequest();
		kpiRequest.setKpiIdList(kpiIdList);
		kpiRequest.setHierarchyName("DTS");
		kpiRequest.setHierarchyId("535");
		kpiRequest.setLevel(4);
		DataCount dataCount = new DataCount();
		dataCount.setMaturity("1");
		dataCount.setMaturityValue("35");
		kpiElement1 = new KpiElement();
		kpiElement1.setTrendValueList(List.of(dataCount));
		DataCountGroup dataCountGroup = new DataCountGroup();
		dataCountGroup.setFilter("Overall");
		dataCountGroup.setValue(List.of(dataCount));
		kpiElement2 = new KpiElement();
		kpiElement2.setTrendValueList(List.of(dataCountGroup));

		HierachyLevelFactory hierachyLevelFactory = HierachyLevelFactory.newInstance();
		List<HierarchyLevel> hierarchyLevels = hierachyLevelFactory.getHierarchyLevels();
		when(hierarchyLevelService.getFullHierarchyLevels(false)).thenReturn(hierarchyLevels);
		OrganizationHierarchy organizationHierarchy = new OrganizationHierarchy();
		organizationHierarchy.setNodeId("123");
		organizationHierarchy.setNodeName("DTS");
		when(organizationHierarchyRepository.findByNodeNameAndHierarchyLevelId(
						anyString(), anyString()))
				.thenReturn(organizationHierarchy);
	}

	@Test
	public void getMaturityValuesTestSuccess() throws EntityNotFoundException {
		when(kpiMasterRepository.findByKpiIdIn(kpiIdList))
				.thenReturn(kpiMasterDataFactory.getSpecificKpis(kpiIdList));
		when(jiraService.processWithExposedApiToken(kpiRequest, false))
				.thenReturn(List.of(kpiElement1));
		boolean withCache = false;
		when(sonarService.processWithExposedApiToken(kpiRequest, withCache))
				.thenReturn(List.of(kpiElement2));
		when(zephyrService.processWithExposedApiToken(kpiRequest, withCache))
				.thenReturn(List.of(kpiElement2));
		when(jenkinsService.processWithExposedApiToken(kpiRequest, withCache))
				.thenReturn(List.of(kpiElement2));
		List<KpiElement> kpiElementList = kpiIntegrationService.processScrumKpiRequest(kpiRequest);
		assertEquals(4, kpiElementList.size());
	}

	@Test
	public void getMaturityValuesTestEmpty() {
		kpiIdList = List.of("kpi84");
		kpiRequest.setKpiIdList(kpiIdList);
		when(kpiMasterRepository.findByKpiIdIn(kpiIdList))
				.thenReturn(kpiMasterDataFactory.getSpecificKpis(kpiIdList));
		List<KpiElement> kpiElementList = kpiIntegrationService.processScrumKpiRequest(kpiRequest);
		assertEquals(0, kpiElementList.size());
	}

	@Test
	public void when_ValidKpiRequestWithJiraSource_Then_ProcessSuccessfully()
			throws EntityNotFoundException {
		// Arrange
		when(kpiMasterRepository.findByKpiIdIn(anyList())).thenReturn(kanbanKpiMastersList);
		when(jiraServiceKanbanR.process(any(KpiRequest.class))).thenReturn(kanbanKpiElements);

		// Act
		List<KpiElement> result = kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		verify(jiraServiceKanbanR).process(any(KpiRequest.class));
		verify(kpiMasterRepository).findByKpiIdIn(kanbanKpiRequest.getKpiIdList());
	}

	@Test
	public void when_ValidKpiRequestWithSonarSource_Then_ProcessSuccessfully() {
		// Arrange
		kanbanKpiMastersList.get(0).setKpiSource("SONAR");
		when(kpiMasterRepository.findByKpiIdIn(anyList())).thenReturn(kanbanKpiMastersList);
		when(sonarServiceKanbanR.process(any(KpiRequest.class))).thenReturn(kanbanKpiElements);

		// Act
		List<KpiElement> result = kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		verify(sonarServiceKanbanR).process(any(KpiRequest.class));
	}

	@Test
	public void when_ValidKpiRequestWithZephyrSource_Then_ProcessSuccessfully()
			throws EntityNotFoundException {
		// Arrange
		kanbanKpiMastersList.get(0).setKpiSource("ZEPHYR");
		when(kpiMasterRepository.findByKpiIdIn(anyList())).thenReturn(kanbanKpiMastersList);
		when(zephyrServiceKanban.process(any(KpiRequest.class))).thenReturn(kanbanKpiElements);

		// Act
		List<KpiElement> result = kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		verify(zephyrServiceKanban).process(any(KpiRequest.class));
	}

	@Test
	public void when_ValidKpiRequestWithJenkinsSource_Then_ProcessSuccessfully()
			throws EntityNotFoundException {
		// Arrange
		kanbanKpiMastersList.get(0).setKpiSource("JENKINS");
		when(kpiMasterRepository.findByKpiIdIn(anyList())).thenReturn(kanbanKpiMastersList);
		when(jenkinsServiceKanbanR.process(any(KpiRequest.class))).thenReturn(kanbanKpiElements);

		// Act
		List<KpiElement> result = kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		verify(jenkinsServiceKanbanR).process(any(KpiRequest.class));
	}

	@Test
	public void when_ValidKpiRequestWithBitbucketSource_Then_ProcessSuccessfully()
			throws EntityNotFoundException {
		// Arrange
		kanbanKpiMastersList.get(0).setKpiSource("BITBUCKET");
		when(kpiMasterRepository.findByKpiIdIn(anyList())).thenReturn(kanbanKpiMastersList);
		when(bitBucketServiceKanbanR.process(any(KpiRequest.class))).thenReturn(kanbanKpiElements);

		// Act
		List<KpiElement> result = kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		verify(bitBucketServiceKanbanR).process(any(KpiRequest.class));
	}

	@Test
	public void when_UnknownKpiSource_Then_LogInfoAndContinue() {
		// Arrange
		kanbanKpiMastersList.get(0).setKpiSource("UNKNOWN");
		when(kpiMasterRepository.findByKpiIdIn(anyList())).thenReturn(kanbanKpiMastersList);

		// Act
		List<KpiElement> result = kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
		verifyNoInteractions(
				jiraServiceKanbanR,
				sonarServiceKanbanR,
				zephyrServiceKanban,
				jenkinsServiceKanbanR,
				bitBucketServiceKanbanR);
	}

	@Test
	public void when_MultipleKpiSourcesPresent_Then_ProcessAllSources()
			throws EntityNotFoundException {
		// Arrange
		List<KpiMaster> multiSourceKpiList =
				List.of(createKpiMaster("kpi1", "JIRA"), createKpiMaster("kpi2", "SONAR"));
		when(kpiMasterRepository.findByKpiIdIn(anyList())).thenReturn(multiSourceKpiList);
		when(jiraServiceKanbanR.process(any(KpiRequest.class)))
				.thenReturn(List.of(createKpiElement("kpi1")));
		when(sonarServiceKanbanR.process(any(KpiRequest.class)))
				.thenReturn(List.of(createKpiElement("kpi2")));

		// Act
		List<KpiElement> result = kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		verify(jiraServiceKanbanR).process(any(KpiRequest.class));
		verify(sonarServiceKanbanR).process(any(KpiRequest.class));
	}

	@Test
	public void when_KpiMasterWithEmptySource_Then_FilterOut() throws EntityNotFoundException {
		// Arrange
		List<KpiMaster> kpiListWithEmptySource =
				List.of(
						createKpiMaster("kpi1", ""),
						createKpiMaster("kpi2", null),
						createKpiMaster("kpi3", "JIRA"));
		when(kpiMasterRepository.findByKpiIdIn(anyList())).thenReturn(kpiListWithEmptySource);
		when(jiraServiceKanbanR.process(any(KpiRequest.class))).thenReturn(kanbanKpiElements);

		// Act
		List<KpiElement> result = kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		verify(jiraServiceKanbanR).process(any(KpiRequest.class));
	}

	@Test
	public void when_NullKpiRequest_Then_ThrowBadRequestException() {
		// Act & Assert
		BadRequestException exception =
				assertThrows(
						BadRequestException.class, () -> kpiIntegrationService.processKanbanKPIRequest(null));
		assertEquals("Received kpi request was null", exception.getMessage());
	}

	@Test
	public void when_EmptyKpiIdList_Then_ThrowBadRequestException() {
		// Arrange
		kanbanKpiRequest.setKpiIdList(Collections.emptyList());

		// Act & Assert
		BadRequestException exception =
				assertThrows(
						BadRequestException.class,
						() -> kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest));
		assertEquals("'kpiIdList' must not be empty", exception.getMessage());
	}

	@Test
	public void when_NullIds_Then_ThrowBadRequestException() {
		// Arrange
		kanbanKpiRequest.setIds(null);

		// Act & Assert
		BadRequestException exception =
				assertThrows(
						BadRequestException.class,
						() -> kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest));
		assertEquals("'ids' must be provided and contain one positive integer", exception.getMessage());
	}

	@Test
	public void when_EmptyIds_Then_ThrowBadRequestException() {
		// Arrange
		kanbanKpiRequest.setIds(new String[0]);

		// Act & Assert
		BadRequestException exception =
				assertThrows(
						BadRequestException.class,
						() -> kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest));
		assertEquals("'ids' must be provided and contain one positive integer", exception.getMessage());
	}

	@Test
	public void when_MultipleIds_Then_ThrowBadRequestException() {
		// Arrange
		kanbanKpiRequest.setIds(new String[] {"1", "2"});

		// Act & Assert
		BadRequestException exception =
				assertThrows(
						BadRequestException.class,
						() -> kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest));
		assertEquals("'ids' must contain only one positive integer", exception.getMessage());
	}

	@Test
	public void when_NonNumericId_Then_ThrowBadRequestException() {
		// Arrange
		kanbanKpiRequest.setIds(new String[] {"abc"});

		// Act & Assert
		BadRequestException exception =
				assertThrows(
						BadRequestException.class,
						() -> kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest));
		assertEquals("'ids' must contain one valid positive integer", exception.getMessage());
	}

	@Test
	public void when_BlankLabel_Then_ThrowBadRequestException() {
		// Arrange
		kanbanKpiRequest.setLabel("");

		// Act & Assert
		BadRequestException exception =
				assertThrows(
						BadRequestException.class,
						() -> kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest));
		assertEquals("'label' must be provided", exception.getMessage());
	}

	@Test
	public void when_NullSelectedMap_Then_ThrowBadRequestException() {
		// Arrange
		kanbanKpiRequest.setSelectedMap(null);

		// Act & Assert
		BadRequestException exception =
				assertThrows(
						BadRequestException.class,
						() -> kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest));
		assertEquals("'selectedMap' must be provided", exception.getMessage());
	}

	@Test
	public void when_EmptySelectedMap_Then_ThrowBadRequestException() {
		// Arrange
		kanbanKpiRequest.setSelectedMap(new HashMap<>());

		// Act & Assert
		BadRequestException exception =
				assertThrows(
						BadRequestException.class,
						() -> kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest));
		assertEquals("'selectedMap' must be provided", exception.getMessage());
	}

	@Test
	public void when_SelectedMapWithoutDate_Then_ThrowBadRequestException() {
		// Arrange
		Map<String, List<String>> selectedMap = new HashMap<>();
		selectedMap.put("project", List.of("project1"));
		kanbanKpiRequest.setSelectedMap(selectedMap);

		// Act & Assert
		BadRequestException exception =
				assertThrows(
						BadRequestException.class,
						() -> kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest));
		assertEquals(
				"'selectedMap.date' must be provided with a valid temporal aggregation unit ex.: Weeks",
				exception.getMessage());
	}

	@Test
	public void when_KpiElementsWithDataCountGroupTrendValues_Then_CalculateOverallMaturity()
			throws EntityNotFoundException {
		// Arrange
		KpiElement kpiElement = createKpiElementWithDataCountGroup();
		List<KpiElement> kpiElementsWithMaturity = List.of(kpiElement);
		when(kpiMasterRepository.findByKpiIdIn(anyList())).thenReturn(kanbanKpiMastersList);
		when(jiraServiceKanbanR.process(any(KpiRequest.class))).thenReturn(kpiElementsWithMaturity);

		// Act
		List<KpiElement> result = kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("M5", result.get(0).getOverallMaturity());
	}

	@Test
	public void when_KpiElementsWithDataCountTrendValues_Then_CalculateOverallMaturity()
			throws EntityNotFoundException {
		// Arrange
		KpiElement kpiElement = createKpiElementWithDataCount();
		List<KpiElement> kpiElementsWithMaturity = List.of(kpiElement);
		when(kpiMasterRepository.findByKpiIdIn(anyList())).thenReturn(kanbanKpiMastersList);
		when(jiraServiceKanbanR.process(any(KpiRequest.class))).thenReturn(kpiElementsWithMaturity);

		// Act
		List<KpiElement> result = kpiIntegrationService.processKanbanKPIRequest(kanbanKpiRequest);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("M3", result.get(0).getOverallMaturity());
	}

	private KpiRequest createKanbanKpiRequest() {
		KpiRequest request = new KpiRequest();
		request.setKpiIdList(List.of("kpi1"));
		request.setIds(new String[] {"123"});
		request.setLabel("project");

		Map<String, List<String>> selectedMap = new HashMap<>();
		selectedMap.put(Constant.DATE, List.of("Weeks"));
		request.setSelectedMap(selectedMap);

		return request;
	}

	private List<KpiMaster> createKpiMasterList() {
		return List.of(createKpiMaster("kpi1", "JIRA"));
	}

	private KpiMaster createKpiMaster(String kpiId, String kpiSource) {
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(kpiId);
		kpiMaster.setKpiSource(kpiSource);
		kpiMaster.setKpiName("Test KPI");
		kpiMaster.setGroupId(1);
		return kpiMaster;
	}

	private List<KpiElement> createMockKpiElements() {
		return List.of(createKpiElement("kpi1"));
	}

	private KpiElement createKpiElement(String kpiId) {
		KpiElement element = new KpiElement();
		element.setKpiId(kpiId);
		element.setKpiName("Test KPI");
		return element;
	}

	private KpiElement createKpiElementWithDataCountGroup() {
		KpiElement element = createKpiElement("kpi1");

		DataCount dataCount = new DataCount();
		dataCount.setMaturityValue(5.0);
		dataCount.setMaturity("M5");

		DataCountGroup dataCountGroup = new DataCountGroup();
		dataCountGroup.setFilter("Story Points");
		dataCountGroup.setValue(List.of(dataCount));

		element.setTrendValueList(List.of(dataCountGroup));
		return element;
	}

	private KpiElement createKpiElementWithDataCount() {
		KpiElement element = createKpiElement("kpi1");

		DataCount dataCount = new DataCount();
		dataCount.setMaturityValue("3.0");
		dataCount.setMaturity("M3");

		element.setTrendValueList(List.of(dataCount));
		return element;
	}
}
