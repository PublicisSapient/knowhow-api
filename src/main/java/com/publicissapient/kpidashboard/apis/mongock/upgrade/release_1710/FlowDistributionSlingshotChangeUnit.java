package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "flow_distribution_slingshot_change_unit",
		order = "17113",
		author = "aksshriv1",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class FlowDistributionSlingshotChangeUnit {

	private static final String KPI_ID = "kpiId";
	private static final String KPI_207 = "kpi207";
	private static final String KPI_MASTER = "kpi_master";
	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";

	private static final String FIELD_NAME = "fieldName";
	private static final String FIELD_LABEL = "fieldLabel";
	private static final String FIELD_TYPE = "fieldType";
	private static final String FIELD_CATEGORY = "fieldCategory";
	private static final String SECTION = "section";
	private static final String PROCESSOR_COMMON = "processorCommon";
	private static final String TOOLTIP = "tooltip";
	private static final String DEFINITION = "definition";
	private static final String MANDATORY = "mandatory";
	private static final String CLASS = "_class";
	private static final String CHIPS = "chips";
	private static final String KEY_BASIC_PROJECT_CONFIG_ID = "basicProjectConfigId";
	private static final String KEY_KPI_COLUMN_DETAILS = "kpiColumnDetails";
	private static final String KPI_EXCEL_COLUMN_CONFIG = "kpi_column_configs";
	private static final String KEY_COLUMN_NAME = "columnName";
	private static final String KEY_ORDER = "order";
	private static final String KEY_IS_SHOWN = "isShown";
	private static final String KEY_IS_DEFAULT = "isDefault";

	private static final String TOOLTIP_CLASS =
			"com.publicissapient.kpidashboard.apis.mongock.FieldMappingStructureForMongock$MappingToolTip";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		insertFlowDistributionKpi();
		insertFieldMappingStructure();
		insertExcelColumnConfig();
	}

	private void insertExcelColumnConfig() {
		Document kpiColumnConfig =
				new Document(KEY_BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, KPI_207)
						.append(
								KEY_KPI_COLUMN_DETAILS,
								new Document[] {
									new Document(KEY_COLUMN_NAME, "Date")
											.append(KEY_ORDER, 1)
											.append(KEY_IS_SHOWN, true)
											.append(KEY_IS_DEFAULT, true)
								});

		mongoTemplate.insert(kpiColumnConfig, KPI_EXCEL_COLUMN_CONFIG);
	}

	private void insertFlowDistributionKpi() {

		Document kpiDocument =
				new Document()
						.append(KPI_ID, KPI_207)
						.append("kpiName", "Flow Distribution")
						.append("kpiUnit", "")
						.append("isDeleted", "False")
						.append("defaultOrder", 5)
						.append("kpiCategory", "Slingshot")
						.append("kpiSource", "Jira")
						.append("combinedKpiSource", "Jira/Azure")
						.append("groupId", 46)
						.append("thresholdValue", "")
						.append("kanban", false)
						.append("chartType", "stacked-area")
						.append(
								"kpiInfo",
								new Document()
										.append(
												DEFINITION,
												"Flow Distribution evaluates the amount of each kind of work (issue types) which are open in the backlog over a period of time.")
										.append(
												"details",
												List.of(
														new Document("type", "link")
																.append(
																		"kpiLinkDetail",
																		new Document("text", "Detailed Information at")
																				.append(
																						"link",
																						"https://knowhow.tools.publicis.sapient.com/wiki/kpi146-Flow+Distribution"))))
										.append(
												CLASS, "com.publicissapient.kpidashboard.common.model.application.KpiInfo"))
						.append("xaxisLabel", "Time")
						.append("yaxisLabel", "Count")
						.append("isPositiveTrend", false)
						.append("kpiFilter", "")
						.append("showTrend", false)
						.append("aggregationCriteria", "sum")
						.append("isAdditionalFilterSupport", false)
						.append("calculateMaturity", false);

		mongoTemplate.getCollection(KPI_MASTER).insertOne(kpiDocument);
	}

	private void insertFieldMappingStructure() {

		Document fieldMappingDocument =
				new Document(FIELD_NAME, "jiraIssueTypeNamesKPI207")
						.append(FIELD_LABEL, "Issue types to be included")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, "Issue_Type")
						.append(PROCESSOR_COMMON, false)
						.append(SECTION, "Issue Types Mapping")
						.append(
								TOOLTIP,
								new Document(DEFINITION, "All issue types used by your Jira project")
										.append(CLASS, TOOLTIP_CLASS))
						.append(MANDATORY, true);
		Document closeStatusDocument =
				new Document(FIELD_NAME, "jiraIssueClosedStateKPI207")
						.append(FIELD_LABEL, "Status to identify Close Statuses")
						.append(FIELD_TYPE, CHIPS)
						.append(SECTION, "WorkFlow Status Mapping")
						.append(FIELD_CATEGORY, "workflow")
						.append(MANDATORY, true)
						.append(
								TOOLTIP,
								new Document(
										DEFINITION,
										"All statuses that signify an issue is 'DONE' based on 'Definition Of Done'"));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.insertMany(Arrays.asList(fieldMappingDocument, closeStatusDocument));
	}

	@RollbackExecution
	public void rollback() {

		mongoTemplate.getCollection(KPI_MASTER).deleteOne(new Document(KPI_ID, KPI_207));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.deleteOne(new Document(FIELD_NAME, "jiraIssueTypeNamesKPI207"));
	}
}
