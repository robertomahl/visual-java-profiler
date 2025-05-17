package io.github.robertomahl.visualjavaprofiler.service;

import com.intellij.openapi.project.Project;
import java.util.Map;
import jdk.jfr.consumer.RecordingFile;

@FunctionalInterface
public interface ProfilingMetricProcessingMethod {

    Map<String, Long> compute(Project project, RecordingFile file);

}
