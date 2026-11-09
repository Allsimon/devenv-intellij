package com.allsimon.intellij.nixlang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Lists the formals of the function a set is passed to, the one being written highlighted. */
public final class NixParameterInfoHandler implements ParameterInfoHandler<PsiElement, List<NixFormals.Formal>> {
    @Override
    public @Nullable PsiElement findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        return argumentAt(context.getFile(), context.getOffset());
    }

    @Override
    public void showParameterInfo(@NotNull PsiElement set, @NotNull CreateParameterInfoContext context) {
        List<NixFormals.Formal> formals = NixFormals.of(set);
        if (formals != null && !formals.isEmpty()) {
            context.setItemsToShow(new Object[]{formals});
            context.showHint(set, set.getTextRange().getStartOffset(), this);
        }
    }

    @Override
    public @Nullable PsiElement findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        return argumentAt(context.getFile(), context.getOffset());
    }

    @Override
    public void updateParameterInfo(@NotNull PsiElement set, @NotNull UpdateParameterInfoContext context) {
        List<NixFormals.Formal> formals = NixFormals.of(set);
        String name = bindingAt(set, context.getOffset());
        int index = -1;
        for (int i = 0; formals != null && name != null && i < formals.size(); i++) {
            if (formals.get(i).name().equals(name)) {
                index = i;
            }
        }
        context.setCurrentParameter(index);
    }

    @Override
    public void updateUI(List<NixFormals.Formal> formals, @NotNull ParameterInfoUIContext context) {
        StringBuilder text = new StringBuilder();
        int start = -1;
        int end = -1;
        for (int i = 0; i < formals.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            if (i == context.getCurrentParameterIndex()) {
                start = text.length();
            }
            text.append(formals.get(i));
            if (i == context.getCurrentParameterIndex()) {
                end = text.length();
            }
        }
        context.setupUIComponentPresentation(text.toString(), start, end, false, false, false,
                context.getDefaultParameterColor());
    }

    /** The innermost set at {@code offset} passed to a function with formals. */
    private static @Nullable PsiElement argumentAt(PsiFile file, int offset) {
        for (PsiElement element = file.findElementAt(offset); element != null && element != file;
             element = element.getParent()) {
            if (NixFormals.is(element.getNode(), NodeKind.ATTR_SET) && NixFormals.of(element) != null) {
                return element;
            }
        }
        return null;
    }

    /** The first name of the binding of {@code set} that {@code offset} is in. */
    private static @Nullable String bindingAt(PsiElement set, int offset) {
        for (ASTNode binding : NixFormals.children(set.getNode())) {
            if (NixFormals.is(binding, NodeKind.BINDING) && binding.getTextRange().containsOffset(offset)) {
                ASTNode path = binding.findChildByType(NixElementTypes.of(NodeKind.ATTR_PATH));
                ASTNode first = path == null ? null : path.getFirstChildNode();
                return first == null ? null : first.getText();
            }
        }
        return null;
    }
}
