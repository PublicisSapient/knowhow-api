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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1330;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "add_lead_time_kpi",
		order = "13305",
		author = "kunkambl",
		systemVersion = "13.3.0")
public class LeadTimeKpiChangeLog {

	private static final String KPI_ID = "kpi3";
	private static final String KPI_LABEL = "kpiId";
	private static final String KPI_MASTER_COLLECTION = "kpi_master";

	private final MongoTemplate mongoTemplate;

	public LeadTimeKpiChangeLog(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execute() {
		updateLeadTimeKpi();
		addToKpiCategoryMapping();
	}

	private void updateLeadTimeKpi() {
		Document updateFields =
				new Document("$unset", new Document("kpiSubCategory", "").append("kpiCategory", ""))
						.append(
								"$set",
								new Document("defaultOrder", 29)
										.append("groupId", 33)
										.append("aggregationCriteria", "average"));
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(new Document(KPI_LABEL, KPI_ID), updateFields);
	}

	public void addToKpiCategoryMapping() {
		Document kpiCategoryMappingDocument =
				new Document()
						.append(KPI_LABEL, KPI_ID)
						.append("categoryId", "speed")
						.append("kpiOrder", 11)
						.append("kanban", false);
		mongoTemplate.getCollection("kpi_category_mapping").insertOne(kpiCategoryMappingDocument);
	}

	@RollbackExecution
	public void rollback() {
		removeLeadTimeKpi();
		removeKpiCategoryMapping();
	}

	private void removeLeadTimeKpi() {
		Document updateFields =
				new Document(
								"$set",
								new Document("kpiSubCategory", "defaultSubCategory")
										.append("kpiCategory", "defaultCategory")
										.append("defaultOrder", 1)
										.append("groupId", 11))
						.append("$unset", new Document("aggregationCriteria", ""));
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(new Document(KPI_LABEL, KPI_ID), updateFields);
	}

	private void removeKpiCategoryMapping() {
		mongoTemplate.getCollection("kpi_category_mapping").deleteOne(new Document(KPI_LABEL, KPI_ID));
	}
}
