package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1320;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "r_insertRallyProcessorIfNotExists",
		order = "013209",
		author = "girpatha",
		systemVersion = "13.2.0")
public class RallyProcessorChangeLog {

	private static final String PROCESSOR_COLLECTION = "processor";
	private static final String PROCESSOR_NAME = "processorName";

	private final MongoTemplate mongoTemplate;

	public RallyProcessorChangeLog(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void deleteProcessor() {
		mongoTemplate
				.getCollection(PROCESSOR_COLLECTION)
				.deleteOne(new Document(PROCESSOR_NAME, "Rally"));
	}

	@RollbackExecution
	public void rollback() {
		// Rollback not required for this
	}
}
