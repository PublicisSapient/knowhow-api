package com.publicissapient.kpidashboard.apis.mongock.installation;

import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@ChangeUnit(
		id = "ai_usage_statistics",
		order = "14105",
		author = "anamitra1",
		systemVersion = "14.1.0")
public class AIUsageStatisticsChangeLog {
	public static final String AI_USAGE_STATISTICS_COLLECTION = "ai_usage_statistics";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void changeSet() {
		if (!mongoTemplate.collectionExists(AI_USAGE_STATISTICS_COLLECTION)) {
			mongoTemplate.createCollection(AI_USAGE_STATISTICS_COLLECTION);
		}
	}

	@RollbackExecution
	public void rollback() {
		if (mongoTemplate.collectionExists(AI_USAGE_STATISTICS_COLLECTION)) {
			mongoTemplate.dropCollection(AI_USAGE_STATISTICS_COLLECTION);
		}
	}
}
