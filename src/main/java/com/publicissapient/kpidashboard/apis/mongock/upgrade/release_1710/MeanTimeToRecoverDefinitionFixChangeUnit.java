package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "kpi217_definition_median_to_mean_fix",
		order = "17171",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class MeanTimeToRecoverDefinitionFixChangeUnit {

	private static final String COLLECTION_KPI_MASTER = "kpi_master";
	private static final String FIELD_KPI_ID = "kpiId";
	private static final String KPI_DEFINITION = "kpiInfo.definition";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		mongoTemplate.updateFirst(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi217")),
				Update.update(
						KPI_DEFINITION, "Mean time from production incident detected to service restored."),
				COLLECTION_KPI_MASTER);
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate.updateFirst(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi217")),
				Update.update(
						KPI_DEFINITION, "Median time from production incident detected to service restored."),
				COLLECTION_KPI_MASTER);
	}
}
