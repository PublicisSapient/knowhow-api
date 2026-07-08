package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.IndexOptions;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

/**
 * Creates performance indexes required by the kpi214 (Lead Time For Change) service.
 *
 * <p>The three DB queries issued per request were missing covering indexes:
 *
 * <ul>
 *   <li>{@code deployments}: existing index starts with {@code deploymentStatus} which is absent
 *       from the date-range query; adds {@code {basicProjectConfigId, startTime}} so the query can
 *       narrow to one project then range-scan on start time.
 *   <li>{@code scm_merge_requests}: no index on {@code mergedAt}; adds {@code {processorItemId,
 *       mergedAt}} to cover the {@code findMergedList} query.
 *   <li>{@code scm_commit_details}: {@code @CompoundIndex} on the entity class is not applied
 *       automatically (auto-index-creation is off); ensures {@code {processorItemId,
 *       commitTimestamp}} exists so {@code findCommitList} can use the compound index instead of a
 *       collection scan.
 * </ul>
 *
 * <p>All three {@code createIndex} calls are idempotent — MongoDB silently skips creation if an
 * index with the same key pattern already exists.
 */
@ChangeUnit(
		id = "lead_time_for_change_slingshot_index",
		order = "17148",
		author = "knowhow",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class LeadTimeForChangeSlingshotIndexChangeUnit {

	private static final String DEPLOYMENTS = "deployments";
	private static final String SCM_MERGE_REQUESTS = "scm_merge_requests";
	private static final String SCM_COMMIT_DETAILS = "scm_commit_details";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		// deployments: allows the date-range query to filter by project first, then
		// range-scan on startTime — far faster than the existing {deploymentStatus,
		// startTime, ...}
		// index whose first field is not in the LTC query.
		mongoTemplate
				.getCollection(DEPLOYMENTS)
				.createIndex(
						new Document("basicProjectConfigId", 1).append("startTime", 1),
						new IndexOptions().name("basicProjectConfigId_1_startTime_1"));

		// scm_merge_requests: the findMergedList query filters on {processorItemId,
		// mergedAt};
		// without this index MongoDB falls back to a processorItemId-only scan +
		// in-memory date filter.
		mongoTemplate
				.getCollection(SCM_MERGE_REQUESTS)
				.createIndex(
						new Document("processorItemId", 1).append("mergedAt", 1),
						new IndexOptions().name("processorItemId_1_mergedAt_1"));

		// scm_commit_details: @CompoundIndex on the entity is not applied when
		// spring.data.mongodb.auto-index-creation is false (the default in Spring Boot
		// 3).
		// This ensures the index exists so findCommitList can use it.
		mongoTemplate
				.getCollection(SCM_COMMIT_DETAILS)
				.createIndex(
						new Document("processorItemId", 1).append("commitTimestamp", -1),
						new IndexOptions().name("processorItemId_1_commitTimestamp_-1"));
	}

	@RollbackExecution
	public void rollback() {
		mongoTemplate.getCollection(DEPLOYMENTS).dropIndex("basicProjectConfigId_1_startTime_1");
		mongoTemplate.getCollection(SCM_MERGE_REQUESTS).dropIndex("processorItemId_1_mergedAt_1");
		mongoTemplate
				.getCollection(SCM_COMMIT_DETAILS)
				.dropIndex("processorItemId_1_commitTimestamp_-1");
	}
}
