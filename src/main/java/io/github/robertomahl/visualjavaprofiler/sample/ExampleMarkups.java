package io.github.robertomahl.visualjavaprofiler.sample;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import java.awt.Color;
import java.awt.Font;

public class ExampleMarkups {

    private void highlightMethod(Project project, PsiMethod method, Editor editor) {
        TextAttributes attributes1 = new TextAttributes(Color.RED, null, null, EffectType.BOLD_DOTTED_LINE.LINE_UNDERSCORE, Font.BOLD); // Bold Red
        TextAttributes attributes2 = new TextAttributes(new Color(255, 165, 0), null, null, EffectType.LINE_UNDERSCORE, Font.PLAIN); // Orange
        TextAttributes attributes3 = new TextAttributes(null, new Color(255, 255, 204), null, null, Font.PLAIN); // Light Yellow Background
        TextAttributes attributes4 = new TextAttributes(null, null, Color.GRAY, EffectType.WAVE_UNDERSCORE, Font.PLAIN); // Wave Underscore
        TextAttributes attributes5 = new TextAttributes(null, new Color(255, 165, 0, 100), null, null, Font.PLAIN); // Semi-transparent orange background
    }

}
