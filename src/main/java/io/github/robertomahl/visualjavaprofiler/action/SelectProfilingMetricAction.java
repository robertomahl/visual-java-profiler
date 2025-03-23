package io.github.robertomahl.visualjavaprofiler.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;

public class SelectProfilingMetricAction extends AnAction {

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(false);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // Using background threads (BGT)
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        throw new NotImplementedException("Not implemented yet");
    }

}
