package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "processor_common_flag_kpi216_217_218",
		order = "17174",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class ProcessorCommonFlagChangeUnit {

	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";

	private static final List<String> FIELDS_REQUIRING_PROCESSOR =
			List.of(
					"jiraBugRaisedByIdentificationKPI216",
					"jiraProductionIncidentIdentificationKPI217",
					"e2eTestBranchKPI218");

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		setProcessorCommon(true);
	}

	@RollbackExecution
	public void rollback() {
		setProcessorCommon(false);
	}

	private void setProcessorCommon(boolean value) {
		for (String fieldName : FIELDS_REQUIRING_PROCESSOR) {
			mongoTemplate
					.getCollection(FIELD_MAPPING_STRUCTURE)
					.updateOne(
							new Document("fieldName", fieldName),
							new Document("$set", new Document("processorCommon", value)));
		}
	}
}
