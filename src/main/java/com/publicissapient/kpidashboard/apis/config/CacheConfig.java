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

package com.publicissapient.kpidashboard.apis.config;

import java.net.URI;
import java.net.URISyntaxException;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

	@Value("${spring.cache.type:jcache}")
	private String cacheType;

	@Bean
	public CacheManager cacheManager() throws URISyntaxException {
		if ("jcache".equalsIgnoreCase(cacheType)) {
			CachingProvider cachingProvider =
					Caching.getCachingProvider("org.ehcache.jsr107.EhcacheCachingProvider");
			URI ehcacheXml = getClass().getResource("/ehcache.xml").toURI();
			javax.cache.CacheManager jCacheManager =
					cachingProvider.getCacheManager(ehcacheXml, getClass().getClassLoader());
			return new JCacheCacheManager(jCacheManager);
		} else {
			return new ConcurrentMapCacheManager("default");
		}
	}
}
