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
package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1400;

import com.mongodb.client.MongoCollection;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@ChangeUnit(id = "update_category_mapping_for_kpi196", order = "14006", author = "rendk", systemVersion = "14.0.0")
public class UpdateCategoryForTestExecutionTimeKPI {
    private final MongoTemplate mongoTemplate;
    private static final String KPI_ID = "kpi196";
    private static final String OLD_CATEGORY_ID = "categoryTwo";
    private static final String NEW_CATEGORY_ID = "quality";

    public UpdateCategoryForTestExecutionTimeKPI(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void execution() {
        MongoCollection<Document> kpiCategoryMapping = mongoTemplate.getCollection("kpi_category_mapping");

        Document filter = new Document("kpiId", KPI_ID).append("categoryId", OLD_CATEGORY_ID);
        Document update = new Document("$set", new Document("categoryId", NEW_CATEGORY_ID));

        log.info("Updating categoryId for KPI '{}' from '{}' to '{}'", KPI_ID, OLD_CATEGORY_ID, NEW_CATEGORY_ID);

        kpiCategoryMapping.updateOne(filter, update);
    }

    @RollbackExecution
    public void rollback() {
        // rollback is not required for this change unit
    }
}
