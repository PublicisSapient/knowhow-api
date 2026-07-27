package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "rollback_defect_escape_rate_slingshot_field_mapping_kpi216",
		order = "17167",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class DefectEscapeRateSlingshotFieldMappingChangeUnit {

	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";
	private static final String FIELD_NAME = "fieldName";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.deleteOne(new Document(FIELD_NAME, "jiraBugRaisedByIdentificationKPI216"));
	}

	@RollbackExecution
	public void rollback() {
		// no-op: forward migration handles the upsert
	}
}
