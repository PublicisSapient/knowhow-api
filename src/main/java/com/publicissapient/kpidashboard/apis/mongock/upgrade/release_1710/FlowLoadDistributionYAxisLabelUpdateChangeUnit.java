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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "flow_load_distribution_y_axis_label_update",
		order = "17134",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class FlowLoadDistributionYAxisLabelUpdateChangeUnit {

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Query kpi206Query = new Query(Criteria.where("kpiId").is("kpi206"));
		mongoTemplate.updateFirst(kpi206Query, new Update().set("yAxisLabel", "Count"), "kpi_master");

		Query kpi207Query = new Query(Criteria.where("kpiId").is("kpi207"));
		Update kpi207Update =
				new Update()
						.set("yAxisLabel", "Count")
						.set("xAxisLabel", "Time")
						.unset("yaxisLabel")
						.unset("xaxisLabel");
		mongoTemplate.updateFirst(kpi207Query, kpi207Update, "kpi_master");
	}

	@RollbackExecution
	public void rollback() {
		Query kpi206Query = new Query(Criteria.where("kpiId").is("kpi206"));
		mongoTemplate.updateFirst(kpi206Query, new Update().unset("yAxisLabel"), "kpi_master");

		Query kpi207Query = new Query(Criteria.where("kpiId").is("kpi207"));
		Update kpi207Rollback =
				new Update()
						.set("yaxisLabel", "Count")
						.set("xaxisLabel", "Time")
						.unset("yAxisLabel")
						.unset("xAxisLabel");
		mongoTemplate.updateFirst(kpi207Query, kpi207Rollback, "kpi_master");
	}
}
