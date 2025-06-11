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

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1330;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;
import com.publicissapient.kpidashboard.apis.ai.constants.PromptKeys;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(id = "r_insert_Prompt_Details", order = "013302", author = "shunaray", systemVersion = "13.3.0")
public class PromptDetailsChangeUnit {

	private static final String PROMPT_DETAILS_COLLECTION = "prompt_details";
	private final MongoTemplate mongoTemplate;

	public PromptDetailsChangeUnit(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		MongoCollection<Document> collection = mongoTemplate.getCollection(PROMPT_DETAILS_COLLECTION);
		collection.deleteOne(new Document("key", PromptKeys.SPRINT_GOALS_PROMPT));

	}

	@RollbackExecution
	public void rollback() {
		MongoCollection<Document> collection = mongoTemplate.getCollection(PROMPT_DETAILS_COLLECTION);

		Document promptDetails = new Document().append("key", PromptKeys.SPRINT_GOALS_PROMPT).append("prompt",
				"Rewrite the following sprint goals into a concise and professional summary tailored for executive stakeholders. Present the output as a bulleted list. Use clear, outcome-focused language. Limit the total response to 50 words. Emphasize business value or strategic alignment where possible. Sprint goals:");

		collection.insertOne(promptDetails);

	}
}