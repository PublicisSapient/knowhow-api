package com.publicissapient.kpidashboard.apis.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Set;

@Component
public class CacheHealthMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheHealthMonitor.class);
    
    @Autowired
    private CacheManager cacheManager;
    
    private boolean accountHierarchyCacheWasEmpty = false;
    private long lastEvictionCount = 0;
    
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void monitorCacheHealth() {
        try {
            // Monitor accountHierarchy cache specifically
            org.springframework.cache.Cache accountCache = cacheManager.getCache("accountHierarchy");
            
            if (accountCache != null) {
                // Check if cache appears empty and get actual count
                boolean cacheEmpty = isCacheEmpty(accountCache);
                int actualCount = getCacheItemCount(accountCache);
                
                logger.info("[CACHE-HEALTH] accountHierarchy cache - Empty: {}, Item Count: {}", cacheEmpty, actualCount);
                
                if (cacheEmpty && !accountHierarchyCacheWasEmpty) {
                    logger.error("[CACHE-ALERT] accountHierarchy cache is EMPTY! This will cause /filterdata API to return null response");
                    logger.error("[CACHE-ALERT] Actual item count: {}", actualCount);
                    logDetailedCacheInfo();
                    accountHierarchyCacheWasEmpty = true;
                } else if (!cacheEmpty && accountHierarchyCacheWasEmpty) {
                    logger.info("[CACHE-RECOVERY] accountHierarchy cache has data again - Item count: {}", actualCount);
                    accountHierarchyCacheWasEmpty = false;
                }
                
                // Check for evictions
                checkForEvictions();
                
            } else {
                logger.error("[CACHE-ALERT] accountHierarchy cache not found in CacheManager!");
            }
            
        } catch (Exception e) {
            logger.error("[CACHE-MONITOR-ERROR] Error monitoring cache health: {}", e.getMessage());
        }
    }
    
    private int getCacheItemCount(org.springframework.cache.Cache cache) {
        try {
            Object nativeCache = cache.getNativeCache();
            if (nativeCache instanceof org.ehcache.Cache) {
                org.ehcache.Cache<?, ?> ehcache = (org.ehcache.Cache<?, ?>) nativeCache;
                int count = 0;
                for (org.ehcache.Cache.Entry<?, ?> entry : ehcache) {
                    count++;
                    // Limit counting for performance
                    if (count > 1000) {
                        logger.info("[CACHE-COUNT] Stopped counting at 1000+ items for performance");
                        return count;
                    }
                }
                return count;
            } else {
                logger.debug("[CACHE-COUNT] Native cache is not an Ehcache instance: {}", nativeCache.getClass().getSimpleName());
                return -1; // Unknown count for non-Ehcache implementations
            }
        } catch (Exception e) {
            logger.debug("[CACHE-COUNT-ERROR] Could not count cache items: {}", e.getMessage());
            return -1; // Unknown count
        }
    }
    
    private boolean isCacheEmpty(org.springframework.cache.Cache cache) {
        try {
            // Method 1: Try to count actual items using native cache
            Object nativeCache = cache.getNativeCache();
            if (nativeCache instanceof org.ehcache.Cache) {
                org.ehcache.Cache<?, ?> ehcache = (org.ehcache.Cache<?, ?>) nativeCache;
                for (org.ehcache.Cache.Entry<?, ?> entry : ehcache) {
                    return false; // Found at least one item
                }
                // If we reach here, no items were found
                logger.warn("[CACHE-COUNT] accountHierarchy cache has 0 items");
                return true;
            } else {
                // Method 2: Fallback - try to put and get a test value for non-Ehcache
                String testKey = "health-check-" + System.currentTimeMillis();
                cache.put(testKey, "test");
                org.springframework.cache.Cache.ValueWrapper result = cache.get(testKey);
                cache.evict(testKey);
                
                boolean isEmpty = result == null || result.get() == null;
                if (isEmpty) {
                    logger.warn("[CACHE-TEST] accountHierarchy cache test failed - cache appears empty or non-functional");
                }
                return isEmpty;
            }
        } catch (Exception e) {
            logger.error("[CACHE-EMPTY-CHECK] Error checking if cache is empty: {}", e.getMessage());
            return true; // Assume empty if we can't check
        }
    }
    
    private void checkForEvictions() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            Set<ObjectName> cacheNames = server.queryNames(new ObjectName("javax.cache:*"), null);
            
            for (ObjectName name : cacheNames) {
                if (name.toString().contains("accountHierarchy")) {
                    try {
                        Long evictions = (Long) server.getAttribute(name, "CacheEvictions");
                        if (evictions != null && evictions > lastEvictionCount) {
                            logger.warn("[EVICTION-ALERT] accountHierarchy cache evictions increased from {} to {}", 
                                       lastEvictionCount, evictions);
                            lastEvictionCount = evictions;
                            logDetailedCacheInfo();
                        }
                    } catch (Exception e) {
                        // MBean might not be available
                    }
                }
            }
        } catch (Exception e) {
            // MBean monitoring not available
        }
    }
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void logMemoryStatus() {
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            
            long usedMB = heapUsage.getUsed() / (1024 * 1024);
            long maxMB = heapUsage.getMax() / (1024 * 1024);
            double usagePercent = (double) heapUsage.getUsed() / heapUsage.getMax() * 100;
            
            if (usagePercent > 75) {
                logger.warn("[MEMORY-ALERT] High memory usage: {}MB/{}MB ({}%) - Risk of cache evictions!", 
                           usedMB, maxMB, String.format("%.1f", usagePercent));
            } else {
                logger.debug("[MEMORY-STATUS] Memory usage: {}MB/{}MB ({}%)", 
                            usedMB, maxMB, String.format("%.1f", usagePercent));
            }
            
        } catch (Exception e) {
            logger.error("[MEMORY-MONITOR-ERROR] Error monitoring memory: {}", e.getMessage());
        }
    }
    
    private void logDetailedCacheInfo() {
        try {
            Runtime runtime = Runtime.getRuntime();
            logger.info("[DETAILED-CACHE-INFO] JVM Memory - Total: {}MB, Free: {}MB, Used: {}MB, Max: {}MB",
                       runtime.totalMemory() / (1024 * 1024),
                       runtime.freeMemory() / (1024 * 1024),
                       (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
                       runtime.maxMemory() / (1024 * 1024));
            
            // Log cache manager status
            logger.info("[DETAILED-CACHE-INFO] CacheManager class: {}", cacheManager.getClass().getSimpleName());
            logger.info("[DETAILED-CACHE-INFO] Available caches: {}", cacheManager.getCacheNames());
            
            // Try to get MBean statistics
            try {
                MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                Set<ObjectName> cacheNames = server.queryNames(new ObjectName("javax.cache:*"), null);
                
                for (ObjectName name : cacheNames) {
                    if (name.toString().contains("accountHierarchy")) {
                        try {
                            Long hits = (Long) server.getAttribute(name, "CacheHits");
                            Long misses = (Long) server.getAttribute(name, "CacheMisses");
                            Long evictions = (Long) server.getAttribute(name, "CacheEvictions");
                            Long puts = (Long) server.getAttribute(name, "CachePuts");
                            
                            logger.info("[DETAILED-CACHE-INFO] {} - Hits: {}, Misses: {}, Evictions: {}, Puts: {}",
                                       name, hits, misses, evictions, puts);
                        } catch (Exception e) {
                            logger.debug("[DETAILED-CACHE-INFO] Could not get MBean stats for {}: {}", name, e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("[DETAILED-CACHE-INFO] MBean monitoring not available: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("[DETAILED-CACHE-INFO-ERROR] Error logging detailed cache info: {}", e.getMessage());
        }
    }
}