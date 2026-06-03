package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "cycle_time_slingshot_update_kpi_width",
		order = "17109",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class UpdateCycleTimeSlingshotKpiWidth {

	private static final String KPI_ID = "kpiId";
	private static final String KPI_NUM = "kpi204";
	private static final String KPI_COLUMN_CONFIG = "kpi_column_configs";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		Query query = new Query(Criteria.where(KPI_ID).is("kpi202"));
		Update update = new Update().set("kpiWidth", 100);
		mongoTemplate.updateMulti(query, update, "kpi_master");

		Query query1 = new Query(Criteria.where(KPI_ID).is(KPI_NUM));
		Document sprintNameColumn =
				new Document()
						.append("columnName", "Sprint Name")
						.append("order", 4)
						.append("isShown", true)
						.append("isDefault", true);
		Update update1 = new Update().push("kpiColumnDetails", sprintNameColumn);
		mongoTemplate.updateMulti(query1, update1, KPI_COLUMN_CONFIG);

		Query query2 =
				new Query(
						Criteria.where(KPI_ID).is(KPI_NUM).and("kpiColumnDetails.columnName").is("Group Map"));
		Update update2 = new Update().set("kpiColumnDetails.$.order", "5");
		mongoTemplate.updateMulti(query2, update2, KPI_COLUMN_CONFIG);
	}

	@RollbackExecution
	public void rollback() {
		Query query = new Query(Criteria.where(KPI_ID).is("kpi202"));
		Update update = new Update().unset("kpiWidth");
		mongoTemplate.updateMulti(query, update, "kpi_master");

		Query query1 = Query.query(Criteria.where(KPI_ID).is(KPI_NUM));
		Update update1 =
				new Update()
						.pull("kpiColumnDetails", Query.query(Criteria.where("columnName").is("Sprint Name")));
		mongoTemplate.updateMulti(query1, update1, KPI_COLUMN_CONFIG);
	}
}
