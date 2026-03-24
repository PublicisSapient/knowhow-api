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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/debug")
public class CacheComparativeTestReport {

	private static final Logger logger = LoggerFactory.getLogger(CacheComparativeTestReport.class);
	private static final DateTimeFormatter formatter =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

	@Autowired private CacheManager cacheManager;

	// Test metrics storage
	private Map<String, TestMetrics> testHistory = new HashMap<>();
	private int testRunCounter = 0;

	@GetMapping("/generate-comparative-report")
	public Map<String, Object> generateComparativeReport() {
		logger.info("=".repeat(100));
		logger.info("🔍 STARTING COMPREHENSIVE CACHE COMPARATIVE TEST REPORT");
		logger.info("=".repeat(100));

		TestMetrics currentMetrics = collectCurrentMetrics();
		testRunCounter++;
		String testId =
				"TEST_RUN_"
						+ testRunCounter
						+ "_"
						+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

		testHistory.put(testId, currentMetrics);

		// Generate detailed report
		generateDetailedReport(testId, currentMetrics);

		// If we have multiple test runs, generate comparison
		if (testHistory.size() > 1) {
			generateHistoricalComparison();
		}

		// Generate recommendations
		generateRecommendations(currentMetrics);

		logger.info("=".repeat(100));
		logger.info("✅ COMPARATIVE TEST REPORT COMPLETED - Test ID: {}", testId);
		logger.info("=".repeat(100));

		Map<String, Object> response = new HashMap<>();
		response.put("testId", testId);
		response.put("status", "COMPLETED");
		response.put("metrics", currentMetrics.toMap());
		response.put("totalTestRuns", testHistory.size());

		return response;
	}

	private TestMetrics collectCurrentMetrics() {
		TestMetrics metrics = new TestMetrics();

		try {
			// JVM Memory Metrics
			MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
			MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

			metrics.heapUsedMB = heapUsage.getUsed() / (1024 * 1024);
			metrics.heapMaxMB = heapUsage.getMax() / (1024 * 1024);
			metrics.heapCommittedMB = heapUsage.getCommitted() / (1024 * 1024);
			metrics.memoryUsagePercent = (double) heapUsage.getUsed() / heapUsage.getMax() * 100;

			// Cache Metrics
			org.springframework.cache.Cache accountCache = cacheManager.getCache("accountHierarchy");
			if (accountCache != null) {
				metrics.cacheExists = true;
				metrics.cacheItemCount = getCacheItemCount(accountCache);
				metrics.cacheEmpty = metrics.cacheItemCount == 0;
				metrics.cacheConfiguration = getCacheConfiguration(accountCache);

				// Test cache operations
				testCacheOperations(accountCache, metrics);
			} else {
				metrics.cacheExists = false;
				metrics.cacheEmpty = true;
				metrics.cacheItemCount = 0;
			}

			// MBean Statistics
			collectMBeanStatistics(metrics);

			// Environment Classification
			classifyEnvironment(metrics);

			metrics.timestamp = LocalDateTime.now();

		} catch (Exception e) {
			logger.error("❌ Error collecting metrics: {}", e.getMessage());
			metrics.hasErrors = true;
			metrics.errorMessage = e.getMessage();
		}

		return metrics;
	}

	private void generateDetailedReport(String testId, TestMetrics metrics) {
		logger.info("📊 DETAILED TEST METRICS REPORT - {}", testId);
		logger.info("-".repeat(80));

		// Environment Info
		logger.info("🏗️  ENVIRONMENT CLASSIFICATION: {}", metrics.environmentType);
		logger.info("⏰ Test Timestamp: {}", metrics.timestamp.format(formatter));

		// Memory Analysis
		logger.info("💾 MEMORY ANALYSIS:");
		logger.info("   ├─ Heap Used: {} MB", metrics.heapUsedMB);
		logger.info("   ├─ Heap Max: {} MB", metrics.heapMaxMB);
		logger.info("   ├─ Heap Committed: {} MB", metrics.heapCommittedMB);
		logger.info("   ├─ Memory Usage: {:.2f}%", metrics.memoryUsagePercent);
		logger.info("   └─ Memory Pressure: {}", getMemoryPressureLevel(metrics.memoryUsagePercent));

		// Cache Analysis
		logger.info("🗄️  CACHE ANALYSIS:");
		logger.info("   ├─ Cache Exists: {}", metrics.cacheExists ? "✅ YES" : "❌ NO");
		logger.info("   ├─ Cache Empty: {}", metrics.cacheEmpty ? "❌ YES (PROBLEM!)" : "✅ NO");
		logger.info("   ├─ Item Count: {}", metrics.cacheItemCount);
		logger.info("   ├─ Configuration: {}", metrics.cacheConfiguration);
		logger.info("   ├─ Put Operation: {}", metrics.putOperationSuccess ? "✅ SUCCESS" : "❌ FAILED");
		logger.info("   ├─ Get Operation: {}", metrics.getOperationSuccess ? "✅ SUCCESS" : "❌ FAILED");
		logger.info(
				"   └─ Evict Operation: {}", metrics.evictOperationSuccess ? "✅ SUCCESS" : "❌ FAILED");

		// MBean Statistics
		if (metrics.cacheHits >= 0) {
			logger.info("📈 MBEAN STATISTICS:");
			logger.info("   ├─ Cache Hits: {}", metrics.cacheHits);
			logger.info("   ├─ Cache Misses: {}", metrics.cacheMisses);
			logger.info(
					"   ├─ Cache Evictions: {} {}",
					metrics.cacheEvictions,
					metrics.cacheEvictions > 0 ? "⚠️ (EVICTIONS DETECTED!)" : "✅");
			logger.info("   ├─ Cache Puts: {}", metrics.cachePuts);
			logger.info("   ├─ Cache Removals: {}", metrics.cacheRemovals);
			logger.info("   └─ Hit Ratio: {:.2f}%", metrics.hitRatio * 100);
		}

		// Problem Indicators
		if (metrics.hasProblems()) {
			logger.error("🚨 PROBLEM INDICATORS DETECTED:");
			if (metrics.cacheEmpty) {
				logger.error("   ❌ Cache is EMPTY - /filterdata API will return null response");
			}
			if (metrics.memoryUsagePercent > 75) {
				logger.error(
						"   ❌ HIGH memory pressure ({:.2f}%) - Risk of cache evictions",
						metrics.memoryUsagePercent);
			}
			if (metrics.cacheEvictions > 0) {
				logger.error(
						"   ❌ Cache evictions detected ({}) - Data being removed due to memory pressure",
						metrics.cacheEvictions);
			}
			if (!metrics.putOperationSuccess || !metrics.getOperationSuccess) {
				logger.error("   ❌ Cache operations failing - Cache may be non-functional");
			}
		} else {
			logger.info("✅ NO PROBLEMS DETECTED - Cache is functioning normally");
		}
	}

	private void generateHistoricalComparison() {
		logger.info("📈 HISTORICAL COMPARISON ANALYSIS");
		logger.info("-".repeat(80));

		TestMetrics latest = null;
		TestMetrics previous = null;

		// Get latest and previous metrics
		String[] testIds = testHistory.keySet().toArray(new String[0]);
		if (testIds.length >= 2) {
			latest = testHistory.get(testIds[testIds.length - 1]);
			previous = testHistory.get(testIds[testIds.length - 2]);

			logger.info(
					"🔄 COMPARING: {} vs {}", testIds[testIds.length - 2], testIds[testIds.length - 1]);

			// Memory Comparison
			double memoryChange = latest.memoryUsagePercent - previous.memoryUsagePercent;
			logger.info(
					"💾 Memory Usage: {:.2f}% → {:.2f}% ({}{}%)",
					previous.memoryUsagePercent,
					latest.memoryUsagePercent,
					memoryChange >= 0 ? "+" : "",
					memoryChange);

			// Cache Count Comparison
			int countChange = latest.cacheItemCount - previous.cacheItemCount;
			logger.info(
					"🗄️  Cache Items: {} → {} ({}{})",
					previous.cacheItemCount,
					latest.cacheItemCount,
					countChange >= 0 ? "+" : "",
					countChange);

			// Eviction Comparison
			long evictionChange = latest.cacheEvictions - previous.cacheEvictions;
			if (evictionChange > 0) {
				logger.error(
						"⚠️  Cache Evictions INCREASED: {} → {} (+{})",
						previous.cacheEvictions,
						latest.cacheEvictions,
						evictionChange);
			} else if (evictionChange < 0) {
				logger.info(
						"✅ Cache Evictions DECREASED: {} → {} ({})",
						previous.cacheEvictions,
						latest.cacheEvictions,
						evictionChange);
			} else {
				logger.info("➡️  Cache Evictions STABLE: {}", latest.cacheEvictions);
			}

			// Configuration Comparison
			if (!previous.cacheConfiguration.equals(latest.cacheConfiguration)) {
				logger.info("🔧 Configuration CHANGED:");
				logger.info("   FROM: {}", previous.cacheConfiguration);
				logger.info("   TO:   {}", latest.cacheConfiguration);
			}

			// Environment Classification Comparison
			if (!previous.environmentType.equals(latest.environmentType)) {
				logger.info(
						"🏗️  Environment Classification CHANGED: {} → {}",
						previous.environmentType,
						latest.environmentType);
			}
		}

		// Overall trend analysis
		if (testHistory.size() >= 3) {
			analyzeTrends();
		}
	}

	private void analyzeTrends() {
		logger.info("📊 TREND ANALYSIS (Last {} test runs)", testHistory.size());
		logger.info("-".repeat(50));

		TestMetrics[] allMetrics = testHistory.values().toArray(new TestMetrics[0]);

		// Memory trend
		double avgMemoryUsage = 0;
		int emptyCount = 0;
		long totalEvictions = 0;

		for (TestMetrics metrics : allMetrics) {
			avgMemoryUsage += metrics.memoryUsagePercent;
			if (metrics.cacheEmpty) emptyCount++;
			totalEvictions += metrics.cacheEvictions;
		}

		avgMemoryUsage /= allMetrics.length;
		double emptyPercentage = (double) emptyCount / allMetrics.length * 100;

		logger.info("📈 Average Memory Usage: {:.2f}%", avgMemoryUsage);
		logger.info(
				"📊 Cache Empty Rate: {:.1f}% ({}/{} runs)",
				emptyPercentage, emptyCount, allMetrics.length);
		logger.info("🔄 Total Evictions Across All Runs: {}", totalEvictions);

		// Stability assessment
		if (emptyPercentage > 50) {
			logger.error(
					"🚨 STABILITY ISSUE: Cache empty in {:.1f}% of test runs - CONFIGURATION PROBLEM!",
					emptyPercentage);
		} else if (emptyPercentage > 0) {
			logger.warn("⚠️  INTERMITTENT ISSUE: Cache occasionally empty - Monitor closely");
		} else {
			logger.info("✅ STABLE: Cache consistently has data across all test runs");
		}
	}

	private void generateRecommendations(TestMetrics metrics) {
		logger.info("💡 RECOMMENDATIONS & ACTION ITEMS");
		logger.info("-".repeat(80));

		if (metrics.cacheEmpty && metrics.memoryUsagePercent > 75) {
			logger.error("🔧 CRITICAL ACTION REQUIRED:");
			logger.error(
					"   1. ❌ Cache is empty due to HIGH memory pressure ({:.2f}%)",
					metrics.memoryUsagePercent);
			logger.error("   2. 🔄 IMMEDIATE FIX: Switch from MB to entries-based configuration");
			logger.error(
					"   3. 📝 Change: <heap unit=\"MB\">25</heap> → <heap unit=\"entries\">10000</heap>");
			logger.error("   4. 🚀 Expected Result: Stable cache with predictable memory usage");
		} else if (metrics.cacheEmpty) {
			logger.warn("🔍 INVESTIGATION REQUIRED:");
			logger.warn(
					"   1. ❓ Cache is empty but memory usage is normal ({:.2f}%)",
					metrics.memoryUsagePercent);
			logger.warn("   2. 🔍 Check cache loading logic and data population");
			logger.warn("   3. 📊 Verify /filterdata API is being called to populate cache");
		} else if (metrics.memoryUsagePercent > 75) {
			logger.warn("⚠️  PREVENTIVE ACTION RECOMMENDED:");
			logger.warn(
					"   1. 📈 High memory usage ({:.2f}%) - Risk of future cache evictions",
					metrics.memoryUsagePercent);
			logger.warn("   2. 🔄 Consider switching to entries-based configuration proactively");
			logger.warn("   3. 📊 Monitor cache evictions closely");
		} else {
			logger.info("✅ CONFIGURATION OPTIMAL:");
			logger.info("   1. 🎯 Cache has data ({} items)", metrics.cacheItemCount);
			logger.info("   2. 💾 Memory usage is healthy ({:.2f}%)", metrics.memoryUsagePercent);
			logger.info("   3. 🔄 No evictions detected");
			logger.info("   4. ✨ Current configuration is working well");
		}

		// Environment-specific recommendations
		if ("CLIENT_UAT_FAILING".equals(metrics.environmentType)) {
			logger.error("🏗️  CLIENT UAT ENVIRONMENT DETECTED:");
			logger.error("   ⚠️  This environment typically fails with MB configuration");
			logger.error("   🔧 Strongly recommend switching to entries configuration");
			logger.error("   📊 Expected improvement: 0 items → 1000+ items in cache");
		} else if ("PROD_WORKING".equals(metrics.environmentType)) {
			logger.info("🏗️  PRODUCTION ENVIRONMENT DETECTED:");
			logger.info("   ✅ Environment has sufficient resources for current configuration");
			logger.info("   📊 Continue monitoring for any degradation");
		}
	}

	// Scheduled automatic report generation
	@Scheduled(fixedRate = 300000) // Every 5 minutes
	public void scheduledComparativeReport() {
		if (testRunCounter > 0) { // Only run if manual test has been triggered at least once
			logger.info("🔄 SCHEDULED COMPARATIVE REPORT - Run #{}", testRunCounter + 1);
			generateComparativeReport();
		}
	}

	// Helper methods
	private int getCacheItemCount(org.springframework.cache.Cache cache) {
		try {
			Object nativeCache = cache.getNativeCache();
			if (nativeCache instanceof org.ehcache.Cache) {
				org.ehcache.Cache<?, ?> ehcache = (org.ehcache.Cache<?, ?>) nativeCache;
				int count = 0;
				for (org.ehcache.Cache.Entry<?, ?> entry : ehcache) {
					count++;
					if (count > 10000) return count; // Limit for performance
				}
				return count;
			}
		} catch (Exception e) {
			logger.debug("Error counting cache items: {}", e.getMessage());
		}
		return -1;
	}

	private String getCacheConfiguration(org.springframework.cache.Cache cache) {
		try {
			Object nativeCache = cache.getNativeCache();
			if (nativeCache instanceof org.ehcache.Cache) {
				org.ehcache.Cache<?, ?> ehcache = (org.ehcache.Cache<?, ?>) nativeCache;
				String config = ehcache.getRuntimeConfiguration().toString();
				if (config.contains("heap")) {
					String heapConfig = config.substring(config.indexOf("heap"));
					if (heapConfig.contains("}")) {
						heapConfig = heapConfig.substring(0, heapConfig.indexOf("}") + 1);
					}
					return heapConfig;
				}
				return config;
			}
		} catch (Exception e) {
			logger.debug("Error getting cache configuration: {}", e.getMessage());
		}
		return "UNKNOWN";
	}

	private void testCacheOperations(org.springframework.cache.Cache cache, TestMetrics metrics) {
		try {
			String testKey = "comparative-test-" + System.currentTimeMillis();
			String testValue = "test-value-" + System.currentTimeMillis();

			// Test put
			cache.put(testKey, testValue);
			metrics.putOperationSuccess = true;

			// Test get
			org.springframework.cache.Cache.ValueWrapper result = cache.get(testKey);
			metrics.getOperationSuccess = result != null && result.get() != null;

			// Test evict
			cache.evict(testKey);
			metrics.evictOperationSuccess = true;

		} catch (Exception e) {
			logger.debug("Error testing cache operations: {}", e.getMessage());
			metrics.putOperationSuccess = false;
			metrics.getOperationSuccess = false;
			metrics.evictOperationSuccess = false;
		}
	}

	private void collectMBeanStatistics(TestMetrics metrics) {
		try {
			MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			Set<ObjectName> cacheNames = server.queryNames(new ObjectName("javax.cache:*"), null);

			for (ObjectName name : cacheNames) {
				if (name.toString().contains("accountHierarchy")) {
					try {
						metrics.cacheHits = (Long) server.getAttribute(name, "CacheHits");
						metrics.cacheMisses = (Long) server.getAttribute(name, "CacheMisses");
						metrics.cacheEvictions = (Long) server.getAttribute(name, "CacheEvictions");
						metrics.cachePuts = (Long) server.getAttribute(name, "CachePuts");
						metrics.cacheRemovals = (Long) server.getAttribute(name, "CacheRemovals");

						if (metrics.cacheHits + metrics.cacheMisses > 0) {
							metrics.hitRatio =
									(double) metrics.cacheHits / (metrics.cacheHits + metrics.cacheMisses);
						}
						break;
					} catch (Exception e) {
						logger.debug("Error getting MBean statistics: {}", e.getMessage());
					}
				}
			}
		} catch (Exception e) {
			logger.debug("MBean monitoring not available: {}", e.getMessage());
		}
	}

	private void classifyEnvironment(TestMetrics metrics) {
		if (metrics.cacheEmpty && metrics.memoryUsagePercent > 75) {
			metrics.environmentType = "CLIENT_UAT_FAILING";
		} else if (!metrics.cacheEmpty && metrics.memoryUsagePercent < 60) {
			metrics.environmentType = "PROD_WORKING";
		} else if (metrics.cacheEmpty) {
			metrics.environmentType = "CACHE_LOADING_ISSUE";
		} else if (metrics.memoryUsagePercent > 75) {
			metrics.environmentType = "HIGH_MEMORY_PRESSURE";
		} else {
			metrics.environmentType = "NORMAL_OPERATION";
		}
	}

	private String getMemoryPressureLevel(double memoryUsagePercent) {
		if (memoryUsagePercent > 85) return "CRITICAL 🔴";
		if (memoryUsagePercent > 75) return "HIGH 🟡";
		if (memoryUsagePercent > 60) return "MEDIUM 🟠";
		return "LOW 🟢";
	}

	// Inner class for test metrics
	private static class TestMetrics {
		LocalDateTime timestamp;

		// Memory metrics
		long heapUsedMB;
		long heapMaxMB;
		long heapCommittedMB;
		double memoryUsagePercent;

		// Cache metrics
		boolean cacheExists;
		boolean cacheEmpty;
		int cacheItemCount;
		String cacheConfiguration;
		String environmentType;

		// Operation test results
		boolean putOperationSuccess;
		boolean getOperationSuccess;
		boolean evictOperationSuccess;

		// MBean statistics
		long cacheHits = -1;
		long cacheMisses = -1;
		long cacheEvictions = -1;
		long cachePuts = -1;
		long cacheRemovals = -1;
		double hitRatio = -1;

		// Error tracking
		boolean hasErrors;
		String errorMessage;

		public boolean hasProblems() {
			return cacheEmpty
					|| memoryUsagePercent > 75
					|| cacheEvictions > 0
					|| !putOperationSuccess
					|| !getOperationSuccess
					|| hasErrors;
		}

		public Map<String, Object> toMap() {
			Map<String, Object> map = new HashMap<>();
			map.put("timestamp", timestamp.toString());
			map.put("heapUsedMB", heapUsedMB);
			map.put("heapMaxMB", heapMaxMB);
			map.put("memoryUsagePercent", memoryUsagePercent);
			map.put("cacheExists", cacheExists);
			map.put("cacheEmpty", cacheEmpty);
			map.put("cacheItemCount", cacheItemCount);
			map.put("cacheConfiguration", cacheConfiguration);
			map.put("environmentType", environmentType);
			map.put("cacheEvictions", cacheEvictions);
			map.put("hasProblems", hasProblems());
			return map;
		}
	}
}
