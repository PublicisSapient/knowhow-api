package com.publicissapient.kpidashboard.apis.executive.service;

import com.publicissapient.kpidashboard.common.model.application.KpiCategory;
import com.publicissapient.kpidashboard.common.repository.application.KpiCategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating project efficiency based on board maturities and weightages.
 */
@Service
@Slf4j
public class ProjectEfficiencyService {

    @Value("${project.efficiency.weightage:}")
    private String efficiencyWeightageConfig;

    private final KpiCategoryRepository kpiCategoryRepository;

    // Default categories and their weightages (will be overridden by config if provided)
    private static final Map<String, Integer> DEFAULT_WEIGHTAGES = Map.of(
        "SPEED", 25,
        "QUALITY", 25,
        "VALUE", 25,
        "DORA", 25
    );

    // Default categories that are always included
    private static final Set<String> DEFAULT_CATEGORIES = Set.of("DORA");

    @Autowired
    public ProjectEfficiencyService(KpiCategoryRepository kpiCategoryRepository) {
        this.kpiCategoryRepository = kpiCategoryRepository;
    }

    /**
     * Calculates the project efficiency score based on board maturities and weightages
     * @param boardMaturities Map of board names to their maturity scores (1-5)
     * @return Map containing efficiency score and health status
     */
    public Map<String, Object> calculateProjectEfficiency(Map<String, Integer> boardMaturities) {
        // Get configured weightages or use defaults
        Map<String, Integer> weightages = parseWeightageConfig();
        
        // Get all categories (default + any from DB)
        Set<String> categories = new HashSet<>(DEFAULT_CATEGORIES);
        categories.addAll(getCategoriesFromDatabase());
        
        // Ensure all categories have a weightage, using default if not specified
        for (String category : categories) {
            weightages.putIfAbsent(category.toUpperCase(), 
                DEFAULT_WEIGHTAGES.getOrDefault(category.toUpperCase(), 0));
        }
        
        // Normalize weightages to ensure they sum to 100%
        normalizeWeightages(weightages);
        
        // Calculate weighted score
        double totalScore = 0.0;
        double totalWeight = 0.0;
        
        for (Map.Entry<String, Integer> entry : weightages.entrySet()) {
            String category = entry.getKey();
            int weight = entry.getValue();
            
            // Find the best matching board for this category
            int maturity = findBestMatchingBoardMaturity(category, boardMaturities);
            
            if (maturity > 0) {
                totalScore += maturity * weight;
                totalWeight += weight;
            }
        }
        
        // Calculate final score (0-5 scale)
        double efficiencyScore = totalWeight > 0 ? totalScore / 100.0 : 0;
        
        // Convert to percentage (0-100%)
        double efficiencyPercentage = (efficiencyScore / 5.0) * 100;
        
        // Determine health status
        String healthStatus = calculateHealthStatus(efficiencyPercentage);
        
        // Prepare result
        Map<String, Object> result = new HashMap<>();
        result.put("score", Math.round(efficiencyScore * 10.0) / 10.0); // Round to 1 decimal place
        result.put("percentage", Math.round(efficiencyPercentage * 10.0) / 10.0); // Round to 1 decimal place
        result.put("healthStatus", healthStatus);
        result.put("weightages", weightages);
        
        return result;
    }
    
    private String calculateHealthStatus(double efficiencyPercentage) {
        if (efficiencyPercentage >= 80) {
            return "GREEN";
        } else if (efficiencyPercentage >= 50) {
            return "AMBER";
        } else {
            return "RED";
        }
    }
    
    /**
     * Parses the weightage configuration from properties
     * @return Map of category to weightage
     */
    private Map<String, Integer> parseWeightageConfig() {
        Map<String, Integer> weightages = new HashMap<>();
        
        if (StringUtils.isBlank(efficiencyWeightageConfig)) {
            return new HashMap<>(DEFAULT_WEIGHTAGES);
        }
        
        try {
            String[] pairs = efficiencyWeightageConfig.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    String category = keyValue[0].trim().toUpperCase();
                    int weight = Integer.parseInt(keyValue[1].trim());
                    weightages.put(category, weight);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing efficiency weightage configuration. Using defaults.", e);
            return new HashMap<>(DEFAULT_WEIGHTAGES);
        }
        
        return weightages;
    }
    
    /**
     * Normalizes weightages to ensure they sum to 100
     */
    private void normalizeWeightages(Map<String, Integer> weightages) {
        if (weightages.isEmpty()) {
            weightages.putAll(DEFAULT_WEIGHTAGES);
            return;
        }
        
        int totalWeight = weightages.values().stream().mapToInt(Integer::intValue).sum();
        
        if (totalWeight != 100) {
            // Scale all weights proportionally to sum to 100
            double scale = 100.0 / totalWeight;
            weightages.replaceAll((k, v) -> (int) Math.round(v * scale));
            
            // Adjust for any rounding errors
            int adjustedTotal = weightages.values().stream().mapToInt(Integer::intValue).sum();
            if (adjustedTotal != 100) {
                String firstKey = weightages.keySet().iterator().next();
                weightages.put(firstKey, weightages.get(firstKey) + (100 - adjustedTotal));
            }
        }
    }
    
    /**
     * Retrieves additional categories from the database
     */
    private Set<String> getCategoriesFromDatabase() {
        try {
            return kpiCategoryRepository.findAll().stream()
                .map(KpiCategory::getCategoryName)
                .filter(Objects::nonNull)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Error fetching categories from database", e);
            return Collections.emptySet();
        }
    }
    
    /**
     * Finds the best matching board maturity for a given category
     */
    private int findBestMatchingBoardMaturity(String category, Map<String, Integer> boardMaturities) {
        // Try exact match first
        for (Map.Entry<String, Integer> entry : boardMaturities.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(category)) {
                return entry.getValue();
            }
        }
        
        // Try partial match
        for (Map.Entry<String, Integer> entry : boardMaturities.entrySet()) {
            if (entry.getKey().toUpperCase().contains(category) || 
                category.contains(entry.getKey().toUpperCase())) {
                return entry.getValue();
            }
        }
        
        return 0; // No match found
    }
}
