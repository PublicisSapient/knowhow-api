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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Sets the {@code chartType} of the Epic Hygiene KPI (kpi312) to {@code table}.
 *
 * <p>kpi312 publishes no trend line - it is rendered purely from its drill-down table - so the
 * empty {@code chartType} seeded by {@link EpicHygieneSlingshotChangeUnit} left the widget without
 * a renderer. Declaring {@code table} makes the UI pick the tabular renderer explicitly.
 */
@ChangeUnit(
		id = "epic_hygiene_chart_type_table",
		order = "17182",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class EpicHygieneChartTypeChangeUnit {

	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_ID = "kpi312";
	private static final String CHART_TYPE_FIELD = "chartType";
	private static final String CHART_TYPE_TABLE = "table";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		mongoTemplate.updateFirst(
				new Query(Criteria.where(KPI_ID_FIELD).is(KPI_ID)),
				new Update().set(CHART_TYPE_FIELD, CHART_TYPE_TABLE),
				KPI_MASTER_COLLECTION);
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate.updateFirst(
				new Query(Criteria.where(KPI_ID_FIELD).is(KPI_ID)),
				new Update().set(CHART_TYPE_FIELD, ""),
				KPI_MASTER_COLLECTION);
	}
}
