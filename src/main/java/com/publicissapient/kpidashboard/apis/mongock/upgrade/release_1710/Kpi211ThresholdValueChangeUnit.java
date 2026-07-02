package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "kpi211_threshold_value_add",
		order = "17141",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class Kpi211ThresholdValueChangeUnit {

	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_ID = "kpi211";
	private static final String THRESHOLD_VALUE = "thresholdValue";
	private static final int DEFAULT_THRESHOLD = 10;

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(
						new Document(KPI_ID_FIELD, KPI_ID),
						new Document("$set", new Document(THRESHOLD_VALUE, DEFAULT_THRESHOLD)));
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(
						new Document(KPI_ID_FIELD, KPI_ID),
						new Document("$unset", new Document(THRESHOLD_VALUE, "")));
	}
}
