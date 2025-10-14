/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *    http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.aiusage.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.publicissapient.kpidashboard.apis.aiusage.config.AIUsageFileFormat;
import com.publicissapient.kpidashboard.apis.aiusage.dto.InitiateUploadResponse;
import com.publicissapient.kpidashboard.apis.aiusage.dto.UploadStatusResponse;
import com.publicissapient.kpidashboard.apis.aiusage.dto.mapper.UploadStatusMapper;
import com.publicissapient.kpidashboard.apis.aiusage.enums.UploadStatus;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsage;
import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsageRequest;
import com.publicissapient.kpidashboard.apis.aiusage.repository.AIUsageRepository;
import com.publicissapient.kpidashboard.apis.aiusage.repository.AIUsageUploadStatusRepository;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
@SuppressWarnings("squid:S2083")
public class AIUsageService {

	public static final String COMMA_DELIMITER = ",";

	private final AIUsageRepository aiUsageRepository;
	private final AIUsageUploadStatusRepository aiUsageUploadStatusRepository;
	private final AIUsageFileFormat aiUsageFileFormat;
	private final UploadStatusMapper uploadStatusMapper;

	@Transactional
	public InitiateUploadResponse uploadFile(
			@NotNull String filePath, UUID requestId, Instant submittedAt) {
		try {
			validateAIUsageCSVFile(filePath);
			AIUsageRequest receivedStatus =
					AIUsageRequest.builder()
							.requestId(String.valueOf(requestId))
							.submittedAt(submittedAt)
							.status(UploadStatus.PENDING)
							.build();
			aiUsageUploadStatusRepository.save(receivedStatus);

			return new InitiateUploadResponse(
					"File upload request accepted for processing", requestId, filePath);
		} catch (IllegalArgumentException e) {
			AIUsageRequest receivedStatus =
					AIUsageRequest.builder()
							.requestId(String.valueOf(requestId))
							.submittedAt(submittedAt)
							.status(UploadStatus.FAILED)
							.build();
			aiUsageUploadStatusRepository.save(receivedStatus);
			return new InitiateUploadResponse("Error while processing the file", requestId, filePath);
		}
	}

	public UploadStatusResponse getProcessingStatus(UUID requestId) {
		AIUsageRequest uploadStatus =
				aiUsageUploadStatusRepository
						.findByRequestId(String.valueOf(requestId))
						.orElseThrow(
								() ->
										new IllegalArgumentException(
												"No upload status found for requestId: " + requestId));
		return uploadStatusMapper.mapToDto(uploadStatus);
	}

	public AIUsageRequest findByRequestId(UUID requestId) {
		return aiUsageUploadStatusRepository
				.findByRequestId(String.valueOf(requestId))
				.orElseThrow(
						() ->
								new IllegalArgumentException("No upload status found for requestId: " + requestId));
	}

	@Transactional
	public void saveUploadStatus(AIUsageRequest uploadStatus) {
		aiUsageUploadStatusRepository.save(uploadStatus);
	}

	@Transactional
	public void saveAIUsageDocument(AIUsage aiUsage) {
		aiUsageRepository.save(aiUsage);
	}

	private void validateAIUsageCSVFile(String filePath) throws IllegalArgumentException {
		File file = new File(filePath);
		if (Boolean.FALSE.equals(file.exists() && file.canRead())) {
			throw new IllegalArgumentException("Unable to access file at given location.");
		}

		if (!file.getName().toLowerCase().endsWith(".csv")) {
			throw new IllegalArgumentException("Invalid file format. Only CSV files are accepted.");
		}

		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			String line;
			Set<String> headerSet = new HashSet<>();
			while ((line = reader.readLine()) != null) {
				if (!line.trim().isEmpty()) {
					String[] headers = line.split(COMMA_DELIMITER);
					for (String header : headers) {
						headerSet.add(header.trim());
					}
					break;
				}
			}
			if (line == null) {
				throw new IllegalArgumentException("CSV file is empty or contains only empty lines.");
			}

			for (String expectedHeader : aiUsageFileFormat.getExpectedHeaders()) {
				if (!headerSet.contains(expectedHeader)) {
					throw new IllegalArgumentException("Missing required header: " + expectedHeader);
				}
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("Error reading the file: " + e.getMessage());
		}
	}
}
