package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.IndexOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Creates the unique compound index on {@code story_hygiene_sprint_results} required by {@link
 * com.publicissapient.kpidashboard.common.model.jira.StoryHygieneSprintResult}. One document per
 * (basicProjectConfigId, sprintId) is enforced here so the application-level upsert-by-key pattern
 * cannot produce duplicates across concurrent writes.
 */
@ChangeUnit(
		id = "story_hygiene_sprint_result_index",
		order = "17150",
		author = "knowhow",
		systemVersion = "17.1.0")
public class StoryHygieneSprintResultIndexChangeUnit {

	private static final String COLLECTION = "story_hygiene_sprint_results";
	private static final String INDEX_NAME = "project_sprint_idx";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		if (!mongoTemplate.collectionExists(COLLECTION)) {
			mongoTemplate.createCollection(COLLECTION);
		}
		Document indexKeys = new Document("basicProjectConfigId", 1).append("sprintId", 1);
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
