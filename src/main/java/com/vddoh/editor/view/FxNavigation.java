package com.vddoh.editor.view;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public final class FxNavigation {

  private final ObjectProperty<PendingSkillNavigation> pendingSkillNavigation =
      new SimpleObjectProperty<>();

  public ObjectProperty<PendingSkillNavigation> pendingSkillNavigationProperty() {
    return pendingSkillNavigation;
  }

  public void requestSkillNavigation(PendingSkillNavigation request) {
    pendingSkillNavigation.set(request);
  }

  public record PendingSkillNavigation(
      int sourceItemId, String sourceItemName, int linkedSkillId, int skillLevel) {}
}
