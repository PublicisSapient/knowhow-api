package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "defect_escape_rate_slingshot_field_mapping_kpi216",
		order = "17167",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class DefectEscapeRateSlingshotFieldMappingChangeUnit {

	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";
	private static final String FIELD_NAME = "fieldName";
	private static final String FIELD_KPI216_RADIO = "jiraBugRaisedByIdentificationKPI216";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.replaceOne(
						new Document(FIELD_NAME, FIELD_KPI216_RADIO),
						new Document(FIELD_NAME, FIELD_KPI216_RADIO)
								.append("fieldLabel", "Escaped defects identification")
								.append("fieldType", "radiobutton")
								.append("section", "Defects Mapping")
								.append("fieldDisplayOrder", 3)
								.append("sectionOrder", 3)
								.append(
										"tooltip",
										new Document(
												"definition",
												"Any custom field or label used to identify escaped defects for Defect Escape Rate (KPI216). Only one field value is allowed."))
								.append(
										"options",
										Arrays.asList(
												new Document("label", "CustomField").append("value", "CustomField"),
												new Document("label", "Labels").append("value", "Labels")))
								.append(
										"nestedFields",
										Arrays.asList(
												new Document(FIELD_NAME, "jiraBugRaisedByCustomFieldKPI216")
														.append("fieldLabel", "Custom field to identify escaped defects")
														.append("placeHolderText", "Custom field to identify escaped defects")
														.append("fieldType", "text")
														.append("fieldCategory", "fields")
														.append("filterGroup", Arrays.asList("CustomField"))
														.append(
																"tooltip",
																new Document(
																		"definition",
																		"Provide customfield name to identify UAT or client raised defects for Defect Escape Rate. <br> Example: customfield_13907<hr>")),
												new Document(FIELD_NAME, "jiraBugRaisedByValueKPI216")
														.append("fieldLabel", "Field values that denote escaped defects")
														.append("placeHolderText", "Field values that denote escaped defects")
														.append("fieldType", "chips")
														.append("filterGroup", Arrays.asList("CustomField", "Labels"))
														.append(
																"tooltip",
																new Document(
																		"definition",
																		"Provide label name to identify UAT or client raised defects for Defect Escape Rate.<br /> Example: Clone_by_QA <hr>")))),
						new ReplaceOptions().upsert(true));
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.deleteOne(new Document(FIELD_NAME, FIELD_KPI216_RADIO));
	}
}
