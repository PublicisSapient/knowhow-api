package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CycleTimeTrendSlingshotStrategyFactory {

    private final Map<String, CycleTimeTrendSlingshotStrategy> strategyMap;

    public CycleTimeTrendSlingshotStrategyFactory(Map<String, CycleTimeTrendSlingshotStrategy> strategyMap) {
        this.strategyMap = strategyMap;
    }

    public CycleTimeTrendSlingshotStrategy process(String type) {

        return strategyMap.get(type);

    }

}
