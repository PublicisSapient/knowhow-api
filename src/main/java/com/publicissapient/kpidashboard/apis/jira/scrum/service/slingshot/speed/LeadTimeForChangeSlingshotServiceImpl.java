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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
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
	private static final String DEPLOYMENTS = "deployments";
	private static final String COMMITS = "commits";
	private static final String PRODUCTION_BRANCH = "productionBranch";
	private static final String DEFAULT_PRODUCTION_BRANCH = "master";
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

		List<ScmMergeRequests> mergedPrs =
				scmKpiHelperService.getMergedRequests(basicProjectConfigId, dateRange).stream()
						.filter(mr -> mr != null && mr.getToBranch() != null)
						.filter(mr -> productionBranch.equalsIgnoreCase(mr.getToBranch().trim()))
						.toList();

		List<Deployment> deployments =
				deploymentRepository.findDeploymentList(
						new HashMap<>(), Collections.singleton(basicProjectConfigId), startDate, endDate);

		// For tools like ArgoCD / GitHubAction, each deployment record carries only the
		// HEAD commit
		// SHA (not the full list of commits included in the release). Expand the
		// changeSets for those
		// deployments by pulling in every commit that landed on the deployed repo
		// between the previous
		// deployment's head commit and this deployment's start time - matching the
		// logic used by the
		// legacy Jira-based Lead Time For Change KPI.
		List<ScmCommits> commits =
				scmKpiHelperService.getCommitDetails(basicProjectConfigId, dateRange);
		enrichHeadOnlyDeployments(deployments, commits);

		resultMap.put(MERGED_PRS, mergedPrs);
		resultMap.put(DEPLOYMENTS, deployments);
		resultMap.put(COMMITS, commits);
		resultMap.put(PRODUCTION_BRANCH, productionBranch);
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

		AtomicReference<LocalDateTime> previousDeploymentTime = new AtomicReference<>();
		for (Deployment deployment : deployments) {
			LocalDateTime deployStartDateTime = parseDeploymentTime(deployment.getStartTime());
			if (deployStartDateTime == null) {
				continue;
			}

			if (previousDeploymentTime.get() != null) {
				String deployRepo = normalizeRepo(getRepoNameFromUrl(deployment.getRepoUrl()));
				LocalDateTime prev = previousDeploymentTime.get();
				List<String> changeSetShas =
						commits.stream()
								.filter(Objects::nonNull)
								.filter(c -> c.getSha() != null && c.getCommitTimestamp() != null)
								.filter(
										c -> {
											String commitRepo = normalizeRepo(c.getRepositoryName());
											return deployRepo == null
													|| commitRepo == null
													|| deployRepo.equals(commitRepo);
										})
								.filter(
										c -> {
											LocalDateTime ts =
													DateUtil.convertMillisToLocalDateTime(c.getCommitTimestamp());
											return ts != null && ts.isAfter(prev) && ts.isBefore(deployStartDateTime);
										})
								.map(ScmCommits::getSha)
								.toList();
				deployment.setChangeSets(changeSetShas);
			}

			// Advance the "previous deployment head" pointer to this deployment's head
			// commit time so
			// the next iteration expands its changeSets correctly.
			List<String> currentChangeSets = deployment.getChangeSets();
			if (CollectionUtils.isNotEmpty(currentChangeSets)) {
				String headSha = currentChangeSets.get(0);
				Optional<ScmCommits> headCommit =
						commits.stream()
								.filter(c -> c != null && headSha.equalsIgnoreCase(c.getSha()))
								.findFirst();
				headCommit.ifPresent(
						c ->
								previousDeploymentTime.set(
										DateUtil.convertMillisToLocalDateTime(c.getCommitTimestamp())));
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
			range.setStartDate(LocalDate.now().minusWeeks(weeks));
			range.setEndDate(LocalDate.now().plusDays(1));
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

		String startDate = LocalDate.now().minusWeeks(dataPoints).toString();
		String endDate = LocalDate.now().plusDays(1).toString();

		Map<String, Object> scmDataMap =
				fetchKPIDataFromDb(List.of(projectLeafNode), startDate, endDate, kpiRequest);

		List<ScmMergeRequests> mergedPrs =
				(List<ScmMergeRequests>) scmDataMap.getOrDefault(MERGED_PRS, Collections.emptyList());
		List<Deployment> deployments =
				(List<Deployment>) scmDataMap.getOrDefault(DEPLOYMENTS, Collections.emptyList());
		List<ScmCommits> commits =
				(List<ScmCommits>) scmDataMap.getOrDefault(COMMITS, Collections.emptyList());

		if (CollectionUtils.isEmpty(mergedPrs) || CollectionUtils.isEmpty(deployments)) {
			log.info(
					"[LEAD-TIME-FOR-CHANGE-SLINGSHOT] Missing SCM/deployment data for project {} "
							+ "(prs={}, deployments={})",
					projectLeafNode.getProjectFilter(),
					mergedPrs.size(),
					deployments.size());
			mapTmp.get(projectLeafNode.getId()).setValue(new LinkedHashMap<String, List<DataCount>>());
			return;
		}

		List<LeadTimeRecord> records = computeLeadTimeRecords(mergedPrs, deployments, commits);

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
		Map<String, Object> hover = new HashMap<>();
		hover.put("Change count", inRange.size());
		hover.put("Avg Lead Time (hrs)", roundedHours);
		dataCount.setHoverValue(hover);

		aggDataMap.computeIfAbsent(kpiGroup, k -> new ArrayList<>()).add(dataCount);
	}

	/**
	 * Populates the excel export rows (one row per computed lead-time record) and attaches them to
	 * the KPI element. Columns match {@link KPIExcelColumn#LEAD_TIME_FOR_CHANGE_SLINGSHOT}:
	 *
	 * <ul>
	 *   <li>Weeks
	 *   <li>Project Name
	 *   <li>Merge Request Id
	 *   <li>Production Branch
	 *   <li>First Commit Date
	 *   <li>Deployment Date
	 *   <li>Lead Time (hrs)
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
				row.setProject(projectName);
				row.setWeeks(DateUtil.getWeekRangeUsingDateTime(r.deploymentTime));
				row.setBranch(productionBranch);
				row.setFirstCommitDate(
						r.commitDateTime == null
								? Constant.EMPTY_STRING
								: r.commitDateTime.format(EXCEL_DATE_FORMATTER));
				row.setDeploymentDate(r.deploymentTime.format(EXCEL_DATE_FORMATTER));
				row.setLeadTimeForChangeHrs(String.valueOf(Math.round(r.leadTimeHours * 100d) / 100d));
				excelRows.add(row);
			}
			kpiElement.setExcelData(excelRows);
		}
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
			List<ScmMergeRequests> mergedPrs, List<Deployment> deployments, List<ScmCommits> commits) {

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

		List<LeadTimeRecord> records = new ArrayList<>();
		for (ScmCommits commit : commits) {
			LeadTimeRecord record = buildLeadTimeRecord(commit, mrBySha, deploymentBySha);
			if (record != null) {
				records.add(record);
			}
		}
		return records;
	}

	/**
	 * Builds a single lead-time record for one commit or returns {@code null} when not applicable.
	 */
	private LeadTimeRecord buildLeadTimeRecord(
			ScmCommits commit,
			Map<String, List<ScmMergeRequests>> mrBySha,
			Map<String, List<Deployment>> deploymentBySha) {
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
		LocalDateTime deployStartDateTime = parseDeploymentTime(earliestDeployment.getStartTime());
		if (commitDateTime == null || mergeDateTime == null || deployStartDateTime == null) {
			return null;
		}

		LocalDateTime lastDeployEndTime =
				matchingDeployments.stream()
						.map(d -> parseDeploymentTime(d.getEndTime()))
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
						getRepoNameFromUrl(earliestDeployment.getRepoUrl()),
						firstNonBlank(commit.getRepositoryName(), earliestMr.getRepositoryName()));
		return new LeadTimeRecord(
				lastDeployEndTime, leadTimeHours, repoName, earliestMr.getExternalId(), commitDateTime);
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
		private final String mergeRequestId;
		private final LocalDateTime commitDateTime;

		LeadTimeRecord(
				LocalDateTime deploymentTime,
				double leadTimeHours,
				String repoName,
				String mergeRequestId,
				LocalDateTime commitDateTime) {
			this.deploymentTime = deploymentTime;
			this.leadTimeHours = leadTimeHours;
			this.repoName = repoName;
			this.mergeRequestId = mergeRequestId;
			this.commitDateTime = commitDateTime;
		}
	}
}
