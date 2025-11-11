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

package com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Enum defining all available sprint metrics with descriptions */
@Getter
@AllArgsConstructor
public enum SprintMetricType {

	/** Stories still in Grooming status on Day 1 of current sprint */
	GROOMING_DAY_ONE(
			"Stories still in Grooming status on Day 1 of current sprint",
			"Measures sprint readiness by tracking stories not refined by sprint start",
			true),

	/**
	 * % of Stories that are not dev completed or have not reached QA as per the Dev Completion Date
	 * of the current sprint
	 */
	DEV_COMPLETION_BREACH(
			"% of Stories that are not dev completed or have not reached QA as per the Dev Completion Date of the current sprint",
			"Tracks stories missing dev completion deadline within sprint", true),

	/** Stories whose scope changed post sprint start */
	SCOPE_CHANGE_POST_START(
			"Stories whose scope changed post sprint start",
			"Identifies stories with scope modifications after sprint begins",
			true),

	/** Age of spillovers Logic applies to past sprints */
	SPILLOVER_AGE(
			"Age of spillovers",
			"Calculates the age of stories that spilled over from previous sprints",
			true),

	/** % of stories missing estimation */
	MISSING_ESTIMATION(
			"% of stories missing estimation",
			"Percentage of stories without estimation based on configured criteria", true),

	/** % of scope added */
	SCOPE_ADDED_PERCENTAGE(
			"% of scope added", "Percentage of work added after sprint planning", true),

	/** % of scope changes */
	SCOPE_CHANGE_PERCENTAGE(
			"% of scope changes", "Percentage of total scope modifications during sprint", true);

	private final String displayName;
	private final String description;
	private final boolean enabled;
}
