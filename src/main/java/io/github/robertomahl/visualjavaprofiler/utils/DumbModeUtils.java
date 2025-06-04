package io.github.robertomahl.visualjavaprofiler.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import io.github.robertomahl.visualjavaprofiler.exception.DumbModeException;
import java.util.concurrent.atomic.AtomicBoolean;

public class DumbModeUtils {

    private static final AtomicBoolean warningShown = new AtomicBoolean(false);

    public static void assertInSmartMode(Project project) {
        if (DumbService.isDumb(project)) {
            if (warningShown.compareAndSet(false, true)) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    Messages.showWarningDialog(project, "File indexing is ongoing, please try again soon.", "Warning");
                    warningShown.set(false);
                });
            }
            throw new DumbModeException("Action cannot be performed while the project is indexing.");
        }
    }

}
