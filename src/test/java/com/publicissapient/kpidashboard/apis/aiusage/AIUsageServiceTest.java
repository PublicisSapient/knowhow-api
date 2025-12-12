package com.publicissapient.kpidashboard.apis.aiusage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.domain.Pageable;

import com.publicissapient.kpidashboard.apis.aiusage.dto.AIUsageSummary;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsageStatistics;
import com.publicissapient.kpidashboard.apis.aiusage.repository.AIUsageStatisticsRepository;
import com.publicissapient.kpidashboard.apis.aiusage.service.AIUsageService;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;

@RunWith(MockitoJUnitRunner.class)
class AIUsageServiceTest {

	@Mock private AIUsageStatisticsRepository aiUsageStatisticsRepository;

	@Mock private AccountHierarchyServiceImpl accountHierarchyServiceImpl;

	@InjectMocks private AIUsageService aiUsageService;

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testGetAIUsageStatsForAccount_expectSuccess() {
		String levelName = "account";
		LocalDate startDate = LocalDate.now().minusDays(10);
		LocalDate endDate = LocalDate.now();
		Pageable pageable = Pageable.unpaged();
		AIUsageSummary summary = new AIUsageSummary(100L, 5L, 1L, 0L, null);

		AccountFilteredData accData = new AccountFilteredData();
		accData.setNodeId("acc");
		accData.setNodeName("Account1");
		accData.setLabelName("acc");
		Set<AccountFilteredData> hierarchyData = Set.of(accData);
		when(accountHierarchyServiceImpl.getFilteredList(any())).thenReturn(hierarchyData);
		when(aiUsageStatisticsRepository
						.findTop1ByLevelNameAndStatsDateBetweenOrderByIngestTimestampDesc(
								anyString(), any(), any()))
				.thenReturn(
						new AIUsageStatistics("Account", "Account1", Instant.now(), Instant.now(), summary));

		ServiceResponse response = null;
		try {
			response = aiUsageService.getAIUsageStats(levelName, startDate, endDate, false, pageable);
		} catch (EntityNotFoundException | BadRequestException e) {
			fail("Exception should not be thrown for valid account level");
		}

		assertNotNull(response);
		verify(aiUsageStatisticsRepository, times(1))
				.findTop1ByLevelNameAndStatsDateBetweenOrderByIngestTimestampDesc(
						anyString(), any(), any());
	}

	@Test
	void testGetAIUsageStats_InvalidLevelName() {
		String levelName = "invalid";
		LocalDate startDate = LocalDate.now().minusDays(10);
		LocalDate endDate = LocalDate.now();
		Pageable pageable = Pageable.unpaged();

		assertThrows(
				BadRequestException.class,
				() -> {
					aiUsageService.getAIUsageStats(levelName, startDate, endDate, false, pageable);
				});
	}

	@Test
	void testGetAIUsageStats_StartDateAfterEndDate() {
		String levelName = "bu";
		LocalDate startDate = LocalDate.now();
		LocalDate endDate = LocalDate.now().minusDays(10);
		Pageable pageable = Pageable.unpaged();

		assertThrows(
				BadRequestException.class,
				() -> {
					aiUsageService.getAIUsageStats(levelName, startDate, endDate, false, pageable);
				});
	}

	@Test
	void testGetAIUsageStats_NoHierarchyData() {
		String levelName = "bu";
		LocalDate startDate = LocalDate.now().minusDays(10);
		LocalDate endDate = LocalDate.now();
		Pageable pageable = Pageable.unpaged();
		when(accountHierarchyServiceImpl.getFilteredList(any())).thenReturn(Collections.emptySet());

		assertThrows(
				EntityNotFoundException.class,
				() -> {
					aiUsageService.getAIUsageStats(levelName, startDate, endDate, false, pageable);
				});
	}

	@Test
	void testAggregateSummaries_MultipleValid() throws Exception {
		AIUsageStatistics stats1 = new AIUsageStatistics();
		stats1.setUsageSummary(new AIUsageSummary(100L, 200L, 10L, 50L, null));

		AIUsageStatistics stats2 = new AIUsageStatistics();
		stats2.setUsageSummary(new AIUsageSummary(300L, 400L, 20L, 70L, null));

		List<AIUsageStatistics> statsList = List.of(stats1, stats2);

		Method method = AIUsageService.class.getDeclaredMethod("aggregateSummaries", List.class);
		method.setAccessible(true);
		AIUsageSummary result = (AIUsageSummary) method.invoke(aiUsageService, statsList);

		assertEquals(400L, result.getTotalLocGenerated());
		assertEquals(600L, result.getTotalPrompts());
		assertEquals(30L, result.getUserCount());
		assertEquals(120L, result.getOtherMetrics());
	}
}
