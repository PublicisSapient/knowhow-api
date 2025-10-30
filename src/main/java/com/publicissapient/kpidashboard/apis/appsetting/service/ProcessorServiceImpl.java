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

package com.publicissapient.kpidashboard.apis.appsetting.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.core.Context;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.google.gson.Gson;
import com.publicissapient.kpidashboard.apis.appsetting.config.ProcessorUrlConfig;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolsStatusResponse;
import com.publicissapient.kpidashboard.apis.repotools.service.RepoToolsConfigServiceImpl;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import com.publicissapient.kpidashboard.common.model.ProcessorExecutionBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.SprintTraceLog;
import com.publicissapient.kpidashboard.common.model.generic.Processor;
import com.publicissapient.kpidashboard.common.repository.application.SprintTraceLogRepository;
import com.publicissapient.kpidashboard.common.repository.generic.ProcessorRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * This class provides various methods related to operations on Processor Data
 *
 * @author pansharm5
 */
@Service
@Slf4j
public class ProcessorServiceImpl implements ProcessorService {

	public static final String AUTHORIZATION = "Authorization";
	private static final List<String> SCM_TOOLS =
			Arrays.asList(
					ProcessorConstants.BITBUCKET,
					ProcessorConstants.GITLAB,
					ProcessorConstants.GITHUB,
					ProcessorConstants.AZUREREPO);
	private static final String PROCESSOR_ERROR_TEMPLATE =
			"Error in running %s processor. Please try after some time.";

	@Context HttpServletRequest httpServletRequest;
	@Autowired SprintTraceLogRepository sprintTraceLogRepository;
	@Autowired private ProcessorRepository<Processor> processorRepository;
	@Autowired private RestTemplate restTemplate;
	@Autowired private ProcessorUrlConfig processorUrlConfig;
	@Autowired private RepoToolsConfigServiceImpl repoToolsConfigService;

	@Autowired private CustomApiConfig customApiConfig;
	@Autowired private CacheService cacheService;
	@Autowired private ConfigHelperService configHelperService;

	@Override
	public ServiceResponse getAllProcessorDetails() {
		List<Processor> listProcessor = new ArrayList<>();
		processorRepository
				.findAll()
				.iterator()
				.forEachRemaining(
						p -> {
							if (null != p) {
								listProcessor.add(p);
							}
						});
		log.debug("Returning list of Processors having size: {}", listProcessor.size());
		return new ServiceResponse(true, StringUtils.EMPTY, listProcessor);
	}

	@Override
	public ServiceResponse runProcessor(
			String processorName, ProcessorExecutionBasicConfig processorExecutionBasicConfig) {

		String url = processorUrlConfig.getProcessorUrl(processorName);

		if (isScmProcessor(processorName)) {
			processorExecutionBasicConfig.setScmProcessorName(processorName);
		}

		ProcessorResult result =
				shouldExecuteScmTool(processorName, processorExecutionBasicConfig)
						? executeScmTool(processorName, processorExecutionBasicConfig)
						: executeRegularProcessor(url, processorName, processorExecutionBasicConfig);

		return new ServiceResponse(
				result.isSuccess(),
				String.format("Got HTTP response: %d on url: %s", result.statusCode(), url),
				result.body());
	}

	private boolean isScmProcessor(String processorName) {
		return SCM_TOOLS.contains(processorName);
	}

	private boolean shouldExecuteScmTool(String processorName, ProcessorExecutionBasicConfig config) {
		return isScmProcessor(processorName)
				&& isSCMToolEnabled(config)
				&& customApiConfig.isRepoToolEnabled();
	}

	private ProcessorResult executeScmTool(
			String processorName, ProcessorExecutionBasicConfig config) {
		String projectBasicConfigId = config.getProjectBasicConfigIds().get(0);
		int statusCode =
				repoToolsConfigService.triggerScanRepoToolProject(processorName, projectBasicConfigId);
		boolean isSuccess = statusCode >= 200 && statusCode < 300;
		String body = isSuccess ? "" : String.format(PROCESSOR_ERROR_TEMPLATE, processorName);
		return new ProcessorResult(isSuccess, statusCode, body);
	}

	private ProcessorResult executeRegularProcessor(
			String url,
			String processorName,
			ProcessorExecutionBasicConfig processorExecutionBasicConfig) {

		if (StringUtils.isEmpty(url)) {
			return new ProcessorResult(
					false,
					HttpStatus.NOT_FOUND.value(),
					String.format(PROCESSOR_ERROR_TEMPLATE, processorName));
		}

		httpServletRequest =
				((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
		String token =
				CommonUtils.handleCrossScriptingTaintedValue(httpServletRequest.getHeader(AUTHORIZATION));

		try {
			HttpHeaders headers = new HttpHeaders();
			headers.add(AUTHORIZATION, token);
			HttpEntity<ProcessorExecutionBasicConfig> requestEntity =
					new HttpEntity<>(processorExecutionBasicConfig, headers);
			ResponseEntity<String> resp =
					restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
			int statusCode = resp.getStatusCode().value();
			boolean isSuccess = statusCode >= 200 && statusCode < 300;
			String body = "";
			if (!isSuccess
					&& (statusCode == HttpStatus.NOT_FOUND.value()
							|| statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value())) {
				body = String.format(PROCESSOR_ERROR_TEMPLATE, processorName);
			}
			return new ProcessorResult(isSuccess, statusCode, body);
		} catch (HttpClientErrorException ex) {
			log.error(
					"HTTP client error while running processor {}: {}", processorName, ex.getMessage(), ex);
			return new ProcessorResult(false, ex.getStatusCode().value(), getBody(ex));
		} catch (ResourceAccessException ex) {
			log.error(
					"Resource access error while running processor {}: {}",
					processorName,
					ex.getMessage(),
					ex);
			return new ProcessorResult(
					false,
					HttpStatus.INTERNAL_SERVER_ERROR.value(),
					String.format(PROCESSOR_ERROR_TEMPLATE, processorName));
		}
	}

	private boolean isSCMToolEnabled(ProcessorExecutionBasicConfig processorExecutionBasicConfig) {
		if (processorExecutionBasicConfig == null
				|| processorExecutionBasicConfig.getProjectBasicConfigIds() == null
				|| processorExecutionBasicConfig.getProjectBasicConfigIds().isEmpty()) {
			return false;
		}
		String projectBasicConfigId = processorExecutionBasicConfig.getProjectBasicConfigIds().get(0);
		return configHelperService.getProjectConfig(projectBasicConfigId).isDeveloperKpiEnabled();
	}

	private record ProcessorResult(boolean isSuccess, int statusCode, String body) {}

	private String getBody(HttpClientErrorException ex) {
		String msg = ex.getMessage();

		if (msg != null) {
			String[] parts = msg.split(":");
			return (parts.length > 1) ? parts[1].trim().replace("\"", "") : "";
		} else {
			return "";
		}
	}

	@Override
	public ServiceResponse fetchActiveSprint(String sprintId) {

		String url =
				processorUrlConfig
						.getProcessorUrl(ProcessorConstants.JIRA)
						.replaceFirst("/startprojectwiseissuejob", "/startfetchsprintjob");

		boolean isSuccess = true;

		httpServletRequest =
				((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
		String token = httpServletRequest.getHeader(AUTHORIZATION);
		token = CommonUtils.handleCrossScriptingTaintedValue(token);
		int statuscode = HttpStatus.NOT_FOUND.value();
		if (StringUtils.isNotEmpty(url)) {
			try {
				HttpHeaders headers = new HttpHeaders();
				headers.add(AUTHORIZATION, token);
				headers.setContentType(MediaType.APPLICATION_JSON);
				Gson gson = new Gson();
				String payload = gson.toJson(sprintId);
				HttpEntity<String> requestEntity = new HttpEntity<>(payload, headers);
				ResponseEntity<String> resp =
						restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
				statuscode = resp.getStatusCode().value();
			} catch (HttpClientErrorException ex) {
				statuscode = ex.getStatusCode().value();
				isSuccess = false;
			} catch (ResourceAccessException ex) {
				isSuccess = false;
			}
		}
		if (HttpStatus.NOT_FOUND.value() == statuscode
				|| HttpStatus.INTERNAL_SERVER_ERROR.value() == statuscode) {
			isSuccess = false;
		}

		// setting the fetchStatus as false for the fetch sprint
		if (HttpStatus.OK.value() == statuscode) {
			SprintTraceLog sprintTrace = sprintTraceLogRepository.findFirstBySprintId(sprintId);
			sprintTrace = sprintTrace == null ? new SprintTraceLog() : sprintTrace;
			sprintTrace.setSprintId(sprintId);
			sprintTrace.setFetchSuccessful(false);
			sprintTrace.setErrorInFetch(false);
			sprintTraceLogRepository.save(sprintTrace);
		}

		return new ServiceResponse(
				isSuccess, "Got HTTP response: " + statuscode + " on url: " + url, null);
	}

    @Override
    public ServiceResponse fetchScmConfigByConnectionId(String connection) {
        String url = processorUrlConfig.getProcessorUrl(ProcessorConstants.BITBUCKET).replaceFirst("/processor/run",
                "/api/scm/connection/sync-metadata");
        boolean isSuccess = true;

        httpServletRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String token = httpServletRequest.getHeader(AUTHORIZATION);
        token = CommonUtils.handleCrossScriptingTaintedValue(token);
        int statuscode = HttpStatus.NOT_FOUND.value();
        if (StringUtils.isNotEmpty(url)) {
            try {
                url = String.format(url, connection);
                HttpHeaders headers = new HttpHeaders();
                headers.add(AUTHORIZATION, token);
                headers.setContentType(MediaType.APPLICATION_JSON);
                Gson gson = new Gson();
                String payload = gson.toJson(connection);
                HttpEntity<String> requestEntity = new HttpEntity<>(payload, headers);
                ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
                statuscode = resp.getStatusCode().value();
            } catch (HttpClientErrorException ex) {
                statuscode = ex.getStatusCode().value();
                isSuccess = false;
            } catch (ResourceAccessException ex) {
                isSuccess = false;
            }
        }
        if (HttpStatus.NOT_FOUND.value() == statuscode || HttpStatus.INTERNAL_SERVER_ERROR.value() == statuscode) {
            isSuccess = false;
        }

        return new ServiceResponse(isSuccess, "Got HTTP response: " + statuscode + " on url: " + url, null);
    }

	/**
	 * saves the response statuses for repo tools
	 *
	 * @param repoToolsStatusResponse repo tool response status
	 */
	public void saveRepoToolTraceLogs(RepoToolsStatusResponse repoToolsStatusResponse) {
		repoToolsConfigService.saveRepoToolProjectTraceLog(repoToolsStatusResponse);
		cacheService.clearCache(CommonConstant.CACHE_TOOL_CONFIG_MAP);
		cacheService.clearCache(CommonConstant.CACHE_PROJECT_TOOL_CONFIG_MAP);
		cacheService.clearCache(CommonConstant.BITBUCKET_KPI_CACHE);
	}

	/**
	 * run the metadata step of processor, to get the options of fieldmapping
	 *
	 * @param projectBasicConfigId id of the project
	 * @return {@code ServiceResponse}
	 */
	@Override
	public ServiceResponse runMetadataStep(String projectBasicConfigId) {

		String url =
				processorUrlConfig
						.getProcessorUrl(ProcessorConstants.JIRA)
						.replaceFirst("/startprojectwiseissuejob", "/runMetadataStep");

		boolean isSuccess = true;

		httpServletRequest =
				((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
		String token = httpServletRequest.getHeader(AUTHORIZATION);
		token = CommonUtils.handleCrossScriptingTaintedValue(token);
		int statuscode = HttpStatus.NOT_FOUND.value();
		if (StringUtils.isNotEmpty(url)) {
			try {
				HttpHeaders headers = new HttpHeaders();
				headers.add(AUTHORIZATION, token);
				headers.setContentType(MediaType.APPLICATION_JSON);
				Gson gson = new Gson();
				String payload = gson.toJson(projectBasicConfigId);
				HttpEntity<String> requestEntity = new HttpEntity<>(payload, headers);
				ResponseEntity<String> resp =
						restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
				statuscode = resp.getStatusCode().value();
			} catch (HttpClientErrorException ex) {
				statuscode = ex.getStatusCode().value();
				isSuccess = false;
			} catch (ResourceAccessException ex) {
				isSuccess = false;
			}
		}
		if (HttpStatus.NOT_FOUND.value() == statuscode
				|| HttpStatus.INTERNAL_SERVER_ERROR.value() == statuscode) {
			isSuccess = false;
		}
		return new ServiceResponse(
				isSuccess, "Got HTTP response: " + statuscode + " on url: " + url, null);
	}
}
