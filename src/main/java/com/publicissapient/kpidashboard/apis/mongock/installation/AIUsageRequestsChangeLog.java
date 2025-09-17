/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *    http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.mongock.installation;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor
@ChangeUnit(id="ddl2", order="002", author="anamitra1")
public class AIUsageRequestsChangeLog {
    public static final String AI_USAGE_REQUESTS_COLLECTION = "ai_usage_requests";

    private final MongoTemplate mongoTemplate;

    @Execution
    public void changeSet() {
        if (!mongoTemplate.collectionExists(AI_USAGE_REQUESTS_COLLECTION)) {
            mongoTemplate.createCollection(AI_USAGE_REQUESTS_COLLECTION);
        }
    }

    @RollbackExecution
    public void rollback() {
        if (mongoTemplate.collectionExists(AI_USAGE_REQUESTS_COLLECTION)) {
            mongoTemplate.dropCollection(AI_USAGE_REQUESTS_COLLECTION);
        }
    }
}
