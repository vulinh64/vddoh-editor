package com.vddoh.editor.view.skills;

import com.vddoh.editor.data.SkillEdit;
import com.vddoh.editor.data.SkillLevelSnapshot;
import java.util.Locale;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public record FxSkillViewModel(
    SkillLevelSnapshot skill,
    IntegerProperty cost,
    ObservableList<FxSkillEffectViewModel> effects) {

  public FxSkillViewModel(SkillLevelSnapshot skill) {
    this(
        skill,
        new SimpleIntegerProperty(skill.cost()),
        FXCollections.observableArrayList(
            skill.effects().stream().map(FxSkillEffectViewModel::new).toList()));
  }

  public int skillId() {
    return skill.skillId();
  }

  public String skillName() {
    return skill.skillName();
  }

  public int level() {
    return skill.levelIndex() + 1;
  }

  public IntegerProperty costProperty() {
    return cost;
  }

  public int areaShape() {
    return skill.areaShape();
  }

  public String area() {
    return "%dx%d".formatted(skill.areaWidth(), skill.areaHeight());
  }

  public int range() {
    return skill.range();
  }

  public String relativeAreaGrowth() {
    return skill.relativeAreaGrowth() ? "Yes" : "";
  }

  public String notes() {
    return skill.notes();
  }

  public boolean changed() {
    return cost.get() != skill.cost() || effects.stream().anyMatch(FxSkillEffectViewModel::changed);
  }

  public void reset() {
    cost.set(skill.cost());
    effects.forEach(FxSkillEffectViewModel::reset);
  }

  public SkillEdit toEdit() {
    return SkillEdit.builder()
        .skillId(skillId())
        .levelIndex(skill.levelIndex())
        .cost(cost.get())
        .effects(effects.stream().map(FxSkillEffectViewModel::toEdit).toList())
        .build();
  }

  public boolean matchesSearch(String query) {
    String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
    if (normalized.isEmpty()) {
      return true;
    }
    String haystack =
        "%d %s %s %s"
            .formatted(
                skillId(),
                skillName(),
                notes(),
                effects.stream()
                    .map(
                        effect ->
                            "%s %s %s".formatted(effect.type(), effect.target(), effect.notes()))
                    .reduce("", (left, right) -> left + " " + right))
            .toLowerCase(Locale.ROOT);
    return haystack.contains(normalized);
  }
}
