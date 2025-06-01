package io.github.robertomahl.visualjavaprofiler.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.messages.MessageBusConnection;
import io.github.robertomahl.visualjavaprofiler.service.JFRProcessingService;
import io.github.robertomahl.visualjavaprofiler.service.ProcessingMethodResult;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public class ToggleVisualizationAction extends AnAction {

    //Profiler Lens

    //Improvements
    //TODO: add new metrics
    //TODO: indicate whether visualization is active or not
    //TODO: allow selection of a base color
    //TODO: allow selection of max-intensity of the color (alpha)

    //Extras
    //TODO: add option to only highlight x% most time-consuming methods - configurable
    //TODO: also include the actual execution time in a label, besides color highlighting
    //TODO: highlight most time-consuming files in the project files view as well
    //TODO: see highlights in the scrollbar
    //TODO: collect user interaction data

    private static final int RED_LIGHT = 255;
    private static final int GREEN_LIGHT = 165;
    private static final int BLUE_LIGHT = 0;

    private static final int RED_DARK = 255;
    private static final int GREEN_DARK = 140;
    private static final int BLUE_DARK = 0;

    private static final int ALPHA_MAX = 127;

    private static final String CONSTRUCTOR_METHOD_NAME = "<init>";

    private static boolean isVisible = false;

    private static MessageBusConnection connection = null;

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        e.getPresentation().setEnabled(project != null
                && editor != null
                && !project.getService(JFRProcessingService.class).isProfilingResultsNotProcessed());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final var project = Optional.ofNullable(anActionEvent.getProject()).orElseThrow();

        if (isVisible) {
            stop(project);
        } else {
            start(project);
        }
    }

    public void start(Project project) {
        final var profilingResults = getProfilingResults(project);

        registerFileOpenListener(project, profilingResults);
        applyToAllOpenFiles(project, profilingResults);
        isVisible = true;
    }

    public void stop(Project project) {
        unregisterFileOpenListener();
        removeFromAllOpenFiles(project);
        isVisible = false;
    }

    private ProcessingMethodResult getProfilingResults(Project project) {
        final var jfrProcessingService = project.getService(JFRProcessingService.class);

        if (jfrProcessingService.isProfilingResultsNotProcessed()) {
            Messages.showWarningDialog(project, "Could not process results. ", "Error");
            throw new IllegalStateException("Profiling results are not set");
        }

        final var profilingResults = jfrProcessingService.getProfilingResults();

        if (profilingResults.getMaxValue() == 0) {
            Messages.showWarningDialog(project, "Profiling results have no data. ", "Error");
            throw new IllegalStateException("Profiling results have no data");
        }
        if (profilingResults.getMinValue() == profilingResults.getMaxValue()) {
            Messages.showWarningDialog(project, "Profiling results have no variation in consumption per method.", "Error");
            throw new IllegalStateException("Profiling results have no variation in consumption per method");
        }

        return profilingResults;
    }

    private void registerFileOpenListener(Project project, ProcessingMethodResult profilingResults) {
        if (connection != null)
            return;

        connection = project.getMessageBus().connect();
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                applyToFile(project, file, profilingResults);
            }
        });
    }

    private void applyToAllOpenFiles(Project project, ProcessingMethodResult profilingResults) {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);

        VirtualFile[] openFiles = fileEditorManager.getOpenFiles();
        for (VirtualFile virtualFile : openFiles) {
            applyToFile(project, virtualFile, profilingResults);
        }
    }

    private void applyToFile(Project project, VirtualFile virtualFile, ProcessingMethodResult profilingResults) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            PsiFile psiFile = DumbService.getInstance(project).runReadActionInSmartMode(() -> PsiManager.getInstance(project).findFile(virtualFile));

            if (psiFile instanceof PsiJavaFile && psiFile.isValid()) {
                List<Editor> editors = getEditors(project, virtualFile);

                if (!editors.isEmpty())
                    applyToAllFileMethods(project, (PsiJavaFile) psiFile, editors, profilingResults);
            }
        });
    }

    private void unregisterFileOpenListener() {
        if (connection == null)
            return;

        connection.disconnect();
        connection = null;
    }

    private void removeFromAllOpenFiles(Project project) {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);

        VirtualFile[] openFiles = fileEditorManager.getOpenFiles();
        for (VirtualFile virtualFile : openFiles) {
            removeFromFile(project, virtualFile);
        }
    }

    private void removeFromFile(Project project, VirtualFile virtualFile) {
        List<Editor> editors = getEditors(project, virtualFile);

        if (!editors.isEmpty())
            editors.forEach(editor -> editor.getMarkupModel().removeAllHighlighters());
    }

    private List<Editor> getEditors(Project project, VirtualFile virtualFile) {
        // FileEditor instances can be non-textual, so we need to get only the textual ones (Editor)
        return FileEditorManager.getInstance(project).getEditorList(virtualFile)
                .stream()
                .filter(TextEditor.class::isInstance)
                .map(TextEditor.class::cast)
                .map(TextEditor::getEditor)
                .toList();
    }

    private void applyToAllFileMethods(Project project, PsiJavaFile psiFile, List<Editor> editors, ProcessingMethodResult profilingResults) {
        List<PsiMethod> psiFileMethods = (DumbService.getInstance(project).runReadActionInSmartMode(() ->
                PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod.class).stream()
                        .filter(psiMethod -> psiMethod.getContainingClass() != null)
                        .toList()
        ));

        for (PsiMethod method : psiFileMethods) {
            editors.forEach(editor -> highlightMethod(project, method, editor, profilingResults));
        }
    }

    private void highlightMethod(Project project, PsiMethod method, Editor editor, ProcessingMethodResult profilingResults) {
        final var methodIdentifier = getMethodIdentifier(project, method);
        final var methodResult = profilingResults.getResultMap().get(methodIdentifier);
        if (methodResult == null)
            return;

        TextAttributes attributes = getTextAttributes(profilingResults, methodResult);

        int startOffset = method.getTextRange().getStartOffset();
        int endOffset = method.getTextRange().getEndOffset();

        ApplicationManager.getApplication().invokeLater(() -> {
            editor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, HighlighterLayer.LAST, attributes, HighlighterTargetArea.EXACT_RANGE);
        });
    }

    private String getMethodIdentifier(Project project, PsiMethod method) {
        final var className = DumbService.getInstance(project).runReadActionInSmartMode(() ->
                Optional.ofNullable(method.getContainingClass())
                        .map(psiClass -> {
                            StringBuilder stringBuilder = new StringBuilder();
                            ClassUtil.formatClassName(psiClass, stringBuilder);
                            return stringBuilder.toString();
                        })
                        .orElseThrow()
        );

        final var methodName = DumbService.getInstance(project).runReadActionInSmartMode(() -> method.isConstructor() ? CONSTRUCTOR_METHOD_NAME : method.getName());
        final var methodDescriptor = DumbService.getInstance(project).runReadActionInSmartMode(() -> ClassUtil.getAsmMethodSignature(method));

        return className + "." + methodName + methodDescriptor;
    }

    @SuppressWarnings("UseJBColor")
    private TextAttributes getTextAttributes(ProcessingMethodResult profilingResults, Long methodResult) {
        final var minValue = profilingResults.getMinValue();
        final var maxValue = profilingResults.getMaxValue();

        // Normalizing the method result to a value between 0 and 1
        double relativePosition = (double) (methodResult - minValue) / (maxValue - minValue);

        int alpha = (int) (relativePosition * ALPHA_MAX);
        Color lightColor = new Color(RED_LIGHT, GREEN_LIGHT, BLUE_LIGHT, alpha);
        Color darkColor = new Color(RED_DARK, GREEN_DARK, BLUE_DARK, alpha);

        return new TextAttributes(null, new JBColor(lightColor, darkColor), null, null, Font.PLAIN);
    }

}
