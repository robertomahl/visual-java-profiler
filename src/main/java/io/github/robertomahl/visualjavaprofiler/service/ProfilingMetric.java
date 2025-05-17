package io.github.robertomahl.visualjavaprofiler.service;

import com.intellij.openapi.project.Project;
import java.util.Map;
import jdk.jfr.consumer.RecordingFile;

public enum ProfilingMetric {
    METHOD_RUN_COUNT(new MethodRunCountProcessingMethod());

    private final ProfilingMetricProcessingMethod processingMethod;

    ProfilingMetric(ProfilingMetricProcessingMethod processingMethod) {
        this.processingMethod = processingMethod;
    }

    public Map<String, Long> compute(Project project, RecordingFile file) {
        return processingMethod.compute(project, file);
    }
}
