package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1320;

import java.util.Collections;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/** Mongock change log for inserting Rally processor if it doesn't exist */
@ChangeUnit(
		id = "insertRallyProcessorIfNotExists",
		order = "13209",
		author = "girpatha",
		systemVersion = "13.2.0")
public class RallyProcessorChangeLog {

	private static final String PROCESSOR_COLLECTION = "processor";
	private static final String CLASS_KEY = "_class";
	private static final String PROCESSOR_NAME = "processorName";
	public static final String RALLY = "Rally";

	private final MongoTemplate mongoTemplate;

	public RallyProcessorChangeLog(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void insertRallyProcessor() {
		// Check if Rally processor already exists
		Query query = new Query(Criteria.where(PROCESSOR_NAME).is(RALLY));
		boolean processorExists = mongoTemplate.exists(query, PROCESSOR_COLLECTION);

		// Only insert if processor doesn't exist
		if (!processorExists) {
			Document rallyProcessor =
					new Document()
							.append(PROCESSOR_NAME, RALLY)
							.append("processorType", "AGILE_TOOL")
							.append("isActive", true)
							.append("isOnline", true)
							.append("lastSuccess", false)
							.append("updatedTime", System.currentTimeMillis())
							.append("errors", Collections.emptyList())
							.append(CLASS_KEY, "com.publicissapient.kpidashboard.rally.model.RallyProcessor");
			mongoTemplate.getCollection(PROCESSOR_COLLECTION).insertOne(rallyProcessor);
		}
	}

	@RollbackExecution
	public void rollback() {
		// Only delete the processor if it was inserted by this changeLog
		// This is a simplified approach - in a real scenario, you might want to track
		// what was inserted
		mongoTemplate
				.getCollection(PROCESSOR_COLLECTION)
				.deleteOne(new Document(PROCESSOR_NAME, RALLY));
	}
}
