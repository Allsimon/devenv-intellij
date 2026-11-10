package com.allsimon.intellij.nixlang;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/** Shows the bindings of a file, each under the binding whose value holds it. */
public final class NixStructureViewFactory implements PsiStructureViewFactory {
    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
        return new TreeBasedStructureViewBuilder() {
            @Override
            public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                return new StructureViewModelBase(psiFile, editor, new Element(psiFile))
                        .withSuitableClasses(PsiElement.class);
            }
        };
    }

    static final class Element implements StructureViewTreeElement, ItemPresentation {
        private final PsiElement element;

        Element(PsiElement element) {
            this.element = element;
        }

        @Override
        public Object getValue() {
            return element;
        }

        @Override
        public @NotNull ItemPresentation getPresentation() {
            return this;
        }

        @Override
        public @Nullable String getPresentableText() {
            return element instanceof PsiFile file ? file.getName() : NixBindings.name(element);
        }

        @Override
        public @Nullable Icon getIcon(boolean unused) {
            return element instanceof PsiFile ? null : AllIcons.Nodes.Property;
        }

        @Override
        public TreeElement @NotNull [] getChildren() {
            return NixBindings.nested(element).stream().map(Element::new).toArray(TreeElement[]::new);
        }

        @Override
        public void navigate(boolean requestFocus) {
            if (element instanceof NavigationItem item) {
                item.navigate(requestFocus);
            }
        }

        @Override
        public boolean canNavigate() {
            return element instanceof NavigationItem item && item.canNavigate();
        }

        @Override
        public boolean canNavigateToSource() {
            return element instanceof NavigationItem item && item.canNavigateToSource();
        }
    }
}
