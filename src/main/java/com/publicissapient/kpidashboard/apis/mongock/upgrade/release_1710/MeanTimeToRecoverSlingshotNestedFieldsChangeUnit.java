package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "mean_time_to_recover_slingshot_nested_fields",
		order = "17163",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class MeanTimeToRecoverSlingshotNestedFieldsChangeUnit {

	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";
	private static final String FIELD_NAME = "fieldName";
	private static final String FIELD_KPI217_RADIO = "jiraProductionIncidentIdentificationKPI217";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.updateOne(
						new Document(FIELD_NAME, FIELD_KPI217_RADIO),
						new Document(
								"$set",
								new Document(
										"nestedFields",
										Arrays.asList(
												new Document(FIELD_NAME, "jiraProdIncidentRaisedByCustomField")
														.append("fieldLabel", "Production Incident Custom Field")
														.append("fieldType", "text")
														.append("fieldCategory", "fields")
														.append("filterGroup", Arrays.asList("CustomField"))
														.append(
																"tooltip",
																new Document(
																		"definition",
																		"Provide customfield name to identify Production Incident. <br> Example: customfield_13907<hr>")),
												new Document(FIELD_NAME, "jiraProdIncidentRaisedByValue")
														.append("fieldLabel", "Production Incident Values")
														.append("fieldType", "chips")
														.append("filterGroup", Arrays.asList("CustomField", "Labels"))
														.append(
																"tooltip",
																new Document(
																		"definition",
																		"Provide label name to identify Production Incident Example: PROD_INCIDENT <hr>"))))));
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.updateOne(
						new Document(FIELD_NAME, FIELD_KPI217_RADIO),
						new Document("$unset", new Document("nestedFields", "")));
	}
}
