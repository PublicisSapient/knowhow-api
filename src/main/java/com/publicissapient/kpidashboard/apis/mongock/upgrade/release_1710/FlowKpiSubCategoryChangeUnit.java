package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(
		id = "flow_kpi_sub_category_update",
		order = "17121",
		author = "aksshriv1",
		systemVersion = "17.1.0")
@RequiredArgsConstructor
public class FlowKpiSubCategoryChangeUnit {

	private static final String KPI_MASTER = "kpi_master";
	private static final String KPI_ID = "kpiId";
	private static final String KPI_SUB_CATEGORY = "kpiSubCategory";
	private static final String FLOW = "Flow";
	private static final String FLOW_KPIS = "Flow KPIs";

	private static final List<String> FLOW_KPI_IDS =
			Arrays.asList("kpi202", "kpi203", "kpi204", "kpi205", "kpi206", "kpi207");

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execute() {
		mongoTemplate.updateMulti(
				Query.query(Criteria.where(KPI_ID).in(FLOW_KPI_IDS)),
				Update.update(KPI_SUB_CATEGORY, FLOW),
				KPI_MASTER);
	}

	@RollbackExecution
	public void rollback() {
		List<String> kpisWithoutSubCategory =
				Arrays.asList("kpi202", "kpi203", "kpi204", "kpi205", "kpi207");

		mongoTemplate.updateMulti(
				Query.query(Criteria.where(KPI_ID).in(kpisWithoutSubCategory)),
				new Update().unset(KPI_SUB_CATEGORY),
				KPI_MASTER);

		mongoTemplate.updateFirst(
				Query.query(Criteria.where(KPI_ID).is("kpi206")),
				Update.update(KPI_SUB_CATEGORY, FLOW_KPIS),
				KPI_MASTER);
	}
}
