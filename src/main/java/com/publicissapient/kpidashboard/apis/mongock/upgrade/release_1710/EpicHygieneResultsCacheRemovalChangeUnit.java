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

import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Drops the {@code epic_hygiene_results} collection.
 *
 * <p>The Epic Hygiene KPI (kpi312) no longer stores LLM verdicts: every Epic is scored by the LLM
 * on each request, so the report always reflects the current state of the Epic and the current
 * prompt. The collection - and the document, repository and index that fed it - are gone, so the
 * leftover data is dropped here rather than lingering in every environment.
 */
@Slf4j
@RequiredArgsConstructor
@ChangeUnit(
		id = "epic_hygiene_results_cache_removal",
		order = "17184",
		author = "kunkambl",
		systemVersion = "17.1.0")
public class EpicHygieneResultsCacheRemovalChangeUnit {

	private static final String EPIC_HYGIENE_RESULTS_COLLECTION = "epic_hygiene_results";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		if (mongoTemplate.collectionExists(EPIC_HYGIENE_RESULTS_COLLECTION)) {
			mongoTemplate.dropCollection(EPIC_HYGIENE_RESULTS_COLLECTION);
			log.info(
					"Dropped the {} collection - kpi312 verdicts are no longer cached",
					EPIC_HYGIENE_RESULTS_COLLECTION);
		}
	}

	/** Nothing to restore: the cached verdicts are recomputed by the LLM on the next request. */
	@RollbackExecution
	public void rollback() {
		// no-op
	}
}
