package ui.util;

import com.intellij.openapi.ui.Messages;
import core.GreenCat;

import javax.swing.*;

public enum DialogMessageType {

    INFO(GreenCat.PLUGIN_NAME, Messages.getInformationIcon()),
    ERROR("Error", Messages.getErrorIcon()),
    QUESTION(GreenCat.PLUGIN_NAME, Messages.getQuestionIcon()),
    WARNING("Warning", Messages.getWarningIcon());

    private final String title;
    private final Icon icon;

    DialogMessageType(String title, Icon icon) {
        this.title = title;
        this.icon = icon;
    }

    public String getTitle() {
        return title;
    }

    public Icon getIcon() {
        return icon;
    }
}
