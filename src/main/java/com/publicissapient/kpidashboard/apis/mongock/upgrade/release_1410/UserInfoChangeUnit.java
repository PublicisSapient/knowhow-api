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
package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1410;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@ChangeUnit(
		id = "addCreatedOnUpdatedOnFields",
		order = "14103",
		author = "gursingh49",
		systemVersion = "14.1.0")
public class UserInfoChangeUnit {

	private final MongoTemplate mongoTemplate;

	private static final String FIELD_CREATED_ON = "createdOn";

	private static final String FIELD_CREATED_BY = "createdBy";

	private static final String FIELD_UPDATED_ON = "updatedOn";

	private static final String FIELD_UPDATED_BY = "updatedBy";

	private static final String DEFAULT_USER = "SUPERADMIN";

	private static final String COLLECTION_NAME = "user_info";

	@Execution
	public void execution() {
		updateUserInfoCollection();
	}

	@RollbackExecution
	public void rollback() {
		log.error("Mongock upgrade failed");
		rollbackUserInfoCollection();
	}

	private void updateUserInfoCollection() {
		var collection = mongoTemplate.getCollection(COLLECTION_NAME);
		List<Document> docs = collection.find().into(new java.util.ArrayList<>());

		for (Document doc : docs) {
			Document updateFields = new Document();

			Object createdOn = doc.get(FIELD_CREATED_ON);
			Object updatedOn = doc.get(FIELD_UPDATED_ON);
			Object createdBy = doc.get(FIELD_CREATED_BY);
			Object updatedBy = doc.get(FIELD_UPDATED_BY);

			if (createdOn == null) {
				updateFields.put(FIELD_CREATED_ON, Date.from(Instant.now()));
			} else if (createdOn instanceof String) {
				updateFields.put(
						FIELD_CREATED_ON, DateUtil.convertingStringToLocalDateTime((String) createdOn, ""));
			}

			if (updatedOn == null || updatedOn.toString().isBlank()) {
				updateFields.put(FIELD_UPDATED_ON, Date.from(Instant.now()));
			}

			if (createdBy == null || createdBy.toString().isBlank()) {
				updateFields.put(FIELD_CREATED_BY, DEFAULT_USER);
			}
			if (updatedBy == null || updatedBy.toString().isBlank()) {
				updateFields.put(FIELD_UPDATED_BY, DEFAULT_USER);
			}

			if (!updateFields.isEmpty()) {
				collection.updateOne(
						new Document("_id", doc.get("_id")), new Document("$set", updateFields));
			}
		}
	}

	private void rollbackUserInfoCollection() {
		MongoCollection<Document> collection = mongoTemplate.getCollection("user_info");
		collection
				.find()
				.forEach(
						doc -> {
							Document updateFields = new Document();
							Document unsetFields = new Document();
							Object createdOn = doc.get(FIELD_CREATED_ON);
							updateFields.put(FIELD_CREATED_ON, ((Date) createdOn).toInstant().toString());
							unsetFields.put(FIELD_UPDATED_ON, "");
							unsetFields.put(FIELD_UPDATED_BY, "");
							unsetFields.put(FIELD_CREATED_BY, "");

							Document updateOps = new Document();
							if (!updateFields.isEmpty()) updateOps.put("$set", updateFields);
							if (!unsetFields.isEmpty()) updateOps.put("$unset", unsetFields);

							if (!updateOps.isEmpty()) {
								collection.updateOne(new Document("_id", doc.get("_id")), updateOps);
							}
						});
	}
}
