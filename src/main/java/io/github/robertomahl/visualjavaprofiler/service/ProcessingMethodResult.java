package io.github.robertomahl.visualjavaprofiler.service;

import java.util.Map;

public class ProcessingMethodResult {

    private final Map<String, Long> resultMap;
    private final long minValue;
    private final long maxValue;

    public ProcessingMethodResult(Map<String, Long> resultMap) {
        this.resultMap = resultMap;

        if (resultMap.isEmpty()) {
            this.minValue = 0L;
            this.maxValue = 0L;
        } else {
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            for (Long value : resultMap.values()) {
                if (value < min) min = value;
                if (value > max) max = value;
            }
            this.minValue = min;
            this.maxValue = max;
        }
    }

    public Map<String, Long> getResultMap() {
        return resultMap;
    }

    public long getMinValue() {
        return minValue;
    }

    public long getMaxValue() {
        return maxValue;
    }
}
