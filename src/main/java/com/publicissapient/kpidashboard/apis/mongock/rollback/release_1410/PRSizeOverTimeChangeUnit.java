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
package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1410;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ChangeUnit(id = "r_pr_size_kpi", order = "014106", author = "aksshriv1", systemVersion = "14.1.0")
public class PRSizeOverTimeChangeUnit {

    private final MongoTemplate mongoTemplate;

    public PRSizeOverTimeChangeUnit(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void execution() {
        rollbackkpi162();
    }


    @RollbackExecution
    public void rollback() {
        updatekpi162();
    }

    public void updatekpi162() {

        Bson filter = Filters.eq("kpiId", "kpi162");

        Bson updates = Updates.combine(
                Updates.set("kpiName", "PR Distribution"),
                Updates.set("chartType", "scatter"),
                Updates.unset("upperThresholdBG"),
                Updates.unset("lowerThresholdBG"),
                Updates.unset("maturityRange")
        );

        mongoTemplate.getCollection("kpi_master").updateOne(filter, updates);

        mongoTemplate.getCollection("kpi_column_config").deleteMany(filter);


        Map<String, Object> document = new HashMap<>();
        document.put("basicProjectConfigId", null);
        document.put("kpiId", "kpi162");

        List<Map<String, Object>> kpiColumnDetails = Arrays.asList(
                createColumn("Project", 1),
                createColumn("Repo", 2),
                createColumn("Branch", 3),
                createColumn("Author", 4),
                createColumn("Days/Weeks", 5),
                createColumn("Merge Request Url", 6),
                createColumn("PR Size (No. of lines)", 7)
        );

        document.put("kpiColumnDetails", kpiColumnDetails);

        mongoTemplate.getCollection("kpi_column_config").insertOne(new org.bson.Document(document));
    }

    private Map<String, Object> createColumn(String name, int order) {
        Map<String, Object> col = new HashMap<>();
        col.put("columnName", name);
        col.put("order", order);
        col.put("isShown", true);
        col.put("isDefault", true);
        return col;
    }

    public void rollbackkpi162() {

        Bson filter = Filters.eq("kpiId", "kpi162");
        // Delete from kpi_master collection
        mongoTemplate.getCollection("kpi_master").deleteOne(filter);
        // (Optional) — delete from kpi_column_config if needed
        mongoTemplate.getCollection("kpi_column_config").deleteMany(filter);
    }
}
