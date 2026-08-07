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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "e2e_test_pass_rate_total_builds_rename",
		order = "17173",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class E2ETestPassRateTotalBuildsRenameChangeUnit {

	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";
	private static final String KPI_ID = "kpi218";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document("basicProjectConfigId", null).append("kpiId", KPI_ID),
						columnConfig("Total Builds"),
						new ReplaceOptions().upsert(true));
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS)
				.replaceOne(
						new Document("basicProjectConfigId", null).append("kpiId", KPI_ID),
						columnConfig("Builds in Week"),
						new ReplaceOptions().upsert(true));
	}

	private Document columnConfig(String buildColumnName) {
		return new Document()
				.append("basicProjectConfigId", null)
				.append("kpiId", KPI_ID)
				.append(
						"kpiColumnDetails",
						Arrays.asList(
								col("Days/Weeks", 1),
								col("Workflow", 2),
								col("Branch", 3),
								col("Suite Name", 4),
								col(buildColumnName, 5),
								col("Avg Tests/Build", 6),
								col("Avg Passed", 7),
								col("Avg Failed", 8),
								col("Avg Skipped", 9),
								col("Pass Rate %", 10)));
	}

	private Document col(String name, int order) {
		return new Document()
				.append("columnName", name)
				.append("order", order)
				.append("isShown", true)
				.append("isDefault", true);
	}
}
