package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1710;

import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "insert_slingshot_flow_efficiency",
		order = "17104",
		author = "kunkambl",
		systemVersion = "17.1.0")
public class FlowEfficiencySlingshotChangeUnit {

	private static final String DEFINITION = "definition";
	private static final String FIELD_NAME = "fieldName";
	private static final String KPI_ID = "kpiId";
	private static final String KPI_203 = "kpi203";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";
	private static final String KPI_MASTER = "kpi_master";
	private static final String KPI_COLUMN_CONFIGS = "kpi_column_configs";
	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";
	private static final String WORKFLOW_STATUS_MAPPING = "WorkFlow Status Mapping";
	private static final String FIELD_TYPE = "fieldType";
	private static final String CHIPS = "chips";
	private static final String SECTION = "section";
	private static final String TOOLTIP = "tooltip";
	private final MongoTemplate mongoTemplate;

	public FlowEfficiencySlingshotChangeUnit(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		addFlowEfficiencyKpiToKpiMasterRollback();
		addFlowEfficiencyFieldMappingStructureRollback();
	}

	public void addFlowEfficiencyKpiToKpiMaster() {
		Document kpiDocument =
				new Document()
						.append(KPI_ID, KPI_203)
						.append("kpiName", "Flow Efficiency")
						.append("kpiUnit", "")
						.append("isDeleted", "False")
						.append("defaultOrder", 2)
						.append("kpiCategory", "Slingshot")
						.append("kpiSource", "Jira")
						.append("groupId", 45)
						.append("thresholdValue", "")
						.append("kanban", false)
						.append("chartType", "line")
						.append(
								"kpiInfo",
								new Document(
										DEFINITION,
										"Flow load indicates how many items are currently in the backlog. This KPI emphasizes on limiting work in progress to enabling a fast flow of issues"))
						.append("xAxisLabel", "Duration")
						.append("yAxisLabel", "Percentage")
						.append("isPositiveTrend", false)
						.append("kpiFilter", "dropDown")
						.append("showTrend", false)
						.append("aggregationCriteria", "average")
						.append("isAdditionalFilterSupport", false)
						.append("calculateMaturity", false);
		mongoTemplate.getCollection(KPI_MASTER).insertOne(kpiDocument);

		Document kpiColumnConfigDocument =
				new Document()
						.append(KPI_ID, KPI_203)
						.append(
								"kpiColumnDetails",
								List.of(
										columnDoc("Issue ID", 1),
										columnDoc("Issue Type", 2),
										columnDoc("Issue Description", 3),
										columnDoc("Size (In Story Points)", 4),
										columnDoc("Wait Time", 5),
										columnDoc("Total Time", 6),
										columnDoc("Flow Efficiency", 7)));

		mongoTemplate.getCollection(KPI_COLUMN_CONFIGS).insertOne(kpiColumnConfigDocument);
	}

	private Document columnDoc(String name, int orderVal) {
		return new Document(COLUMN_NAME, name)
				.append(ORDER, orderVal)
				.append(IS_SHOWN, true)
				.append(IS_DEFAULT, true);
	}

	public void addFlowEfficiencyFieldMappingStructure() {

		Document closeStatusDocument =
				new Document(FIELD_NAME, "jiraIssueClosedStateKPI203")
						.append("fieldLabel", "Status to identify Close Statuses")
						.append(FIELD_TYPE, CHIPS)
						.append(SECTION, WORKFLOW_STATUS_MAPPING)
						.append(
								TOOLTIP,
								new Document(
										DEFINITION,
										"All statuses that signify an issue is 'DONE' based on 'Definition Of Done'"));

		Document waitStatusDocument =
				new Document(FIELD_NAME, "jiraIssueWaitStateKPI203")
						.append("fieldLabel", "Status to identify Wait Statuses")
						.append(FIELD_TYPE, CHIPS)
						.append(SECTION, WORKFLOW_STATUS_MAPPING)
						.append(
								TOOLTIP,
								new Document(
										DEFINITION,
										"The statuses wherein no activity takes place and signifies that issue is in queue"));

		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.insertMany(Arrays.asList(waitStatusDocument, closeStatusDocument));
	}

	@RollbackExecution
	public void rollback() {
		addFlowEfficiencyKpiToKpiMaster();
		addFlowEfficiencyFieldMappingStructure();
	}

	public void addFlowEfficiencyKpiToKpiMasterRollback() {
		Document filter = new Document(KPI_ID, KPI_203);
		mongoTemplate.getCollection(KPI_MASTER).deleteOne(filter);
		Query kpiColumnConfigQuery = new Query(Criteria.where(KPI_ID).is(KPI_203));
		mongoTemplate.remove(kpiColumnConfigQuery, KPI_COLUMN_CONFIGS);
	}

	public void addFlowEfficiencyFieldMappingStructureRollback() {
		List<String> fieldNamesToDelete =
				Arrays.asList("jiraIssueClosedStateKPI203", "jiraIssueWaitStateKPI203");
		Document filter = new Document(FIELD_NAME, new Document("$in", fieldNamesToDelete));
		mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE).deleteMany(filter);
	}
}
