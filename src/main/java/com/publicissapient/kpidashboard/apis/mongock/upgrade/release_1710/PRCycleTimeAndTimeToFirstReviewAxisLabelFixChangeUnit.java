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
		id = "pr_cycle_time_and_time_to_first_review_axis_label_fix",
		order = "17135",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class PRCycleTimeAndTimeToFirstReviewAxisLabelFixChangeUnit {

	private static final String KPI_MASTER = "kpi_master";
	private static final String KPI_ID = "kpiId";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Query kpi208Query = new Query(Criteria.where(KPI_ID).is("kpi208"));
		Update kpi208Update = new Update().set("xAxisLabel", "Weeks").set("yAxisLabel", "Count");
		mongoTemplate.updateFirst(kpi208Query, kpi208Update, KPI_MASTER);

		Query kpi209Query = new Query(Criteria.where(KPI_ID).is("kpi209"));
		Update kpi209Update =
				new Update()
						.set("xAxisLabel", "Weeks")
						.set("yAxisLabel", "Hours")
						.unset("xaxisLabel")
						.unset("yaxisLabel");
		mongoTemplate.updateFirst(kpi209Query, kpi209Update, KPI_MASTER);

		Query kpi210Query = new Query(Criteria.where(KPI_ID).is("kpi210"));
		Update kpi210Update =
				new Update()
						.set("xAxisLabel", "Weeks")
						.set("yAxisLabel", "Hours")
						.unset("xaxisLabel")
						.unset("yaxisLabel");
		mongoTemplate.updateFirst(kpi210Query, kpi210Update, KPI_MASTER);
	}

	@RollbackExecution
	public void rollback() {
		Query kpi208Query = new Query(Criteria.where(KPI_ID).is("kpi208"));
		Update kpi208Rollback = new Update().set("xAxisLabel", "Days").set("yAxisLabel", "Count");
		mongoTemplate.updateFirst(kpi208Query, kpi208Rollback, KPI_MASTER);

		Query kpi209Query = new Query(Criteria.where(KPI_ID).is("kpi209"));
		Update kpi209Rollback =
				new Update()
						.set("xaxisLabel", "Weeks")
						.set("yaxisLabel", "Hours")
						.unset("xAxisLabel")
						.unset("yAxisLabel");
		mongoTemplate.updateFirst(kpi209Query, kpi209Rollback, KPI_MASTER);

		Query kpi210Query = new Query(Criteria.where(KPI_ID).is("kpi210"));
		Update kpi210Rollback = new Update().unset("xAxisLabel").unset("yAxisLabel");
		mongoTemplate.updateFirst(kpi210Query, kpi210Rollback, KPI_MASTER);
	}
}
