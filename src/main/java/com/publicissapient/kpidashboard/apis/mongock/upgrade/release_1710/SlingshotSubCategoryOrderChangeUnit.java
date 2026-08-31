package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Backfills kpiSubCategoryOrder on all Slingshot KPIs so that the board config builder can sort
 * sub-category tabs explicitly, independent of defaultOrder.
 *
 * <p>Intake=1, Flow=2, Speed=3, Quality=4, Sandbox=5. kpi222 (Intake) is already set by migration
 * 17185.
 */
@ChangeUnit(
		id = "slingshot_sub_category_order_backfill",
		order = "17186",
		author = "knowhow",
		systemVersion = "17.1.0")
public class SlingshotSubCategoryOrderChangeUnit {

	private static final String KPI_MASTER_COLLECTION = "kpi_master";
	private static final String KPI_CATEGORY = "kpiCategory";
	private static final String KPI_SUB_CATEGORY = "kpiSubCategory";
	private static final String KPI_SUB_CATEGORY_ORDER = "kpiSubCategoryOrder";
	private static final String SLINGSHOT = "Slingshot";

	@Execution
	public void execution(MongoTemplate mongoTemplate) {
		backfillSubCategoryOrder(mongoTemplate, "Intake", 1);
		backfillSubCategoryOrder(mongoTemplate, "Flow", 2);
		backfillSubCategoryOrder(mongoTemplate, "Speed", 3);
		backfillSubCategoryOrder(mongoTemplate, "Quality", 4);
		backfillSubCategoryOrder(mongoTemplate, "Sandbox", 5);
	}

	private void backfillSubCategoryOrder(
			MongoTemplate mongoTemplate, String subCategory, int order) {
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateMany(
						new Document(KPI_CATEGORY, SLINGSHOT).append(KPI_SUB_CATEGORY, subCategory),
						new Document("$set", new Document(KPI_SUB_CATEGORY_ORDER, order)));
	}

	@RollbackExecution
	public void rollback(MongoTemplate mongoTemplate) {
		mongoTemplate
				.getCollection(KPI_MASTER_COLLECTION)
				.updateMany(
						new Document(KPI_CATEGORY, SLINGSHOT),
						new Document("$unset", new Document(KPI_SUB_CATEGORY_ORDER, "")));
	}
}
