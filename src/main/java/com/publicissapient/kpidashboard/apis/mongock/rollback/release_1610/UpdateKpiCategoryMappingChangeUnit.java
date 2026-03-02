package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1610;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.client.result.UpdateResult;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "update_kpi_category_mapping", order = "16101", author = "kunkambl", systemVersion = "16.1.0")
public class UpdateKpiCategoryMappingChangeUnit {

    @RollbackExecution
    public void execution(MongoTemplate mongoTemplate) {
        updateCategory(mongoTemplate, "categoryThree", "value");
        updateCategory(mongoTemplate, "categoryTwo", "quality");
        updateCategory(mongoTemplate, "categoryOne", "speed");
        log.info("Successfully updated KPI categories");
    }

    @Execution
    public void rollback(MongoTemplate mongoTemplate) {
        updateCategory(mongoTemplate, "value", "categoryThree");
        updateCategory(mongoTemplate, "quality", "categoryTwo");
        updateCategory(mongoTemplate, "speed", "categoryOne");
        log.info("Successfully rolled back KPI categories");
    }

    private void updateCategory(MongoTemplate mongoTemplate, String oldId, String newId) {
        Query query = new Query(Criteria.where("categoryId").is(oldId));
        Update update = new Update().set("categoryId", newId);
        
        UpdateResult result1 = mongoTemplate.updateMulti(query, update, "kpi_category");
        log.info("Updated kpi_category: {} -> {} (matched: {}, modified: {})", 
                oldId, newId, result1.getMatchedCount(), result1.getModifiedCount());
        
        UpdateResult result2 = mongoTemplate.updateMulti(query, update, "kpi_category_mapping");
        log.info("Updated kpi_category_mapping: {} -> {} (matched: {}, modified: {})", 
                oldId, newId, result2.getMatchedCount(), result2.getModifiedCount());
    }
}
