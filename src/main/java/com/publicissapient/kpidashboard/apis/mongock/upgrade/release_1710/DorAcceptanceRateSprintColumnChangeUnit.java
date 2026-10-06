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

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Adds the "Sprint Name" column to the DOR Acceptance Rate (kpi228) export.
 *
 * <p>The export now lists the weekly rows first and the sprint rows after them, in the same shape
 * as Defect Escape Rate: {@code Days/Weeks}, {@code Project}, then {@code Sprint Name}. Weekly rows
 * leave the sprint name blank; sprint rows carry the sprint name and its date range.
 */
@ChangeUnit(
		id = "dor_acceptance_rate_sprint_name_column",
		order = "17214",
		author = "knowhow",
		systemVersion = "17.1.0")
public class DorAcceptanceRateSprintColumnChangeUnit {

	private static final String KPI_ID = "kpi228";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String BASIC_PROJECT_CONFIG_ID = "basicProjectConfigId";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String KPI_COLUMN_DETAILS = "kpiColumnDetails";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";

	private static final List<String> COLUMNS =
			List.of(
					"Days/Weeks",
					"Project",
					"Sprint Name",
					"Issue ID",
					"Issue Type",
					"Issue Description",
					"Status",
					"Ready Time",
					"Dev Start Date",
					"Description Revisions",
					"Acceptance Criteria Revisions",
					"Substantive Revisions",
					"DOR Outcome");

	private static final List<String> PREVIOUS_COLUMNS =
			COLUMNS.stream().filter(name -> !"Sprint Name".equals(name)).toList();

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		replaceColumns(mongoTemplate, COLUMNS);
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		replaceColumns(mongoTemplate, PREVIOUS_COLUMNS);
	}

	private void replaceColumns(MongoTemplate mongoTemplate, List<String> columnNames) {
		List<Document> details = new ArrayList<>();
		for (int i = 0; i < columnNames.size(); i++) {
			details.add(
					new Document()
							.append(COLUMN_NAME, columnNames.get(i))
							.append(ORDER, i + 1)
							.append(IS_SHOWN, true)
							.append(IS_DEFAULT, true));
		}

		Document columnConfig =
				new Document()
						.append(BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID_FIELD, KPI_ID)
						.append(KPI_COLUMN_DETAILS, details);

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID).append(BASIC_PROJECT_CONFIG_ID, null),
						columnConfig,
						new ReplaceOptions().upsert(true));
	}
}
