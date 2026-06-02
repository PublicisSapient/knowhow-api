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
		id = "cycle_time_slingshot_update_kpi_width",
		order = "17109",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class UpdateCycleTimeSlingshotKpiWidth {

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		Query query = new Query(Criteria.where("kpiId").is("kpi202"));
		Update update = new Update().set("kpiWidth", 100);
		mongoTemplate.updateMulti(query, update, "kpi_master");
	}

	@RollbackExecution
	public void rollback() {
		Query query = new Query(Criteria.where("kpiId").is("kpi202"));
		Update update = new Update().unset("kpiWidth");
		mongoTemplate.updateMulti(query, update, "kpi_master");
	}
}
