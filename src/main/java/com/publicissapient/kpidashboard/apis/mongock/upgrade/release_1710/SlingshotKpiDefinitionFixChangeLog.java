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
		id = "slingshot_kpi_definition_fix",
		order = "17116",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class SlingshotKpiDefinitionFixChangeLog {

	private static final String COLLECTION_KPI_MASTER = "kpi_master";
	private static final String FIELD_KPI_ID = "kpiId";
	private static final String KPI_DEFINITION = "kpiInfo.definition";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		mongoTemplate.updateFirst(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi205")),
				Update.update(
						KPI_DEFINITION,
						"Number of flow items completed per unit time, segmented by flow item type. Count of issues where status transitioned to Done within the period, grouped by Flow Item Type custom field."),
				COLLECTION_KPI_MASTER);

		mongoTemplate.updateFirst(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi206")),
				Update.update(
						KPI_DEFINITION,
						"Number of flow items currently in progress in the value stream (WIP). Too high = thrashing; too low = underutilisation. Count of issues NOT in Backlog/To Do AND NOT in Done at point in time."),
				COLLECTION_KPI_MASTER);
	}

	@RollbackExecution
	public void rollback() {
		/* rollback not needed */
	}
}
