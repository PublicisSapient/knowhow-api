package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "flow_load_kpi_column_cleanup",
		order = "17125",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class FlowLoadKpiColumnCleanupChangeUnit {

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		mongoTemplate
				.getCollection("kpi_column_configs")
				.updateOne(
						new Document("basicProjectConfigId", null).append("kpiId", "kpi206"),
						new Document(
								"$pull",
								new Document(
										"kpiColumnDetails",
										new Document(
												"columnName",
												new Document(
														"$in",
														Arrays.asList(
																"In-Analysis",
																"In-Testing",
																"In-Progress",
																"In-Development",
																"Open"))))));
	}

	@RollbackExecution
	public void rollback() {}
}
