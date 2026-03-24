package com.publicissapient.kpidashboard.apis.listener;

import org.ehcache.event.CacheEvent;
import org.ehcache.event.CacheEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CacheEventLogger implements CacheEventListener<Object, Object> {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheEventLogger.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    @Override
    public void onEvent(CacheEvent<? extends Object, ? extends Object> event) {
        String timestamp = LocalDateTime.now().format(formatter);
        String cacheName = event.getSource().toString();
        String eventType = event.getType().toString();
        Object key = event.getKey();
        
        // Log all events for accountHierarchy cache
        if (cacheName.contains("accountHierarchy")) {
            switch (event.getType()) {
                case CREATED:
                    logger.info("[CACHE-EVENT] {} - {} - CREATED - Key: {} - Cache now has data", 
                               timestamp, cacheName, key);
                    break;
                    
                case UPDATED:
                    logger.info("[CACHE-EVENT] {} - {} - UPDATED - Key: {} - Cache data refreshed", 
                               timestamp, cacheName, key);
                    break;
                    
                case EVICTED:
                    logger.warn("[CACHE-EVENT] {} - {} - EVICTED - Key: {} - CRITICAL: Cache entry removed due to memory pressure!", 
                               timestamp, cacheName, key);
                    logMemoryInfo();
                    break;
                    
                case EXPIRED:
                    logger.warn("[CACHE-EVENT] {} - {} - EXPIRED - Key: {} - Cache entry expired", 
                               timestamp, cacheName, key);
                    break;
                    
                case REMOVED:
                    logger.warn("[CACHE-EVENT] {} - {} - REMOVED - Key: {} - Cache entry manually removed", 
                               timestamp, cacheName, key);
                    break;
                    
                default:
                    logger.info("[CACHE-EVENT] {} - {} - {} - Key: {}", 
                               timestamp, cacheName, eventType, key);
            }
        }
        
        // Log critical events for all caches
        if (event.getType().toString().equals("EVICTED")) {
            logger.warn("[CACHE-EVICTION] {} - Cache: {} - Key: {} - Evicted due to memory pressure", 
                       timestamp, cacheName, key);
        }
    }
    
    private void logMemoryInfo() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory() / (1024 * 1024);
            long freeMemory = runtime.freeMemory() / (1024 * 1024);
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory() / (1024 * 1024);
            
            logger.warn("[MEMORY-INFO] Total: {}MB, Used: {}MB, Free: {}MB, Max: {}MB", 
                       totalMemory, usedMemory, freeMemory, maxMemory);
            
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
            if (memoryUsagePercent > 80) {
                logger.error("[MEMORY-ALERT] Memory usage is {}% - This may cause cache evictions!", 
                           String.format("%.2f", memoryUsagePercent));
            }
        } catch (Exception e) {
            logger.error("[MEMORY-INFO-ERROR] Failed to get memory info: {}", e.getMessage());
        }
    }
}