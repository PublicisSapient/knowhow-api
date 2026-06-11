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

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		BulkOperations bulkOps =
				mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, COLLECTION_KPI_MASTER);

		bulkOps.updateOne(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi205")),
				Update.update(FIELD_X_AXIS_LABEL, "Weeks").set(FIELD_DEFAULT_ORDER, 1));
		bulkOps.updateOne(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi203")),
				Update.update(FIELD_X_AXIS_LABEL, "Range").set(FIELD_DEFAULT_ORDER, 4));
		bulkOps.updateOne(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi202")),
				Update.update(FIELD_DEFAULT_ORDER, 2));
		bulkOps.updateOne(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi204")),
				Update.update(FIELD_DEFAULT_ORDER, 3));
		bulkOps.updateOne(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi206")),
				Update.update(FIELD_DEFAULT_ORDER, 5));
		bulkOps.updateOne(
				Query.query(Criteria.where(FIELD_KPI_ID).is("kpi207")),
				Update.update(FIELD_DEFAULT_ORDER, 6));

		bulkOps.execute();
	}

	@RollbackExecution
	public void rollback() {
		/* rollback not needed */ }
}
