package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsKPIService;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.DeploymentFrequencySlingshotInfo;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.DeploymentStatus;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.Deployment;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.repository.application.DeploymentRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deployment Frequency KPI for the Slingshot Speed tab. Measures how often code is successfully
 * deployed to production.
 *
 * <p>This is a standalone copy of the DORA Deployment Frequency KPI kept intentionally independent
 * so that future changes to either KPI do not impact the other.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentFrequencySlingshotServiceImpl
		extends JenkinsKPIService<Long, Long, Map<ObjectId, List<Deployment>>> {

	private static final String DEPLOYMENT_STATUS_FILTER = "deploymentStatus";

	private final DeploymentRepository deploymentRepository;

	/** {@inheritDoc} */
	@Override
	public Long calculateKPIMetrics(Map<ObjectId, List<Deployment>> objectIdListMap) {
		return 0L;
	}

	/** {@inheritDoc} */
	@Override
	public String getQualifierType() {
		return KPICode.DEPLOYMENT_FREQUENCY_SLINGSHOT.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {

		Node root = treeAggregatorDetail.getRoot();
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();

		List<Node> projectList =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT);
		calculateProjectWiseLeafNodeValue(mapTmp, projectList, kpiElement);

		log.debug(
				"[DEPLOYMENT-FREQUENCY-SLINGSHOT-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(root, nodeWiseKPIValue, KPICode.DEPLOYMENT_FREQUENCY_SLINGSHOT);
		kpiElement.setNodeWiseKPIValue(nodeWiseKPIValue);
		Map<String, List<DataCount>> trendValuesMap =
				getAggregateTrendValuesMap(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.DEPLOYMENT_FREQUENCY_SLINGSHOT);

		kpiElement.setTrendValueList(buildEnvironmentWiseDataCountGroups(trendValuesMap));
		log.debug(
				"[DEPLOYMENT-FREQUENCY-SLINGSHOT-LEAF-AGGREGATED-VALUE][{}]. Aggregated Value at each level in the tree {}",
				kpiRequest.getRequestTrackerId(),
				root);
		return kpiElement;
	}

	private List<DataCountGroup> buildEnvironmentWiseDataCountGroups(
			Map<String, List<DataCount>> trendValuesMap) {
		Map<String, Map<String, List<DataCount>>> envNameProjectWiseDc = new LinkedHashMap<>();
		trendValuesMap.forEach(
				(envName, dataCounts) -> {
					Map<String, List<DataCount>> projectWiseDc =
							dataCounts.stream().collect(Collectors.groupingBy(DataCount::getData));
					envNameProjectWiseDc.put(envName, projectWiseDc);
				});

		List<DataCountGroup> dataCountGroups = new ArrayList<>();
		envNameProjectWiseDc.forEach(
				(envName, projectWiseDc) -> {
					DataCountGroup dataCountGroup = new DataCountGroup();
					List<DataCount> dataList = new ArrayList<>();
					projectWiseDc.forEach((project, dataCounts) -> dataList.addAll(dataCounts));
					dataCountGroup.setFilter(envName);
					dataCountGroup.setValue(dataList);
					dataCountGroups.add(dataCountGroup);
				});
		return dataCountGroups;
	}

	/**
	 * Calculates and sets the deployment frequency value per project leaf node.
	 *
	 * @param mapTmp node map
	 * @param projectLeafNodeList list of project leaf nodes
	 * @param kpiElement kpi element
	 */
	private void calculateProjectWiseLeafNodeValue(
			Map<String, Node> mapTmp, List<Node> projectLeafNodeList, KpiElement kpiElement) {

		String requestTrackerId = getRequestTrackerId();
		LocalDateTime localEndDate = DateUtil.getTodayTime();
		LocalDateTime localStartDate = localEndDate.minusWeeks(12);
		Map<String, Object> durationFilter = new HashMap<>();
		durationFilter.put(Constant.DATE, localStartDate);
		durationFilter.put(Constant.DURATION, CommonConstant.WEEK);
		durationFilter.put(Constant.COUNT, 12);
		DateTimeFormatter formatterMonth = DateTimeFormatter.ofPattern(DateUtil.TIME_FORMAT);
		String startDate = localStartDate.format(formatterMonth);
		String endDate = localEndDate.format(formatterMonth);
		List<KPIExcelData> excelData = new ArrayList<>();
		Map<ObjectId, List<Deployment>> deploymentGroup =
				fetchKPIDataFromDb(projectLeafNodeList, startDate, endDate, null);

		if (MapUtils.isEmpty(deploymentGroup)) {
			return;
		}

		DeploymentFrequencySlingshotInfo deploymentFrequencySlingshotInfo =
				new DeploymentFrequencySlingshotInfo();
		projectLeafNodeList.forEach(
				node ->
						populateNodeValue(
								mapTmp,
								kpiElement,
								node,
								deploymentGroup,
								deploymentFrequencySlingshotInfo,
								durationFilter,
								excelData,
								requestTrackerId));
		DateTimeFormatter displayFmt =
				DateTimeFormatter.ofPattern(DateUtil.DISPLAY_DATE_FORMAT, Locale.ENGLISH);
		DateTimeFormatter displayDateTimeFmt =
				DateTimeFormatter.ofPattern(DateUtil.DISPLAY_DATE_TIME_FORMAT, Locale.ENGLISH);
		excelData.sort(
				Comparator.comparing(
								(KPIExcelData d) -> {
									try {
										String raw = d.getDaysWeeks();
										String weekStart =
												raw != null && raw.contains(" to ") ? raw.split(" to ")[0].trim() : "";
										return LocalDate.parse(weekStart, displayFmt);
									} catch (Exception e) {
										return LocalDate.MIN;
									}
								})
						.thenComparing(
								d -> {
									try {
										return LocalDateTime.parse(d.getDeploymentDate(), displayDateTimeFmt);
									} catch (Exception e) {
										return LocalDateTime.MIN;
									}
								}));
		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.DEPLOYMENT_FREQUENCY_SLINGSHOT.getColumns());
	}

	private void populateNodeValue(
			Map<String, Node> mapTmp,
			KpiElement kpiElement,
			Node node,
			Map<ObjectId, List<Deployment>> deploymentGroup,
			DeploymentFrequencySlingshotInfo deploymentFrequencySlingshotInfo,
			Map<String, Object> durationFilter,
			List<KPIExcelData> excelData,
			String requestTrackerId) {
		Map<String, List<DataCount>> trendValueMap = new HashMap<>();
		List<DataCount> dataCountAggList = new ArrayList<>();
		String trendLineName = node.getProjectFilter().getName();
		ObjectId basicProjectConfigId = node.getProjectFilter().getBasicProjectConfigId();
		String projectName = node.getProjectFilter().getName();
		List<Deployment> deploymentListProjectWise = deploymentGroup.get(basicProjectConfigId);

		if (CollectionUtils.isNotEmpty(deploymentListProjectWise)) {
			prepareDeploymentTrendForProject(
					deploymentListProjectWise,
					dataCountAggList,
					trendValueMap,
					trendLineName,
					deploymentFrequencySlingshotInfo,
					durationFilter);
		}
		if (CollectionUtils.isEmpty(dataCountAggList)) {
			mapTmp.get(node.getId()).setValue(null);
			return;
		}
		List<DataCount> aggData =
				calculateAggregatedWeeksWise(
						KPICode.DEPLOYMENT_FREQUENCY_SLINGSHOT.getKpiId(), dataCountAggList);
		if (CollectionUtils.isNotEmpty(aggData)) {
			trendValueMap.put(CommonConstant.OVERALL, aggData);
		}
		mapTmp.get(node.getId()).setValue(trendValueMap);

		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			Map<String, String> deploymentMapPipelineNameWise =
					deploymentListProjectWise.stream()
							.filter(
									d ->
											StringUtils.isNotEmpty(d.getJobName())
													&& StringUtils.isNotEmpty(d.getPipelineName()))
							.collect(
									Collectors.toMap(
											Deployment::getJobName,
											Deployment::getPipelineName,
											(e1, e2) -> e1,
											LinkedHashMap::new));
			KPIExcelUtility.populateDeploymentFrequencySlingshotExcelData(
					excelData, projectName, deploymentFrequencySlingshotInfo, deploymentMapPipelineNameWise);
		}
	}

	/** {@inheritDoc} */
	@Override
	public Map<ObjectId, List<Deployment>> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {

		Map<String, List<String>> mapOfFilters = new HashMap<>();
		List<String> statusList = new ArrayList<>();
		Set<ObjectId> projectBasicConfigIds = new HashSet<>();
		leafNodeList.forEach(
				node -> {
					ObjectId basicProjectConfigId = node.getProjectFilter().getBasicProjectConfigId();
					projectBasicConfigIds.add(basicProjectConfigId);
				});
		statusList.add(DeploymentStatus.SUCCESS.name());
		mapOfFilters.put(DEPLOYMENT_STATUS_FILTER, statusList);
		List<Deployment> deploymentList =
				deploymentRepository.findDeploymentList(
						mapOfFilters, projectBasicConfigIds, startDate, endDate);
		return deploymentList.stream()
				.collect(Collectors.groupingBy(Deployment::getBasicProjectConfigId, Collectors.toList()));
	}

	/**
	 * Groups deployments by environment and time bucket, building the trend data for a single
	 * project.
	 *
	 * @param deploymentListProjectWise deployments for the project
	 * @param aggDataCountList aggregated data count list to populate
	 * @param trendValueMap environment-wise trend value map to populate
	 * @param trendLineName project trend line name
	 * @param deploymentFrequencySlingshotInfo excel info bean to populate
	 * @param durationFilter duration filter (count)
	 */
	private void prepareDeploymentTrendForProject(
			List<Deployment> deploymentListProjectWise,
			List<DataCount> aggDataCountList,
			Map<String, List<DataCount>> trendValueMap,
			String trendLineName,
			DeploymentFrequencySlingshotInfo deploymentFrequencySlingshotInfo,
			Map<String, Object> durationFilter) {
		int previousTimeCount = (int) durationFilter.getOrDefault(Constant.COUNT, 12);
		Map<String, List<Deployment>> deploymentMapEnvWise =
				deploymentListProjectWise.stream()
						.collect(Collectors.groupingBy(Deployment::getEnvName, Collectors.toList()));

		deploymentMapEnvWise.forEach(
				(envName, deploymentListEnvWise) -> {
					if (StringUtils.isNotEmpty(envName)
							&& CollectionUtils.isNotEmpty(deploymentListEnvWise)) {

						Map<String, List<Deployment>> deploymentMapTimeWise =
								buildLastNWeekBuckets(previousTimeCount);
						List<DataCount> dataCountList = new ArrayList<>();

						for (Deployment deployment : deploymentListEnvWise) {
							DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DateUtil.TIME_FORMAT);
							LocalDateTime dateValue = LocalDateTime.parse(deployment.getStartTime(), formatter);
							CustomDateRange deploymentPeriod =
									KpiDataHelper.getStartAndEndDateTimeForDataFiltering(
											dateValue, CommonConstant.WEEK);
							String timeValue =
									KpiHelperService.getDateRange(deploymentPeriod, CommonConstant.WEEK);

							deploymentMapTimeWise.computeIfPresent(
									timeValue,
									(key, deploymentListCurrentTime) -> {
										deploymentListCurrentTime.add(deployment);
										return deploymentListCurrentTime;
									});
						}

						deploymentMapTimeWise.forEach(
								(time, deploymentListCurrentTime) -> {
									DataCount dataCount =
											createDataCount(trendLineName, envName, time, deploymentListCurrentTime);
									dataCountList.add(dataCount);
									populateExcelInfo(deploymentFrequencySlingshotInfo, deploymentListCurrentTime);
								});

						aggDataCountList.addAll(dataCountList);
						addToTrendValueMap(trendValueMap, envName, deploymentListEnvWise, dataCountList);
					}
				});
	}

	/**
	 * @param trendValueMap trendValueMap
	 * @param envName envName
	 * @param deploymentListEnvWise deploymentListEnvWise
	 * @param dataCountList dataCountList
	 */
	private static void addToTrendValueMap(
			Map<String, List<DataCount>> trendValueMap,
			String envName,
			List<Deployment> deploymentListEnvWise,
			List<DataCount> dataCountList) {
		if (StringUtils.isNotEmpty(deploymentListEnvWise.get(0).getEnvName())) {
			trendValueMap.putIfAbsent(deploymentListEnvWise.get(0).getEnvName(), new ArrayList<>());
			trendValueMap.get(deploymentListEnvWise.get(0).getEnvName()).addAll(dataCountList);
		} else {
			trendValueMap.putIfAbsent(envName, new ArrayList<>());
			trendValueMap.get(envName).addAll(dataCountList);
		}
	}

	/**
	 * Creates a data count for a project/environment/time bucket combination.
	 *
	 * @param trendLineName trend line (project) name
	 * @param envName environment name
	 * @param time time bucket label
	 * @param deploymentListCurrentTime deployments falling in the time bucket
	 * @return the populated data count
	 */
	private DataCount createDataCount(
			String trendLineName,
			String envName,
			String time,
			List<Deployment> deploymentListCurrentTime) {
		Long envCount = (long) deploymentListCurrentTime.size();
		DataCount dataCount = new DataCount();
		dataCount.setData(String.valueOf(envCount));
		dataCount.setSProjectName(trendLineName);
		dataCount.setSSprintID(time);
		dataCount.setSSprintName(time);
		dataCount.setSprintIds(Arrays.asList(time));
		dataCount.setSprintNames(Arrays.asList(time));
		dataCount.setDate(time);
		dataCount.setKpiGroup(envName);
		dataCount.setValue(envCount);
		Map<String, Object> hoverMap = new HashMap<>();
		hoverMap.put(envName, envCount.intValue());
		dataCount.setHoverValue(hoverMap);
		return dataCount;
	}

	/**
	 * Populates the excel info bean with the deployments falling in a single time bucket.
	 *
	 * @param deploymentFrequencySlingshotInfo excel info bean
	 * @param deploymentListCurrentTime deployments falling in the time bucket
	 */
	private void populateExcelInfo(
			DeploymentFrequencySlingshotInfo deploymentFrequencySlingshotInfo,
			List<Deployment> deploymentListCurrentTime) {
		if (deploymentFrequencySlingshotInfo != null
				&& CollectionUtils.isNotEmpty(deploymentListCurrentTime)) {
			DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(DateUtil.TIME_FORMAT);
			DateTimeFormatter displayFormatter =
					DateTimeFormatter.ofPattern(DateUtil.DISPLAY_DATE_TIME_FORMAT);
			deploymentListCurrentTime.forEach(
					deployment -> {
						deploymentFrequencySlingshotInfo.addEnvironment(deployment.getEnvName());
						if (StringUtils.isNotEmpty(deployment.getJobFolderName())) {
							deploymentFrequencySlingshotInfo.addJobName(deployment.getJobFolderName());
						} else {
							deploymentFrequencySlingshotInfo.addJobName(deployment.getJobName());
						}
						LocalDateTime deploymentDateTime =
								LocalDateTime.parse(deployment.getStartTime(), inputFormatter);
						deploymentFrequencySlingshotInfo.addDeploymentDate(
								deploymentDateTime.format(displayFormatter));
						String endDateFormatted = "";
						if (StringUtils.isNotEmpty(deployment.getEndTime())) {
							try {
								endDateFormatted =
										LocalDateTime.parse(deployment.getEndTime(), inputFormatter)
												.format(displayFormatter);
							} catch (Exception ignored) {
							}
						}
						deploymentFrequencySlingshotInfo.addDeploymentEndDate(endDateFormatted);
						String repoUrl = deployment.getRepoUrl();
						String repoName =
								repoUrl != null && !repoUrl.isEmpty()
										? repoUrl.substring(repoUrl.lastIndexOf('/') + 1).replace(".git", "")
										: "";
						deploymentFrequencySlingshotInfo.addRepoName(repoName);
						deploymentFrequencySlingshotInfo.addDeploymentStatus(
								deployment.getDeploymentStatus() != null
										? deployment.getDeploymentStatus().name()
										: "");
						deploymentFrequencySlingshotInfo.addWeeks(
								KpiHelperService.getDateRange(
										KpiDataHelper.getStartAndEndDateTimeForDataFiltering(
												deploymentDateTime, CommonConstant.WEEK),
										CommonConstant.WEEK));
					});
		}
	}

	/**
	 * Builds an ordered map of the last N weeks (as empty buckets) keyed by the week range label,
	 * ordered oldest to newest so chart and excel data render left-to-right chronologically.
	 *
	 * @param count number of weeks
	 * @return ordered map of week buckets (oldest first)
	 */
	private Map<String, List<Deployment>> buildLastNWeekBuckets(int count) {
		List<String> weekLabels = new ArrayList<>();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		for (int i = 0; i < count; i++) {
			CustomDateRange periodRange =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, CommonConstant.WEEK);
			weekLabels.add(KpiHelperService.getDateRange(periodRange, CommonConstant.WEEK));
			currentDate = currentDate.minusWeeks(1);
		}
		Collections.reverse(weekLabels);
		Map<String, List<Deployment>> lastNWeek = new LinkedHashMap<>();
		for (String label : weekLabels) {
			lastNWeek.put(label, new ArrayList<>());
		}
		return lastNWeek;
	}

	public List<DataCount> calculateAggregatedWeeksWise(
			String kpiId, List<DataCount> jobsAggregatedValueList) {

		Map<String, List<DataCount>> weeksWiseDataCount =
				jobsAggregatedValueList.stream()
						.collect(
								Collectors.groupingBy(DataCount::getDate, LinkedHashMap::new, Collectors.toList()));

		List<DataCount> aggregatedDataCount = new ArrayList<>();
		weeksWiseDataCount.forEach(
				(date, data) -> {
					Set<String> projectNames = new HashSet<>();
					DataCount dataCount = new DataCount();
					List<Long> values = new ArrayList<>();
					Map<String, Object> hoverMap = new HashMap<>();
					for (DataCount dc : data) {
						projectNames.add(dc.getSProjectName());
						Object obj = dc.getValue();
						String keyName = dc.getKpiGroup();
						Long value = obj instanceof Long ? ((Long) obj) : 0L;
						values.add(value);
						hoverMap.put(keyName, value.intValue());
					}
					Long aggregatedValue = calculateKpiValue(values, kpiId);
					dataCount.setProjectNames(new ArrayList<>(projectNames));
					dataCount.setSSprintID(date);
					dataCount.setSSprintName(date);
					dataCount.setSprintIds(Arrays.asList(date));
					dataCount.setSprintNames(Arrays.asList(date));
					dataCount.setSProjectName(projectNames.stream().collect(Collectors.joining(" ")));
					dataCount.setValue(aggregatedValue);
					dataCount.setData(aggregatedValue.toString());
					dataCount.setDate(date);
					dataCount.setHoverValue(hoverMap);
					aggregatedDataCount.add(dataCount);
				});
		return aggregatedDataCount;
	}

	/** {@inheritDoc} */
	@Override
	public Long calculateKpiValue(List<Long> valueList, String kpiId) {
		return calculateKpiValueForLong(valueList, kpiId);
	}

	/** {@inheritDoc} */
	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI213(), KPICode.DEPLOYMENT_FREQUENCY_SLINGSHOT.getKpiId());
	}
}
