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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;

import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import org.apache.commons.lang.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.publicissapient.kpidashboard.apis.abac.UserAuthorizedProjectsService;
import com.publicissapient.kpidashboard.apis.bitbucket.factory.BitBucketKPIServiceFactory;
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
import com.publicissapient.kpidashboard.common.constant.CommonConstant;

import lombok.extern.slf4j.Slf4j;

/**
 * Bitbucket service to process bitbucket data.
 *
 * @author anisingh4
 */
@Service
@Slf4j
public class BitBucketServiceR {

	@Autowired
	private KpiHelperService kpiHelperService;

    @Autowired
    private ScmKpiHelperService scmKpiHelperService;

	@Autowired
	private FilterHelperService filterHelperService;

	@Autowired
	private CacheService cacheService;

	@Autowired
	private UserAuthorizedProjectsService authorizedProjectsService;

	private boolean referFromProjectCache = true;

    private static final ThreadLocal<List<ScmCommits>> THREAD_LOCAL_COMMITS =
            ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<ScmMergeRequests>> THREAD_LOCAL_MERGE_REQUESTS =
            ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<Assignee>> THREAD_LOCAL_ASSIGNEES =
            ThreadLocal.withInitial(ArrayList::new);

    private static final ForkJoinPool CUSTOM_FORK_JOIN_POOL =
            new ForkJoinPool(Runtime.getRuntime().availableProcessors());

	@SuppressWarnings("unchecked")
	public List<KpiElement> process(KpiRequest kpiRequest) throws EntityNotFoundException {

		log.info("[BITBUCKET][{}]. Processing KPI calculation for data {}", kpiRequest.getRequestTrackerId(),
				kpiRequest.getKpiList());
		List<KpiElement> origRequestedKpis = kpiRequest.getKpiList().stream().map(KpiElement::new)
				.collect(Collectors.toList());
		List<KpiElement> responseList = new ArrayList<>();
		String[] projectKeyCache = null;
		try {
			Integer groupId = kpiRequest.getKpiList().get(0).getGroupId();
			String groupName = filterHelperService.getHierarachyLevelId(kpiRequest.getLevel(), kpiRequest.getLabel(), false);
			if (null != groupName) {
				kpiRequest.setLabel(groupName.toUpperCase());
			}
			List<AccountHierarchyData> filteredAccountDataList = filterHelperService.getFilteredBuilds(kpiRequest, groupName);
			if (!CollectionUtils.isEmpty(filteredAccountDataList)) {

				projectKeyCache = getProjectKeyCache(kpiRequest, filteredAccountDataList);
				filteredAccountDataList = kpiHelperService.getAuthorizedFilteredList(kpiRequest, filteredAccountDataList,
						referFromProjectCache);
				if (filteredAccountDataList.isEmpty()) {
					return responseList;
				}

				Object cachedData = cacheService.getFromApplicationCache(projectKeyCache, KPISource.BITBUCKET.name(), groupId,
						kpiRequest.getSprintIncluded());
				if (!kpiRequest.getRequestTrackerId().toLowerCase().contains(KPISource.EXCEL.name().toLowerCase()) &&
						null != cachedData) {
					log.info("[BITBUCKET][{}]. Fetching value from cache for {}", kpiRequest.getRequestTrackerId(),
							kpiRequest.getIds());
					return (List<KpiElement>) cachedData;
				}

				Node filteredNode = getFilteredNodes(kpiRequest, filteredAccountDataList);
				kpiRequest.setXAxisDataPoints(Integer.parseInt(kpiRequest.getIds()[0]));
				kpiRequest.setDuration(kpiRequest.getSelectedMap().get(CommonConstant.date).get(0));
                loadDataIntoThreadLocal(filteredAccountDataList.get(0), kpiRequest);
                responseList = executeParallelKpiProcessing(kpiRequest, filteredNode);

                List<KpiElement> finalResponseList = responseList;
                List<KpiElement> missingKpis = origRequestedKpis.stream()
                        .filter(reqKpi -> finalResponseList.stream()
                                .noneMatch(responseKpi -> reqKpi.getKpiId().equals(responseKpi.getKpiId())))
                        .toList();

                responseList.addAll(missingKpis);
				setIntoApplicationCache(kpiRequest, responseList, groupId, projectKeyCache);
			} else {
				responseList.addAll(origRequestedKpis);
			}

		} catch (Exception e) {
			log.error("[BITBUCKET][{}]. Error while KPI calculation for data {} {}", kpiRequest.getRequestTrackerId(),
					kpiRequest.getKpiList(), e);
			throw new HttpMessageNotWritableException(e.getMessage(), e);
		} finally {
            cleanupThreadLocalData();
        }

		return responseList;
	}

	private Node getFilteredNodes(KpiRequest kpiRequest, List<AccountHierarchyData> filteredAccountDataList) {
		Node filteredNode = filteredAccountDataList.get(0).getNode().get(kpiRequest.getLevel() - 1);

		if (null != filteredNode.getProjectHierarchy()) {
			filteredNode.setProjectFilter(new ProjectFilter(filteredNode.getId(), filteredNode.getName(),
					filteredNode.getProjectHierarchy().getBasicProjectConfigId()));
		}

		return filteredNode;
	}

	private String[] getProjectKeyCache(KpiRequest kpiRequest, List<AccountHierarchyData> filteredAccountDataList) {
		return authorizedProjectsService.getProjectKey(filteredAccountDataList, kpiRequest);
	}

    private void loadDataIntoThreadLocal(AccountHierarchyData accountData, KpiRequest kpiRequest) {
        try {
            CompletableFuture<List<ScmCommits>> commitsFuture = CompletableFuture.supplyAsync(() ->
                    scmKpiHelperService.getCommitDetails(
                            accountData.getBasicProjectConfigId(),
                            KpiDataHelper.getStartAndEndDate(kpiRequest)));

            CompletableFuture<List<ScmMergeRequests>> mergeRequestsFuture = CompletableFuture.supplyAsync(() ->
                    scmKpiHelperService.getMergeRequests(
                            accountData.getBasicProjectConfigId(),
                            KpiDataHelper.getStartAndEndDate(kpiRequest)));

            CompletableFuture<List<Assignee>> assigneesFuture = CompletableFuture.supplyAsync(() ->
                    scmKpiHelperService.getScmUsers(accountData.getBasicProjectConfigId()));

            CompletableFuture.allOf(commitsFuture, mergeRequestsFuture, assigneesFuture).join();

            THREAD_LOCAL_COMMITS.set(commitsFuture.get());
            THREAD_LOCAL_MERGE_REQUESTS.set(mergeRequestsFuture.get());
            THREAD_LOCAL_ASSIGNEES.set(assigneesFuture.get());

            log.info("[BITBUCKET][{}]. Data loaded into ThreadLocal - Commits: {}, MergeRequests: {}, Assignees: {}",
                    kpiRequest.getRequestTrackerId(),
                    THREAD_LOCAL_COMMITS.get().size(),
                    THREAD_LOCAL_MERGE_REQUESTS.get().size(),
                    THREAD_LOCAL_ASSIGNEES.get().size());

        } catch (Exception e) {
            log.error("[BITBUCKET][{}]. Error loading data into ThreadLocal: {}",
                    kpiRequest.getRequestTrackerId(), e.getMessage(), e);
            THREAD_LOCAL_COMMITS.set(new ArrayList<>());
            THREAD_LOCAL_MERGE_REQUESTS.set(new ArrayList<>());
            THREAD_LOCAL_ASSIGNEES.set(new ArrayList<>());
        }
    }

    /**
     * Enhanced parallel execution with custom ForkJoinPool
     */
    private List<KpiElement> executeParallelKpiProcessing(KpiRequest kpiRequest, Node filteredNode) {
        List<KpiElement> responseList = new ArrayList<>();
        List<ParallelBitBucketServices> listOfTask = new ArrayList<>();

        for (KpiElement kpiEle : kpiRequest.getKpiList()) {
            listOfTask.add(new ParallelBitBucketServices(kpiRequest, responseList, kpiEle, filteredNode));
        }

        try {
            CUSTOM_FORK_JOIN_POOL.submit(() -> {
                ForkJoinTask.invokeAll(listOfTask);
                return null;
            }).get();

            log.info("[BITBUCKET][{}]. Parallel execution completed for {} KPIs",
                    kpiRequest.getRequestTrackerId(), listOfTask.size());

        } catch (Exception e) {
            log.error("[BITBUCKET][{}]. Error in parallel execution: {}",
                    kpiRequest.getRequestTrackerId(), e.getMessage(), e);
            listOfTask.forEach(ParallelBitBucketServices::compute);
        }

        return responseList;
    }

    /**
     * Clean up ThreadLocal variables to prevent memory leaks
     */
    private void cleanupThreadLocalData() {
        try {
            THREAD_LOCAL_COMMITS.remove();
            THREAD_LOCAL_MERGE_REQUESTS.remove();
            THREAD_LOCAL_ASSIGNEES.remove();
            log.debug("ThreadLocal data cleaned up successfully");
        } catch (Exception e) {
            log.warn("Error cleaning up ThreadLocal data: {}", e.getMessage());
        }
    }

    /**
     * Static methods to access ThreadLocal data from other classes
     */
    public static List<ScmCommits> getThreadLocalCommits() {
        return new ArrayList<>(THREAD_LOCAL_COMMITS.get());
    }

    public static List<ScmMergeRequests> getThreadLocalMergeRequests() {
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
	private void setIntoApplicationCache(KpiRequest kpiRequest, List<KpiElement> responseList, Integer groupId,
			String[] projectKeyCache) {
		Integer projectLevel = filterHelperService.getHierarchyIdLevelMap(false)
				.get(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
		if (!kpiRequest.getRequestTrackerId().toLowerCase().contains(KPISource.EXCEL.name().toLowerCase()) &&
				projectLevel >= kpiRequest.getLevel()) {

			cacheService.setIntoApplicationCache(projectKeyCache, responseList, KPISource.BITBUCKET.name(), groupId,
					kpiRequest.getSprintIncluded());
		}
	}

	public class ParallelBitBucketServices extends RecursiveAction {

		private final KpiRequest kpiRequest;
		private final transient List<KpiElement> responseList;
		private final transient KpiElement kpiEle;
		private final Node filteredNode;

		/**
		 * @param kpiRequest
		 *          kpi request
		 * @param responseList
		 *          response list
		 * @param kpiEle
		 *          kpi element
		 * @param filteredNode
		 *          filtered project node
		 */
		public ParallelBitBucketServices(KpiRequest kpiRequest, List<KpiElement> responseList, KpiElement kpiEle,
				Node filteredNode) {
			super();
			this.kpiRequest = kpiRequest;
			this.responseList = responseList;
			this.kpiEle = kpiEle;
			this.filteredNode = filteredNode;
		}

		/** {@inheritDoc} */
		@SuppressWarnings("PMD.AvoidCatchingGenericException")
		@Override
		public void compute() {
			responseList.add(calculateAllKPIAggregatedMetrics(kpiRequest, kpiEle, filteredNode));
		}

		/**
		 * This method call by multiple thread, take object of specific KPI and call
		 * method of these KPIs
		 *
		 * @param kpiRequest
		 *          Bitbucket KPI request
		 * @param kpiElement
		 *          kpiElement object
		 * @param filteredAccountNode
		 *          filter tree object
		 * @return Kpielement
		 */
		private KpiElement calculateAllKPIAggregatedMetrics(KpiRequest kpiRequest, KpiElement kpiElement,
				Node filteredAccountNode) {

			BitBucketKPIService<?, ?, ?> bitBucketKPIService = null;

			KPICode kpi = KPICode.getKPI(kpiElement.getKpiId());
			try {
				bitBucketKPIService = BitBucketKPIServiceFactory.getBitBucketKPIService(kpi.name());
				long startTime = System.currentTimeMillis();
				Node nodeDataClone = (Node) SerializationUtils.clone(filteredAccountNode);

				if (Objects.nonNull(nodeDataClone) && kpiHelperService.isToolConfigured(kpi, kpiElement, nodeDataClone)) {
					kpiElement = bitBucketKPIService.getKpiData(kpiRequest, kpiElement, nodeDataClone);
					kpiElement.setResponseCode(CommonConstant.KPI_PASSED);
					kpiHelperService.isMandatoryFieldSet(kpi, kpiElement, nodeDataClone);
				}

				long processTime = System.currentTimeMillis() - startTime;
				log.info("[BITBUCKET-{}-TIME][{}]. KPI took {} ms", kpi.name(), kpiRequest.getRequestTrackerId(), processTime);
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
	}

	/**
	 * This method is called when the request for kpi is done from exposed API
	 *
	 * @param kpiRequest
	 *            JIRA KPI request true if flow for precalculated, false for direct
	 *            flow.
	 * @return List of KPI data
	 * @throws EntityNotFoundException
	 *             EntityNotFoundException
	 */
	public List<KpiElement> processWithExposedApiToken(KpiRequest kpiRequest) throws EntityNotFoundException {
		boolean originalReferFromProjectCache = referFromProjectCache;
		try {
			referFromProjectCache = false;
			return process(kpiRequest);
		} finally {
			referFromProjectCache = originalReferFromProjectCache;
		}
	}
}
