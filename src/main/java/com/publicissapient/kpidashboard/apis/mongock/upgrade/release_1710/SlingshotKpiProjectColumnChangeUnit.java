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

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "slingshot_kpi_project_column",
		order = "17193",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class SlingshotKpiProjectColumnChangeUnit {

	private static final String KPI_ID = "kpiId";
	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		ReplaceOptions upsert = new ReplaceOptions().upsert(true);

		upsert(
				upsert,
				"kpi202",
				List.of(
						col("Project", 1),
						col("Issue ID", 2),
						col("Issue Type", 3),
						col("Issue Description", 4),
						col("Status", 5),
						col("Group Map", 6)));

		upsert(
				upsert,
				"kpi204",
				List.of(
						col("Project", 1),
						col("Issue ID", 2),
						col("Issue Type", 3),
						col("Issue Description", 4),
						col("Sprint Name", 5),
						col("Status", 6),
						col("Group Map", 7)));

		upsert(
				upsert,
				"kpi203",
				List.of(
						col("Project", 1),
						col("Issue ID", 2),
						col("Issue Type", 3),
						col("Issue Description", 4),
						col("Size (In Story Points)", 5),
						col("Status", 6),
						col("Wait Time", 7),
						col("Total Time", 8),
						col("Flow Efficiency", 9),
						col("Group Map", 10)));

		upsert(
				upsert,
				"kpi205",
				List.of(
						col("Days/Weeks", 1),
						col("Project", 2),
						col("Issue ID", 3),
						col("Issue Type", 4),
						col("Issue Description", 5),
						col("Sprint Name", 6),
						col("Squad", 7),
						col("Priority", 8),
						col("Story Points", 9),
						col("Status", 10),
						col("Original Time Estimate (in hours)", 11),
						col("Time Spent (in hours)", 12)));

		upsert(
				upsert,
				"kpi216",
				List.of(
						col("Days/Weeks", 1),
						col("Project", 2),
						col("Sprint Name", 3),
						col("Defect ID", 4),
						col("Description", 5),
						col("Escaped Defect", 6),
						col("Escaped defect identifier", 7),
						col("Defect Priority", 8),
						col("Defect Status", 9),
						col("Story ID", 10),
						col("Squad", 11),
						col("Time Spent (in hours)", 12)));

		upsert(
				upsert,
				"kpi217",
				List.of(
						col("Days/Weeks", 1),
						col("Project", 2),
						col("Issue ID", 3),
						col("Issue Type", 4),
						col("Issue Description", 5),
						col("Created Time", 6),
						col("Closed Time", 7),
						col("Time to Recover (In Hours)", 8)));

		upsert(
				upsert,
				"kpi222",
				List.of(
						col("Days/Weeks", 1),
						col("Project", 2),
						col("Issue ID", 3),
						col("Issue Type", 4),
						col("Issue Description", 5),
						col("Start Time", 6),
						col("Ready Time", 7),
						col("Refinement Cycle Time (Days)", 8)));

		upsert(
				upsert,
				"kpi223",
				List.of(
						col("Days/Weeks", 1),
						col("Project", 2),
						col("Repository", 3),
						col("Severity", 4),
						col("Alert Count", 5),
						col("Mean Lead Time (Days)", 6)));

		upsert(
				upsert,
				"kpi311",
				List.of(
						col("Project", 1),
						col("Sprint Name", 2),
						col("Issue Id", 3),
						col("Issue Type", 4),
						col("Assignee", 5),
						col("Hygiene Score", 6),
						col("Overall Status", 7),
						col("Recommendations", 8)));

		upsert(
				upsert,
				"kpi312",
				List.of(
						col("Project", 1),
						col("Epic ID", 2),
						col("Epic Name", 3),
						col("Status", 4),
						col("Assignee", 5),
						col("Business Clarity", 6),
						col("Scope Definition", 7),
						col("Solution Readiness", 8),
						col("Dependency Readiness", 9),
						col("Risk Readiness", 10),
						col("Readiness Score", 11),
						col("Overall Status", 12),
						col("Recommendations", 13)));
	}

	@RollbackExecution
	public void rollback() {
		ReplaceOptions upsert = new ReplaceOptions().upsert(true);

		upsert(
				upsert,
				"kpi202",
				List.of(
						col("Issue ID", 1),
						col("Issue Type", 2),
						col("Issue Description", 3),
						col("Status", 4),
						col("Group Map", 5)));

		upsert(
				upsert,
				"kpi204",
				List.of(
						col("Issue ID", 1),
						col("Issue Type", 2),
						col("Issue Description", 3),
						col("Sprint Name", 4),
						col("Status", 5),
						col("Group Map", 6)));

		upsert(
				upsert,
				"kpi203",
				List.of(
						col("Issue ID", 1),
						col("Issue Type", 2),
						col("Issue Description", 3),
						col("Size (In Story Points)", 4),
						col("Status", 5),
						col("Wait Time", 6),
						col("Total Time", 7),
						col("Flow Efficiency", 8),
						col("Group Map", 9)));

		upsert(
				upsert,
				"kpi205",
				List.of(
						col("Days/Weeks", 1),
						col("Issue ID", 2),
						col("Issue Type", 3),
						col("Issue Description", 4),
						col("Sprint Name", 5),
						col("Squad", 6),
						col("Priority", 7),
						col("Story Points", 8),
						col("Status", 9),
						col("Original Time Estimate (in hours)", 10),
						col("Time Spent (in hours)", 11)));

		upsert(
				upsert,
				"kpi216",
				List.of(
						col("Days/Weeks", 1),
						col("Sprint Name", 2),
						col("Defect ID", 3),
						col("Description", 4),
						col("Escaped Defect", 5),
						col("Escaped defect identifier", 6),
						col("Defect Priority", 7),
						col("Defect Status", 8),
						col("Story ID", 9),
						col("Squad", 10),
						col("Time Spent (in hours)", 11)));

		upsert(
				upsert,
				"kpi217",
				List.of(
						col("Days/Weeks", 1),
						col("Issue ID", 2),
						col("Issue Type", 3),
						col("Issue Description", 4),
						col("Created Time", 5),
						col("Closed Time", 6),
						col("Time to Recover (In Hours)", 7)));

		upsert(
				upsert,
				"kpi222",
				List.of(
						col("Days/Weeks", 1),
						col("Issue ID", 2),
						col("Issue Type", 3),
						col("Issue Description", 4),
						col("Start Time", 5),
						col("Ready Time", 6),
						col("Refinement Cycle Time (Days)", 7)));

		upsert(
				upsert,
				"kpi223",
				List.of(
						col("Days/Weeks", 1),
						col("Repository", 2),
						col("Severity", 3),
						col("Alert Count", 4),
						col("Mean Lead Time (Days)", 5)));

		upsert(
				upsert,
				"kpi311",
				List.of(
						col("Sprint Name", 1),
						col("Issue Id", 2),
						col("Issue Type", 3),
						col("Assignee", 4),
						col("Hygiene Score", 5),
						col("Overall Status", 6),
						col("Recommendations", 7)));

		upsert(
				upsert,
				"kpi312",
				List.of(
						col("Epic ID", 1),
						col("Epic Name", 2),
						col("Status", 3),
						col("Assignee", 4),
						col("Business Clarity", 5),
						col("Scope Definition", 6),
						col("Solution Readiness", 7),
						col("Dependency Readiness", 8),
						col("Risk Readiness", 9),
						col("Readiness Score", 10),
						col("Overall Status", 11),
						col("Recommendations", 12)));
	}

	private void upsert(ReplaceOptions options, String kpiId, List<Document> columns) {
		Document doc =
				new Document()
						.append("basicProjectConfigId", null)
						.append(KPI_ID, kpiId)
						.append("kpiColumnDetails", columns);
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(new Document("basicProjectConfigId", null).append(KPI_ID, kpiId), doc, options);
	}

	private Document col(String name, int order) {
		return new Document()
				.append("columnName", name)
				.append("order", order)
				.append("isShown", true)
				.append("isDefault", true);
	}
}
