package com.publicissapient.kpidashboard.apis.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() throws URISyntaxException {
        CachingProvider cachingProvider = Caching.getCachingProvider("org.ehcache.jsr107.EhcacheCachingProvider");
        // Note: The resource path starts with a '/' to search from the root of the classpath
        URI ehcacheXml = getClass().getResource("/ehcache.xml").toURI();
        javax.cache.CacheManager jCacheManager = cachingProvider.getCacheManager(ehcacheXml, getClass().getClassLoader());
        return new JCacheCacheManager(jCacheManager);
    }
}