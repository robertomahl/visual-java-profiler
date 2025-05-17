package io.github.robertomahl.visualjavaprofiler.service;

import com.intellij.openapi.project.Project;
import jdk.jfr.consumer.RecordingFile;

@FunctionalInterface
public interface ProfilingMetricProcessingMethod {

    ProcessingMethodResult compute(Project project, RecordingFile file);

}
