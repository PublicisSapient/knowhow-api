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

package com.publicissapient.kpidashboard.apis.recommendations.dto;

import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationLevel;

/**
 * Request object for retrieving AI-generated project recommendations at a specific organizational
 * hierarchy level.
 *
 * @param levelName the organizational hierarchy level name (e.g., "project", "account")
 * @param parentNodeId optional parent node ID for filtering to specific organizational subtree
 * @param recommendationLevel optional recommendation level filter (PROJECT_LEVEL or KPI_LEVEL); defaults to PROJECT_LEVEL
 */
public record RecommendationRequest(String levelName, String parentNodeId, RecommendationLevel recommendationLevel) {}
