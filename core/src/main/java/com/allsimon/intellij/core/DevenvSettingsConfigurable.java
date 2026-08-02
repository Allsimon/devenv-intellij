package com.allsimon.intellij.core;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The 'Devenv' page under Settings | Tools: one switch per feature of the plugin, in sections named
 * after the part of devenv each belongs to.
 * <p>
 * Features whose plugin this IDE doesn't have are left out entirely - see
 * {@link DevenvFeature#isAvailable()} - because their module isn't loaded and their switch would
 * therefore change nothing.
 */
public final class DevenvSettingsConfigurable implements Configurable {
    /**
     * Where the comments wrap, in characters of their own text - the platform measures that much of
     * the string and pins the HTML to the width it comes to.
     * <p>
     * It has to be decided here and cannot follow the dialog: an HTML label reports the same
     * preferred size whatever width it is given, so a comment with no width of its own asks for one
     * long line rather than filling the page. 120 characters comes to some 640 pixels, which fills a
     * settings page of the usual size without pushing it wider; the platform's own default of
     * {@link ComponentPanelBuilder#MAX_COMMENT_WIDTH} would use barely half of it.
     */
    private static final int COMMENT_WIDTH_CHARACTERS = 120;

    private final Map<DevenvFeature, JBCheckBox> checkBoxes = new LinkedHashMap<>();

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return MyMessageBundle.message("settings.devenv.displayName");
    }

    @Override
    public @Nullable JComponent createComponent() {
        checkBoxes.clear();
        FormBuilder builder = FormBuilder.createFormBuilder();
        DevenvFeatureGroup currentGroup = null;
        for (DevenvFeature feature : DevenvFeature.values()) {
            if (!feature.isAvailable()) {
                continue;
            }
            if (feature.group() != currentGroup) {
                currentGroup = feature.group();
                // Written out only once the group is known to have a switch to show: in an IDE
                // without the Java plugin, 'Languages' would otherwise head an empty section.
                builder.addComponent(new TitledSeparator(currentGroup.displayName()));
            }
            JBCheckBox checkBox = new JBCheckBox(feature.displayName());
            checkBoxes.put(feature, checkBox);
            builder.addComponent(checkBox).addComponentToRightColumn(comment(feature.description(), checkBox), 0);
        }
        // Empty filler, so the switches sit at the top of the page instead of spreading over it.
        return builder.addComponentFillVertically(new JPanel(), 0).getPanel();
    }

    /**
     * The greyed-out line of explanation under a switch.
     * <p>
     * Not {@link FormBuilder#addTooltip}, which is the obvious candidate and the wrong one: it builds
     * a bare {@link javax.swing.JLabel}, and a bare label neither renders the {@code <br/>} of a
     * description nor wraps, so the sentences run off the side of the dialog in one line. The label
     * built here is the one the platform's own settings pages use - HTML, wrapping at a sane width,
     * and indented to line up with the checkbox's text rather than with its box.
     */
    @SuppressWarnings("deprecation") // ComponentPanelBuilder is deprecated in favour of the Kotlin
    // UI DSL, which is not an option in a project with no Kotlin source set. Kept to this one method
    // so that adopting a replacement later is a single edit.
    private static @NotNull JComponent comment(@Nls @NotNull String text, @NotNull JComponent under) {
        JLabel comment = ComponentPanelBuilder.createCommentComponent(text, true, COMMENT_WIDTH_CHARACTERS, true);
        comment.setBorder(JBUI.Borders.empty(ComponentPanelBuilder.computeCommentInsets(under, true)));
        return comment;
    }

    @Override
    public boolean isModified() {
        return !selected().equals(DevenvSettings.getInstance().getEnabled());
    }

    @Override
    public void apply() {
        DevenvSettings.getInstance().setEnabled(selected());
    }

    @Override
    public void reset() {
        DevenvSettings settings = DevenvSettings.getInstance();
        checkBoxes.forEach((feature, checkBox) -> checkBox.setSelected(settings.isEnabled(feature)));
    }

    @Override
    public void disposeUIResources() {
        checkBoxes.clear();
    }

    /**
     * The features the page currently has ticked. A feature without a checkbox - one this IDE has no
     * plugin for - keeps whatever it was set to, so that switching between IDEs doesn't quietly
     * re-enable it.
     */
    private @NotNull Set<DevenvFeature> selected() {
        DevenvSettings settings = DevenvSettings.getInstance();
        Set<DevenvFeature> enabled = EnumSet.noneOf(DevenvFeature.class);
        for (DevenvFeature feature : DevenvFeature.values()) {
            JBCheckBox checkBox = checkBoxes.get(feature);
            if (checkBox == null ? settings.isEnabled(feature) : checkBox.isSelected()) {
                enabled.add(feature);
            }
        }
        return enabled;
    }
}
