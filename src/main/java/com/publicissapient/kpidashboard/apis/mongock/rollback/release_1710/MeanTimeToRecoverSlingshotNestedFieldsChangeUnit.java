package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@ChangeUnit(
		id = "r_mean_time_to_recover_slingshot_nested_fields",
		order = "17163",
		author = "knowhow",
		systemVersion = "17.1.0")
public class MeanTimeToRecoverSlingshotNestedFieldsChangeUnit {

	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";
	private static final String FIELD_NAME = "fieldName";
	private static final String FIELD_KPI217_RADIO = "jiraProductionIncidentIdentificationKPI217";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		log.info("Rolling back nestedFields from jiraProductionIncidentIdentificationKPI217");
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.updateOne(
						new Document(FIELD_NAME, FIELD_KPI217_RADIO),
						new Document("$unset", new Document("nestedFields", "")));
		log.info("Completed rollback of nestedFields from jiraProductionIncidentIdentificationKPI217");
	}

	@RollbackExecution
	public void rollback() {
		log.info("Re-applying nestedFields to jiraProductionIncidentIdentificationKPI217");
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.updateOne(
						new Document(FIELD_NAME, FIELD_KPI217_RADIO),
						new Document(
								"$set",
								new Document(
										"nestedFields",
										java.util.Arrays.asList(
												new Document(FIELD_NAME, "jiraProdIncidentRaisedByCustomField")
														.append("fieldLabel", "Production Incident Custom Field")
														.append("fieldType", "text")
														.append("fieldCategory", "fields")
														.append("filterGroup", java.util.Arrays.asList("CustomField"))
														.append(
																"tooltip",
																new Document(
																		"definition",
																		"Provide customfield name to identify Production Incident. <br> Example: customfield_13907<hr>")),
												new Document(FIELD_NAME, "jiraProdIncidentRaisedByValue")
														.append("fieldLabel", "Production Incident Values")
														.append("fieldType", "chips")
														.append("filterGroup", java.util.Arrays.asList("CustomField", "Labels"))
														.append(
																"tooltip",
																new Document(
																		"definition",
																		"Provide label name to identify Production Incident Example: PROD_INCIDENT <hr>"))))));
		log.info("Completed re-applying nestedFields to jiraProductionIncidentIdentificationKPI217");
	}
}
