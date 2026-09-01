package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Seeds the Backlog Aging KPI (kpi224) — Slingshot / Intake.
 *
 * <p>Registers the kpi_master document, the excel column configuration and the four field mapping
 * structure entries that drive the project level configuration screen.
 */
@ChangeUnit(
		id = "backlog_aging_slingshot_kpi_insert",
		order = "17203",
		author = "knowhow",
		systemVersion = "17.1.0")
public class BacklogAgingSlingshotChangeUnit {

	private static final String KPI_ID = "kpi224";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";
	private static final String FIELD_NAME = "fieldName";
	private static final String FIELD_LABEL = "fieldLabel";
	private static final String FIELD_TYPE = "fieldType";
	private static final String FIELD_CATEGORY = "fieldCategory";
	private static final String FIELD_DISPLAY_ORDER = "fieldDisplayOrder";
	private static final String SECTION = "section";
	private static final String SECTION_ORDER = "sectionOrder";
	private static final String TOOLTIP = "tooltip";
	private static final String DEFINITION = "definition";
	private static final String MANDATORY = "mandatory";
	private static final String CHIPS = "chips";
	private static final String WORKFLOW = "workflow";
	private static final String WORKFLOW_STATUS_MAPPING = "WorkFlow Status Mapping";

	private static final String ISSUE_TYPE_FIELD = "jiraIssueTypeKPI224";
	private static final String BACKLOG_STATUS_FIELD = "jiraBacklogStatusKPI224";
	private static final String REFINED_STATUS_FIELD = "jiraStatusForRefinedKPI224";
	private static final String THRESHOLD_FIELD = "thresholdValueKPI224";

	private static final String KPI_DEFINITION =
			"Distribution of how long items have been sitting in the backlog without being refined or closed. "
					+ "For every issue whose current status is one of the configured backlog statuses (e.g. Backlog, To Do) "
					+ "the age is computed as now() - created date, and the issue is plotted in one of four buckets: "
					+ "0-30, 30-90, 90-180 and 180+ days. Each bucket carries a drill-down by issue type. "
					+ "Stale backlogs are mostly noise — but the noise hides real demand. "
					+ "A healthy backlog has a working set that turns over and a clear policy for retiring items older than ~6 months, "
					+ "so a heavy 180+ bucket is the signal to act on.";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		insertKpiMaster(mongoTemplate);
		insertKpiColumnConfig(mongoTemplate);
		insertFieldMappingStructure(mongoTemplate);
	}

	public void insertKpiMaster(MongoTemplate mongoTemplate) {
		Document kpiMaster =
				new Document()
						.append(KPI_ID_FIELD, KPI_ID)
						.append("kpiName", "Backlog Aging")
						.append("isDeleted", "False")
						.append("defaultOrder", 2)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Intake")
						.append("kpiUnit", "Count")
						.append("chartType", "stacked-bar-chart")
						.append("xAxisLabel", "Age (Days)")
						.append("yAxisLabel", "Issue Count")
						.append("showTrend", false)
						.append("isPositiveTrend", false)
						.append("calculateMaturity", false)
						.append("hideOverallFilter", false)
						.append("kpiSource", "Jira")
						.append("thresholdValue", 10.0)
						.append("kanban", false)
						.append("groupId", 315)
						.append("kpiInfo", new Document().append(DEFINITION, KPI_DEFINITION))
						// no KPI filter: the breakdown is exposed as a per-bucket issue type drill-down
						.append("kpiFilter", null)
						.append("aggregationCriteria", "sum")
						.append("isAdditionalFilterSupport", false)
						.append("combinedKpiSource", "Jira/Azure/Rally")
						.append("upperThresholdBG", "red")
						.append("lowerThresholdBG", "white")
						.append("kpiWidth", 100)
						.append("kpiSubCategoryOrder", 1);

		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID), kpiMaster, new ReplaceOptions().upsert(true));
	}

	public void insertKpiColumnConfig(MongoTemplate mongoTemplate) {
		Document columnConfig =
				new Document()
						.append("basicProjectConfigId", null)
						.append(KPI_ID_FIELD, KPI_ID)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										column("Aging Bucket", 1),
										column("Issue ID", 2),
										column("Issue Type", 3),
										column("Issue Description", 4),
										column("Status", 5),
										column("Priority", 6),
										column("Created Date", 7),
										column("Age (Days)", 8)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI_ID), columnConfig, new ReplaceOptions().upsert(true));
	}

	private Document column(String name, int order) {
		return new Document()
				.append(COLUMN_NAME, name)
				.append(ORDER, order)
				.append(IS_SHOWN, true)
				.append(IS_DEFAULT, true);
	}

	public void insertFieldMappingStructure(MongoTemplate mongoTemplate) {
		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, ISSUE_TYPE_FIELD)
						.append(FIELD_LABEL, "Issue type to include in Backlog Aging")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, "Issue_Type")
						.append(SECTION, "Issue Types Mapping")
						.append(
								TOOLTIP,
								new Document()
										.append(
												DEFINITION,
												"All issue types whose backlog age should be measured (e.g., Story, Bug, Task). Leave blank to include all types.")));

		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, BACKLOG_STATUS_FIELD)
						.append(FIELD_LABEL, "Status(es) that represent the Backlog")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, WORKFLOW)
						.append(FIELD_DISPLAY_ORDER, 10)
						.append(SECTION_ORDER, 4)
						.append(SECTION, WORKFLOW_STATUS_MAPPING)
						.append(MANDATORY, false)
						.append(
								TOOLTIP,
								new Document()
										.append(
												DEFINITION,
												"Workflow statuses that mean an item is still sitting in the backlog and has not been picked up (e.g., Backlog, To Do, Open). Only issues currently in one of these statuses are aged. When left blank, 'Backlog', 'To Do', 'Open' and 'New' are used.")));

		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, REFINED_STATUS_FIELD)
						.append(FIELD_LABEL, "Status(es) that mean the item is already refined (optional)")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, WORKFLOW)
						.append(FIELD_DISPLAY_ORDER, 11)
						.append(SECTION_ORDER, 4)
						.append(SECTION, WORKFLOW_STATUS_MAPPING)
						.append(MANDATORY, false)
						.append(
								TOOLTIP,
								new Document()
										.append(
												DEFINITION,
												"Optional. Workflow statuses that indicate an item has already been refined / met the Definition of Ready (e.g., Ready, Refined). Issues in these statuses are excluded from the aging distribution.")));

		upsertFieldMapping(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, THRESHOLD_FIELD)
						.append(FIELD_LABEL, "Target KPI Value")
						.append(FIELD_TYPE, "number")
						.append(SECTION, "Project Level Threshold")
						.append("processorCommon", false)
						.append(
								TOOLTIP,
								new Document()
										.append(
												DEFINITION,
												"Target KPI value denotes the bare minimum a project should maintain for a KPI. User should just input the number and the unit like percentage, hours will automatically be considered. If the threshold is empty, then a common target KPI line will be shown"))
						.append(FIELD_DISPLAY_ORDER, 1)
						.append(SECTION_ORDER, 6)
						.append(MANDATORY, false)
						.append("nodeSpecific", false));
	}

	private void upsertFieldMapping(MongoTemplate mongoTemplate, Document fieldMapping) {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, fieldMapping.getString(FIELD_NAME)),
						fieldMapping,
						new ReplaceOptions().upsert(true));
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.deleteOne(new Document(KPI_ID_FIELD, KPI_ID));
		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.deleteOne(new Document(KPI_ID_FIELD, KPI_ID));
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteMany(
						new Document(
								FIELD_NAME,
								new Document(
										"$in",
										Arrays.asList(
												ISSUE_TYPE_FIELD,
												BACKLOG_STATUS_FIELD,
												REFINED_STATUS_FIELD,
												THRESHOLD_FIELD))));
	}
}
