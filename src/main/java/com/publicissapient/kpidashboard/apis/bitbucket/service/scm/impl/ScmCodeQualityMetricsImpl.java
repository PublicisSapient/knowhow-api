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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketKPIService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.ScmCodeQualityReworkRateServiceImpl.ReworkCalculation;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.*;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SCM Rework Rate Service Implementation
 *
 * <h3>REWORK RATE LOGIC</h3>
 *
 * <b>What:</b> Measures how much code is being changed that was already changed in the past 21
 * days.
 *
 * <p><b>Why:</b> High rework indicates quality issues - developers are fixing/changing recently
 * written code.
 *
 * <p><b>How it works:</b>
 *
 * <pre>
 * 1. Look back 21 days (Past three weeks) to build a "reference pool" of all changed lines
 * 2. For current period, check if any changed lines were in the reference pool
 * 3. Rework Rate = (reworked lines / total lines changed) * 100
 *
 * Example:
 * - Day 1: Changed lines 10-20 in FileA.java (goes into reference pool)
 * - Day 15: Changed lines 15-25 in FileA.java
 *   - Lines 15-20 are rework (were changed before)
 *   - Lines 21-25 are new changes
 *   - Rework = 6 lines, Total = 11 lines
 *   - Rate = (6/11) * 100 = 54.55%
 *
 * Higher percentage = More rework (bad)
 * Lower percentage = Less rework (good)
 * </pre>
 *
 * @author shunaray
 */
@Slf4j
@Service
@AllArgsConstructor
public class ScmCodeQualityMetricsImpl
		extends BitBucketKPIService<Double, List<Object>, Map<String, Object>> {

	@Autowired private ScmCodeQualityRevertRateServiceImpl scmCodeQualityRevertRateServiceImpl;
	@Autowired private ScmCodeQualityReworkRateServiceImpl scmCodeQualityReworkRateServiceImpl;

	@Override
	public String getQualifierType() {
		return KPICode.CODE_QUALITY_REVERT_RATE.name();
	}

	// @Override
	// public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement,
	// Node projectNode)
	// throws ApplicationException {
	// Map<String, ReworkCalculation> reworkMap = new HashMap<>();
	// Map<String, MetricsHolder> revertMap = new HashMap<>();
	//
	// try {
	// KpiElement reworkRateElement =
	// scmCodeQualityReworkRateServiceImpl.getKpiData(kpiRequest, new KpiElement(),
	// projectNode);
	// if (reworkRateElement != null && reworkRateElement.getTrendValueList() !=
	// null) {
	// reworkMap = (Map<String, ReworkCalculation>)
	// reworkRateElement.getTrendValueList();
	// }
	// } catch (Exception e) {
	// log.error("Error processing Rework Rate KPI: {}", e.getMessage(), e);
	// }
	//
	// try {
	// KpiElement revertRateElement =
	// scmCodeQualityRevertRateServiceImpl.getKpiData(kpiRequest, new KpiElement(),
	// projectNode);
	// if (revertRateElement != null && revertRateElement.getTrendValueList() !=
	// null) {
	// revertMap = (Map<String, MetricsHolder>)
	// revertRateElement.getTrendValueList();
	// }
	// } catch (Exception e) {
	// log.error("Error processing Revert Rate KPI: {}", e.getMessage(), e);
	// }
	//
	// List<DataCountGroup> result = convertMetricsToDataCountGroups(reworkMap,
	// revertMap, kpiRequest);
	// kpiElement.setTrendValueList(result);
	//
	// return kpiElement;
	// }

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {
		Map<String, ReworkCalculation> reworkMap = new HashMap<>();
		Map<String, MetricsHolder> revertMap = new HashMap<>();

		ExecutorService executorService =
				Executors.newFixedThreadPool(2); // Create a thread pool with 2 threads

		try {
			// Define callables for both tasks
			Callable<Map<String, ReworkCalculation>> reworkTask =
					() -> {
						KpiElement reworkRateElement =
								scmCodeQualityReworkRateServiceImpl.getKpiData(
										kpiRequest, new KpiElement(), projectNode);
						if (reworkRateElement != null && reworkRateElement.getTrendValueList() != null) {
							return (Map<String, ReworkCalculation>) reworkRateElement.getTrendValueList();
						}
						return new HashMap<>(); // Return an empty map if null
					};

			Callable<Map<String, MetricsHolder>> revertTask =
					() -> {
						KpiElement revertRateElement =
								scmCodeQualityRevertRateServiceImpl.getKpiData(
										kpiRequest, new KpiElement(), projectNode);
						if (revertRateElement != null && revertRateElement.getTrendValueList() != null) {
							return (Map<String, MetricsHolder>) revertRateElement.getTrendValueList();
						}
						return new HashMap<>(); // Return an empty map if null
					};

			// Submit tasks to executor service
			Future<Map<String, ReworkCalculation>> reworkFuture = executorService.submit(reworkTask);
			Future<Map<String, MetricsHolder>> revertFuture = executorService.submit(revertTask);

			// Retrieve results from futures
			try {
				reworkMap = reworkFuture.get(); // Retrieve and store result from rework task
			} catch (InterruptedException | ExecutionException e) {
				log.error("Error processing Rework Rate KPI: {}", e.getMessage(), e);
			}

			try {
				revertMap = revertFuture.get(); // Retrieve and store result from revert task
			} catch (InterruptedException | ExecutionException e) {
				log.error("Error processing Revert Rate KPI: {}", e.getMessage(), e);
			}

		} finally {
			// Shutdown the executor service
			executorService.shutdown();
		}

		List<DataCountGroup> result = convertMetricsToDataCountGroups(reworkMap, revertMap, kpiRequest);
		kpiElement.setTrendValueList(result);
		return kpiElement;
	}

	private List<DataCountGroup> convertMetricsToDataCountGroups(
			Map<String, ReworkCalculation> reworkMap,
			Map<String, MetricsHolder> revertMap,
			KpiRequest kpiRequest) {
		Map<String, DataCountGroup> groupMap = new HashMap<>();

		String duration = kpiRequest.getDuration();
		int dataPoints = kpiRequest.getXAxisDataPoints();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		CustomDateRange periodRange =
				KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration, dataPoints);

		String dateLabel =
				DateUtil.dateTimeConverter(
								periodRange.getStartDate().toString(),
								DateUtil.DATE_FORMAT,
								DateUtil.DISPLAY_DATE_FORMAT)
						+ " to "
						+ DateUtil.dateTimeConverter(
								periodRange.getEndDate().toString(),
								DateUtil.DATE_FORMAT,
								DateUtil.DISPLAY_DATE_FORMAT);

		for (Map.Entry<String, ReworkCalculation> entry : reworkMap.entrySet()) {
			String key = entry.getKey();
			String[] keyParts = key.split("#");
			String filter1 = keyParts.length > 0 ? keyParts[0] : key;
			String filter2 = keyParts.length > 1 ? keyParts[1] : "Unknown";

			DataCountGroup group =
					groupMap.computeIfAbsent(
							key,
							k -> {
								DataCountGroup g = new DataCountGroup();
								g.setFilter1(filter1);
								g.setFilter2(filter2);
								g.setValue(new ArrayList<>());
								return g;
							});

			DataCount dataCount = new DataCount();
			dataCount.setData("Rework Rate");
			dataCount.setValue(entry.getValue().getPercentage());
			dataCount.setSProjectName(extractProjectName(filter1));
			dataCount.setKpiGroup(key);
			dataCount.setDate(dateLabel);
			group.getValue().add(dataCount);
		}

		// Process revert rate data
		for (Map.Entry<String, MetricsHolder> entry : revertMap.entrySet()) {
			String key = entry.getKey();
			String[] keyParts = key.split("#");
			String filter1 = keyParts.length > 0 ? keyParts[0] : key;
			String filter2 = keyParts.length > 1 ? keyParts[1] : "Unknown";

			DataCountGroup group =
					groupMap.computeIfAbsent(
							key,
							k -> {
								DataCountGroup g = new DataCountGroup();
								g.setFilter1(filter1);
								g.setFilter2(filter2);
								g.setValue(new ArrayList<>());
								return g;
							});

			DataCount dataCount = new DataCount();
			dataCount.setData("Revert Rate");
			dataCount.setValue(entry.getValue().calculateRevertPercentage());
			dataCount.setSProjectName(extractProjectName(filter1));
			dataCount.setKpiGroup(key);
			dataCount.setDate(dateLabel);
			group.getValue().add(dataCount);
		}

		return new ArrayList<>(groupMap.values());
	}

	private String extractProjectName(String filter1) {
		String[] parts = filter1.split(" -> ");
		return parts.length >= 3 ? parts[2] : "";
	}

	@Override
	public Double calculateKPIMetrics(Map<String, Object> stringObjectMap) {
		return null;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		return Map.of();
	}
}
