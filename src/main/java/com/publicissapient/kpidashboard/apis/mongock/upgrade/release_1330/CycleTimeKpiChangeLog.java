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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1330;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeUnit(id = "add_cycle_time_kpi", order = "13305", author = "kunkambl", systemVersion = "13.3.0")
public class CycleTimeKpiChangeLog {

	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_ID = "kpi171";
	private static final String KPI_LABEL = "kpiId";

	private final MongoTemplate mongoTemplate;

	public CycleTimeKpiChangeLog(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		updateLeadTimeKpi();
		addToKpiCategoryMapping();
		updateKpiColumnConfig();
	}

	private void updateLeadTimeKpi() {
		Document updateFields = new Document("$set",
				new Document("boxType", "3_column").append("chartType", "table").append("defaultOrder", 29)
						.append("aggregationCriteria", "sum").append("groupId", 33))
				.append("$unset", new Document("kpiSubCategory", "").append("kpiCategory", ""));
		mongoTemplate.getCollection(KPI_MASTER_COLLECTION).updateOne(new Document(KPI_LABEL, KPI_ID), updateFields);
	}

	public void addToKpiCategoryMapping() {
		Document kpiCategoryMappingDocument = new Document().append(KPI_LABEL, KPI_ID).append("categoryId", "speed")
				.append("kpiOrder", 12).append("kanban", false);
		mongoTemplate.getCollection("kpi_category_mapping").insertOne(kpiCategoryMappingDocument);
	}

	public void updateKpiColumnConfig() {
		Document query = new Document(KPI_LABEL, KPI_ID).append("kpiColumnDetails.columnName", "Issue Id");
		Document update = new Document("$set", new Document("kpiColumnDetails.$.columnName", "Issue ID"));
		mongoTemplate.getCollection("kpi_column_configs").updateMany(query, update);
	}

	@RollbackExecution
	public void rollback() {
		Document updateFields = new Document("$set",
				new Document("kpiSubCategory", "defaultSubCategory").append("kpiCategory", "defaultCategory")
						.append("groupId", 11))
				.append("$unset",
						new Document("boxType", "").append("chartType", "").append("aggregationCriteria", ""));
		mongoTemplate.getCollection(KPI_MASTER_COLLECTION).updateOne(new Document(KPI_LABEL, KPI_ID), updateFields);

		Document query = new Document(KPI_LABEL, KPI_ID).append("kpiColumnDetails.columnName", "Issue ID");
		Document update = new Document("$set", new Document("kpiColumnDetails.$.columnName", "Issue Id"));
		mongoTemplate.getCollection("kpi_column_configs").updateMany(query, update);

		mongoTemplate.getCollection("kpi_category_mapping")
				.deleteOne(new Document(KPI_LABEL, KPI_ID).append("categoryId", "speed"));
	}
}
