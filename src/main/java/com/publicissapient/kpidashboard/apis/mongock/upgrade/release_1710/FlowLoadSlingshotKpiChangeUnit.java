package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "flow_load_slingshot_kpi",
		order = "17112",
		author = "kunkambl",
		systemVersion = "17.1.0")
public class FlowLoadSlingshotKpiChangeUnit {

	private static final String KPI_206 = "kpi206";
	private static final String KPI_ID = "kpiId";
	private static final String FIELD_NAME = "fieldName";
	private static final String WORKFLOW = "workflow";
	private static final String WORKFLOW_STATUS_MAPPING = "WorkFlow Status Mapping";
	private static final String DEFINITION = "definition";
	private static final String CHIPS = "chips";
	private static final String PROCESSOR_COMMON = "processorCommon";
	private static final String MANDATORY = "mandatory";
	private static final String NODE_SPECIFIC = "nodeSpecific";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		insertKpiMaster(mongoTemplate);
		insertFieldMappings(mongoTemplate);
		insertKpiColumnConfig(mongoTemplate);
	}

	@RollbackExecution
	public void rollback(MongoDatabase mongoDatabase) {
		mongoDatabase.getCollection("kpi_master").deleteOne(new Document(KPI_ID, KPI_206));
		mongoDatabase
				.getCollection("field_mapping_structure")
				.deleteMany(
						new Document(
								FIELD_NAME,
								new Document(
										"$in",
										Arrays.asList(
												"jiraIssueTypeNamesKPI206",
												"storyFirstStatusKPI206",
												"jiraStatusForInProgressKPI206",
												"jiraStatusForQaKPI206"))));
		mongoDatabase.getCollection("kpi_column_configs").deleteOne(new Document(KPI_ID, KPI_206));
	}

	private void insertKpiMaster(MongoTemplate mongoTemplate) {
		MongoCollection<Document> collection = mongoTemplate.getCollection("kpi_master");

		Document kpiMaster =
				new Document()
						.append(KPI_ID, KPI_206)
						.append("kpiName", "Flow Load")
						.append("isDeleted", "False")
						.append("defaultOrder", 13)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Flow KPIs")
						.append("kpiUnit", "")
						.append("chartType", "stacked-area")
						.append("xAxisLabel", "Time")
						.append("yAxisLabel", "Count")
						.append("showTrend", false)
						.append("isPositiveTrend", false)
						.append("calculateMaturity", false)
						.append("hideOverallFilter", false)
						.append("kpiSource", "Jira")
						.append("kanban", false)
						.append("groupId", 46)
						.append(
								"kpiInfo",
								new Document()
										.append(
												DEFINITION,
												"Number of flow items currently in progress in the value stream (WIP). Too high = thrashing; too low = underutilisation. Count of issues NOT in Backlog/To Do AND NOT in Done at point in time.")
										.append(
												"details",
												Collections.singletonList(
														new Document("type", "link")
																.append(
																		"kpiLinkDetail",
																		new Document()
																				.append("text", "Detailed Information at")
																				.append(
																						"link",
																						"https://knowhow.tools.publicis.sapient.com/wiki/kpi148-Flow+Load")))))
						.append("kpiFilter", "")
						.append("aggregationCriteria", "sum")
						.append("isTrendCalculative", false)
						.append("isAdditionalFilterSupport", false)
						.append("combinedKpiSource", "Jira/Azure/Rally");

		collection.replaceOne(
				new Document(KPI_ID, KPI_206), kpiMaster, new ReplaceOptions().upsert(true));
	}

	private void insertFieldMappings(MongoTemplate mongoTemplate) {
		MongoCollection<Document> collection = mongoTemplate.getCollection("field_mapping_structure");

		List<Document> mappings =
				Arrays.asList(
						createFieldMapping(
								"jiraIssueTypeNamesKPI206",
								"Issue types to be included",
								CHIPS,
								"Issue_Type",
								"Issue Types Mapping",
								"All issue types used by your Jira project"),
						createFieldMapping(
								"storyFirstStatusKPI206",
								"Status to identify issues in refinement",
								"text",
								WORKFLOW,
								WORKFLOW_STATUS_MAPPING,
								"All issue types that identify with a Story."),
						createFieldMapping(
								"jiraStatusForInProgressKPI206",
								"Status to identify issues in development",
								CHIPS,
								WORKFLOW,
								WORKFLOW_STATUS_MAPPING,
								"All statuses that denote incomplete issues but have advanced from their initial creation status."),
						createFieldMapping(
								"jiraStatusForQaKPI206",
								"Status to identify issues in testing",
								CHIPS,
								WORKFLOW,
								WORKFLOW_STATUS_MAPPING,
								"All workflow statuses used to identify issues in testing state"));

		for (Document mapping : mappings) {
			String fieldName = mapping.getString(FIELD_NAME);
			collection.replaceOne(
					new Document(FIELD_NAME, fieldName), mapping, new ReplaceOptions().upsert(true));
		}
	}

	private Document createFieldMapping(
			String fieldName,
			String fieldLabel,
			String fieldType,
			String fieldCategory,
			String section,
			String definition) {
		return new Document()
				.append(FIELD_NAME, fieldName)
				.append("fieldLabel", fieldLabel)
				.append("fieldType", fieldType)
				.append("fieldCategory", fieldCategory)
				.append("section", section)
				.append(PROCESSOR_COMMON, false)
				.append("tooltip", new Document().append(DEFINITION, definition))
				.append(MANDATORY, true)
				.append(NODE_SPECIFIC, false);
	}

	private void insertKpiColumnConfig(MongoTemplate mongoTemplate) {
		MongoCollection<Document> collection = mongoTemplate.getCollection("kpi_column_configs");

		List<Document> columns =
				Arrays.asList(
						createColumn("Date", 0, true),
						createColumn("In-Analysis", 1, false),
						createColumn("In-Testing", 2, false),
						createColumn("In-Progress", 3, false),
						createColumn("In-Development", 4, false),
						createColumn("Open", 5, false));

		Document filter = new Document("basicProjectConfigId", null).append(KPI_ID, KPI_206);
		Document kpiColumnConfig =
				new Document("basicProjectConfigId", null)
						.append(KPI_ID, KPI_206)
						.append("kpiColumnDetails", columns);

		collection.replaceOne(filter, kpiColumnConfig, new ReplaceOptions().upsert(true));
	}

	private Document createColumn(String columnName, int order, boolean isDefault) {
		return new Document()
				.append("columnName", columnName)
				.append("order", order)
				.append("isShown", true)
				.append("isDefault", isDefault);
	}
}
