package io.github.robertomahl.visualjavaprofiler.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import jdk.jfr.consumer.RecordingFile;

@Service(Service.Level.PROJECT)
public final class JFRProcessingService {

    private ProfilingMetric activeProfilingMetric = ProfilingMetric.METHOD_RUN_COUNT;

    private final MethodRunCountProfiler methodRunCountProfiler;

    private final Map<ProfilingMetric, Map<String, Long>> profilingResultsPerMetric = new HashMap<>() {{
        put(ProfilingMetric.METHOD_RUN_COUNT, null);
    }};

    JFRProcessingService(Project project) {
        this.methodRunCountProfiler = new MethodRunCountProfiler(project);
    }

    public enum ProfilingMetric {
        METHOD_RUN_COUNT((service, file) -> service.methodRunCountProfiler.compute(file));

        ProfilingMetric(BiFunction<JFRProcessingService, RecordingFile, Map<String, Long>> action) {
            this.action = action;
        }

        private final BiFunction<JFRProcessingService, RecordingFile, Map<String, Long>> action;

        public Map<String, Long> apply(JFRProcessingService service, RecordingFile file) {
            return action.apply(service, file);
        }
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

        for (Map.Entry<ProfilingMetric, Map<String, Long>> entry : profilingResultsPerMetric.entrySet()) {
            entry.setValue(entry.getKey().apply(this, recordingFile));
        }
    }
}
