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

package com.publicissapient.kpidashboard.apis.ai.constants;

public final class PromptKeys {
	public static final String SPRINT_GOALS_SUMMARY = "sprint-goals-summary";
	public static final String KPI_CORRELATION_ANALYSIS_REPORT = "kpi-correlation-analysis-report";
	public static final String KPI_RECOMMENDATION_PROMPT = "kpi-recommendation";
	public static final String KPI_SEARCH = "kpi-search";
	public static final String KPI_DATA = "kpi-data";

	private PromptKeys() {
		throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
	}
}
