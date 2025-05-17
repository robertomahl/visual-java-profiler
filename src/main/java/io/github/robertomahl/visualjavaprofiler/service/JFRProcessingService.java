package io.github.robertomahl.visualjavaprofiler.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.util.HashMap;
import java.util.Map;
import jdk.jfr.consumer.RecordingFile;

@Service(Service.Level.PROJECT)
public final class JFRProcessingService {

    private ProfilingMetric activeProfilingMetric = ProfilingMetric.METHOD_RUN_COUNT;

    private final Project project;
    private final Map<ProfilingMetric, Map<String, Long>> profilingResultsPerMetric;

    JFRProcessingService(Project project) {
        this.project = project;
        this.profilingResultsPerMetric = new HashMap<>();
    }

    public boolean isProfilingResultsNotProcessed() {
        return profilingResultsPerMetric.get(activeProfilingMetric) == null;
    }

    public Map<String, Long> getProfilingResults() {
        return profilingResultsPerMetric.get(activeProfilingMetric);
    }

    public void setActiveProfilingMetric(ProfilingMetric profilingMetric) {
        this.activeProfilingMetric = profilingMetric;
    }

    public void read(RecordingFile recordingFile) {
        if (recordingFile == null) {
            throw new IllegalArgumentException("RecordingFile cannot be null.");
        }

        for (ProfilingMetric profilingMetric : ProfilingMetric.values()) {
            profilingResultsPerMetric.put(profilingMetric, profilingMetric.compute(project, recordingFile));
        }
    }
}
