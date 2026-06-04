package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1710;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "sprint_velocity_slingshot_change_unit",
		order = "17110",
		author = "kunkambl",
		systemVersion = "17.1.0")
public class SprintVelocitySlingshotChangeUnit {

	private static final String KPI_ID = "kpiId";
	private static final String KPI_205 = "kpi205";
	private static final String KPI_MASTER = "kpi_master";
	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";
	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";
	private static final String TOOLTIP_CLASS =
			"com.publicissapient.kpidashboard.apis.mongock.FieldMappingStructureForMongock$MappingToolTip";
	private static final String FIELD_NAME = "fieldName";
	private static final String FIELD_LABEL = "fieldLabel";
	private static final String FIELD_TYPE = "fieldType";
	private static final String FIELD_CATEGORY = "fieldCategory";
	private static final String TOGGLE_LABEL = "toggleLabel";
	private static final String SECTION = "section";
	private static final String PROCESSOR_COMMON = "processorCommon";
	private static final String TOOLTIP = "tooltip";
	private static final String DEFINITION = "definition";
	private static final String OPTIONS = "options";
	private static final String FILTER_GROUP = "filterGroup";
	private static final String NESTED_FIELDS = "nestedFields";
	private static final String PLACE_HOLDER_TEXT = "placeHolderText";
	private static final String FIELD_DISPLAY_ORDER = "fieldDisplayOrder";
	private static final String TOGGLE_LABEL_LEFT = "toggleLabelLeft";
	private static final String TOGGLE_LABEL_RIGHT = "toggleLabelRight";
	private static final String SECTION_ORDER = "sectionOrder";
	private static final String MANDATORY = "mandatory";
	private static final String READ_ONLY = "readOnly";
	private static final String NODE_SPECIFIC = "nodeSpecific";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";
	private static final String CLASS = "_class";
	private static final String CHIPS = "chips";
	private static final String WORKFLOW_STATUS_MAPPING = "WorkFlow Status Mapping";
	private final MongoTemplate mongoTemplate;

	public SprintVelocitySlingshotChangeUnit(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		mongoTemplate.getCollection(KPI_MASTER).deleteOne(new Document(KPI_ID, KPI_205));
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.deleteMany(
						new Document(
								FIELD_NAME,
								new Document(
										"$in",
										List.of(
												"thresholdValueKPI205",
												"jiraIterationCompletionStatusKpi205",
												"jiraIterationIssuetypeKPI205"))));
		mongoTemplate.getCollection(KPI_COLUMN_CONFIGS).deleteOne(new Document(KPI_ID, KPI_205));
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate.getCollection(KPI_MASTER).insertOne(buildKpiDoc());
		mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE).insertMany(buildFieldMappingDocs());
		mongoTemplate.getCollection(KPI_COLUMN_CONFIGS).insertOne(buildKpiColumnConfigDoc());
	}

	private Document buildKpiDoc() {
		return new Document()
				.append(KPI_ID, KPI_205)
				.append("kpiName", "Sprint Velocity")
				.append("isDeleted", "False")
				.append("defaultOrder", 20)
				.append("kpiCategory", null)
				.append("kpiSubCategory", "Slingshot")
				.append("kpiInAggregatedFeed", null)
				.append("kpiOnDashboard", null)
				.append("kpiBaseLine", null)
				.append("kpiUnit", "SP")
				.append("chartType", "grouped_column_plus_line")
				.append("upperThresholdBG", "white")
				.append("lowerThresholdBG", "red")
				.append("xAxisLabel", "Sprints")
				.append("yAxisLabel", "Count")
				.append("showTrend", false)
				.append("isPositiveTrend", true)
				.append("lineLegend", "Sprint Velocity")
				.append("barLegend", "Last 5 Sprints Average")
				.append("boxType", null)
				.append("calculateMaturity", true)
				.append("hideOverallFilter", false)
				.append("videoLink", null)
				.append("isTrendUpOnValIncrease", null)
				.append("kpiSource", "Jira")
				.append("maxValue", "300")
				.append("thresholdValue", "40")
				.append("kanban", false)
				.append("groupId", 2)
				.append(
						"kpiInfo",
						new Document()
								.append(
										DEFINITION,
										"Measures the rate of delivery across Sprints. Average velocity is calculated for the latest 5 sprints")
								.append(
										"details",
										List.of(
												new Document("type", "link")
														.append(
																"kpiLinkDetail",
																new Document("text", "Detailed Information at")
																		.append(
																				"link",
																				"https://knowhow.tools.publicis.sapient.com/wiki/kpi39-Sprint+Velocity"))))
								.append(CLASS, "com.publicissapient.kpidashboard.common.model.application.KpiInfo"))
				.append("kpiFilter", null)
				.append("aggregationCriteria", "sum")
				.append("aggregationCircleCriteria", null)
				.append("isTrendCalculative", false)
				.append(
						"trendCalculation",
						List.of(trendDoc("Upwards", "<"), trendDoc("Upwards", "="), trendDoc("Downwards", ">")))
				.append("isAdditionalFilterSupport", true)
				.append("maturityRange", List.of("1-2", "2-3", "3-4", "4-5", "5-6"))
				.append("kpiWidth", null)
				.append("maturityLevel", null)
				.append("isRepoToolKpi", null)
				.append("yaxisOrder", null)
				.append("combinedKpiSource", "Jira/Azure/Rally")
				.append("forecastModel", "exponentialSmoothing");
	}

	private Document trendDoc(String type, String operator) {
		return new Document("type", type)
				.append("lhs", "value")
				.append("rhs", "lineValue")
				.append("operator", operator)
				.append(CLASS, "com.publicissapient.kpidashboard.common.model.application.KpiFormula");
	}

	private List<Document> buildFieldMappingDocs() {
		return List.of(
				buildFieldMappingDoc(
						new FieldMappingParams(
								"thresholdValueKPI205",
								"Target KPI Value",
								"number",
								null,
								"Project Level Threshold"),
						1,
						6,
						"Target KPI value denotes the bare minimum a project should maintain for a KPI. User should just input the number and the unit like percentage, hours will automatically be considered. If the threshold is empty, then a common target KPI line will be shown"),
				buildFieldMappingDoc(
						new FieldMappingParams(
								"jiraIterationCompletionStatusKpi205",
								"Status to identify completed issues",
								CHIPS,
								"workflow",
								WORKFLOW_STATUS_MAPPING),
						8,
						4,
						"All workflow statuses used to identify completed issues. If multiple statuses are specified, the first status an issue transitions to will be considered."),
				buildFieldMappingDoc(
						new FieldMappingParams(
								"jiraIterationIssuetypeKPI205",
								"Issue types filter for completed issues",
								CHIPS,
								"Issue_Type",
								"Issue Types Mapping"),
						1,
						2,
						"Completed issues of the specified issue types will be considered. If left blank, all issue types will be included by default."));
	}

	private record FieldMappingParams(
			String fieldName,
			String fieldLabel,
			String fieldType,
			String fieldCategory,
			String section) {}

	private Document buildFieldMappingDoc(
			FieldMappingParams p, int fieldDisplayOrder, int sectionOrder, String tooltipDefinition) {
		return new Document(FIELD_NAME, p.fieldName())
				.append(FIELD_LABEL, p.fieldLabel())
				.append(FIELD_TYPE, p.fieldType())
				.append(FIELD_CATEGORY, p.fieldCategory())
				.append(TOGGLE_LABEL, null)
				.append(SECTION, p.section())
				.append(PROCESSOR_COMMON, false)
				.append(TOOLTIP, new Document(DEFINITION, tooltipDefinition).append(CLASS, TOOLTIP_CLASS))
				.append(OPTIONS, null)
				.append(FILTER_GROUP, null)
				.append(NESTED_FIELDS, null)
				.append(PLACE_HOLDER_TEXT, null)
				.append(FIELD_DISPLAY_ORDER, fieldDisplayOrder)
				.append(TOGGLE_LABEL_LEFT, null)
				.append(TOGGLE_LABEL_RIGHT, null)
				.append(SECTION_ORDER, sectionOrder)
				.append(MANDATORY, false)
				.append(READ_ONLY, null)
				.append(NODE_SPECIFIC, false);
	}

	private Document buildKpiColumnConfigDoc() {
		return new Document()
				.append("basicProjectConfigId", null)
				.append(KPI_ID, KPI_205)
				.append(
						"kpiColumnDetails",
						List.of(
								columnDoc("Sprint Name", 1),
								columnDoc("Issue ID", 2),
								columnDoc("Issue Description", 3),
								columnDoc("Squad", 4),
								columnDoc("Issue Type", 5),
								columnDoc("Priority", 6),
								columnDoc("Story Points", 7),
								columnDoc("Original Time Estimate (in hours)", 8),
								columnDoc("Time Spent (in hours)", 9)));
	}

	private Document columnDoc(String name, int order) {
		return new Document(COLUMN_NAME, name)
				.append(ORDER, order)
				.append(IS_SHOWN, true)
				.append(IS_DEFAULT, true);
	}
}
