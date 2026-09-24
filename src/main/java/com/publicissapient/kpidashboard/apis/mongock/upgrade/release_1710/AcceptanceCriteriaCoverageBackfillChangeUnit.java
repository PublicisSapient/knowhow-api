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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Gives Acceptance Criteria Coverage (kpi227) a starting configuration on projects that already
 * existed when the KPI shipped.
 *
 * <p>kpi227 needs to know two things: which issue types carry acceptance criteria, and which
 * statuses mean work started. Both are matched by exact name against what the board recorded, so a
 * project whose work items are called "Task" or whose workflow says "In Development" reports
 * nothing at all under the built-in defaults of "Story" and "In Progress" — and reports it as a
 * flat zero, which reads like a quality problem rather than a missing mapping.
 *
 * <p>Every project already answered both questions for the other story based KPIs, so those answers
 * are copied across once, here. Doing it as a migration rather than as a read time fallback is
 * deliberate:
 *
 * <ul>
 *   <li>the value is <b>visible</b> in the project configuration, instead of the field showing as
 *       blank while the KPI quietly used something else;
 *   <li>the value is <b>editable</b> — a team that wants kpi227 scoped differently from kpi129 just
 *       changes it, and nothing overwrites them afterwards;
 *   <li>kpi227 does not <b>silently change</b> when an unrelated KPI's mapping is edited.
 * </ul>
 *
 * <p>Only mappings that are still unset are touched, so re-running is a no-op and no existing
 * configuration is overwritten.
 */
@ChangeUnit(id = "acceptance_criteria_coverage_backfill", order = "17209", author = "knowhow")
public class AcceptanceCriteriaCoverageBackfillChangeUnit {

	private static final String FIELD_MAPPING = "field_mapping";

	private static final String STORY_TYPE_TARGET = "jiraStoryIdentificationKPI227";
	private static final String IN_PROGRESS_TARGET = "jiraStatusForInProgressKPI227";

	/**
	 * Issue type mappings to copy from, most similar in intent first. kpi129, kpi166, kpi40 and
	 * kpi164 all mean "the issue types this project delivers work as", which is exactly the
	 * population kpi227 measures.
	 */
	private static final List<String> STORY_TYPE_SOURCES =
			List.of(
					"jiraStoryIdentificationKPI129",
					"jiraStoryIdentificationKPI166",
					"jiraStoryIdentificationKpi40",
					"jiraStoryIdentificationKPI164",
					"jiraIssueTypeKPI3");

	/**
	 * In Progress mappings to copy from. These matter more than the issue types: boards disagree far
	 * more about status names ("In Progress", "In Development", "Construction", "Implementing") than
	 * about what a unit of work is called.
	 */
	private static final List<String> IN_PROGRESS_SOURCES =
			List.of(
					"jiraStatusForInProgressKPI148",
					"jiraStatusForInProgressKPI122",
					"jiraStatusForInProgressKPI145",
					"jiraStatusForInProgressKPI125",
					"jiraStatusForInProgressKPI128",
					"jiraStatusForInProgressKPI123",
					"jiraStatusForInProgressKPI119",
					"jiraStatusForInProgressKPI154");

	private final MongoTemplate mongoTemplate;

	public AcceptanceCriteriaCoverageBackfillChangeUnit(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		MongoCollection<Document> fieldMapping = mongoTemplate.getCollection(FIELD_MAPPING);
		List<WriteModel<Document>> writes = new ArrayList<>();

		for (Document mapping : fieldMapping.find()) {
			Document valuesToSet = new Document();

			if (isUnset(mapping, STORY_TYPE_TARGET)) {
				firstConfigured(mapping, STORY_TYPE_SOURCES)
						.ifPresent(values -> valuesToSet.append(STORY_TYPE_TARGET, values));
			}
			if (isUnset(mapping, IN_PROGRESS_TARGET)) {
				firstConfigured(mapping, IN_PROGRESS_SOURCES)
						.ifPresent(values -> valuesToSet.append(IN_PROGRESS_TARGET, values));
			}

			if (!valuesToSet.isEmpty()) {
				writes.add(
						new UpdateOneModel<>(
								new Document("_id", mapping.get("_id")), new Document("$set", valuesToSet)));
			}
		}

		if (!writes.isEmpty()) {
			fieldMapping.bulkWrite(writes);
		}
	}

	/** First source mapping the project actually filled in. */
	private static Optional<Object> firstConfigured(Document mapping, List<String> sources) {
		return sources.stream()
				.map(mapping::get)
				.filter(value -> value instanceof List<?> list && !list.isEmpty())
				.findFirst();
	}

	/** Absent, null or an empty list all mean "the project has not answered this yet". */
	private static boolean isUnset(Document mapping, String field) {
		Object value = mapping.get(field);
		if (value == null) {
			return true;
		}
		return value instanceof List<?> list && list.isEmpty();
	}

	/**
	 * Intentionally a no-op.
	 *
	 * <p>Unsetting the two fields would also discard any value a user edited after the migration ran,
	 * and the migration only ever fills mappings that were empty — so leaving them in place restores
	 * nothing that was lost while destroying something that might not have been.
	 */
	@RollbackExecution
	public void rollback() {
		// no-op, see javadoc
	}
}
