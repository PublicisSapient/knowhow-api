package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Seeds Backlog Aging (kpi224) and Mid-Sprint Re-Refinement Rate (kpi225) — both Slingshot / Intake
 * KPIs.
 */
@ChangeUnit(
		id = "slingshot_intake_kpi224_kpi225_insert",
		order = "17203",
		author = "knowhow",
		systemVersion = "17.1.0")
public class BacklogAgingSlingshotChangeUnit {

	// ── Shared ──────────────────────────────────────────────────────────────
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

	// ── kpi224 field names ───────────────────────────────────────────────────
	private static final String KPI224 = "kpi224";
	private static final String ISSUE_TYPE_FIELD_224 = "jiraIssueTypeKPI224";
	private static final String BACKLOG_STATUS_FIELD_224 = "jiraBacklogStatusKPI224";
	private static final String BACKLOG_START_STATUS_FIELD_224 = "jiraStatusToStartBacklogKPI224";
	private static final String REFINED_STATUS_FIELD_224 = "jiraStatusForRefinedKPI224";
	private static final String THRESHOLD_FIELD_224 = "thresholdValueKPI224";

	// ── kpi225 field names ───────────────────────────────────────────────────
	private static final String KPI225 = "kpi225";
	private static final String STORY_TYPE_FIELD_225 = "jiraStoryIdentificationKPI225";
	private static final String DEV_START_FIELD_225 = "jiraStatusStartDevKPI225";
	private static final String RETURN_FIELD_225 = "jiraStatusReturnToRefinementKPI225";
	private static final String THRESHOLD_FIELD_225 = "thresholdValueKPI225";

	private static final String KPI224_DEFINITION =
			"Distribution of how long items have been sitting in the backlog without being refined or closed. "
					+ "For every issue whose current status is one of the configured backlog statuses (e.g. Backlog, To Do) "
					+ "the age is computed as now() - created date, and the issue is plotted in one of four buckets: "
					+ "0-4 Weeks, 4-13 Weeks, 13-26 Weeks and 26+ Weeks. Each bucket carries a drill-down by issue type. "
					+ "Stale backlogs are mostly noise — but the noise hides real demand. "
					+ "A healthy backlog has a working set that turns over and a clear policy for retiring items older than ~6 months, "
					+ "so a heavy 26+ Weeks bucket is the signal to act on.";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		insertKpiMaster224(mongoTemplate);
		insertKpiColumnConfig224(mongoTemplate);
		insertFieldMappingStructure224(mongoTemplate);

		insertKpiMaster225(mongoTemplate);
		insertKpiColumnConfig225(mongoTemplate);
		insertFieldMappingStructure225(mongoTemplate);
	}

	// ── kpi224 — Backlog Aging ───────────────────────────────────────────────

	public void insertKpiMaster224(MongoTemplate mongoTemplate) {
		Document kpiMaster =
				new Document()
						.append(KPI_ID_FIELD, KPI224)
						.append("kpiName", "Backlog Aging")
						.append("isDeleted", "False")
						.append("defaultOrder", 6)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Intake")
						.append("kpiUnit", "Count")
						.append("chartType", "stacked-bar-chart")
						.append("xAxisLabel", "Aging Bucket")
						.append("yAxisLabel", "Issue Count")
						.append("showTrend", false)
						.append("isPositiveTrend", false)
						.append("calculateMaturity", false)
						.append("hideOverallFilter", false)
						.append("kpiSource", "Jira")
						.append("thresholdValue", 10.0)
						.append("kanban", false)
						.append("groupId", 315)
						.append("kpiInfo", new Document().append(DEFINITION, KPI224_DEFINITION))
						.append("kpiFilter", null)
						.append("aggregationCriteria", "sum")
						.append("isAdditionalFilterSupport", false)
						.append("combinedKpiSource", "Jira/Azure/Rally")
						.append("upperThresholdBG", "red")
						.append("lowerThresholdBG", "white")
						.append("kpiSubCategoryOrder", 6);

		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI224), kpiMaster, new ReplaceOptions().upsert(true));
	}

	public void insertKpiColumnConfig224(MongoTemplate mongoTemplate) {
		Document columnConfig =
				new Document()
						.append("basicProjectConfigId", null)
						.append(KPI_ID_FIELD, KPI224)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										column("Aging Bucket", 1),
										column("Project", 2),
										column("Issue ID", 3),
										column("Issue Type", 4),
										column("Issue Description", 5),
										column("Status", 6),
										column("Priority", 7),
										column("Created Date", 8),
										column("Age (Days)", 9)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI224), columnConfig, new ReplaceOptions().upsert(true));
	}

	public void insertFieldMappingStructure224(MongoTemplate mongoTemplate) {
		upsert(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, ISSUE_TYPE_FIELD_224)
						.append(FIELD_LABEL, "Issue type to include in Backlog Aging")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, "Issue_Type")
						.append(SECTION, "Issue Types Mapping")
						.append(
								TOOLTIP,
								tooltip(
										"All issue types whose backlog age should be measured (e.g., Story, Bug, Task). Leave blank to include all types.")));

		upsert(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, BACKLOG_STATUS_FIELD_224)
						.append(FIELD_LABEL, "Active backlog statuses")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, WORKFLOW)
						.append(FIELD_DISPLAY_ORDER, 10)
						.append(SECTION_ORDER, 4)
						.append(SECTION, WORKFLOW_STATUS_MAPPING)
						.append(MANDATORY, false)
						.append(
								TOOLTIP,
								tooltip(
										"Issues currently in one of these statuses are included and aged (e.g., Open, To Do, Backlog). Defaults to Open, To Do, Backlog and New when left blank.")));

		upsert(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, BACKLOG_START_STATUS_FIELD_224)
						.append(FIELD_LABEL, "Age starts from (optional)")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, WORKFLOW)
						.append(FIELD_DISPLAY_ORDER, 11)
						.append(SECTION_ORDER, 4)
						.append(SECTION, WORKFLOW_STATUS_MAPPING)
						.append(MANDATORY, false)
						.append(
								TOOLTIP,
								tooltip(
										"Optional. When set, age is measured from the first time an issue transitioned into one of these statuses instead of its creation date. Use this when issues are created before they formally enter the backlog.")));

		upsert(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, REFINED_STATUS_FIELD_224)
						.append(FIELD_LABEL, "Age ends at (optional)")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, WORKFLOW)
						.append(FIELD_DISPLAY_ORDER, 12)
						.append(SECTION_ORDER, 4)
						.append(SECTION, WORKFLOW_STATUS_MAPPING)
						.append(MANDATORY, false)
						.append(
								TOOLTIP,
								tooltip(
										"Optional. Issues currently in one of these statuses are considered already refined and are excluded from the aging chart (e.g., Ready for Sprint, Refined).")));

		upsert(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, THRESHOLD_FIELD_224)
						.append(FIELD_LABEL, "Target KPI Value")
						.append(FIELD_TYPE, "number")
						.append(SECTION, "Project Level Threshold")
						.append("processorCommon", false)
						.append(
								TOOLTIP,
								tooltip(
										"Target KPI value denotes the bare minimum a project should maintain for a KPI. User should just input the number and the unit like percentage, hours will automatically be considered. If the threshold is empty, then a common target KPI line will be shown"))
						.append(FIELD_DISPLAY_ORDER, 1)
						.append(SECTION_ORDER, 6)
						.append(MANDATORY, false)
						.append("nodeSpecific", false));
	}

	// ── kpi225 — Mid-Sprint Re-Refinement Rate ───────────────────────────────

	public void insertKpiMaster225(MongoTemplate mongoTemplate) {
		Document kpiMaster =
				new Document()
						.append(KPI_ID_FIELD, KPI225)
						.append("kpiName", "Mid-Sprint Re-Refinement Rate")
						.append("isDeleted", "False")
						.append("defaultOrder", 3)
						.append("kpiSubCategoryOrder", 3)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Intake")
						.append("kpiUnit", "%")
						.append("chartType", "line")
						.append("xAxisLabel", "Sprints")
						.append("yAxisLabel", "Percentage")
						.append("showTrend", true)
						.append("isPositiveTrend", false)
						.append("calculateMaturity", true)
						.append("maturityRange", Arrays.asList("20-", "15-20", "10-15", "5-10", "-5"))
						.append("hideOverallFilter", false)
						.append("kpiSource", "Jira")
						.append("thresholdValue", 5.0)
						.append("kanban", false)
						.append("groupId", 316)
						.append(
								"kpiInfo",
								new Document()
										.append(
												DEFINITION,
												"% of stories that move backward to a refinement state (after dev has started) relative to the count of stories that entered development in the same period. A rate above 5% indicates systematic DOR gaps; above 15% signals that refinement is not completing before sprint start."))
						.append("kpiFilter", null)
						.append("aggregationCriteria", "average")
						.append("isAdditionalFilterSupport", false)
						.append("combinedKpiSource", "Jira/Azure/Rally")
						.append("upperThresholdBG", "red")
						.append("lowerThresholdBG", "white")
						.append("forecastModel", "thetaMethod");

		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI225), kpiMaster, new ReplaceOptions().upsert(true));
	}

	public void insertKpiColumnConfig225(MongoTemplate mongoTemplate) {
		Document columnConfig =
				new Document()
						.append("basicProjectConfigId", null)
						.append(KPI_ID_FIELD, KPI225)
						.append(
								"kpiColumnDetails",
								Arrays.asList(
										column("Days/Weeks", 1),
										column("Project", 2),
										column("Issue ID", 3),
										column("Issue Type", 4),
										column("Issue Description", 5),
										column("Dev Start Date", 6),
										column("First Return Date", 7),
										column("Return-to Status", 8),
										column("Return Count", 9)));

		mongoTemplate
				.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
				.replaceOne(
						new Document(KPI_ID_FIELD, KPI225), columnConfig, new ReplaceOptions().upsert(true));
	}

	public void insertFieldMappingStructure225(MongoTemplate mongoTemplate) {
		upsert(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, STORY_TYPE_FIELD_225)
						.append(FIELD_LABEL, "Issue types to include")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, "Issue_Type")
						.append(SECTION, "Issue Types Mapping")
						.append(
								TOOLTIP,
								tooltip(
										"All issue types to measure re-refinement for (e.g., Story, Bug, Task). Leave blank to include all types.")));

		upsert(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, DEV_START_FIELD_225)
						.append(FIELD_LABEL, "Status(es) marking dev start")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, WORKFLOW)
						.append(FIELD_DISPLAY_ORDER, 8)
						.append(SECTION_ORDER, 4)
						.append(SECTION, WORKFLOW_STATUS_MAPPING)
						.append(
								TOOLTIP,
								tooltip(
										"Workflow statuses that indicate development has begun (e.g., In Progress, In Development). The first transition to any of these statuses is treated as the dev start point.")));

		upsert(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, RETURN_FIELD_225)
						.append(FIELD_LABEL, "Status(es) counting as returned to refinement")
						.append(FIELD_TYPE, CHIPS)
						.append(FIELD_CATEGORY, WORKFLOW)
						.append(FIELD_DISPLAY_ORDER, 9)
						.append(SECTION_ORDER, 4)
						.append(SECTION, WORKFLOW_STATUS_MAPPING)
						.append(
								TOOLTIP,
								tooltip(
										"Workflow statuses that represent a story moving back to refinement after dev has started (e.g., In Refinement, Needs Grooming, Backlog). Any transition to one of these statuses after dev start counts as a re-refinement event.")));

		upsert(
				mongoTemplate,
				new Document()
						.append(FIELD_NAME, THRESHOLD_FIELD_225)
						.append(FIELD_LABEL, "Target KPI Value")
						.append(FIELD_TYPE, "number")
						.append(SECTION, "Project Level Threshold")
						.append("processorCommon", false)
						.append(
								TOOLTIP,
								tooltip(
										"Target KPI value denotes the bare minimum a project should maintain for a KPI. User should just input the number and the unit like percentage, hours will automatically be considered. If the threshold is empty, then a common target KPI line will be shown"))
						.append(FIELD_DISPLAY_ORDER, 1)
						.append(SECTION_ORDER, 6)
						.append(MANDATORY, false)
						.append("nodeSpecific", false));
	}

	// ── Helpers ──────────────────────────────────────────────────────────────

	private Document column(String name, int order) {
		return new Document()
				.append(COLUMN_NAME, name)
				.append(ORDER, order)
				.append(IS_SHOWN, true)
				.append(IS_DEFAULT, true);
	}

	private Document tooltip(String text) {
		return new Document().append(DEFINITION, text);
	}

	private void upsert(MongoTemplate mongoTemplate, Document doc) {
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.replaceOne(
						new Document(FIELD_NAME, doc.getString(FIELD_NAME)),
						doc,
						new ReplaceOptions().upsert(true));
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		for (String kpiId : Arrays.asList(KPI224, KPI225)) {
			mongoTemplate
					.getCollection(KPI_MASTER_COLLECTION)
					.deleteOne(new Document(KPI_ID_FIELD, kpiId));
			mongoTemplate
					.getCollection(KPI_COLUMN_CONFIGS_COLLECTION)
					.deleteOne(new Document(KPI_ID_FIELD, kpiId));
		}
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE_COLLECTION)
				.deleteMany(
						new Document(
								FIELD_NAME,
								new Document(
										"$in",
										Arrays.asList(
												ISSUE_TYPE_FIELD_224,
												BACKLOG_STATUS_FIELD_224,
												BACKLOG_START_STATUS_FIELD_224,
												REFINED_STATUS_FIELD_224,
												THRESHOLD_FIELD_224,
												STORY_TYPE_FIELD_225,
												DEV_START_FIELD_225,
												RETURN_FIELD_225,
												THRESHOLD_FIELD_225))));
	}
}
