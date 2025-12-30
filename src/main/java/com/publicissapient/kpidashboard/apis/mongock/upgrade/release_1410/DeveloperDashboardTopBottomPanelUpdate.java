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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1410;

import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "developer_dashboard_top_bottom_panel_kpi_update",
		order = "14109",
		author = "kunkambl",
		systemVersion = "14.1.0")
public class DeveloperDashboardTopBottomPanelUpdate {

	private static final String KPI_158 = "kpi158";
	private static final String KPI_160 = "kpi160";
	private static final String KPI_185 = "kpi185";
	private static final String KPI_186 = "kpi186";
	private static final List<String> kpiIds = List.of(KPI_158, KPI_160, KPI_186, KPI_185);
	private static final String KPI_ID = "kpiId";
	private static final String KPI_MASTER = "kpi_master";

	private final MongoTemplate mongoTemplate;

	public DeveloperDashboardTopBottomPanelUpdate(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		mongoTemplate.updateMulti(
				new Query(Criteria.where(KPI_ID).in(kpiIds)),
				new Update().set("chartType", "card").set("groupId", 6),
				KPI_MASTER);

		Map.of(KPI_158, 1, KPI_160, 2, KPI_185, 3, KPI_186, 4)
				.forEach(
						(kpiId, order) ->
								mongoTemplate.updateFirst(
										new Query(Criteria.where(KPI_ID).is(kpiId)),
										new Update().set("defaultOrder", order),
										KPI_MASTER));
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate.updateMulti(
				new Query(Criteria.where(KPI_ID).in(kpiIds)),
				new Update().set("chartType", "line"),
				KPI_MASTER);

		Map.of(KPI_158, 2, KPI_160, 3, KPI_185, 9, KPI_186, 10)
				.forEach(
						(kpiId, order) ->
								mongoTemplate.updateFirst(
										new Query(Criteria.where(KPI_ID).is(kpiId)),
										new Update().set("defaultOrder", order),
										KPI_MASTER));
	}
}
