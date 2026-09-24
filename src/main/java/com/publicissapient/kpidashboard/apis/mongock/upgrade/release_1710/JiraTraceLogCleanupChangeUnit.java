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

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

/**
 * Drops every Jira entry from {@code processor_execution_trace_log} so the next Jira processor run
 * re-collects from scratch.
 *
 * <p>The trace log is what makes Jira collection incremental: the processor reads {@code
 * lastSuccessfulRun} and {@code lastSavedEntryUpdatedDateByType} off this document and narrows its
 * JQL to issues changed since then. An issue that has not been touched in Jira is therefore never
 * re-fetched, which means a field newly added to {@code JiraIssue} stays empty on historical issues
 * no matter how often the processor runs.
 *
 * <p>Removing the Jira trace log resets that watermark. The next run has no delta to work from, so
 * it walks the full backlog and backfills {@code acceptanceCriteria} — added in this release — onto
 * issues that already existed.
 *
 * <p>Only documents whose {@code processorName} is {@code Jira} are removed, so trace logs for
 * Sonar, Bitbucket, Jenkins and the rest keep their watermarks and stay incremental.
 *
 * <p>The delete is scoped by processor name rather than by project, unlike {@code
 * release_1221/TraceLogChangeUnit} which targeted a single {@code basicProjectConfigId}. Every
 * project needs the backfill here, not just one.
 *
 * <p>Re-running is harmless: once the rows are gone the filter matches nothing. Mongock also only
 * executes a change unit once per database.
 *
 * @see com.publicissapient.kpidashboard.common.constant.ProcessorConstants#JIRA
 */
@Slf4j
@ChangeUnit(
		id = "jira_trace_log_cleanup",
		order = "17208",
		author = "knowhow",
		systemVersion = "17.1.0")
public class JiraTraceLogCleanupChangeUnit {

	private static final String TRACE_LOG_COLLECTION = "processor_execution_trace_log";
	private static final String PROCESSOR_NAME = "processorName";

	/** Must match {@code ProcessorConstants.JIRA}, which is what the processor writes. */
	private static final String JIRA = "Jira";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		long deleted =
				mongoTemplate
						.getCollection(TRACE_LOG_COLLECTION)
						.deleteMany(new Document(PROCESSOR_NAME, JIRA))
						.getDeletedCount();
		log.info(
				"Removed {} Jira entries from {}; the next Jira run will collect in full",
				deleted,
				TRACE_LOG_COLLECTION);
	}

	/**
	 * Deleted trace logs cannot be restored, and they do not need to be.
	 *
	 * <p>The collection is a progress watermark rather than a source of truth: the processor simply
	 * rebuilds an entry on its next run. A rollback that recreated the old timestamps would be
	 * actively harmful, since it would re-establish the very watermark that is blocking the backfill.
	 */
	@RollbackExecution
	public void rollback() {
		log.info("Nothing to roll back; the Jira processor recreates its trace log on the next run");
	}
}
