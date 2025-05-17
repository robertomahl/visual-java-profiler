package io.github.robertomahl.visualjavaprofiler.service;

import com.intellij.openapi.project.Project;
import jdk.jfr.consumer.RecordingFile;

public enum ProfilingMetric {
    METHOD_RUN_COUNT(new MethodRunCountProcessingMethod());

    private final ProfilingMetricProcessingMethod processingMethod;

    ProfilingMetric(ProfilingMetricProcessingMethod processingMethod) {
        this.processingMethod = processingMethod;
    }

    public ProcessingMethodResult compute(Project project, RecordingFile file) {
        return processingMethod.compute(project, file);
    }
}
