/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.IndexOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Creates the unique compound index on {@code epic_hygiene_results} required by {@link
 * com.publicissapient.kpidashboard.common.model.jira.EpicHygieneResult}. One document per
 * (basicProjectConfigId, epicKey) is enforced here so the application-level upsert-by-key pattern
 * cannot produce duplicates when several batches persist concurrently.
 */
@ChangeUnit(id = "epic_hygiene_result_index", order = "17176", author = "kunkambl", systemVersion = "17.1.0")
public class EpicHygieneResultIndexChangeUnit {

	private static final String COLLECTION = "epic_hygiene_results";
	private static final String INDEX_NAME = "project_epic_idx";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		if (!mongoTemplate.collectionExists(COLLECTION)) {
			mongoTemplate.createCollection(COLLECTION);
		}
		Document indexKeys = new Document("basicProjectConfigId", 1).append("epicKey", 1);
		IndexOptions options = new IndexOptions().unique(true).name(INDEX_NAME).background(false);
		mongoTemplate.getCollection(COLLECTION).createIndex(indexKeys, options);
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		if (mongoTemplate.collectionExists(COLLECTION)) {
			mongoTemplate.getCollection(COLLECTION).dropIndex(INDEX_NAME);
		}
	}
}

