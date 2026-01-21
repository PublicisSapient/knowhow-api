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

package com.publicissapient.kpidashboard.apis.common.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * REST controller managing all cache request
 *
 * @author anisingh4
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Cache Controller", description = "APIs for Cache Management")
public class CacheController {

	private final CacheService service;

	/**
	 * Clear Specified cache.
	 *
	 * @param cacheName the cache name
	 */
	@GetMapping(value = "/cache/clearCache/{cacheName}", produces = APPLICATION_JSON_VALUE)
	public void clearCache(@PathVariable String cacheName) {
		service.clearCache(cacheName);
	}

	/** Clear all cache. */
	@GetMapping(value = "/cache/clearAllCache", produces = APPLICATION_JSON_VALUE)
	public void clearAllCache() {
		service.clearAllCache();
	}
}
