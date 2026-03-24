package com.publicissapient.kpidashboard.apis.debug;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/debug")
public class CacheStatisticsController {

	@Autowired private CacheManager cacheManager;

	@GetMapping("/cache-statistics")
	public Map<String, Object> getCacheStatistics() {
		Map<String, Object> stats = new HashMap<>();

		try {
			// JVM Memory Statistics
			MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
			MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
			MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

			Map<String, Object> jvmStats = new HashMap<>();
			jvmStats.put("heapUsed", heapUsage.getUsed() / (1024 * 1024) + " MB");
			jvmStats.put("heapMax", heapUsage.getMax() / (1024 * 1024) + " MB");
			jvmStats.put("heapCommitted", heapUsage.getCommitted() / (1024 * 1024) + " MB");
			jvmStats.put("nonHeapUsed", nonHeapUsage.getUsed() / (1024 * 1024) + " MB");
			stats.put("jvmMemory", jvmStats);

			// Cache Manager Info
			stats.put("cacheManagerClass", cacheManager.getClass().getSimpleName());
			stats.put("availableCaches", cacheManager.getCacheNames());

			// Individual Cache Statistics
			Map<String, Object> cacheStats = new HashMap<>();

			// Important caches to monitor
			String[] importantCaches = {
				"accountHierarchy",
				"accountHierarchyKanban",
				"sprintHierarchy",
				"projectConfigMap",
				"filters",
				"hierarchyLevelCache",
				"projectToolConfigMap"
			};

			for (String cacheName : importantCaches) {
				org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
				if (cache != null) {
					Map<String, Object> cacheInfo = getCacheDetails(cache);
					cacheStats.put(cacheName, cacheInfo);
				} else {
					Map<String, Object> cacheInfo = new HashMap<>();
					cacheInfo.put("status", "NOT_FOUND");
					cacheInfo.put("exists", false);
					cacheStats.put(cacheName, cacheInfo);
				}
			}

			stats.put("caches", cacheStats);

			// MBean Statistics (if available)
			try {
				MBeanServer server = ManagementFactory.getPlatformMBeanServer();
				Set<ObjectName> cacheNames = server.queryNames(new ObjectName("javax.cache:*"), null);

				Map<String, Object> mbeanStats = new HashMap<>();
				for (ObjectName name : cacheNames) {
					if (name.toString().contains("accountHierarchy")) {
						try {
							Long hits = (Long) server.getAttribute(name, "CacheHits");
							Long misses = (Long) server.getAttribute(name, "CacheMisses");
							Long evictions = (Long) server.getAttribute(name, "CacheEvictions");
							Long puts = (Long) server.getAttribute(name, "CachePuts");
							Long removals = (Long) server.getAttribute(name, "CacheRemovals");

							Map<String, Object> mbeanData = new HashMap<>();
							mbeanData.put("hits", hits);
							mbeanData.put("misses", misses);
							mbeanData.put("evictions", evictions);
							mbeanData.put("puts", puts);
							mbeanData.put("removals", removals);
							mbeanData.put("hitRatio", hits + misses > 0 ? (double) hits / (hits + misses) : 0.0);

							mbeanStats.put(name.toString(), mbeanData);
						} catch (Exception e) {
							mbeanStats.put(name.toString(), "Error: " + e.getMessage());
						}
					}
				}
				stats.put("mbeanStatistics", mbeanStats);
			} catch (Exception e) {
				stats.put("mbeanError", e.getMessage());
			}

		} catch (Exception e) {
			stats.put("error", e.getMessage());
		}

		return stats;
	}

	private Map<String, Object> getCacheDetails(org.springframework.cache.Cache cache) {
		Map<String, Object> details = new HashMap<>();

		try {
			details.put("status", "EXISTS");
			details.put("exists", true);
			details.put("cacheName", cache.getName());
			details.put("cacheClass", cache.getClass().getSimpleName());

			// Get native cache for more details
			Object nativeCache = cache.getNativeCache();
			details.put("nativeCacheClass", nativeCache.getClass().getSimpleName());
			details.put("nativeCacheString", nativeCache.toString());

			// Try to get cache statistics if it's an Ehcache
			if (nativeCache instanceof org.ehcache.Cache) {
				org.ehcache.Cache<?, ?> ehcache = (org.ehcache.Cache<?, ?>) nativeCache;
				details.put("runtimeConfiguration", ehcache.getRuntimeConfiguration().toString());

				// Try to count items
				int itemCount = getEhcacheItemCount(ehcache);
				details.put("itemCount", itemCount);
				details.put("isEmpty", itemCount == 0);
			} else {
				details.put("itemCount", "UNKNOWN - Not an Ehcache");
			}

		} catch (Exception e) {
			details.put("error", e.getMessage());
		}

		return details;
	}

	private int getEhcacheItemCount(org.ehcache.Cache<?, ?> cache) {
		int count = 0;
		try {
			for (org.ehcache.Cache.Entry<?, ?> entry : cache) {
				count++;
				// Limit counting to prevent performance issues
				if (count > 10000) {
					return count; // Return approximate count
				}
			}
		} catch (Exception e) {
			return -1; // Unknown count
		}
		return count;
	}

	@GetMapping("/cache-test-operations")
	public Map<String, Object> testCacheOperations() {
		Map<String, Object> results = new HashMap<>();

		try {
			org.springframework.cache.Cache cache = cacheManager.getCache("accountHierarchy");

			if (cache != null) {
				// Test put operation
				String testKey = "debug-test-" + System.currentTimeMillis();
				String testValue = "test-value-" + System.currentTimeMillis();

				cache.put(testKey, testValue);
				results.put("putOperation", "SUCCESS");

				// Test get operation
				org.springframework.cache.Cache.ValueWrapper retrieved = cache.get(testKey);
				boolean getSuccess = retrieved != null && retrieved.get() != null;
				results.put("getOperation", getSuccess ? "SUCCESS" : "FAILED");
				results.put("retrievedValue", retrieved != null ? retrieved.get() : null);

				// Test evict operation
				cache.evict(testKey);
				results.put("evictOperation", "SUCCESS");

				// Test cache after eviction
				org.springframework.cache.Cache.ValueWrapper afterEviction = cache.get(testKey);
				results.put("afterEvictionCheck", afterEviction == null ? "SUCCESS" : "FAILED");

			} else {
				results.put("error", "accountHierarchy cache not found");
			}

		} catch (Exception e) {
			results.put("error", e.getMessage());
		}

		return results;
	}

	@GetMapping("/cache-detailed-count")
	public Map<String, Object> getDetailedCacheCount() {
		Map<String, Object> result = new HashMap<>();

		try {
			// Focus on critical caches with detailed counting
			String[] criticalCaches = {
				"accountHierarchy",
				"accountHierarchyKanban",
				"sprintHierarchy",
				"projectConfigMap",
				"filters",
				"hierarchyLevelCache"
			};

			Map<String, Object> cacheCounts = new HashMap<>();
			int totalItems = 0;

			for (String cacheName : criticalCaches) {
				org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
				if (cache != null) {
					Map<String, Object> cacheInfo = new HashMap<>();

					// Get native cache and try to count items
					Object nativeCache = cache.getNativeCache();
					int count = -1;

					if (nativeCache instanceof org.ehcache.Cache) {
						count = getEhcacheItemCount((org.ehcache.Cache<?, ?>) nativeCache);
					}

					cacheInfo.put("itemCount", count);
					cacheInfo.put("isEmpty", count == 0);
					cacheInfo.put("nativeCacheType", nativeCache.getClass().getSimpleName());

					cacheCounts.put(cacheName, cacheInfo);
					if (count > 0) {
						totalItems += count;
					}
				} else {
					Map<String, Object> cacheInfo = new HashMap<>();
					cacheInfo.put("status", "NOT_FOUND");
					cacheInfo.put("itemCount", 0);
					cacheInfo.put("isEmpty", true);
					cacheCounts.put(cacheName, cacheInfo);
				}
			}

			result.put("cacheCounts", cacheCounts);
			result.put("totalItemsAcrossAllCaches", totalItems);
			result.put("timestamp", LocalDateTime.now().toString());

			// Memory info for context
			Runtime runtime = Runtime.getRuntime();
			Map<String, String> memoryInfo = new HashMap<>();
			memoryInfo.put(
					"usedMemory", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) + " MB");
			memoryInfo.put("freeMemory", runtime.freeMemory() / (1024 * 1024) + " MB");
			memoryInfo.put("totalMemory", runtime.totalMemory() / (1024 * 1024) + " MB");
			memoryInfo.put("maxMemory", runtime.maxMemory() / (1024 * 1024) + " MB");
			result.put("memoryInfo", memoryInfo);

		} catch (Exception e) {
			result.put("error", e.getMessage());
		}

		return result;
	}

	@GetMapping("/cache-count-comparison")
	public Map<String, Object> getCacheCountComparison() {
		Map<String, Object> comparison = new HashMap<>();

		try {
			org.springframework.cache.Cache cache = cacheManager.getCache("accountHierarchy");

			if (cache != null) {
				Object nativeCache = cache.getNativeCache();
				int currentCount = -1;

				if (nativeCache instanceof org.ehcache.Cache) {
					org.ehcache.Cache<?, ?> ehcache = (org.ehcache.Cache<?, ?>) nativeCache;
					currentCount = getEhcacheItemCount(ehcache);

					// Get configuration info
					String config = ehcache.getRuntimeConfiguration().toString();
					comparison.put("cacheConfiguration", config);

					// Extract heap configuration
					if (config.contains("heap")) {
						String heapConfig = config.substring(config.indexOf("heap"));
						if (heapConfig.contains("}")) {
							heapConfig = heapConfig.substring(0, heapConfig.indexOf("}") + 1);
						}
						comparison.put("heapConfiguration", heapConfig);
					}
				}

				// Store count with timestamp for comparison
				String timestamp =
						LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

				comparison.put("currentCount", currentCount);
				comparison.put("timestamp", timestamp);
				comparison.put("isEmpty", currentCount == 0);
				comparison.put("nativeCacheType", nativeCache.getClass().getSimpleName());

				// Memory pressure indicator
				Runtime runtime = Runtime.getRuntime();
				long usedMemory = runtime.totalMemory() - runtime.freeMemory();
				long maxMemory = runtime.maxMemory();
				double memoryUsagePercent = (double) usedMemory / maxMemory * 100;

				comparison.put("memoryUsagePercent", String.format("%.2f%%", memoryUsagePercent));
				comparison.put(
						"memoryPressure",
						memoryUsagePercent > 75 ? "HIGH" : memoryUsagePercent > 50 ? "MEDIUM" : "LOW");

				// Recommendation based on count and memory
				if (currentCount == 0 && memoryUsagePercent > 75) {
					comparison.put(
							"recommendation",
							"Cache is empty due to memory pressure. Consider switching to entries-based configuration.");
				} else if (currentCount == 0) {
					comparison.put(
							"recommendation",
							"Cache is empty but memory usage is normal. Check cache loading logic.");
				} else if (currentCount == -1) {
					comparison.put(
							"recommendation",
							"Cannot determine cache count. Cache may not be an Ehcache instance.");
				} else {
					comparison.put("recommendation", "Cache has data and is functioning normally.");
				}

			} else {
				comparison.put("error", "accountHierarchy cache not found");
			}

		} catch (Exception e) {
			comparison.put("error", e.getMessage());
		}

		return comparison;
	}

	@GetMapping("/gc-info")
	public Map<String, Object> getGCInfo() {
		Map<String, Object> gcInfo = new HashMap<>();

		try {
			// Force garbage collection for testing
			long beforeGC = Runtime.getRuntime().freeMemory();
			System.gc();
			Thread.sleep(100); // Give GC time to run
			long afterGC = Runtime.getRuntime().freeMemory();

			gcInfo.put("memoryBeforeGC", beforeGC / (1024 * 1024) + " MB");
			gcInfo.put("memoryAfterGC", afterGC / (1024 * 1024) + " MB");
			gcInfo.put("memoryFreed", (afterGC - beforeGC) / (1024 * 1024) + " MB");

			Runtime runtime = Runtime.getRuntime();
			gcInfo.put("totalMemory", runtime.totalMemory() / (1024 * 1024) + " MB");
			gcInfo.put("freeMemory", runtime.freeMemory() / (1024 * 1024) + " MB");
			gcInfo.put("maxMemory", runtime.maxMemory() / (1024 * 1024) + " MB");
			gcInfo.put(
					"usedMemory", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) + " MB");

		} catch (Exception e) {
			gcInfo.put("error", e.getMessage());
		}

		return gcInfo;
	}
}
