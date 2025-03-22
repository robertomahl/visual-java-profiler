package io.github.robertomahl.visualjavaprofiler.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import io.github.robertomahl.visualjavaprofiler.service.JFRReaderService;
import org.jetbrains.annotations.NotNull;

public class SelectProfilingMetricAction extends AnAction {

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean isProfilingResultPathSet = JFRReaderService.isProfilingResultPathSet();

        e.getPresentation().setEnabled(project != null && editor != null && isProfilingResultPathSet);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // Using background threads (BGT)
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        //TODO: get metric from user
        final var metric = JFRReaderService.ProfilingMetric.METHOD_EXECUTION_TIME;

        JFRReaderService.setProfilingMetric(metric);
        JFRReaderService.read();
    }

}
