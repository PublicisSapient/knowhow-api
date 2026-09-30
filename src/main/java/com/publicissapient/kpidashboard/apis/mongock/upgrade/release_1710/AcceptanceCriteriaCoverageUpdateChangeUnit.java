/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Brings Acceptance Criteria Coverage (kpi227) up to date on environments that already ran {@code
 * AcceptanceCriteriaCoverageChangeUnit} or were seeded from the default JSON files. Single change
 * unit for every post-release adjustment of the KPI:
 *
 * <ul>
 *   <li>kpi_master: {@code defaultOrder} and {@code kpiSubCategoryOrder} 4, {@code kpiWidth} 50,
 *       {@code yAxisLabel} "Count", and the definition replaced with the product definition;
 *   <li>kpi_column_configs (default and every project copy): "In Progress Date" renamed to "Dev
 *       Start Date" (same concept and name as kpi225) and "Acceptance Criteria Count" moved last.
 *       Each column's {@code isShown} / {@code isDefault} flags are preserved, so project level
 *       customisations survive;
 *   <li>field_mapping_structure: the issue type and In Progress fields become mandatory (the KPI
 *       assumes nothing and shows no data without them), and their tooltips no longer claim values
 *       are copied from other KPIs — kpi227's mapping is never auto-populated.
 * </ul>
 */
@ChangeUnit(
		id = "acceptance_criteria_coverage_update",
		order = "17210",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class AcceptanceCriteriaCoverageUpdateChangeUnit {

	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_ID = "kpi227";
	private static final String KPI_COLUMN_DETAILS = "kpiColumnDetails";
	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";

	private static final String NEW_DATE_COLUMN = "Dev Start Date";
	private static final String OLD_DATE_COLUMN = "In Progress Date";

	private static final List<String> NEW_COLUMNS =
			List.of(
					"Days/Weeks",
					"Project",
					"Issue ID",
					"Issue Type",
					"Issue Description",
					"Status",
					NEW_DATE_COLUMN,
					"Acceptance Criteria Format",
					"Coverage Band",
					"Acceptance Criteria Count");

	private static final List<String> OLD_COLUMNS =
			List.of(
					"Days/Weeks",
					"Project",
					"Issue ID",
					"Issue Type",
					"Issue Description",
					"Status",
					OLD_DATE_COLUMN,
					"Acceptance Criteria Count",
					"Acceptance Criteria Format",
					"Coverage Band");

	private static final String NEW_DEFINITION =
			"Average count of acceptance criteria attached to a story at the moment it enters In Progress. "
					+ "A team consistently shipping with 0–1 ACs per story is a quality risk; "
					+ "a team with 8+ ACs per story may be over-specifying.";

	/** The definition the original insert wrote, restored on rollback. */
	private static final String OLD_DEFINITION =
			"Average number of acceptance criteria attached to a story at the moment it enters In Progress: "
					+ "total_acceptance_criteria / stories_that_entered_in_progress. The sampling moment is the story's first "
					+ "transition into one of the configured In Progress statuses, so every story is counted exactly once, in "
					+ "the period development actually started. The criteria are read from the Jira custom field configured "
					+ "under 'Custom field for Acceptance Criteria' and split into individual criteria, understanding Gherkin "
					+ "scenarios, bullet / checkbox / numbered lists and plain one-per-line text. "
					+ "There is deliberately no universal target - the right number depends on story size, so the trend is what "
					+ "matters. Each data point carries the full distribution across five bands "
					+ "(None 0, Thin 1-2, Healthy 3-5, Detailed 6-7, Over-specified 8+)"
					+ " and the share of stories that started work with no acceptance criteria at "
					+ "all. A team consistently shipping 0-1 criteria per story is carrying quality risk; a team at 8+ is "
					+ "over-specifying and the stories should probably be split. "
					+ "Note: Jira does not retain the historical value of a text custom field, so the criteria counted are the "
					+ "ones on the story today - the transition decides which stories are counted and in which period.";

	private static final String STORY_TYPE_FIELD = "jiraStoryIdentificationKPI227";
	private static final String IN_PROGRESS_FIELD = "jiraStatusForInProgressKPI227";

	private static final String STORY_TYPE_TOOLTIP =
			"All issue types that should carry acceptance criteria. "
					+ "Not every project tracks work as 'Story' - if the board delivers work as Task, "
					+ "QA Task, Enabler and so on, list those instead, otherwise this KPI reports nothing. "
					+ "Required - when left blank, this KPI shows no data. <hr>";

	private static final String IN_PROGRESS_TOOLTIP =
			"Workflow statuses that mean development has started (e.g., In Progress, In Development). "
					+ "The <b>first</b> transition into any of these is the moment the acceptance criteria are counted, "
					+ "so a story that bounces in and out of In Progress is still counted only once. "
					+ "Required - when left blank, this KPI shows no data. <hr>";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		updateKpiMaster(4, 50, 4, "Count", NEW_DEFINITION);
		updateColumnConfigs(OLD_DATE_COLUMN, NEW_DATE_COLUMN, NEW_COLUMNS);
		updateFieldStructure(STORY_TYPE_FIELD, STORY_TYPE_TOOLTIP);
		updateFieldStructure(IN_PROGRESS_FIELD, IN_PROGRESS_TOOLTIP);
	}

	/**
	 * Restores what {@code AcceptanceCriteriaCoverageChangeUnit} originally wrote. The tooltips are
	 * deliberately not restored: the old wording described an auto-population that no longer exists.
	 */
	@RollbackExecution
	public void rollback() {
		updateKpiMaster(7, 50, 7, "Acceptance Criteria per Story", OLD_DEFINITION);
		updateColumnConfigs(NEW_DATE_COLUMN, OLD_DATE_COLUMN, OLD_COLUMNS);
	}

	/** The KPI shows no data without these fields, so they are required and say so. */
	private void updateFieldStructure(String fieldName, String definition) {
		mongoTemplate
				.getCollection("field_mapping_structure")
				.updateOne(
						new Document("fieldName", fieldName),
						new Document(
								"$set", new Document("tooltip.definition", definition).append("mandatory", true)));
	}

	private void updateKpiMaster(
			int defaultOrder, int kpiWidth, int subCategoryOrder, String yAxisLabel, String definition) {
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateOne(
						new Document(KPI_ID_FIELD, KPI_ID),
						new Document(
								"$set",
								new Document("defaultOrder", defaultOrder)
										.append("kpiWidth", kpiWidth)
										.append("kpiSubCategoryOrder", subCategoryOrder)
										.append("yAxisLabel", yAxisLabel)
										.append("kpiInfo.definition", definition)));
	}

	/**
	 * Renames {@code fromName} to {@code toName} and rewrites the {@code order} values to follow
	 * {@code targetOrder}. Columns not in {@code targetOrder} are kept after the known ones.
	 */
	private void updateColumnConfigs(String fromName, String toName, List<String> targetOrder) {
		MongoCollection<Document> collection =
				mongoTemplate.getCollection(KPI_COLUMN_CONFIGS_COLLECTION);

		for (Document config : collection.find(new Document(KPI_ID_FIELD, KPI_ID))) {
			List<Document> columns = config.getList(KPI_COLUMN_DETAILS, Document.class);
			if (columns == null) {
				continue;
			}

			Map<String, Document> byName = new LinkedHashMap<>();
			for (Document column : columns) {
				String name = column.getString(COLUMN_NAME);
				if (fromName.equals(name)) {
					name = toName;
					column.put(COLUMN_NAME, toName);
				}
				byName.putIfAbsent(name, column);
			}

			List<Document> reordered = new ArrayList<>();
			targetOrder.stream().map(byName::remove).filter(c -> c != null).forEach(reordered::add);
			reordered.addAll(byName.values());
			for (int i = 0; i < reordered.size(); i++) {
				reordered.get(i).put(ORDER, i + 1);
			}

			collection.updateOne(
					new Document("_id", config.get("_id")),
					new Document("$set", new Document(KPI_COLUMN_DETAILS, reordered)));
		}
	}
}
