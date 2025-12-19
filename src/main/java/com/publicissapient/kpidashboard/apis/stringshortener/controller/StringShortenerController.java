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

package com.publicissapient.kpidashboard.apis.stringshortener.controller;

import java.util.Optional;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.stringshortener.dto.StringShortenerDTO;
import com.publicissapient.kpidashboard.apis.stringshortener.model.StringShortener;
import com.publicissapient.kpidashboard.apis.stringshortener.service.StringShortenerService;

@RestController
@RequestMapping("/stringShortener")
@RequiredArgsConstructor
@Tag(name = "String Shortener Controller", description = "APIs for String Shortener Management")
public class StringShortenerController {

	private static final String SHORT_STRING_RESPONSE_MESSAGE = "Successfully Created Short String";
	private static final String FAILURE_RESPONSE_MESSAGE = "Invalid URL.";
	private static final String FETCH_SUCCESS_MESSAGE = "Successfully fetched";

	private final StringShortenerService stringShortenerService;

	@PostMapping("/shorten")
	public ResponseEntity<ServiceResponse> createShortString(
			@RequestBody StringShortenerDTO stringShortenerDTO) {
		StringShortener stringShortener = stringShortenerService.createShortString(stringShortenerDTO);
		final ModelMapper modelMapper = new ModelMapper();
		final StringShortenerDTO responseDTO =
				modelMapper.map(stringShortener, StringShortenerDTO.class);
		if (responseDTO != null && !responseDTO.toString().isEmpty()) {
			ServiceResponse response = new ServiceResponse(true, SHORT_STRING_RESPONSE_MESSAGE, responseDTO);
			return ResponseEntity.status(HttpStatus.OK).body(response);
		} else {
			ServiceResponse response = new ServiceResponse(false, FAILURE_RESPONSE_MESSAGE, null);
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
		}
	}

	@GetMapping("/longString")
	public ResponseEntity<ServiceResponse> getLongString(
			@RequestParam String kpiFilters, @RequestParam String stateFilters) {
		Optional<StringShortener> stringShortener =
				stringShortenerService.getLongString(kpiFilters, stateFilters);
		if (stringShortener.isPresent()) {
			final ModelMapper modelMapper = new ModelMapper();
			final StringShortenerDTO responseDTO =
					modelMapper.map(stringShortener.get(), StringShortenerDTO.class);
			ServiceResponse response = new ServiceResponse(true, FETCH_SUCCESS_MESSAGE, responseDTO);
			return ResponseEntity.status(HttpStatus.OK).body(response);
		} else {
			ServiceResponse response = new ServiceResponse(false, FAILURE_RESPONSE_MESSAGE, null);
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
		}
	}
}
