package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.List;

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
		id = "update_slingshot_flow_efficiency",
		order = "17107",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class FlowEfficiencyUpdate {

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		Query query = new Query(Criteria.where("kpiId").is("kpi203"));
		Update update =
				new Update()
						.set(
								"kpiInfo.definition",
								"Flow efficiency is a metric commonly used in Agile and Lean methodologies to measure the proportion of time work items spend actively being worked on (value-adding time) compared to their total lead time (including waiting or idle time). It helps teams identify process bottlenecks and optimize workflow.")
						.set(
								"kpiInfo.formula",
								List.of(
										new Document("lhs", "Flow efficiency")
												.append("operator", "division")
												.append(
														"operands", List.of("(Active work time)", "(Total lead time) x 100%"))))
						.set("defaultOrder", 3);

		Query query1 = new Query(Criteria.where("kpiId").is("kpi204"));
		Update update1 = new Update().set("xAxisLabel", "Range");

		mongoTemplate.updateMulti(query, update, "kpi_master");
		mongoTemplate.updateMulti(query1, update1, "kpi_master");
	}

	@RollbackExecution
	public void rollback() {
		/* rollback not needed */ }
}
