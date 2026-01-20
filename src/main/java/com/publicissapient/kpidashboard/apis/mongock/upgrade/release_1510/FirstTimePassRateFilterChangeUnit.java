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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1510;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@ChangeUnit(
		id = "ftpr_filter_change_unit",
		order = "15101",
		author = "kunkambl",
		systemVersion = "15.1.0")
public class FirstTimePassRateFilterChangeUnit {
	private final MongoTemplate mongoTemplate;

	private static final String EXISTING_KPI_FILTER_TYPE = "multiSelectDropDown";

	private static final String KPI_FILTER_TYPE_FIELD = "kpiFilter";

	private static final String KPI_SEARCH_FIELD = "kpiId";

	private static final String COLLECTION_NAME = "kpi_master";

	private static final String KPI_ID = "kpi82";

	@Execution
	public void execution() {
		MongoCollection<Document> kpiMaster = mongoTemplate.getCollection(COLLECTION_NAME);
		// Update documents
		updateDocument(kpiMaster, KPI_ID, null);
	}

	private void updateDocument(
			MongoCollection<Document> kpiCategoryMapping, String kpiId, String kpiSource) {
		// Create the filter
		Document filter = new Document(KPI_SEARCH_FIELD, kpiId);
		// Create the update
		Document update = new Document("$set", new Document(KPI_FILTER_TYPE_FIELD, kpiSource));
		// Perform the update
		kpiCategoryMapping.updateOne(filter, update);
	}

	@RollbackExecution
	public void rollback() {
		MongoCollection<Document> kpiMaster = mongoTemplate.getCollection(COLLECTION_NAME);
		// Update documents
		updateDocument(kpiMaster, KPI_ID, EXISTING_KPI_FILTER_TYPE);
	}
}
