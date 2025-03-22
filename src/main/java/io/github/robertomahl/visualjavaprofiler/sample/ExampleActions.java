package io.github.robertomahl.visualjavaprofiler.sample;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import java.awt.Color;
import java.awt.Font;
import org.jetbrains.annotations.NotNull;

public class ExampleActions {

    public void actionPerformedShowDialog(AnActionEvent event) {
        // Using the event, create and show a dialog
        Project currentProject = event.getProject();
        StringBuilder message = new StringBuilder(event.getPresentation().getText() + " Selected!");
        // If an element is selected in the editor, add info about it.
        Navigatable selectedElement = event.getData(CommonDataKeys.NAVIGATABLE);
        if (selectedElement != null) {
            message.append("\nSelected Element: ").append(selectedElement);
        }
        String title = event.getPresentation().getDescription();
        Messages.showMessageDialog(
                currentProject,
                message.toString(),
                title,
                Messages.getInformationIcon());
    }

    public void actionPerformedReplaceSelectedText(AnActionEvent event) {
        // Get all the required data from data keys
        Editor editor = event.getRequiredData(CommonDataKeys.EDITOR);
        Project project = event.getRequiredData(CommonDataKeys.PROJECT);
        Document document = editor.getDocument();

        // Work off of the primary caret to get the selection info
        Caret primaryCaret = editor.getCaretModel().getPrimaryCaret();
        int start = primaryCaret.getSelectionStart();
        int end = primaryCaret.getSelectionEnd();

        // Replace the selection with a fixed string.
        // Must do this document change in a write action context.
        WriteCommandAction.runWriteCommandAction(project, () ->
                document.replaceString(start, end, "editor_basics")
        );

        // De-select the text range that was just replaced
        primaryCaret.removeSelection();
    }

    public void actionPerformedSetStaticBackgroundColor(AnActionEvent event) {
        Editor editor = event.getRequiredData(CommonDataKeys.EDITOR);
        Project project = event.getRequiredData(CommonDataKeys.PROJECT);

        // Get the primary caret selection
        Caret primaryCaret = editor.getCaretModel().getPrimaryCaret();
        int start = primaryCaret.getSelectionStart();
        int end = primaryCaret.getSelectionEnd();

        if (start == end) return; // No selection, nothing to highlight

        // Get the markup model and apply a background highlight
        MarkupModel markupModel = editor.getMarkupModel();
        TextAttributes attributes = new TextAttributes();
        attributes.setBackgroundColor(Color.YELLOW); // Change to any color

        editor.getMarkupModel().addRangeHighlighter(1, 3, 9999, new TextAttributes(null, Color.red, null, null, Font.PLAIN), HighlighterTargetArea.EXACT_RANGE);

        // Remove selection to apply the effect without keeping the text highlighted
        primaryCaret.removeSelection();
    }

    public void actionPerformedShowPsiDataAtCaret(AnActionEvent anActionEvent) {

        Editor editor = anActionEvent.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = anActionEvent.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || psiFile == null) {
            return;
        }
        int offset = editor.getCaretModel().getOffset();

        final StringBuilder infoBuilder = new StringBuilder();
        PsiElement element = psiFile.findElementAt(offset);
        infoBuilder.append("Element at caret: ").append(element).append("\n");
        if (element != null) {
            PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            infoBuilder
                    .append("Containing method: ")
                    .append(containingMethod != null ? containingMethod.getName() : "none")
                    .append("\n");
            if (containingMethod != null) {
                PsiClass containingClass = containingMethod.getContainingClass();
                infoBuilder
                        .append("Containing class: ")
                        .append(containingClass != null ? containingClass.getName() : "none")
                        .append("\n");

                infoBuilder.append("Local variables:\n");
                containingMethod.accept(new JavaRecursiveElementVisitor() {
                    @Override
                    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
                        super.visitLocalVariable(variable);
                        infoBuilder.append(variable.getName()).append("\n");
                    }
                });
            }
        }
        Messages.showMessageDialog(anActionEvent.getProject(), infoBuilder.toString(), "PSI Info", null);
    }

}
