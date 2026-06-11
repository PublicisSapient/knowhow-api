package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "slingshot_kpi_order_update",
		order = "17114",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class SlingshotKpisUpdateChangeLog {

	private static final String COLLECTION_KPI_MASTER = "kpi_master";
	private static final String FIELD_KPI_ID = "kpiId";
	private static final String FIELD_X_AXIS_LABEL = "xAxisLabel";
	private static final String FIELD_DEFAULT_ORDER = "defaultOrder";
	private static final String KPI_DEFINITION = "kpiInfo.definition";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		BulkOperations bulkOps =
				mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, COLLECTION_KPI_MASTER);

		bulkOps.updateOne(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi205")),
				Update.update(FIELD_X_AXIS_LABEL, "Weeks")
						.set(FIELD_DEFAULT_ORDER, 3)
						.set(
								KPI_DEFINITION,
								"""
								Number of flow items completed per unit time, segmented by flow item type.
								"Count of issues where status transitioned to Done within the period, grouped by Flow Item Type custom field."""));
		bulkOps.updateOne(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi203")),
				Update.update(FIELD_X_AXIS_LABEL, "Range")
						.set(FIELD_DEFAULT_ORDER, 4)
						.set(
								KPI_DEFINITION,
								"Ratio of active work time to total flow time. Shows how much of an item's lifetime is spent moving forward versus waiting."));
		bulkOps.updateOne(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi202")),
				Update.update(FIELD_DEFAULT_ORDER, 1)
						.set(
								KPI_DEFINITION,
								"Elapsed wall-clock time from when work started (entered an active state) to when it reached Done.")
						.set("kpiWidth", 50));
		bulkOps.updateOne(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi204")),
				Update.update(FIELD_DEFAULT_ORDER, 2));
		bulkOps.updateOne(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi206")),
				Update.update(FIELD_DEFAULT_ORDER, 5)
						.set(
								KPI_DEFINITION,
								"""
						Number of flow items currently in progress in the value stream (WIP).
						Too high = thrashing; too low = underutilisation.
						"Count of issues NOT in Backlog/To Do AND NOT in Done at point in time.."""));
		bulkOps.updateOne(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi207")),
				Update.update(FIELD_DEFAULT_ORDER, 6)
						.set(
								KPI_DEFINITION,
								"Percentage mix of completed work across Features, Defects, Risk, and Tech Debt."));

		bulkOps.execute();
	}

	@RollbackExecution
	public void rollback() {
		/* rollback not needed */ }
}
