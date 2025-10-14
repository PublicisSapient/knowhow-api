/*
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1320;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "r_update_refinement", order = "013203", author = "shi6", systemVersion = "13.2.0")
public class UpdateRefinementRollback {
	private static final String KPI_MASTER = "kpi_master";
	private static final String KPI_EXCEL_COLUMN_CONFIG = "kpi_column_configs";
	private static final String KPI188 = "kpi188";
	private static final String KPI187 = "kpi187";

	// Document keys
	private static final String KEY_DEFAULT_ORDER = "defaultOrder";
	private static final String KEY_KPI_ID = "kpiId";
	private static final String KEY_COLUMN_NAME = "columnName";
	private static final String KEY_ORDER = "order";
	private static final String KEY_IS_SHOWN = "isShown";
	private static final String KEY_IS_DEFAULT = "isDefault";
	private static final String KEY_IS_RAW_DATA = "isRawData";
	private static final String KEY_KPI_NAME = "kpiName";
	private static final String KEY_KPI_SUB_CATEGORY = "kpiSubCategory";
	private static final String KEY_CHART_TYPE = "chartType";
	private static final String KEY_KPI_WIDTH = "kpiWidth";
	private static final String KEY_KPI_HEIGHT = "kpiHeight";
	private static final String KEY_BASIC_PROJECT_CONFIG_ID = "basicProjectConfigId";
	private static final String KEY_KPI_COLUMN_DETAILS = "kpiColumnDetails";

	private final MongoTemplate mongoTemplate;

	public UpdateRefinementRollback(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void changeSet() {
		MongoCollection<Document> kpiMaster = mongoTemplate.getCollection(KPI_MASTER);
		Document filter = new Document(KEY_KPI_ID, "kpi189").append(KEY_KPI_NAME, "Sprint Goals");

		// Rollback update to set kpiId back to "kpi187"
		Document rollbackUpdate = new Document("$set", new Document(KEY_KPI_ID, KPI187));
		kpiMaster.updateMany(filter, rollbackUpdate);

		Document filter1 = new Document(KEY_KPI_ID, KPI188);
		Document rollbackFields =
				new Document()
						.append(KEY_KPI_WIDTH, null)
						.append(KEY_KPI_HEIGHT, null)
						.append(KEY_IS_RAW_DATA, null)
						.append(KEY_CHART_TYPE, null);

		Document rollbackUpdate188 = new Document("$set", rollbackFields);
		kpiMaster.updateOne(filter1, rollbackUpdate188);

		Document filter2 = new Document(KEY_KPI_ID, KPI187);
		Document rollbackfields =
				new Document()
						.append(KEY_KPI_WIDTH, 100)
						.append(KEY_KPI_HEIGHT, null)
						.append(KEY_IS_RAW_DATA, null)
						.append(KEY_CHART_TYPE, null)
						.append(KEY_KPI_SUB_CATEGORY, "Iteration Progress")
						.append(KEY_DEFAULT_ORDER, 10);

		Document rollbackUpdate187 = new Document("$set", rollbackfields);
		kpiMaster.updateOne(filter2, rollbackUpdate187);

		kpiMaster.updateOne(
				new Document(KEY_KPI_ID, "kpi133"),
				new Document("$set", new Document().append(KEY_DEFAULT_ORDER, 11)));

		mongoTemplate
				.getCollection(KPI_EXCEL_COLUMN_CONFIG)
				.deleteMany(new Document(KEY_KPI_ID, KPI187));
	}

	private static void updateKpi188(MongoCollection<Document> kpiMaster) {
		Document filter = new Document(KEY_KPI_ID, KPI188);

		Document updateFields =
				new Document()
						.append(KEY_KPI_WIDTH, 33)
						.append(KEY_KPI_HEIGHT, 50)
						.append(KEY_IS_RAW_DATA, false)
						.append(KEY_CHART_TYPE, "tabular-with-donut-chart")
						.append(
								"kpiInfo.details.0.kpiLinkDetail.link",
								"https://knowhow.tools.publicis.sapient.com/wiki/kpi188-Late+Refinemnt+Future+Sprint");
		Document update = new Document("$set", updateFields);
		kpiMaster.updateOne(filter, update);
	}

	private static void updateKpi187(MongoCollection<Document> kpiMaster) {
		Document filter = new Document(KEY_KPI_ID, KPI187);
		Document updateFields =
				new Document()
						.append(KEY_KPI_SUB_CATEGORY, "Iteration Review")
						.append(KEY_KPI_WIDTH, 66)
						.append(KEY_KPI_HEIGHT, 100)
						.append(KEY_IS_RAW_DATA, false)
						.append(KEY_DEFAULT_ORDER, 11)
						.append(
								"kpiInfo.details.0.kpiLinkDetail.link",
								"https://knowhow.tools.publicis.sapient.com/wiki/kpi187-Late+Refinemnt+Current+Sprint");
		Document update = new Document("$set", updateFields);
		kpiMaster.updateOne(filter, update);
		kpiMaster.updateOne(
				new Document(KEY_KPI_ID, "kpi133"),
				new Document("$set", new Document().append(KEY_DEFAULT_ORDER, 10)));
	}

	private static void updateSprintGoalKPIID(MongoCollection<Document> mongoTemplate) {
		Document filter =
				new Document().append(KEY_KPI_ID, KPI187).append(KEY_KPI_NAME, "Sprint Goals");
		Document update = new Document("$set", new Document(KEY_KPI_ID, "kpi189"));
		mongoTemplate.updateMany(filter, update);
	}

	public void addKpi188ExcelConfig(MongoTemplate mongoTemplate) {

		// Construct the document with column details
		Document kpiColumnConfig =
				new Document(KEY_BASIC_PROJECT_CONFIG_ID, null)
						.append(KEY_KPI_ID, KPI187)
						.append(
								KEY_KPI_COLUMN_DETAILS,
								new Document[] {
									new Document(KEY_COLUMN_NAME, "Sprint Name")
											.append(KEY_ORDER, 0)
											.append(KEY_IS_SHOWN, true)
											.append(KEY_IS_DEFAULT, false),
									new Document(KEY_COLUMN_NAME, "Date")
											.append(KEY_ORDER, 1)
											.append(KEY_IS_SHOWN, true)
											.append(KEY_IS_DEFAULT, false),
									new Document(KEY_COLUMN_NAME, "Issue Id")
											.append(KEY_ORDER, 2)
											.append(KEY_IS_SHOWN, true)
											.append(KEY_IS_DEFAULT, false),
									new Document(KEY_COLUMN_NAME, "Issue Type")
											.append(KEY_ORDER, 3)
											.append(KEY_IS_SHOWN, true)
											.append(KEY_IS_DEFAULT, true),
									new Document(KEY_COLUMN_NAME, "Issue Description")
											.append(KEY_ORDER, 4)
											.append(KEY_IS_SHOWN, true)
											.append(KEY_IS_DEFAULT, true),
									new Document(KEY_COLUMN_NAME, "Size(story point/hours)")
											.append(KEY_ORDER, 5)
											.append(KEY_IS_SHOWN, true)
											.append(KEY_IS_DEFAULT, true),
									new Document(KEY_COLUMN_NAME, "Due Date")
											.append(KEY_ORDER, 6)
											.append(KEY_IS_SHOWN, true)
											.append(KEY_IS_DEFAULT, false),
									new Document(KEY_COLUMN_NAME, "Assignee")
											.append(KEY_ORDER, 7)
											.append(KEY_IS_SHOWN, true)
											.append(KEY_IS_DEFAULT, false),
									new Document(KEY_COLUMN_NAME, "Issue Status")
											.append(KEY_ORDER, 8)
											.append(KEY_IS_SHOWN, true)
											.append(KEY_IS_DEFAULT, true),
									new Document(KEY_COLUMN_NAME, "Un-Refined")
											.append(KEY_ORDER, 9)
											.append(KEY_IS_SHOWN, true)
											.append(KEY_IS_DEFAULT, true)
								});

		mongoTemplate.insert(kpiColumnConfig, KPI_EXCEL_COLUMN_CONFIG);
	}

	@RollbackExecution
	public void rollback() {
		MongoCollection<Document> kpiMaster = mongoTemplate.getCollection(KPI_MASTER);
		updateSprintGoalKPIID(kpiMaster);
		updateKpi188(kpiMaster);
		updateKpi187(kpiMaster);
		addKpi188ExcelConfig(mongoTemplate);
	}
}
