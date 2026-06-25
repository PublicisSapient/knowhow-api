package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "flow_velocity_weekly_start_date_field_mapping",
		order = "17129",
		author = "kunkambl",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class FlowVelocityWeeklyStartDateFieldMappingChangeUnit {

	private static final String FIELD_NAME = "fieldName";
	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";
	private static final String TOOLTIP_CLASS =
			"com.publicissapient.kpidashboard.apis.mongock.FieldMappingStructureForMongock$MappingToolTip";
	private static final String WEEKLY_DATA_START_DATE_KPI205 = "weeklyDataStartDateKPI205";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		Document fieldMappingDoc =
				new Document(FIELD_NAME, WEEKLY_DATA_START_DATE_KPI205)
						.append("fieldLabel", "Weekly Cycle Reference Start Date")
						.append("fieldType", "text")
						.append("fieldCategory", null)
						.append("toggleLabel", null)
						.append("section", "Custom Fields Mapping")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document(
												"definition",
												"Reference date used to align weekly data point boundaries for Flow Velocity. Enter a date in yyyy-MM-dd format (e.g. 2026-06-17). The system will compute 7-day cycle boundaries anchored to this date, always starting the first data point from the next boundary after the current date. If left blank or invalid, the default Monday-based week boundary is used.")
										.append("_class", TOOLTIP_CLASS))
						.append("options", null)
						.append("filterGroup", null)
						.append("nestedFields", null)
						.append("placeHolderText", "yyyy-MM-dd")
						.append("fieldDisplayOrder", 2)
						.append("toggleLabelLeft", null)
						.append("toggleLabelRight", null)
						.append("sectionOrder", 7)
						.append("mandatory", false)
						.append("readOnly", null)
						.append("nodeSpecific", false);

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.replaceOne(
						new Document(FIELD_NAME, WEEKLY_DATA_START_DATE_KPI205),
						fieldMappingDoc,
						new ReplaceOptions().upsert(true));
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.deleteOne(new Document(FIELD_NAME, WEEKLY_DATA_START_DATE_KPI205));
	}
}
