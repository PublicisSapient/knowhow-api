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

package com.publicissapient.kpidashboard.apis.bitbucket.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.publicissapient.kpidashboard.apis.abac.UserAuthorizedProjectsService;
import com.publicissapient.kpidashboard.apis.auth.apikey.ApiKeyAuthenticationService;
import com.publicissapient.kpidashboard.apis.bitbucket.factory.BitBucketKPIServiceFactory;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ProjectFilter;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;

import lombok.extern.slf4j.Slf4j;

/**
 * Bitbucket service to process bitbucket data.
 *
 * @author anisingh4
 */
@Service
@Slf4j
public class BitBucketServiceR {

	@Autowired private KpiHelperService kpiHelperService;

	@Autowired private ScmKpiHelperService scmKpiHelperService;

	@Autowired private FilterHelperService filterHelperService;

	@Autowired private CacheService cacheService;

	@Autowired private UserAuthorizedProjectsService authorizedProjectsService;

	private boolean referFromProjectCache = true;
	private List<ScmCommits> scmCommitsList = new ArrayList<>();
	private List<ScmMergeRequests> scmMergeRequestList = new ArrayList<>();
	private List<Assignee> assigneeList = new ArrayList<>();

	private static final ThreadLocal<List<ScmCommits>> THREAD_LOCAL_COMMITS =
			ThreadLocal.withInitial(ArrayList::new);
	private static final ThreadLocal<List<ScmMergeRequests>> THREAD_LOCAL_MERGE_REQUESTS =
			ThreadLocal.withInitial(ArrayList::new);
	private static final ThreadLocal<List<Assignee>> THREAD_LOCAL_ASSIGNEES =
			ThreadLocal.withInitial(ArrayList::new);

	@SuppressWarnings("unchecked")
	public List<KpiElement> process(KpiRequest kpiRequest) throws EntityNotFoundException {

		log.info(
				"[BITBUCKET][{}]. Processing KPI calculation for data {}",
				kpiRequest.getRequestTrackerId(),
				kpiRequest.getKpiList());
		List<KpiElement> origRequestedKpis =
				kpiRequest.getKpiList().stream().map(KpiElement::new).collect(Collectors.toList());
		List<KpiElement> responseList = new ArrayList<>();
		String[] projectKeyCache = null;
		try {
			Integer groupId = kpiRequest.getKpiList().get(0).getGroupId();
			String groupName =
					filterHelperService.getHierarchyLevelId(
							kpiRequest.getLevel(), kpiRequest.getLabel(), false);
			if (null != groupName) {
				kpiRequest.setLabel(groupName.toUpperCase());
			}
			List<AccountHierarchyData> filteredAccountDataList =
					filterHelperService.getFilteredBuilds(kpiRequest, groupName);
			if (!CollectionUtils.isEmpty(filteredAccountDataList)) {

				projectKeyCache = getProjectKeyCache(kpiRequest, filteredAccountDataList);
				filteredAccountDataList =
						kpiHelperService.getAuthorizedFilteredList(
								kpiRequest, filteredAccountDataList, referFromProjectCache);
				if (filteredAccountDataList.isEmpty()) {
					return responseList;
				}

				// skip using cache when the request is made with an api key
				if (Boolean.FALSE.equals(ApiKeyAuthenticationService.isApiKeyRequest())) {
					Object cachedData =
							cacheService.getFromApplicationCache(
									projectKeyCache,
									KPISource.BITBUCKET.name(),
									groupId,
									kpiRequest.getSprintIncluded());
					if (!kpiRequest
									.getRequestTrackerId()
									.toLowerCase()
									.contains(KPISource.EXCEL.name().toLowerCase())
							&& null != cachedData) {
						log.info(
								"[BITBUCKET][{}]. Fetching value from cache for {}",
								kpiRequest.getRequestTrackerId(),
								kpiRequest.getIds());
						return (List<KpiElement>) cachedData;
					}
				}

				Node filteredNode = getFilteredNodes(kpiRequest, filteredAccountDataList);
				kpiRequest.setXAxisDataPoints(Integer.parseInt(kpiRequest.getIds()[0]));
				kpiRequest.setDuration(kpiRequest.getSelectedMap().get(CommonConstant.DATE).get(0));
				responseList =
						executeParallelKpiProcessing(kpiRequest, filteredNode, filteredAccountDataList.get(0));

				List<KpiElement> finalResponseList = responseList;
				List<KpiElement> missingKpis =
						origRequestedKpis.stream()
								.filter(
										reqKpi ->
												finalResponseList.stream()
														.noneMatch(
																responseKpi -> reqKpi.getKpiId().equals(responseKpi.getKpiId())))
								.toList();
				List<KpiElement> mutableResponseList = new ArrayList<>(responseList);
				mutableResponseList.addAll(missingKpis);
				responseList = mutableResponseList;
				setIntoApplicationCache(kpiRequest, responseList, groupId, projectKeyCache);
			} else {
				responseList.addAll(origRequestedKpis);
			}

		} catch (Exception e) {
			log.error(
					"[BITBUCKET][{}]. Error while KPI calculation for data {} {}",
					kpiRequest.getRequestTrackerId(),
					kpiRequest.getKpiList(),
					e);
			throw new HttpMessageNotWritableException(e.getMessage(), e);
		} finally {
			cleanupThreadLocalData();
		}

		return responseList;
	}

	private Node getFilteredNodes(
			KpiRequest kpiRequest, List<AccountHierarchyData> filteredAccountDataList) {
		Node filteredNode = filteredAccountDataList.get(0).getNode().get(kpiRequest.getLevel() - 1);

		if (null != filteredNode.getProjectHierarchy()) {
			filteredNode.setProjectFilter(
					new ProjectFilter(
							filteredNode.getId(),
							filteredNode.getName(),
							filteredNode.getProjectHierarchy().getBasicProjectConfigId()));
		}

		return filteredNode;
	}

	private String[] getProjectKeyCache(
			KpiRequest kpiRequest, List<AccountHierarchyData> filteredAccountDataList) {
		return authorizedProjectsService.getProjectKey(filteredAccountDataList, kpiRequest);
	}

	private void loadDataIntoThreadLocal(AccountHierarchyData accountData, KpiRequest kpiRequest) {
		try {
			CompletableFuture<List<ScmCommits>> commitsFuture =
					CompletableFuture.supplyAsync(
							() ->
									scmKpiHelperService.getCommitDetails(
											accountData.getBasicProjectConfigId(),
											DeveloperKpiHelper.getStartAndEndDate(kpiRequest)));

			CompletableFuture<List<ScmMergeRequests>> mergeRequestsFuture =
					CompletableFuture.supplyAsync(
							() ->
									scmKpiHelperService.getMergeRequests(
											accountData.getBasicProjectConfigId(),
											DeveloperKpiHelper.getStartAndEndDate(kpiRequest)));

			CompletableFuture<List<Assignee>> assigneesFuture =
					CompletableFuture.supplyAsync(
							() ->
									scmKpiHelperService.getJiraAssigneeForScmUsers(
											accountData.getBasicProjectConfigId()));

			CompletableFuture.allOf(commitsFuture, mergeRequestsFuture, assigneesFuture).join();

			scmCommitsList = commitsFuture.get();
			log.info("Setting data in ThreadLocal on thread: {}", Thread.currentThread().getId());
			scmMergeRequestList = mergeRequestsFuture.get();
			assigneeList = assigneesFuture.get();

			log.info(
					"[BITBUCKET][{}]. Data loaded into ThreadLocal - Commits: {}, MergeRequests: {}, Assignees: {}",
					kpiRequest.getRequestTrackerId(),
					scmCommitsList.size(),
					scmMergeRequestList.size(),
					assigneeList.size());

		} catch (ExecutionException | InterruptedException e) {
			log.error(
					"[BITBUCKET][{}]. Error loading data into ThreadLocal: {}",
					kpiRequest.getRequestTrackerId(),
					e.getMessage(),
					e);
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/** Enhanced parallel execution with custom ForkJoinPool */
	private List<KpiElement> executeParallelKpiProcessing(
			KpiRequest kpiRequest, Node filteredNode, AccountHierarchyData accountHierarchyData) {
		ExecutorService executorService =
				Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		try {
			loadDataIntoThreadLocal(accountHierarchyData, kpiRequest);

			// Create futures for each KPI element
			List<CompletableFuture<KpiElement>> futures =
					kpiRequest.getKpiList().stream()
							.map(
									kpiEle ->
											CompletableFuture.supplyAsync(
													() -> {
														try {
															// Set ThreadLocal data for each thread
															THREAD_LOCAL_COMMITS.set(new ArrayList<>(scmCommitsList));
															THREAD_LOCAL_MERGE_REQUESTS.set(new ArrayList<>(scmMergeRequestList));
															THREAD_LOCAL_ASSIGNEES.set(new ArrayList<>(assigneeList));

															return calculateAllKPIAggregatedMetrics(
																	kpiRequest, kpiEle, filteredNode);
														} catch (Exception e) {
															log.error(
																	"[BITBUCKET][{}]. Error processing KPI {}: {}",
																	kpiRequest.getRequestTrackerId(),
																	kpiEle.getKpiId(),
																	e.getMessage(),
																	e);
															kpiEle.setResponseCode(CommonConstant.KPI_FAILED);
															return kpiEle;
														} finally {
															// Clean up ThreadLocal for this specific thread
															cleanupCurrentThreadLocal();
														}
													},
													executorService))
							.toList();

			// Convert futures to KpiElement list by joining all futures
			return futures.stream().map(CompletableFuture::join).toList();

		} finally {
			// Proper executor shutdown with timeout
			shutdownExecutorService(executorService);
		}
	}

	/** Clean up ThreadLocal for current thread only */
	private void cleanupCurrentThreadLocal() {
		try {
			THREAD_LOCAL_COMMITS.remove();
			THREAD_LOCAL_MERGE_REQUESTS.remove();
			THREAD_LOCAL_ASSIGNEES.remove();
			log.debug("ThreadLocal data cleaned up for thread: {}", Thread.currentThread().getId());
		} catch (Exception e) {
			log.warn(
					"Error cleaning up ThreadLocal data for thread {}: {}",
					Thread.currentThread().getId(),
					e.getMessage());
		}
	}

	/** Properly shutdown executor service with timeout handling */
	private void shutdownExecutorService(ExecutorService executorService) {
		try {
			executorService.shutdown();
			if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
				log.warn("ExecutorService did not terminate gracefully, forcing shutdown");
				executorService.shutdownNow();
				if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
					log.error("ExecutorService did not terminate after forced shutdown");
				}
			}
		} catch (InterruptedException e) {
			log.error("Thread interrupted during executor shutdown", e);
			executorService.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	/** Clean up ThreadLocal variables to prevent memory leaks */
	private void cleanupThreadLocalData() {
		try {
			THREAD_LOCAL_COMMITS.remove();
			THREAD_LOCAL_MERGE_REQUESTS.remove();
			THREAD_LOCAL_ASSIGNEES.remove();
			log.info("ThreadLocal data cleaned up successfully");
		} catch (Exception e) {
			log.warn("Error cleaning up ThreadLocal data: {}", e.getMessage());
		}
	}

	/** Static methods to access ThreadLocal data from other classes */
	public static List<ScmCommits> getThreadLocalCommits() {
		return new ArrayList<>(THREAD_LOCAL_COMMITS.get());
	}

	public static List<ScmMergeRequests> getThreadLocalMergeRequests() {
		log.info("Getting data from ThreadLocal on thread: {}", Thread.currentThread().getId());
		return new ArrayList<>(THREAD_LOCAL_MERGE_REQUESTS.get());
	}

	public static List<Assignee> getThreadLocalAssignees() {
		return new ArrayList<>(THREAD_LOCAL_ASSIGNEES.get());
	}

	/**
	 * Cache response.
	 *
	 * @param kpiRequest
	 * @param responseList
	 * @param groupId
	 * @param projectKeyCache
	 */
	private void setIntoApplicationCache(
			KpiRequest kpiRequest,
			List<KpiElement> responseList,
			Integer groupId,
			String[] projectKeyCache) {
		Integer projectLevel =
				filterHelperService
						.getHierarchyIdLevelMap(false)
						.get(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
		if (!kpiRequest
						.getRequestTrackerId()
						.toLowerCase()
						.contains(KPISource.EXCEL.name().toLowerCase())
				&& projectLevel >= kpiRequest.getLevel()) {

			cacheService.setIntoApplicationCache(
					projectKeyCache,
					responseList,
					KPISource.BITBUCKET.name(),
					groupId,
					kpiRequest.getSprintIncluded());
		}
	}

	/**
	 * This method call by multiple thread, take object of specific KPI and call method of these KPIs
	 *
	 * @param kpiRequest Bitbucket KPI request
	 * @param kpiElement kpiElement object
	 * @param filteredAccountNode filter tree object
	 * @return Kpielement
	 */
	private KpiElement calculateAllKPIAggregatedMetrics(
			KpiRequest kpiRequest, KpiElement kpiElement, Node filteredAccountNode) {

		BitBucketKPIService<?, ?, ?> bitBucketKPIService = null;

		KPICode kpi = KPICode.getKPI(kpiElement.getKpiId());
		try {
			bitBucketKPIService = BitBucketKPIServiceFactory.getBitBucketKPIService(kpi.name());
			long startTime = System.currentTimeMillis();
			Node nodeDataClone = (Node) SerializationUtils.clone(filteredAccountNode);

			if (Objects.nonNull(nodeDataClone)
					&& kpiHelperService.isToolConfigured(kpi, kpiElement, nodeDataClone)) {

				kpiRequest.setKanbanXaxisDataPoints(
						Optional.ofNullable(kpiRequest.getIds())
								.filter(ids -> ids.length > 0)
								.map(ids -> ids[0])
								.filter(id -> id.matches("\\d+"))
								.map(Integer::parseInt)
								.orElse(5));
				kpiElement = bitBucketKPIService.getKpiData(kpiRequest, kpiElement, nodeDataClone);
				kpiElement.setResponseCode(CommonConstant.KPI_PASSED);
				kpiHelperService.isMandatoryFieldSet(kpi, kpiElement, nodeDataClone);
			}

			long processTime = System.currentTimeMillis() - startTime;
			log.info(
					"[BITBUCKET-{}-TIME][{}]. KPI took {} ms",
					kpi.name(),
					kpiRequest.getRequestTrackerId(),
					processTime);
		} catch (ApplicationException exception) {
			kpiElement.setResponseCode(CommonConstant.KPI_FAILED);
			log.error("Kpi not found", exception);
		} catch (Exception exception) {
			kpiElement.setResponseCode(CommonConstant.KPI_FAILED);
			log.error("[PARALLEL_BITBUCKET_SERVICE].Exception occurred", exception);
			return kpiElement;
		}
		return kpiElement;
	}

	/**
	 * This method is called when the request for kpi is done from exposed API
	 *
	 * @param kpiRequest JIRA KPI request true if flow for precalculated, false for direct flow.
	 * @param withCache
	 * @return List of KPI data
	 * @throws EntityNotFoundException EntityNotFoundException
	 */
	public List<KpiElement> processWithExposedApiToken(KpiRequest kpiRequest, boolean withCache)
			throws EntityNotFoundException {
		boolean originalReferFromProjectCache = referFromProjectCache;
		try {
			referFromProjectCache = withCache;
			return process(kpiRequest);
		} finally {
			referFromProjectCache = originalReferFromProjectCache;
		}
	}
}
