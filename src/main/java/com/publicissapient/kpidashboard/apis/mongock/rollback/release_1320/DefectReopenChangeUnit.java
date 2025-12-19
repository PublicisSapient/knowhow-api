/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1320;

import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Change unit to insert a new field mapping & kpi master for new defect reopen rate KPI
 *
 * @author shunaray
 */
@ChangeUnit(
		id = "r_defect_reopen_quality",
		order = "013205",
		author = "shunaray",
		systemVersion = "13.2.0")
public class DefectReopenChangeUnit {

	public static final String FIELD_NAME = "fieldName";
	public static final String FIELD_LABEL = "fieldLabel";
	public static final String FIELD_TYPE = "fieldType";
	public static final String SECTION = "section";
	public static final String TOOLTIP = "tooltip";
	public static final String FIELD_DISPLAY_ORDER = "fieldDisplayOrder";
	public static final String SECTION_ORDER = "sectionOrder";
	public static final String MANDATORY = "mandatory";
	public static final String WORK_FLOW_STATUS_MAPPING = "WorkFlow Status Mapping";
	public static final String FIELD_CATEGORY = "fieldCategory";
	public static final String KPI_ID = "kpiId";
	public static final String KPI_190 = "kpi190";
	private final MongoTemplate mongoTemplate;

	public DefectReopenChangeUnit(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		mongoTemplate.getCollection("kpi_master").deleteOne(new Document(KPI_ID, KPI_190));
		mongoTemplate
				.getCollection("field_mapping_structure")
				.deleteMany(
						new Document(
								FIELD_NAME,
								new Document(
										"$in",
										List.of(
												"resolutionTypeForRejectionKPI190",
												"jiraDefectRejectionStatusKPI190",
												"defectReopenStatusKPI190",
												"thresholdValueKPI190"))));
		mongoTemplate.getCollection("kpi_category_mapping").deleteOne(new Document(KPI_ID, KPI_190));
		mongoTemplate.getCollection("kpi_column_configs").deleteOne(new Document(KPI_ID, KPI_190));
	}

	private void insertKpiCategoryMapping() {
		Document kpiCategoryMapping =
				new Document()
						.append(KPI_ID, KPI_190)
						.append("categoryId", "quality")
						.append("kpiOrder", 6.0)
						.append("kanban", false);

		mongoTemplate.getCollection("kpi_category_mapping").insertOne(kpiCategoryMapping);
	}

	private void insertFieldMapping() {
		List<Document> fieldMappings =
				List.of(
						createFieldMapping(
								Map.of(
										FIELD_NAME,
										"resolutionTypeForRejectionKPI190",
										FIELD_LABEL,
										"Resolution type to be excluded",
										FIELD_TYPE,
										"chips",
										SECTION,
										WORK_FLOW_STATUS_MAPPING,
										TOOLTIP,
										"Resolution types for defects that can be excluded from Defect reopen rate calculation",
										FIELD_DISPLAY_ORDER,
										1,
										SECTION_ORDER,
										1,
										MANDATORY,
										false)),
						createFieldMapping(
								Map.of(
										FIELD_NAME,
										"jiraDefectRejectionStatusKPI190",
										FIELD_LABEL,
										"Status to identify rejected defects",
										FIELD_TYPE,
										"text",
										FIELD_CATEGORY,
										"workflow",
										SECTION,
										WORK_FLOW_STATUS_MAPPING,
										TOOLTIP,
										"All workflow statuses used to reject defects.",
										FIELD_DISPLAY_ORDER,
										2,
										SECTION_ORDER,
										1,
										MANDATORY,
										false)),
						createFieldMapping(
								Map.of(
										FIELD_NAME,
										"defectReopenStatusKPI190",
										FIELD_LABEL,
										"Defect Reopen Status",
										FIELD_TYPE,
										"text",
										FIELD_CATEGORY,
										"workflow",
										SECTION,
										WORK_FLOW_STATUS_MAPPING,
										FIELD_DISPLAY_ORDER,
										1,
										SECTION_ORDER,
										4,
										TOOLTIP,
										"Enter the workflow status to which a defect transitions when it is reopened.",
										MANDATORY,
										true)),
						createFieldMapping(
								Map.of(
										FIELD_NAME,
										"thresholdValueKPI190",
										FIELD_LABEL,
										"Target KPI Value",
										FIELD_TYPE,
										"number",
										SECTION,
										"Project Level Threshold",
										TOOLTIP,
										"Target KPI value denotes the bare minimum a project should maintain for a KPI. User should just input the number and the unit like percentage, hours will automatically be considered. If the threshold is empty, then a common target KPI line will be shown",
										FIELD_DISPLAY_ORDER,
										1,
										SECTION_ORDER,
										2,
										MANDATORY,
										false)));

		mongoTemplate.getCollection("field_mapping_structure").insertMany(fieldMappings);
	}

	private static Document insertKPIMaster() {
		return new Document()
				.append(KPI_ID, KPI_190)
				.append("kpiName", "Defect Reopen Rate")
				.append("isDeleted", "False")
				.append("defaultOrder", 6)
				.append("kpiUnit", "%")
				.append("chartType", "line")
				.append("upperThresholdBG", "red")
				.append("lowerThresholdBG", "white")
				.append("xAxisLabel", "Sprints")
				.append("yAxisLabel", "Percentage")
				.append("showTrend", true)
				.append("isPositiveTrend", false)
				.append("calculateMaturity", false)
				.append("hideOverallFilter", false)
				.append("kpiSource", "Jira")
				.append("maxValue", "90")
				.append("thresholdValue", "55")
				.append("kanban", false)
				.append("groupId", 24)
				.append(
						"kpiInfo",
						new Document()
								.append(
										"definition",
										"It shows number of defects reopened in a given span of time in comparison to the total closed defects. For all the reopened defects, the average time to reopen is also available.")
								.append(
										"formula",
										List.of(
												new Document()
														.append("lhs", "Defect Reopen Rate (%)")
														.append("operator", "division")
														.append(
																"operands", List.of("Reopened Defects", "Total Resolved Defects"))))
								.append(
										"details",
										List.of(
												new Document()
														.append("type", "link")
														.append(
																"kpiLinkDetail",
																new Document()
																		.append("text", "Detailed Information at")
																		.append(
																				"link",
																				"https://knowhow.tools.publicis.sapient.com/wiki/kpi190-Defect+Reopen+Rate")))))
				.append("kpiFilter", "dropDown")
				.append("aggregationCriteria", "average")
				.append("isTrendCalculative", false)
				.append("isAdditionalFilterSupport", true)
				.append("combinedKpiSource", "Jira/Azure");
	}

	private Document createFieldMapping(Map<String, Object> fieldData) {
		Document document = new Document();
		document
				.append(FIELD_NAME, fieldData.get(FIELD_NAME))
				.append(FIELD_LABEL, fieldData.get(FIELD_LABEL))
				.append(FIELD_TYPE, fieldData.get(FIELD_TYPE))
				.append(SECTION, fieldData.get(SECTION))
				.append("processorCommon", false)
				.append(TOOLTIP, new Document("definition", fieldData.get(TOOLTIP)))
				.append(FIELD_DISPLAY_ORDER, fieldData.get(FIELD_DISPLAY_ORDER))
				.append(SECTION_ORDER, fieldData.get(SECTION_ORDER))
				.append(MANDATORY, fieldData.get(MANDATORY))
				.append("nodeSpecific", false);

		if (fieldData.containsKey(FIELD_CATEGORY)) {
			document.append(FIELD_CATEGORY, fieldData.get(FIELD_CATEGORY));
		}

		return document;
	}

	private void insertKPIColumnConfig() {
		Document kpiColumnConfig =
				new Document()
						.append("basicProjectConfigId", null)
						.append(KPI_ID, KPI_190)
						.append(
								"kpiColumnDetails",
								List.of(
										createColumnDetail("Sprint Name", 1),
										createColumnDetail("Defect ID", 2),
										createColumnDetail("Defect Description", 3),
										createColumnDetail("Defect Priority", 4),
										createColumnDetail("Closed Date", 5),
										createColumnDetail("Reopen Date", 6),
										createColumnDetail("Time taken to reopen", 7)));

		mongoTemplate.getCollection("kpi_column_configs").insertOne(kpiColumnConfig);
	}

	private Document createColumnDetail(String columnName, int order) {
		return new Document()
				.append("columnName", columnName)
				.append("order", order)
				.append("isShown", true)
				.append("isDefault", true);
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate.getCollection("kpi_master").insertOne(insertKPIMaster());
		insertFieldMapping();
		insertKpiCategoryMapping();
		insertKPIColumnConfig();
	}
}
