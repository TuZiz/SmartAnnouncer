package ym.smartannouncer.config.model;

import java.util.List;

public record AnnouncementMessage(
    String text,
    MessageClickAction clickAction,
    String clickValue,
    List<String> hover
) {
    public AnnouncementMessage {
        text = text == null ? "" : text;
        clickValue = clickValue == null ? "" : clickValue;
        hover = hover == null ? List.of() : List.copyOf(hover);
    }

    public boolean clickable() {
        return clickAction != null && !clickValue.isBlank();
    }

    public boolean hoverable() {
        return !hover.isEmpty();
    }

    public String hoverText() {
        return String.join("\n", hover);
    }
}
