package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "pr_cycle_time_change_unit",
		order = "17127",
		author = "aksshriv1",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class PRCycleTimeSlingShotChangeunit {

	private static final String KPI_ID = "kpiId";
	private static final String KPI_209 = "kpi209";
	private static final String KPI_MASTER = "kpi_master";
	private static final String FIELD_MAPPING_STRUCTURE = "field_mapping_structure";

	private static final String DEFINITION = "definition";
	private static final String CLASS = "_class";
	private static final String KEY_BASIC_PROJECT_CONFIG_ID = "basicProjectConfigId";
	private static final String KEY_KPI_COLUMN_DETAILS = "kpiColumnDetails";
	private static final String KPI_EXCEL_COLUMN_CONFIG = "kpi_column_configs";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";

	private static final String TOOLTIP_CLASS =
			"com.publicissapient.kpidashboard.apis.mongock.FieldMappingStructureForMongock$MappingToolTip";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		insertPRCycleTimeKPI();
		insertFieldMappingStructure();
		insertExcelColumnConfig();
	}

	private void insertExcelColumnConfig() {
		Document filter = new Document(KEY_BASIC_PROJECT_CONFIG_ID, null).append(KPI_ID, KPI_209);

		Document kpiColumnConfig =
				new Document(KEY_BASIC_PROJECT_CONFIG_ID, null)
						.append(KPI_ID, KPI_209)
						.append(
								KEY_KPI_COLUMN_DETAILS,
								List.of(
										new Document()
												.append(COLUMN_NAME, "Project")
												.append(ORDER, 1)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Repo")
												.append(ORDER, 2)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Branch")
												.append(ORDER, 3)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Days/Weeks")
												.append(ORDER, 4)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Developer")
												.append(ORDER, 5)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Merge Request Url")
												.append(ORDER, 6)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "PR Raised Time")
												.append(ORDER, 7)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "PR Merged Time")
												.append(ORDER, 8)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true),
										new Document()
												.append(COLUMN_NAME, "Time Spent (in hours)")
												.append(ORDER, 9)
												.append(IS_SHOWN, true)
												.append(IS_DEFAULT, true)));

		mongoTemplate.getCollection(KPI_EXCEL_COLUMN_CONFIG).insertOne(kpiColumnConfig);
	}

	private void insertPRCycleTimeKPI() {

		Document kpiDocument =
				new Document()
						.append(KPI_ID, KPI_209)
						.append("kpiName", "PR Cycle Time")
						.append("kpiUnit", "Hours")
						.append("isDeleted", "False")
						.append("defaultOrder", 2)
						.append("kpiCategory", "Slingshot")
						.append("kpiSubCategory", "Speed")
						.append("kpiSource", "BitBucket")
						.append("combinedKpiSource", "Bitbucket/AzureRepository/GitHub/GitLab")
						.append("groupId", 7)
						.append("thresholdValue", 50)
						.append("kanban", false)
						.append("chartType", "line")
						.append("isRepoToolKpi", true)
						.append(
								"kpiInfo",
								new Document()
										.append(DEFINITION, "Time from PR opened to merged.")
										.append(
												"details",
												List.of(
														new Document("type", "link")
																.append(
																		"kpiLinkDetail",
																		new Document("text", "Detailed Information at")
																				.append(
																						"link",
																						"https://psknowhow.atlassian.net/wiki/spaces/PSKNOWHOW/pages/70713477/Developer+Mean+time+to+Merge"))))
										.append(
												CLASS, "com.publicissapient.kpidashboard.common.model.application.KpiInfo"))
						.append("xaxisLabel", "Week")
						.append("yaxisLabel", "Time Spent")
						.append("isPositiveTrend", true)
						.append("kpiFilter", "dropDown")
						.append("showTrend", false)
						.append("aggregationCriteria", "average")
						.append("isAdditionalFilterSupport", false)
						.append("calculateMaturity", false);

		mongoTemplate.getCollection(KPI_MASTER).insertOne(kpiDocument);
	}

	private void insertFieldMappingStructure() {

		Document fieldMapping =
				new Document()
						.append("fieldName", "thresholdValueKPI209")
						.append("fieldLabel", "Target KPI Value")
						.append("fieldType", "number")
						.append("section", "Project Level Threshold")
						.append("processorCommon", false)
						.append(
								"tooltip",
								new Document()
										.append(
												"definition",
												"Target KPI value denotes the bare minimum a project should maintain for a KPI. User should just input the number and the unit like percentage, hours will automatically be considered. If the threshold is empty, then a common target KPI line will be shown"))
						.append("fieldDisplayOrder", 1)
						.append("sectionOrder", 6)
						.append("mandatory", false)
						.append("nodeSpecific", false);

		mongoTemplate.getCollection(FIELD_MAPPING_STRUCTURE).insertOne(fieldMapping);
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate.getCollection(KPI_MASTER).deleteOne(new Document(KPI_ID, KPI_209));
		mongoTemplate.getCollection(KPI_EXCEL_COLUMN_CONFIG).deleteOne(new Document(KPI_ID, KPI_209));
		mongoTemplate
				.getCollection(FIELD_MAPPING_STRUCTURE)
				.deleteOne(new Document("fieldName", "thresholdValueKPI209"));
	}
}
