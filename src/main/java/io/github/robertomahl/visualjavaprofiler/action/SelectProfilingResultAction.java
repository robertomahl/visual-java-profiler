package io.github.robertomahl.visualjavaprofiler.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class SelectProfilingResultAction extends AnAction {

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        e.getPresentation().setEnabled(project != null && editor != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // TODO: think about what would be the best thread to run this action
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {

    }

}
