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

package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsKPIService;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.Deployment;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.repository.application.DeploymentRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Slingshot / DORA speed KPI: Lead Time For Change.
 *
 * <p>Definition per problem statement:
 *
 * <pre>
 *   Lead Time For Change = ts(deploy to prod) - ts(first commit on PR branch)
 * </pre>
 *
 * <p>This implementation intentionally does NOT depend on Jira issue data (no issue fetch, no DoD
 * status, no Jira issue-type mapping). It computes the metric purely from SCM (merge requests +
 * first commit timestamp of the PR branch) and deployment data.
 *
 * <p>The KPI uses two lightweight field-mapping entries:
 *
 * <ul>
 *   <li>{@code productionBranchKPI214} - Name of the production branch. Only merged PRs whose
 *       {@code toBranch} matches this value are considered (defaults to {@code master} when not
 *       configured).
 *   <li>{@code thresholdValueKPI214} - Target KPI value (hours) used for {@link
 *       #calculateThresholdValue(FieldMapping)}.
 * </ul>
 *
 * <p>Algorithm per project:
 *
 * <ol>
 *   <li>Fetch all merged pull requests within the reporting window using {@link
 *       ScmKpiHelperService#getMergedRequests}.
 *   <li>Fetch all deployments within the reporting window using {@link
 *       DeploymentRepository#findDeploymentList}.
 *   <li>For every merged PR, find the earliest deployment whose {@code changeSets} contain any of
 *       the PR's commit SHAs. If no deployment references those SHAs, fall back to the earliest
 *       deployment whose {@code startTime} is on/after the PR's {@code mergedAt} time (and whose
 *       repo matches, when available).
 *   <li>Lead time (in hours) = {@code deployment.startTime - PR.firstCommitDate}. PRs with no
 *       matching deployment or missing timestamps are skipped.
 *   <li>Values are bucketed weekly (by deployment date) for the trend line, and the KPI value per
 *       bucket is the average lead time (in hours).
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadTimeForChangeSlingshotServiceImpl
		extends JenkinsKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String MERGED_PRS = "mergedPrs";
	private static final String ALL_MERGED_PRS = "allMergedPrs";
	private static final String DEPLOYMENTS = "deployments";
	private static final String COMMITS = "commits";
	private static final String PRODUCTION_BRANCH = "productionBranch";
	private static final String DEFAULT_PRODUCTION_BRANCH = "master";
	private static final String PRODUCTION_JOB_NAME = "productionJobName";
	private static final String CALCULATION_STRATEGY = "calculationStrategy";
	private static final String STRATEGY_COMMIT = "COMMIT";
	private static final String DEPLOYMENT = "DEPLOYMENT";
	private static final int DEFAULT_DATA_POINTS = 12;

	private static final DateTimeFormatter DEPLOYMENT_TS_FORMATTER =
			DateTimeFormatter.ofPattern(DateUtil.TIME_FORMAT);
	private static final DateTimeFormatter EXCEL_DATE_FORMATTER =
			DateTimeFormatter.ofPattern(DateUtil.DISPLAY_DATE_TIME_FORMAT);

	private final ScmKpiHelperService scmKpiHelperService;
	private final DeploymentRepository deploymentRepository;
	private final ConfigHelperService configHelperService;

	/** {@inheritDoc} */
	@Override
	public String getQualifierType() {
		return KPICode.LEAD_TIME_FOR_CHANGE_SLINGSHOT.name();
	}

	/** {@inheritDoc} */
	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {
		Node projectNode =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT).get(0);
		kpiRequest.setXAxisDataPoints(DEFAULT_DATA_POINTS);
		kpiRequest.setDuration(CommonConstant.WEEK);

		Map<String, Node> nodeMap = Map.of(projectNode.getId(), projectNode);
		calculateProjectKpiTrendData(nodeMap, projectNode, kpiRequest, kpiElement);

		log.debug(
				"[LEAD-TIME-FOR-CHANGE-SLINGSHOT][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				projectNode);

		// Single dropdown filter (repository name) + Overall aggregate.
		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(
				projectNode, nodeWiseKPIValue, KPICode.LEAD_TIME_FOR_CHANGE_SLINGSHOT);

		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMap(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.LEAD_TIME_FOR_CHANGE_SLINGSHOT);

		// Emit exactly one DataCountGroup per repository (plus Overall) using a single
		// filter, so the
		// UI renders a single dropdown (mirrors FlowEfficiencySlingshotServiceImpl).
		List<DataCountGroup> dataCountGroups = new ArrayList<>();
		trendValuesMap.forEach(
				(filter, dataCounts) -> {
					DataCountGroup group = new DataCountGroup();
					group.setFilter(filter);
					group.setValue(dataCounts);
					dataCountGroups.add(group);
				});
		kpiElement.setTrendValueList(dataCountGroups);
		kpiElement.setExcelColumns(KPIExcelColumn.LEAD_TIME_FOR_CHANGE_SLINGSHOT.getColumns());
		return kpiElement;
	}

	/** {@inheritDoc} */
	@Override
	public Double calculateKPIMetrics(Map<String, Object> stringObjectMap) {
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiId) {
		return calculateKpiValueForDouble(valueList, kpiId);
	}

	/** {@inheritDoc} */
	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		if (fieldMapping == null) {
			return calculateThresholdValue(null, KPICode.LEAD_TIME_FOR_CHANGE_SLINGSHOT.getKpiId());
		}
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI214(), KPICode.LEAD_TIME_FOR_CHANGE_SLINGSHOT.getKpiId());
	}

	/**
	 * Resolves the production branch to filter merged PRs against, defaulting to {@value
	 * #DEFAULT_PRODUCTION_BRANCH} when the field mapping is missing.
	 */
	String resolveProductionBranch(ObjectId basicProjectConfigId) {
		if (basicProjectConfigId == null || configHelperService == null) {
			return DEFAULT_PRODUCTION_BRANCH;
		}
		Map<ObjectId, FieldMapping> fieldMappingMap = configHelperService.getFieldMappingMap();
		if (fieldMappingMap == null) {
			return DEFAULT_PRODUCTION_BRANCH;
		}
		FieldMapping fieldMapping = fieldMappingMap.get(basicProjectConfigId);
		if (fieldMapping == null) {
			return DEFAULT_PRODUCTION_BRANCH;
		}
		String branch = fieldMapping.getProductionBranchKPI214();
		return (branch == null || branch.trim().isEmpty()) ? DEFAULT_PRODUCTION_BRANCH : branch.trim();
	}

	/**
	 * Resolves the production deployment job name to filter deployments against. Returns {@code null}
	 * when unconfigured, which means all deployments are used (preserving existing behaviour).
	 */
	String resolveProductionJobName(ObjectId basicProjectConfigId) {
		if (basicProjectConfigId == null || configHelperService == null) {
			return null;
		}
		Map<ObjectId, FieldMapping> fieldMappingMap = configHelperService.getFieldMappingMap();
		if (fieldMappingMap == null) {
			return null;
		}
		FieldMapping fieldMapping = fieldMappingMap.get(basicProjectConfigId);
		if (fieldMapping == null) {
			return null;
		}
		String jobName = fieldMapping.getProductionJobNameKPI214();
		return (jobName == null || jobName.trim().isEmpty()) ? null : jobName.trim();
	}

	/**
	 * Returns {@value #STRATEGY_COMMIT} when the field mapping opts into commit-based calculation,
	 * otherwise returns {@code "DEPLOYMENT"} (the default, backward-compatible path).
	 */
	String resolveCalculationStrategy(ObjectId basicProjectConfigId) {
		if (basicProjectConfigId == null || configHelperService == null) {
			return DEPLOYMENT;
		}
		Map<ObjectId, FieldMapping> fieldMappingMap = configHelperService.getFieldMappingMap();
		if (fieldMappingMap == null) {
			return DEPLOYMENT;
		}
		FieldMapping fieldMapping = fieldMappingMap.get(basicProjectConfigId);
		if (fieldMapping == null) {
			return DEPLOYMENT;
		}
		String strategy = fieldMapping.getCalculationStrategyKPI214();
		return STRATEGY_COMMIT.equalsIgnoreCase(strategy) ? STRATEGY_COMMIT : DEPLOYMENT;
	}

	/**
	 * Fetches SCM merged PRs and deployment records for the given project(s). No Jira issue fetch is
	 * performed.
	 */
	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> resultMap = new HashMap<>();
		if (CollectionUtils.isEmpty(leafNodeList)) {
			return resultMap;
		}

		ObjectId basicProjectConfigId =
				leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId();

		CustomDateRange dateRange = buildDateRangeFromStrings(startDate, endDate, kpiRequest);

		String productionBranch = resolveProductionBranch(basicProjectConfigId);
		String productionJobName = resolveProductionJobName(basicProjectConfigId);
		String calculationStrategy = resolveCalculationStrategy(basicProjectConfigId);

		List<ScmMergeRequests> allMergedPrs =
				scmKpiHelperService.getMergedRequests(basicProjectConfigId, dateRange).stream()
						.filter(mr -> mr != null && mr.getToBranch() != null)
						.toList();
		List<ScmMergeRequests> mergedPrs =
				allMergedPrs.stream()
						.filter(mr -> productionBranch.equalsIgnoreCase(mr.getToBranch().trim()))
						.toList();

		List<ScmCommits> commits =
				scmKpiHelperService.getCommitDetails(basicProjectConfigId, dateRange);

		List<Deployment> deployments;
		if (STRATEGY_COMMIT.equals(calculationStrategy)) {
			// In COMMIT mode, deployments are not needed — the PR merge date is the proxy.
			deployments = Collections.emptyList();
		} else {
			deployments =
					deploymentRepository.findDeploymentList(
							new HashMap<>(), Collections.singleton(basicProjectConfigId), startDate, endDate);

			// When a production job name is configured, restrict to that ArgoCD application
			// only so we measure time-to-production rather than time-to-first-deploy.
			if (productionJobName != null) {
				final String jobNameFilter = productionJobName;
				deployments =
						deployments.stream()
								.filter(d -> jobNameFilter.equalsIgnoreCase(d.getJobName()))
								.collect(Collectors.toList());
			}

			// For tools like ArgoCD / GitHubAction, each deployment record carries only the
			// HEAD commit SHA. Expand the changeSets by pulling in every commit that landed
			// on the deployed repo between the previous deployment's head commit and this
			// deployment's start time.
			enrichHeadOnlyDeployments(deployments, commits);
		}

		resultMap.put(MERGED_PRS, mergedPrs);
		resultMap.put(ALL_MERGED_PRS, allMergedPrs);
		resultMap.put(DEPLOYMENTS, deployments);
		resultMap.put(COMMITS, commits);
		resultMap.put(PRODUCTION_BRANCH, productionBranch);
		resultMap.put(PRODUCTION_JOB_NAME, productionJobName);
		resultMap.put(CALCULATION_STRATEGY, calculationStrategy);
		return resultMap;
	}

	/**
	 * For ArgoCD / GitHubAction deployments the incoming {@link Deployment#getChangeSets()
	 * changeSets} list contains only the HEAD commit SHA. This method expands each such deployment's
	 * changeSets to include every commit on the same repo whose commit timestamp lies strictly
	 * between the previous deployment's head-commit time and this deployment's start time. The first
	 * (earliest) deployment retains its original head-only changeSets so we still record its
	 * head-commit time for subsequent expansions.
	 */
	private void enrichHeadOnlyDeployments(List<Deployment> deployments, List<ScmCommits> commits) {
		if (CollectionUtils.isEmpty(deployments) || CollectionUtils.isEmpty(commits)) {
			return;
		}

		boolean hasHeadOnlyTool =
				deployments.stream()
						.anyMatch(
								d ->
										ProcessorConstants.ARGOCD.equalsIgnoreCase(d.getTool())
												|| ProcessorConstants.GITHUBACTION.equalsIgnoreCase(d.getTool()));
		if (!hasHeadOnlyTool) {
			return;
		}

		deployments.sort(
				Comparator.comparing(
						Deployment::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));

		// Pre-compute once: a SHA→timestamp map for O(1) head-commit pointer advance,
		// and a filtered list with pre-computed timestamps so the per-deployment window
		// filter runs in O(C) total rather than O(D×C) with repeated
		// convertMillisToLocalDateTime.
		// Pre-compute once: a SHA→timestamp map for O(1) head-commit pointer advance,
		// and a filtered list with pre-computed timestamps so the per-deployment window
		// filter runs in O(C) total rather than O(D×C) with repeated
		// convertMillisToLocalDateTime.
		Map<String, LocalDateTime> commitTsBySha = new HashMap<>(commits.size() * 2);
		List<Pair<ScmCommits, LocalDateTime>> timedCommits = new ArrayList<>(commits.size());
		for (ScmCommits c : commits) {
			if (c == null || c.getSha() == null || c.getCommitTimestamp() == null) {
				continue;
			}
			LocalDateTime ts = DateUtil.convertMillisToLocalDateTime(c.getCommitTimestamp());
			if (ts != null) {
				commitTsBySha.putIfAbsent(c.getSha().toLowerCase(), ts);
				timedCommits.add(Pair.of(c, ts));
			}
		}

		LocalDateTime previousDeploymentTime = null;
		for (Deployment deployment : deployments) {
			LocalDateTime deployStartDateTime = parseDeploymentTime(deployment.getStartTime());
			if (deployStartDateTime == null) {
				continue;
			}

			if (previousDeploymentTime != null) {
				// HEAD-only deployments (ArgoCD, GitHub Actions) deploy the full stack, so all
				// project commits in the time window between deployments are relevant — no repo
				// filter here.
				final LocalDateTime prev = previousDeploymentTime;
				List<String> changeSetShas =
						timedCommits.stream()
								.filter(
										p -> p.getRight().isAfter(prev) && p.getRight().isBefore(deployStartDateTime))
								.map(p -> p.getLeft().getSha())
								.toList();
				deployment.setChangeSets(changeSetShas);
			}

			// Advance the "previous deployment head" pointer to this deployment's head
			// commit time so the next iteration expands its changeSets correctly.
			// For ArgoCD the single changeSet SHA is an env hash, not a real git SHA, so
			// commitTsBySha won't contain it. Fall back to deployStartDateTime so the
			// pointer advances and subsequent deployments can be enriched.
			List<String> currentChangeSets = deployment.getChangeSets();
			if (CollectionUtils.isNotEmpty(currentChangeSets)) {
				LocalDateTime headTs = commitTsBySha.get(currentChangeSets.get(0).toLowerCase());
				previousDeploymentTime = headTs != null ? headTs : deployStartDateTime;
			}
		}
	}

	/** Builds a {@link CustomDateRange} covering the KPI's reporting window. */
	private CustomDateRange buildDateRangeFromStrings(
			String startDate, String endDate, KpiRequest kpiRequest) {
		CustomDateRange range = new CustomDateRange();
		try {
			range.setStartDate(LocalDate.parse(startDate));
			range.setEndDate(LocalDate.parse(endDate));
		} catch (Exception e) {
			int weeks =
					kpiRequest != null && kpiRequest.getXAxisDataPoints() > 0
							? kpiRequest.getXAxisDataPoints()
							: DEFAULT_DATA_POINTS;
			range.setStartDate(LocalDate.now(ZoneId.systemDefault()).minusWeeks(weeks));
			range.setEndDate(LocalDate.now(ZoneId.systemDefault()).plusDays(1));
		}
		return range;
	}

	/** Populates the KPI value for the project leaf node and builds the weekly trend line. */
	@SuppressWarnings("unchecked")
	private void calculateProjectKpiTrendData(
			Map<String, Node> mapTmp,
			Node projectLeafNode,
			KpiRequest kpiRequest,
			KpiElement kpiElement) {

		int dataPoints = kpiRequest.getXAxisDataPoints();
		String duration = kpiRequest.getDuration();

		String startDate = LocalDate.now(ZoneId.systemDefault()).minusWeeks(dataPoints).toString();
		String endDate = LocalDate.now(ZoneId.systemDefault()).plusDays(1).toString();

		Map<String, Object> scmDataMap =
				fetchKPIDataFromDb(List.of(projectLeafNode), startDate, endDate, kpiRequest);

		List<ScmMergeRequests> mergedPrs =
				(List<ScmMergeRequests>) scmDataMap.getOrDefault(MERGED_PRS, Collections.emptyList());
		List<ScmMergeRequests> allMergedPrs =
				(List<ScmMergeRequests>) scmDataMap.getOrDefault(ALL_MERGED_PRS, Collections.emptyList());
		List<Deployment> deployments =
				(List<Deployment>) scmDataMap.getOrDefault(DEPLOYMENTS, Collections.emptyList());
		List<ScmCommits> commits =
				(List<ScmCommits>) scmDataMap.getOrDefault(COMMITS, Collections.emptyList());
		String calculationStrategy =
				(String) scmDataMap.getOrDefault(CALCULATION_STRATEGY, DEPLOYMENT);

		boolean isCommitStrategy = STRATEGY_COMMIT.equals(calculationStrategy);

		if (CollectionUtils.isEmpty(mergedPrs)
				|| (!isCommitStrategy && CollectionUtils.isEmpty(deployments))) {
			log.info(
					"[LEAD-TIME-FOR-CHANGE-SLINGSHOT] Missing SCM/deployment data for project {} "
							+ "(prs={}, deployments={}, strategy={})",
					projectLeafNode.getProjectFilter(),
					mergedPrs.size(),
					deployments.size(),
					calculationStrategy);
			mapTmp.get(projectLeafNode.getId()).setValue(new LinkedHashMap<String, List<DataCount>>());
			return;
		}

		List<LeadTimeRecord> records =
				isCommitStrategy
						? new ArrayList<>(computeLeadTimeRecordsFromMerges(mergedPrs, allMergedPrs, commits))
						: new ArrayList<>(
								computeLeadTimeRecords(mergedPrs, allMergedPrs, deployments, commits));
		records.sort(
				Comparator.comparing((LeadTimeRecord r) -> r.deploymentTime)
						.thenComparing(r -> r.commitDateTime, Comparator.nullsLast(Comparator.naturalOrder())));

		String productionBranch =
				(String) scmDataMap.getOrDefault(PRODUCTION_BRANCH, DEFAULT_PRODUCTION_BRANCH);
		populateLeadTimeExcelData(
				getRequestTrackerId(), records, projectLeafNode, productionBranch, kpiElement);

		String projectName = projectLeafNode.getProjectFilter().getName();
		Map<String, List<DataCount>> aggDataMap = new LinkedHashMap<>();

		// Build the dropdown from EVERY repository seen in the project's deployments
		// (and merge
		// requests), not just repos that produced a lead-time record. This way the
		// filter still lists
		// each repo when there are simply no PR<->deployment matches yet.
		Set<String> repoNames = collectRepoLabels(mergedPrs, deployments, records);

		String overallKpiGroup = CommonConstant.OVERALL;
		aggDataMap.put(overallKpiGroup, new ArrayList<>());
		for (String repo : repoNames) {
			aggDataMap.put(repo, new ArrayList<>());
		}

		LocalDateTime currentDate = DateUtil.getTodayTime();
		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);

			List<LeadTimeRecord> inRange =
					records.stream()
							.filter(r -> isWithin(r.deploymentTime.toLocalDate(), periodRange))
							.toList();

			// Overall bucket.
			addBucketDataCount(aggDataMap, overallKpiGroup, projectName, dateLabel, inRange);

			// Per-repo buckets - include every known repo so the trend line has every date
			// point even
			// when a given period has no data for that repo.
			for (String repo : repoNames) {
				List<LeadTimeRecord> repoInRange =
						inRange.stream().filter(r -> repo.equals(resolveRepoLabel(r.repoName))).toList();
				addBucketDataCount(aggDataMap, repo, projectName, dateLabel, repoInRange);
			}

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}

		mapTmp.get(projectLeafNode.getId()).setValue(aggDataMap);
	}

	/** Adds a single {@link DataCount} (average lead time in hours) to the given trend bucket. */
	private void addBucketDataCount(
			Map<String, List<DataCount>> aggDataMap,
			String kpiGroup,
			String projectName,
			String dateLabel,
			List<LeadTimeRecord> inRange) {

		double avgHours =
				inRange.isEmpty()
						? 0d
						: inRange.stream().mapToDouble(r -> r.leadTimeHours).average().orElse(0d);
		double roundedHours = Math.round(avgHours * 100d) / 100d;

		DataCount dataCount = new DataCount();
		dataCount.setSProjectName(projectName);
		dataCount.setDate(dateLabel);
		dataCount.setKpiGroup(kpiGroup);
		dataCount.setValue(roundedHours);
		dataCount.setData(String.valueOf(roundedHours));
		double totalHours =
				Math.round(inRange.stream().mapToDouble(r -> r.leadTimeHours).sum() * 100d) / 100d;
		Map<String, Object> hover = new LinkedHashMap<>();
		hover.put("Change count", inRange.size());
		hover.put("Total Lead Time (Hrs)", totalHours);
		hover.put("Avg Lead Time (Hrs)", roundedHours);
		dataCount.setHoverValue(hover);

		aggDataMap.computeIfAbsent(kpiGroup, k -> new ArrayList<>()).add(dataCount);
	}

	/**
	 * Populates the excel export rows (one row per computed lead-time record) and attaches them to
	 * the KPI element. Columns match {@link KPIExcelColumn#LEAD_TIME_FOR_CHANGE_SLINGSHOT}:
	 *
	 * <ul>
	 *   <li>Days/Weeks
	 *   <li>Project
	 *   <li>Repo
	 *   <li>Branch (production branch)
	 *   <li>Author
	 *   <li>Merge Request Url (PR number as label, clickable link)
	 *   <li>First Commit Date (UTC)
	 *   <li>Deployment Date (UTC)
	 *   <li>Lead Time (Hrs)
	 * </ul>
	 */
	private void populateLeadTimeExcelData(
			String requestTrackerId,
			List<LeadTimeRecord> records,
			Node projectLeafNode,
			String productionBranch,
			KpiElement kpiElement) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			if (CollectionUtils.isEmpty(records)) {
				kpiElement.setExcelData(new ArrayList<>());
				return;
			}
			String projectName = projectLeafNode.getProjectFilter().getName();
			List<KPIExcelData> excelRows = new ArrayList<>();
			for (LeadTimeRecord r : records) {
				KPIExcelData row = new KPIExcelData();
				row.setDaysWeeks(formatWeekRange(r.deploymentTime));
				row.setProject(projectName);
				row.setRepositoryName(resolveRepoLabel(r.repoName));
				row.setSourceBranch(
						r.fromBranch != null && !r.fromBranch.trim().isEmpty()
								? r.fromBranch
								: productionBranch);
				row.setAuthor(r.author != null ? r.author : Constant.EMPTY_STRING);
				row.setPrTrail(r.prTrail != null ? r.prTrail : Constant.EMPTY_STRING);
				if (r.prId != null && r.prUrl != null && !r.prUrl.isEmpty()) {
					row.setFinalPullRequest(Map.of(r.prId, r.prUrl));
				}
				row.setJobPipelineName(r.jobName != null ? r.jobName : Constant.EMPTY_STRING);
				row.setDeployedEnvironment(r.envName != null ? r.envName : Constant.EMPTY_STRING);
				row.setFirstCommitDate(
						r.commitDateTime == null
								? Constant.EMPTY_STRING
								: r.commitDateTime.format(EXCEL_DATE_FORMATTER));
				row.setDeploymentOrMergedDate(r.deploymentTime.format(EXCEL_DATE_FORMATTER));
				row.setLeadTimeForChangeHrs(String.valueOf(Math.round(r.leadTimeHours * 100d) / 100d));
				excelRows.add(row);
			}
			kpiElement.setExcelData(excelRows);
		}
	}

	/**
	 * COMMIT strategy: computes lead-time records purely from merged pull requests.
	 *
	 * <p>For each PR merged into the production branch:
	 *
	 * <pre>
	 *   lead time (hours) = (pr.mergedAt − min(commitTimestamp for that PR)) / 3600
	 * </pre>
	 *
	 * <p>The record is bucketed by {@code pr.mergedAt}. No deployment data is required.
	 */
	private List<LeadTimeRecord> computeLeadTimeRecordsFromMerges(
			List<ScmMergeRequests> mergedPrs,
			List<ScmMergeRequests> allMergedPrs,
			List<ScmCommits> commits) {

		if (CollectionUtils.isEmpty(mergedPrs)) {
			return Collections.emptyList();
		}

		// Build a SHA→timestamp lookup so we can find the earliest commit in each PR.
		Map<String, Long> commitTsByShа = new HashMap<>(commits == null ? 0 : commits.size() * 2);
		if (!CollectionUtils.isEmpty(commits)) {
			for (ScmCommits c : commits) {
				if (c != null && c.getSha() != null && c.getCommitTimestamp() != null) {
					commitTsByShа.putIfAbsent(c.getSha().toLowerCase(), c.getCommitTimestamp());
				}
			}
		}

		// Build a SHA→PRs lookup across ALL merged PRs for Option-2 trail walk.
		// This map lets us follow commit SHAs back through every integration branch
		// PR to reach the original feature branch PR and its createdDate.
		Map<String, List<ScmMergeRequests>> allPrBySha = new HashMap<>();
		if (!CollectionUtils.isEmpty(allMergedPrs)) {
			for (ScmMergeRequests mr : allMergedPrs) {
				if (mr == null || CollectionUtils.isEmpty(mr.getCommitShas())) {
					continue;
				}
				for (String sha : mr.getCommitShas()) {
					if (sha != null) {
						allPrBySha.computeIfAbsent(sha.toLowerCase(), k -> new ArrayList<>()).add(mr);
					}
				}
			}
		}

		List<LeadTimeRecord> records = new ArrayList<>();
		for (ScmMergeRequests pr : mergedPrs) {
			if (pr == null || pr.getMergedAt() == null) {
				continue;
			}

			// Find the earliest commit timestamp across all SHAs in this PR.
			Long earliestCommitMillis = null;
			if (!CollectionUtils.isEmpty(pr.getCommitShas())) {
				for (String sha : pr.getCommitShas()) {
					if (sha == null) {
						continue;
					}
					Long ts = commitTsByShа.get(sha.toLowerCase());
					if (ts != null && (earliestCommitMillis == null || ts < earliestCommitMillis)) {
						earliestCommitMillis = ts;
					}
				}
			}
			// Fall back to firstCommitDate carried on the PR model itself.
			if (earliestCommitMillis == null && pr.getFirstCommitDate() != null) {
				earliestCommitMillis =
						pr.getFirstCommitDate().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			}
			// Always build the PR trail — used for the Excel column and as Option-2
			// fallback.
			TrailResult trailResult = buildTrailResult(pr, allPrBySha);

			// Option-2 fallback: when no commit timestamps exist in scm_commits, use the
			// earliest PR createdDate from the trail as a proxy for "first commit time".
			// This WARN disappears once the SCM processor is rebuilt and populates
			// scm_commits.
			if (earliestCommitMillis == null) {
				if (trailResult.earliestCreatedDate != null) {
					earliestCommitMillis = trailResult.earliestCreatedDate;
					log.warn(
							"[LTC-OPTION2-FALLBACK] PR #{} repo '{}': no commit timestamps in scm_commits — "
									+ "walked PR trail, earliest PR in chain opened at epoch-ms {}. "
									+ "This warning will stop once the SCM processor populates scm_commits.",
							pr.getExternalId(),
							pr.getRepositoryName(),
							trailResult.earliestCreatedDate);
				}
			}
			if (earliestCommitMillis == null) {
				continue;
			}

			LocalDateTime firstCommitDateTime =
					DateUtil.convertMillisToLocalDateTime(earliestCommitMillis);
			LocalDateTime mergedAt = pr.getMergedAt();
			if (firstCommitDateTime == null || mergedAt.isBefore(firstCommitDateTime)) {
				continue;
			}

			double leadTimeMinutes = KpiDataHelper.calWeekMinutes(firstCommitDateTime, mergedAt);
			if (leadTimeMinutes < 0) {
				continue;
			}
			double leadTimeHours = leadTimeMinutes / 60d;

			String repoName = pr.getRepositoryName();
			String author =
					pr.getAuthorId() != null
							? firstNonBlank(pr.getAuthorId().getDisplayName(), pr.getAuthorId().getUsername())
							: pr.getAuthorUserId();
			String prId = pr.getExternalId();
			String prUrl = pr.getMergeRequestUrl();
			String fromBranch = pr.getFromBranch();
			records.add(
					new LeadTimeRecord(
							mergedAt,
							leadTimeHours,
							repoName,
							firstCommitDateTime,
							author,
							prId,
							prUrl,
							null,
							null,
							fromBranch,
							trailResult.trailString));
		}
		return records;
	}

	/**
	 * Builds a PR trail for the DEPLOYMENT strategy by looking up all PRs that contain a specific
	 * commit SHA in {@code allMrBySha}, then sorting by {@code mergedAt} ascending.
	 *
	 * <p>This is reliable because {@code allMrBySha} is indexed by individual commit SHAs (not
	 * PR-level commitShas), the same lookup already used to find {@code originMr}. A commit that
	 * flowed feature → dev → qa → prod will appear in every integration PR's commitShas list, so the
	 * full chain surfaces naturally.
	 */
	private TrailResult buildTrailFromCommitSha(
			String sha, Map<String, List<ScmMergeRequests>> allMrBySha) {

		List<ScmMergeRequests> candidates = allMrBySha.getOrDefault(sha, Collections.emptyList());

		// Deduplicate by externalId preserving mergedAt order
		Map<String, ScmMergeRequests> seen = new LinkedHashMap<>();
		candidates.stream()
				.filter(mr -> mr != null && mr.getExternalId() != null)
				.sorted(
						Comparator.comparing(
								ScmMergeRequests::getMergedAt, Comparator.nullsLast(Comparator.naturalOrder())))
				.forEach(mr -> seen.putIfAbsent(mr.getExternalId(), mr));

		List<ScmMergeRequests> chain = new ArrayList<>(seen.values());

		Long earliestCreatedDate =
				chain.stream()
						.map(ScmMergeRequests::getCreatedDate)
						.filter(Objects::nonNull)
						.min(Long::compareTo)
						.orElse(null);

		String trailString =
				chain.stream()
						.map(
								mr -> {
									String entry = "#" + mr.getExternalId();
									String branch = mr.getFromBranch();
									return (branch != null && !branch.isEmpty())
											? entry + " (" + branch + ")"
											: entry;
								})
						.collect(Collectors.joining(" → "));

		return new TrailResult(earliestCreatedDate, trailString);
	}

	/**
	 * Walks backward through the PR integration chain using directed branch matching.
	 *
	 * <p>At each step, the current PR's {@code fromBranch} tells us which branch was the source. We
	 * find the upstream PR whose {@code toBranch} equals that value and which shares at least one
	 * commit SHA with the current PR — confirming the code actually flowed through it. This produces
	 * a clean linear chain (feature → dev → qa → prod) with no repeated branches, unlike a pure SHA
	 * BFS which finds every PR that ever touched any shared commit.
	 *
	 * <p>Returns the earliest {@code createdDate} in the chain (Option-2 fallback) and the formatted
	 * trail string.
	 */
	private TrailResult buildTrailResult(
			ScmMergeRequests productionPr, Map<String, List<ScmMergeRequests>> allPrBySha) {

		LinkedList<ScmMergeRequests> chain = new LinkedList<>();
		chain.addFirst(productionPr);

		Set<String> visitedIds = new HashSet<>();
		if (productionPr.getExternalId() != null) {
			visitedIds.add(productionPr.getExternalId());
		}

		ScmMergeRequests current = productionPr;
		int maxDepth = 20;
		while (maxDepth-- > 0
				&& current.getFromBranch() != null
				&& !CollectionUtils.isEmpty(current.getCommitShas())) {

			String targetBranch = current.getFromBranch();
			ScmMergeRequests best = null;

			for (String sha : current.getCommitShas()) {
				if (sha == null) {
					continue;
				}
				List<ScmMergeRequests> candidates = allPrBySha.get(sha.toLowerCase());
				if (CollectionUtils.isEmpty(candidates)) {
					continue;
				}
				for (ScmMergeRequests c : candidates) {
					if (c == null || c.getExternalId() == null) {
						continue;
					}
					if (visitedIds.contains(c.getExternalId())) {
						continue;
					}
					if (!targetBranch.equals(c.getToBranch())) {
						continue;
					}
					// Among multiple matches prefer the most recently merged
					if (best == null
							|| (c.getMergedAt() != null
									&& (best.getMergedAt() == null || c.getMergedAt().isAfter(best.getMergedAt())))) {
						best = c;
					}
				}
			}

			if (best == null) {
				break;
			}
			visitedIds.add(best.getExternalId());
			chain.addFirst(best);
			current = best;
		}

		Long earliestCreatedDate = chain.getFirst().getCreatedDate();

		String trailString =
				chain.stream()
						.map(
								mr -> {
									String entry = "#" + mr.getExternalId();
									String branch = mr.getFromBranch();
									return (branch != null && !branch.isEmpty())
											? entry + " (" + branch + ")"
											: entry;
								})
						.collect(Collectors.joining(" → "));

		return new TrailResult(earliestCreatedDate, trailString);
	}

	/**
	 * Computes lead-time records by iterating every commit and joining it to its containing merge
	 * request(s) and deployment(s) via SHA. Mirrors the algorithm in {@code
	 * RepoDeploymentLeadTimeStrategy} so both KPIs produce identical numbers.
	 *
	 * <p>For each commit that is present in at least one merge request and at least one deployment:
	 *
	 * <pre>
	 *   lead time (minutes) =
	 *       (commitTime           -> earliestMr.mergedAt)
	 *     + (earliestMr.mergedAt  -> earliestDeployment.startTime)
	 *     + (earliestDeployment.startTime -> maxDeployment.endTime)
	 * </pre>
	 *
	 * <p>The record is bucketed by {@code maxDeployment.endTime} (the same reference date used by
	 * {@code RepoDeploymentLeadTimeStrategy}).
	 */
	private List<LeadTimeRecord> computeLeadTimeRecords(
			List<ScmMergeRequests> mergedPrs,
			List<ScmMergeRequests> allMergedPrs,
			List<Deployment> deployments,
			List<ScmCommits> commits) {

		if (CollectionUtils.isEmpty(mergedPrs)
				|| CollectionUtils.isEmpty(deployments)
				|| CollectionUtils.isEmpty(commits)) {
			return Collections.emptyList();
		}

		Map<String, List<ScmMergeRequests>> mrBySha =
				mergedPrs.stream()
						.filter(mr -> mr != null && CollectionUtils.isNotEmpty(mr.getCommitShas()))
						.flatMap(
								mr ->
										mr.getCommitShas().stream()
												.filter(Objects::nonNull)
												.map(sha -> Map.entry(sha, mr)))
						.collect(
								Collectors.groupingBy(
										e -> e.getKey().toLowerCase(),
										Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

		// All MRs across every branch — used only to resolve the originating feature
		// branch for display. The production join above stays unchanged.
		Map<String, List<ScmMergeRequests>> allMrBySha =
				allMergedPrs.stream()
						.filter(mr -> mr != null && CollectionUtils.isNotEmpty(mr.getCommitShas()))
						.flatMap(
								mr ->
										mr.getCommitShas().stream()
												.filter(Objects::nonNull)
												.map(sha -> Map.entry(sha, mr)))
						.collect(
								Collectors.groupingBy(
										e -> e.getKey().toLowerCase(),
										Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

		Map<String, List<Deployment>> deploymentBySha =
				deployments.stream()
						.filter(d -> d != null && CollectionUtils.isNotEmpty(d.getChangeSets()))
						.flatMap(
								d ->
										d.getChangeSets().stream()
												.filter(Objects::nonNull)
												.map(sha -> Map.entry(sha, d)))
						.collect(
								Collectors.groupingBy(
										e -> e.getKey().toLowerCase(),
										Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

		// Pre-parse deployment timestamps once — the same Deployment object is
		// referenced by many
		// commits, so parsing its start/end string per commit multiplies the cost
		// unnecessarily.
		// IdentityHashMap uses reference equality, which is correct here since we hold
		// the same
		// Deployment instances throughout.
		Map<Deployment, LocalDateTime> deployStartTimes = new IdentityHashMap<>(deployments.size() * 2);
		Map<Deployment, LocalDateTime> deployEndTimes = new IdentityHashMap<>(deployments.size() * 2);
		for (Deployment d : deployments) {
			if (d == null) {
				continue;
			}
			if (d.getStartTime() != null) {
				LocalDateTime t = parseDeploymentTime(d.getStartTime());
				if (t != null) {
					deployStartTimes.put(d, t);
				}
			}
			if (d.getEndTime() != null) {
				LocalDateTime t = parseDeploymentTime(d.getEndTime());
				if (t != null) {
					deployEndTimes.put(d, t);
				}
			}
		}

		List<LeadTimeRecord> records = new ArrayList<>();
		for (ScmCommits commit : commits) {
			LeadTimeRecord leadTimeRecord =
					buildLeadTimeRecord(
							commit, mrBySha, allMrBySha, deploymentBySha, deployStartTimes, deployEndTimes);
			if (leadTimeRecord != null) {
				records.add(leadTimeRecord);
			}
		}

		// One row per (MR, deployment): keep the record with the earliest commit date
		// so
		// lead time is measured from the first commit in the PR, not a later one.
		Map<String, LeadTimeRecord> deduped = new LinkedHashMap<>();
		for (LeadTimeRecord r : records) {
			String key = r.prUrl + "|" + r.deploymentTime;
			deduped.merge(
					key,
					r,
					(existing, incoming) ->
							incoming.commitDateTime != null
											&& (existing.commitDateTime == null
													|| incoming.commitDateTime.isBefore(existing.commitDateTime))
									? incoming
									: existing);
		}
		return new ArrayList<>(deduped.values());
	}

	/**
	 * Builds a single lead-time record for one commit or returns {@code null} when not applicable.
	 */
	private LeadTimeRecord buildLeadTimeRecord(
			ScmCommits commit,
			Map<String, List<ScmMergeRequests>> mrBySha,
			Map<String, List<ScmMergeRequests>> allMrBySha,
			Map<String, List<Deployment>> deploymentBySha,
			Map<Deployment, LocalDateTime> deployStartTimes,
			Map<Deployment, LocalDateTime> deployEndTimes) {
		if (commit == null || commit.getSha() == null || commit.getCommitTimestamp() == null) {
			return null;
		}
		String sha = commit.getSha().toLowerCase();
		List<ScmMergeRequests> mrs = mrBySha.getOrDefault(sha, Collections.emptyList());
		List<Deployment> matchingDeployments =
				deploymentBySha.getOrDefault(sha, Collections.emptyList());
		if (mrs.isEmpty() || matchingDeployments.isEmpty()) {
			return null;
		}

		ScmMergeRequests earliestMr =
				mrs.stream()
						.filter(mr -> mr.getMergedAt() != null)
						.min(Comparator.comparing(ScmMergeRequests::getMergedAt))
						.orElse(null);

		// For branch display: find the earliest MR across ALL branches so we show the
		// originating feature branch rather than the release/sync branch.
		ScmMergeRequests originMr =
				allMrBySha.getOrDefault(sha, Collections.emptyList()).stream()
						.filter(mr -> mr.getMergedAt() != null && mr.getFromBranch() != null)
						.min(Comparator.comparing(ScmMergeRequests::getMergedAt))
						.orElse(null);
		Deployment earliestDeployment =
				matchingDeployments.stream()
						.filter(d -> d.getStartTime() != null && !d.getStartTime().isEmpty())
						.min(Comparator.comparing(Deployment::getStartTime))
						.orElse(null);
		if (earliestMr == null || earliestDeployment == null) {
			return null;
		}

		LocalDateTime commitDateTime =
				DateUtil.convertMillisToLocalDateTime(commit.getCommitTimestamp());
		LocalDateTime mergeDateTime = earliestMr.getMergedAt();
		LocalDateTime deployStartDateTime = deployStartTimes.get(earliestDeployment);
		if (commitDateTime == null || mergeDateTime == null || deployStartDateTime == null) {
			return null;
		}

		LocalDateTime lastDeployEndTime =
				matchingDeployments.stream()
						.map(deployEndTimes::get)
						.filter(Objects::nonNull)
						.max(Comparator.naturalOrder())
						.orElse(deployStartDateTime);

		double commitToDeploy = KpiDataHelper.calWeekMinutes(commitDateTime, deployStartDateTime);
		double totalDeployDuration =
				KpiDataHelper.calWeekMinutes(deployStartDateTime, lastDeployEndTime);
		double totalLeadTimeMinutes = commitToDeploy + totalDeployDuration;
		if (totalLeadTimeMinutes < 0) {
			return null;
		}
		double leadTimeHours = totalLeadTimeMinutes / 60d;

		String repoName =
				firstNonBlank(
						firstNonBlank(commit.getRepositoryName(), earliestMr.getRepositoryName()),
						getRepoNameFromUrl(earliestDeployment.getRepoUrl()));
		String author = firstNonBlank(commit.getAuthorName(), commit.getAuthor());
		String prId = earliestMr.getExternalId();
		String prUrl = earliestMr.getMergeRequestUrl();
		String jobName = earliestDeployment.getJobName();
		String envName = earliestDeployment.getEnvName();
		String fromBranch = originMr != null ? originMr.getFromBranch() : earliestMr.getFromBranch();
		// Use the individual commit SHA — same lookup that correctly finds originMr —
		// to collect every PR this commit flowed through, then sort by mergedAt for the
		// trail.
		TrailResult trailResult = buildTrailFromCommitSha(sha, allMrBySha);
		return new LeadTimeRecord(
				lastDeployEndTime,
				leadTimeHours,
				repoName,
				commitDateTime,
				author,
				prId,
				prUrl,
				jobName,
				envName,
				fromBranch,
				trailResult.trailString);
	}

	private static String firstNonBlank(String a, String b) {
		if (a != null && !a.trim().isEmpty()) {
			return a.trim();
		}
		return (b != null && !b.trim().isEmpty()) ? b.trim() : null;
	}

	/** Returns the display label for a repository, falling back to a fixed bucket when unknown. */
	private static String resolveRepoLabel(String repoName) {
		return (repoName == null || repoName.trim().isEmpty()) ? "Unknown Repository" : repoName;
	}

	/**
	 * Collects every distinct repository label seen in the project's deployments, merge requests and
	 * computed lead-time records. Used to seed the filter dropdown even when zero lead-time records
	 * were produced.
	 */
	private Set<String> collectRepoLabels(
			List<ScmMergeRequests> mergedPrs,
			List<Deployment> deployments,
			List<LeadTimeRecord> records) {
		Set<String> labels = new TreeSet<>();
		for (Deployment d : deployments) {
			String label = getRepoNameFromUrl(d.getRepoUrl());
			if (label != null && !label.trim().isEmpty()) {
				labels.add(label.trim());
			}
		}
		for (ScmMergeRequests pr : mergedPrs) {
			String label = pr.getRepositoryName();
			if (label != null && !label.trim().isEmpty()) {
				labels.add(label.trim());
			}
		}
		for (LeadTimeRecord r : records) {
			labels.add(resolveRepoLabel(r.repoName));
		}
		return labels;
	}

	/**
	 * Formats the week containing {@code dt} as "dd-MMM-yyyy to dd-MMM-yyyy" (e.g. "20-Apr-2026 to
	 * 26-Apr-2026").
	 */
	private static final DateTimeFormatter WEEK_RANGE_FORMATTER =
			DateTimeFormatter.ofPattern("dd-MMM-yyyy");

	private static String formatWeekRange(LocalDateTime dt) {
		LocalDate monday = dt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate sunday = dt.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
		return monday.format(WEEK_RANGE_FORMATTER) + " to " + sunday.format(WEEK_RANGE_FORMATTER);
	}

	private LocalDateTime parseDeploymentTime(String startTime) {
		if (startTime == null || startTime.isEmpty()) {
			return null;
		}
		try {
			return LocalDateTime.parse(startTime, DEPLOYMENT_TS_FORMATTER);
		} catch (Exception e) {
			try {
				return LocalDateTime.parse(startTime);
			} catch (Exception ex) {
				log.warn("Unable to parse deployment timestamp '{}'", startTime);
				return null;
			}
		}
	}

	private boolean isWithin(LocalDate date, CustomDateRange range) {
		if (date == null || range.getStartDate() == null || range.getEndDate() == null) {
			return false;
		}
		LocalDate start = range.getStartDate();
		LocalDate end = range.getEndDate();
		return (date.isEqual(start) || date.isAfter(start))
				&& (date.isEqual(end) || date.isBefore(end));
	}

	private String getRepoNameFromUrl(String repoUrl) {
		if (repoUrl == null || repoUrl.isEmpty()) {
			return null;
		}
		return repoUrl.substring(repoUrl.lastIndexOf('/') + 1).replace(".git", "");
	}

	private String normalizeRepo(String repo) {
		return repo == null ? null : repo.trim().toLowerCase();
	}

	/** Internal container: one deployed change with its computed lead time in hours and repo. */
	private static final class LeadTimeRecord {
		private final LocalDateTime deploymentTime;
		private final double leadTimeHours;
		private final String repoName;
		private final LocalDateTime commitDateTime;
		private final String author;
		private final String prId;
		private final String prUrl;
		private final String jobName;
		private final String envName;
		private final String fromBranch;
		private final String prTrail;

		LeadTimeRecord(
				LocalDateTime deploymentTime,
				double leadTimeHours,
				String repoName,
				LocalDateTime commitDateTime,
				String author,
				String prId,
				String prUrl,
				String jobName,
				String envName,
				String fromBranch,
				String prTrail) {
			this.deploymentTime = deploymentTime;
			this.leadTimeHours = leadTimeHours;
			this.repoName = repoName;
			this.commitDateTime = commitDateTime;
			this.author = author;
			this.prId = prId;
			this.prUrl = prUrl;
			this.jobName = jobName;
			this.envName = envName;
			this.fromBranch = fromBranch;
			this.prTrail = prTrail;
		}
	}

	/** Holds the result of a PR-trail walk: the earliest createdDate and the formatted trail. */
	private static final class TrailResult {
		final Long earliestCreatedDate;
		final String trailString;

		TrailResult(Long earliestCreatedDate, String trailString) {
			this.earliestCreatedDate = earliestCreatedDate;
			this.trailString = trailString;
		}
	}
}
