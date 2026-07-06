package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsKPIService;
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
		Map<String, Object> durationFilter = KpiDataHelper.getDurationFilter(kpiElement);
		LocalDateTime localStartDate = (LocalDateTime) durationFilter.get(Constant.DATE);
		LocalDateTime localEndDate = DateUtil.getTodayTime();
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
	 * @param durationFilter duration filter (week/month + count)
	 */
	private void prepareDeploymentTrendForProject(
			List<Deployment> deploymentListProjectWise,
			List<DataCount> aggDataCountList,
			Map<String, List<DataCount>> trendValueMap,
			String trendLineName,
			DeploymentFrequencySlingshotInfo deploymentFrequencySlingshotInfo,
			Map<String, Object> durationFilter) {
		String duration = (String) durationFilter.getOrDefault(Constant.DURATION, CommonConstant.WEEK);
		int previousTimeCount = (int) durationFilter.getOrDefault(Constant.COUNT, 5);
		Map<String, List<Deployment>> deploymentMapEnvWise =
				deploymentListProjectWise.stream()
						.collect(Collectors.groupingBy(Deployment::getEnvName, Collectors.toList()));

		deploymentMapEnvWise.forEach(
				(envName, deploymentListEnvWise) -> {
					if (StringUtils.isNotEmpty(envName)
							&& CollectionUtils.isNotEmpty(deploymentListEnvWise)) {

						Map<String, List<Deployment>> deploymentMapTimeWise =
								duration.equalsIgnoreCase(CommonConstant.WEEK)
										? buildLastNWeekBuckets(previousTimeCount)
										: buildLastNMonthBuckets(previousTimeCount);
						List<DataCount> dataCountList = new ArrayList<>();

						for (Deployment deployment : deploymentListEnvWise) {
							DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DateUtil.TIME_FORMAT);
							LocalDateTime dateValue = LocalDateTime.parse(deployment.getStartTime(), formatter);
							String timeValue =
									duration.equalsIgnoreCase(CommonConstant.WEEK)
											? DateUtil.getWeekRange(dateValue.toLocalDate())
											: dateValue.getYear() + Constant.DASH + dateValue.getMonthValue();

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
			deploymentListCurrentTime.forEach(
					deployment -> {
						deploymentFrequencySlingshotInfo.addEnvironment(deployment.getEnvName());
						if (StringUtils.isNotEmpty(deployment.getJobFolderName())) {
							deploymentFrequencySlingshotInfo.addJobName(deployment.getJobFolderName());
						} else {
							deploymentFrequencySlingshotInfo.addJobName(deployment.getJobName());
						}
						deploymentFrequencySlingshotInfo.addDeploymentDate(
								DateUtil.dateTimeConverter(
										deployment.getStartTime(), DateUtil.TIME_FORMAT, DateUtil.DISPLAY_DATE_FORMAT));
						DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DateUtil.TIME_FORMAT);
						LocalDateTime dateTime = LocalDateTime.parse(deployment.getStartTime(), formatter);
						deploymentFrequencySlingshotInfo.addWeeks(
								DateUtil.getWeekRange(dateTime.toLocalDate()));
					});
		}
	}

	/**
	 * Builds an ordered map of the last N months (as empty buckets) keyed by "yyyy-M".
	 *
	 * @param count number of months
	 * @return ordered map of month buckets
	 */
	private Map<String, List<Deployment>> buildLastNMonthBuckets(int count) {
		Map<String, List<Deployment>> lastNMonth = new LinkedHashMap<>();
		LocalDateTime currentDate = LocalDateTime.now();
		String currentDateStr = currentDate.getYear() + Constant.DASH + currentDate.getMonthValue();
		lastNMonth.put(currentDateStr, new ArrayList<>());
		LocalDateTime lastMonth = LocalDateTime.now();
		for (int i = 1; i < count; i++) {
			lastMonth = lastMonth.minusMonths(1);
			String lastMonthStr = lastMonth.getYear() + Constant.DASH + lastMonth.getMonthValue();
			lastNMonth.put(lastMonthStr, new ArrayList<>());
		}
		return lastNMonth;
	}

	/**
	 * Builds an ordered map of the last N weeks (as empty buckets) keyed by the week range label.
	 *
	 * @param count number of weeks
	 * @return ordered map of week buckets
	 */
	private Map<String, List<Deployment>> buildLastNWeekBuckets(int count) {
		Map<String, List<Deployment>> lastNWeek = new LinkedHashMap<>();
		LocalDate endDateTime = LocalDate.now();

		for (int i = 0; i < count; i++) {
			String currentWeekStr = DateUtil.getWeekRange(endDateTime);
			lastNWeek.put(currentWeekStr, new ArrayList<>());
			endDateTime = endDateTime.minusWeeks(1);
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
