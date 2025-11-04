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

package com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for Sprint Analytics calculations
 */
@Slf4j
@UtilityClass
public class SprintAnalyticsUtil {

    public static final String UNKNOWN_SPRINT = "Unknown Sprint";

    /**
	 * Rounds off a double value to 2 decimal places
	 *
	 * @param value
	 *            Value to round
	 * @return Rounded value
	 */
	public static double roundingOff(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	/**
	 * Creates a data point with calculated values and custom trend unit
	 *
	 * @param sprintDetails
	 *            Sprint details
	 * @param value
	 *            Calculated metric value
	 * @param trend
	 *            Trend value (percentage or count)
	 * @param sprintIndex
	 *            Index of sprint
	 * @param trendUnit
	 *            Unit for trend value (e.g., "%", "count", "days")
	 * @return SprintDataPoint with String values
	 */
	public static SprintDataPoint createDataPoint(SprintDetails sprintDetails, Number value, Number trend,
			int sprintIndex, String trendUnit) {
		return SprintDataPoint.builder().sprint(normalizeSprintId(sprintIndex)).name(getSprintName(sprintDetails))
				.value(String.valueOf(value)).trend(String.valueOf(trend)).trendUnit(trendUnit).build();
	}

	/**
	 * Creates a NA (Not Available) data point when metric cannot be calculated Also
	 * adds the reason to context warnings for API response
	 * 
	 * @param sprintDetails
	 *            Sprint details
	 * @param reason
	 *            Reason why data is not available
	 * @param sprintIndex
	 *            Index of sprint
	 * @param context
	 *            Metric context to add warning
	 * @return SprintDataPoint with "NA" values for value and trend
	 */
	public static SprintDataPoint createNADataPoint(SprintDetails sprintDetails, String reason, int sprintIndex,
			SprintMetricContext context) {
		final String sprintName = getSprintName(sprintDetails);
		String warning = String.format("Project: %s | Sprint: %s | %s", context.getBasicProjectConfigId(), sprintName,
				reason);
		context.addWarning(warning);
		log.debug("Creating NA data point for sprint: {} - Reason: {}", sprintName, reason);
		return SprintDataPoint.builder().sprint(normalizeSprintId(sprintIndex)).name(sprintName)
				.value(Constant.NOT_AVAILABLE).trend(Constant.NOT_AVAILABLE).build();
	}

    /**
     * Get sprint name with null safety
     * @param sprintDetails Sprint details
     * @return Sprint name or "Unknown Sprint" if null
     */
	@NotNull
    public static String getSprintName(SprintDetails sprintDetails) {
		return sprintDetails != null && sprintDetails.getSprintName() != null ? sprintDetails.getSprintName()
				: UNKNOWN_SPRINT;
	}

	/**
	 * Normalize sprint ID across projects
	 * 
	 * @param sprintIndex
	 *            Index
	 * @return Normalized sprint ID (e.g., "Sprint 1", "Sprint 2")
	 */
	public static String normalizeSprintId(int sprintIndex) {
		return "Sprint " + (sprintIndex + 1);
	}

	/**
	 * Calculates percentage with null/zero safety
	 *
	 * @param numerator
	 *            Numerator value
	 * @param denominator
	 *            Denominator value
	 * @return Percentage (0-100) or 0.0 if denominator is 0
	 */
	public static double calculatePercentage(long numerator, long denominator) {
		if (denominator == 0) {
			return 0.0;
		}
		return roundingOff(((double) numerator / denominator) * 100);
	}

	/**
	 * Validates sprint details is not null Logs warning if null
	 *
	 * @param sprintDetails
	 *            Sprint details to validate
	 * @return true if valid (not null), false otherwise
	 */
	public static boolean isValidSprintDetails(SprintDetails sprintDetails) {
		if (sprintDetails == null) {
			log.warn("Sprint details is null - cannot calculate metric");
			return false;
		}
		return true;
	}

	/**
	 * Validates field mapping is configured Logs warning if null
	 *
	 * @param fieldMapping
	 *            Field mapping to validate
	 * @param projectName
	 *            Project name for logging
	 * @return true if valid (not null), false otherwise
	 */
	public static boolean isValidFieldMapping(Object fieldMapping, String projectName) {
		if (fieldMapping == null) {
			log.warn("Field mapping not configured for project: {}", projectName);
			return false;
		}
		return true;
	}
}
