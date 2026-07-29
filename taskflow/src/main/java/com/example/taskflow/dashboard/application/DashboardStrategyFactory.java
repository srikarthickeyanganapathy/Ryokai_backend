package com.example.taskflow.dashboard.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DashboardStrategyFactory {

    private final List<DashboardStatsStrategy> strategies;
    private final PersonalDashboardStrategy fallbackStrategy;

    public DashboardStatsStrategy getStrategy(String scope) {
        if (scope == null || scope.isBlank()) {
            return fallbackStrategy;
        }
        return strategies.stream()
                .filter(strategy -> strategy.supports(scope))
                .findFirst()
                .orElse(fallbackStrategy);
    }
}