/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.management.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.config.DashboardConfig;
import com.publicissapient.kpidashboard.apis.management.service.DashboardHealthService;
import com.publicissapient.kpidashboard.apis.management.service.MetricsService;
import com.publicissapient.kpidashboard.apis.model.ApiDetailDto;
import com.publicissapient.kpidashboard.apis.model.ComponentDto;
import com.publicissapient.kpidashboard.apis.model.DetailsDto;
import com.publicissapient.kpidashboard.apis.model.HealthResponseDto;
import com.publicissapient.kpidashboard.apis.model.LinkDto;
import com.publicissapient.kpidashboard.apis.model.LinksDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardHealthServiceImpl implements DashboardHealthService {

	private final DashboardConfig dashboardConfig;
	private final MetricsService metricsService;

	private static final String STATUS_UP = Status.UP.getCode();
	private static final String STATUS_DOWN = Status.DOWN.getCode();

	@Override
	public HealthResponseDto getDashboardHealth() {
		log.info("Starting dashboard health overview generation");
		Map<String, ComponentDto> components = new HashMap<>();
		MetricsAggregate metricsAggregate = new MetricsAggregate();
		dashboardConfig
				.getTypes()
				.forEach(
						(boardType, boardTypeConfig) -> {
							log.debug("Processing board type: {}", boardType);
							HealthResponseDto boardHealth = getBoardTypeHealth(boardType);
							components.put(boardType, buildBoardTypeComponentDto(boardType, boardHealth));
							updateAggregatedMetrics(metricsAggregate, boardHealth);
						});
		log.info("Dashboard health overview generation completed successfully");
		return buildHealthResponseDto(
				metricsAggregate, components, dashboardConfig.getHealthApiBasePath());
	}

	@Override
	public HealthResponseDto getBoardTypeHealth(String boardType) {
		log.info("Starting health generation for board type: {}", boardType);
		DashboardConfig.BoardType boardTypeConfig = validateBoardType(boardType, dashboardConfig);
		Map<String, ComponentDto> components = new HashMap<>();
		MetricsAggregate metricsAggregate = new MetricsAggregate();

		boardTypeConfig
				.getBoards()
				.forEach(
						(dashboardName, board) -> {
							log.debug("Processing dashboard: {} in board type: {}", dashboardName, boardType);
							HealthResponseDto dashboardHealth =
									getDashboardDetailHealth(boardType, dashboardName);
							components.put(
									dashboardName,
									buildDashboardComponentDto(boardType, dashboardName, dashboardHealth));
							updateAggregatedMetrics(metricsAggregate, dashboardHealth);
						});
		log.info("Health generation for board type: {} completed successfully", boardType);
		return buildHealthResponseDto(
				metricsAggregate, components, dashboardConfig.getHealthApiBasePath() + "/" + boardType);
	}

	@Override
	public HealthResponseDto getDashboardDetailHealth(String boardType, String dashboard) {
		log.info(
				"Starting health generation for dashboard: {} in board type: {}", dashboard, boardType);
		DashboardConfig.BoardType boardTypeConfig = validateBoardType(boardType, dashboardConfig);
		DashboardConfig.Board board = validateDashboard(boardType, dashboard, boardTypeConfig);
		log.debug("Fetching API metrics for dashboard: {}", dashboard);

		List<ApiDetailDto> apiMetrics = metricsService.getApisMetrics(board.getApis());
		MetricsAggregate metricsAggregate = calculateAggregatedMetrics(apiMetrics);

		log.info(
				"Health generation for dashboard: {} in board type: {} completed successfully",
				dashboard,
				boardType);
		return buildHealthResponseDto(
				metricsAggregate,
				apiMetrics,
				dashboardConfig.getHealthApiBasePath() + "/" + boardType + "/" + dashboard);
	}

	/**
	 * Builds a {@link ComponentDto} from board type health response.
	 *
	 * @param boardType board type name
	 * @param boardHealth {@link HealthResponseDto} health response
	 * @return {@link ComponentDto}
	 */
	private ComponentDto buildBoardTypeComponentDto(String boardType, HealthResponseDto boardHealth) {
		return ComponentDto.builder()
				.status(boardHealth.getStatus())
				.max(boardHealth.getMax())
				.count(boardHealth.getCount())
				.totalTime(boardHealth.getTotalTime())
				.errorRate(boardHealth.getErrorRate())
				.errorThreshold(dashboardConfig.getMaxApiErrorThreshold())
				.links(
						LinksDto.builder()
								.self(
										LinkDto.builder()
												.href(dashboardConfig.getHealthApiBasePath() + "/" + boardType)
												.build())
								.build())
				.build();
	}

	/**
	 * Builds a {@link ComponentDto} from dashboard health response.
	 *
	 * @param boardType board type name
	 * @param dashboardName dashboard name
	 * @param dashboardHealth {@link HealthResponseDto} health response
	 * @return {@link ComponentDto}
	 */
	private ComponentDto buildDashboardComponentDto(
			String boardType, String dashboardName, HealthResponseDto dashboardHealth) {
		return ComponentDto.builder()
				.status(dashboardHealth.getStatus())
				.max(dashboardHealth.getMax())
				.count(dashboardHealth.getCount())
				.totalTime(dashboardHealth.getTotalTime())
				.errorRate(dashboardHealth.getErrorRate())
				.errorThreshold(dashboardConfig.getMaxApiErrorThreshold())
				.links(
						LinksDto.builder()
								.self(
										LinkDto.builder()
												.href(
														dashboardConfig.getHealthApiBasePath()
																+ "/"
																+ boardType
																+ "/"
																+ dashboardName)
												.build())
								.build())
				.build();
	}

	/**
	 * Builds a {@link HealthResponseDto} from aggregated metrics and components.
	 *
	 * @param metrics {@link MetricsAggregate} metrics aggregate
	 * @param components map of components
	 * @param path API path
	 * @return {@link HealthResponseDto}
	 */
	private HealthResponseDto buildHealthResponseDto(
			MetricsAggregate metrics, Map<String, ComponentDto> components, String path) {
		return HealthResponseDto.builder()
				.status(metrics.isAllUp() ? STATUS_UP : STATUS_DOWN)
				.max(metrics.getMaxResponseTime())
				.count(metrics.getTotalCount())
				.totalTime(metrics.getTotalResponseTime())
				.errorRate(metrics.getErrorRate())
				.errorThreshold(dashboardConfig.getMaxApiErrorThreshold())
				.components(components)
				.links(LinksDto.builder().self(LinkDto.builder().href(path).build()).build())
				.build();
	}

	/**
	 * Builds a {@link HealthResponseDto} from aggregated metrics and API metrics.
	 *
	 * @param metrics {@link MetricsAggregate} metrics aggregate
	 * @param apiMetrics list of {@link ApiDetailDto}
	 * @param path API path
	 * @return {@link HealthResponseDto}
	 */
	private HealthResponseDto buildHealthResponseDto(
			MetricsAggregate metrics, List<ApiDetailDto> apiMetrics, String path) {
		return HealthResponseDto.builder()
				.status(metrics.isAllUp() ? STATUS_UP : STATUS_DOWN)
				.max(metrics.getMaxResponseTime())
				.count(metrics.getTotalCount())
				.totalTime(metrics.getTotalResponseTime())
				.errorRate(metrics.getErrorRate())
				.errorThreshold(dashboardConfig.getMaxApiErrorThreshold())
				.details(DetailsDto.builder().apis(apiMetrics).build())
				.links(LinksDto.builder().self(LinkDto.builder().href(path).build()).build())
				.build();
	}

	/**
	 * Updates aggregated metrics with health response data.
	 *
	 * @param metrics {@link MetricsAggregate}
	 * @param healthResponse {@link HealthResponseDto}
	 */
	private void updateAggregatedMetrics(MetricsAggregate metrics, HealthResponseDto healthResponse) {
		metrics.setMaxResponseTime(Math.max(metrics.getMaxResponseTime(), healthResponse.getMax()));
		int originalTotalCount = metrics.getTotalCount();
		metrics.setTotalCount(originalTotalCount + healthResponse.getCount());
		metrics.setTotalResponseTime(metrics.getTotalResponseTime() + healthResponse.getTotalTime());
		if (STATUS_DOWN.equals(healthResponse.getStatus())) {
			metrics.setAllUp(false);
		}
		double totalWeightedErrorRate =
				(metrics.getErrorRate() * originalTotalCount)
						+ (healthResponse.getErrorRate() * healthResponse.getCount());
		int totalWeight = originalTotalCount + healthResponse.getCount();
		metrics.setErrorRate(totalWeight == 0 ? 0 : totalWeightedErrorRate / totalWeight);
		metrics.setErrorThreshold(dashboardConfig.getMaxApiErrorThreshold());
	}

	/**
	 * Calculates aggregated metrics from API metrics.
	 *
	 * @param apiMetrics list of {@link ApiDetailDto}
	 * @return {@link MetricsAggregate}
	 */
	private MetricsAggregate calculateAggregatedMetrics(List<ApiDetailDto> apiMetrics) {
		double maxResponseTime = apiMetrics.stream().mapToDouble(ApiDetailDto::getMax).max().orElse(0);
		int totalCount = apiMetrics.stream().mapToInt(ApiDetailDto::getCount).sum();
		double totalResponseTime = apiMetrics.stream().mapToDouble(ApiDetailDto::getTotalTime).sum();
		boolean allUp = apiMetrics.stream().allMatch(api -> STATUS_UP.equals(api.getStatus()));

		// Calculate weighted average error rate
		double weightedErrorRate =
				apiMetrics.stream().mapToDouble(api -> api.getErrorRate() * api.getCount()).sum()
						/ (totalCount > 0 ? totalCount : 1);

		double errorThreshold = dashboardConfig.getMaxApiErrorThreshold();

		return new MetricsAggregate(
				maxResponseTime, totalCount, totalResponseTime, allUp, weightedErrorRate, errorThreshold);
	}

	/**
	 * Validates and returns the board type config.
	 *
	 * @param boardType board type name
	 * @param dashboardConfig {@link DashboardConfig}
	 * @return {@link DashboardConfig.BoardType}
	 * @throws IllegalArgumentException if not found
	 */
	public DashboardConfig.BoardType validateBoardType(
			String boardType, DashboardConfig dashboardConfig) {
		DashboardConfig.BoardType boardTypeConfig = dashboardConfig.getTypes().get(boardType);
		if (boardTypeConfig == null) {
			throw new IllegalArgumentException("Board type not found: " + boardType);
		}
		return boardTypeConfig;
	}

	/**
	 * Validates and returns the dashboard config.
	 *
	 * @param boardType board type name
	 * @param dashboard dashboard name
	 * @param boardTypeConfig {@link DashboardConfig.BoardType}
	 * @return {@link DashboardConfig.Board}
	 * @throws IllegalArgumentException if not found
	 */
	private DashboardConfig.Board validateDashboard(
			String boardType, String dashboard, DashboardConfig.BoardType boardTypeConfig) {
		DashboardConfig.Board board = boardTypeConfig.getBoards().get(dashboard);
		if (board == null) {
			throw new IllegalArgumentException(
					"Dashboard not found: " + dashboard + " in board type " + boardType);
		}
		return board;
	}

	/** Helper class for aggregating metrics. */
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	private static class MetricsAggregate {
		private double maxResponseTime = 0;
		private int totalCount = 0;
		private double totalResponseTime = 0;
		private boolean allUp = true;
		private double errorRate = 0.0;
		private double errorThreshold = 0.0;
	}
}
