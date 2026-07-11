package com.vddoh.editor.view.items;

import com.vddoh.editor.data.ItemEffectSnapshot;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record FxItemEffectViewModel(ItemEffectSnapshot effect) {

  private static final Pattern SKILL_ID = Pattern.compile("skill id=(\\d+)");

  public String side() {
    return effect.side();
  }

  public String type() {
    return effect.type();
  }

  public String target() {
    return effect.target();
  }

  public String value() {
    return effect.value();
  }

  public String extra() {
    return effect.extra();
  }

  public String raw() {
    return effect.raw();
  }

  public boolean linkedSkill() {
    return "Linked skill".equals(effect.type());
  }

  public int linkedSkillId() {
    Matcher matcher = SKILL_ID.matcher(effect.extra() == null ? "" : effect.extra());
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
  }

  public int skillLevel() {
    try {
      return Integer.parseInt(effect.value());
    } catch (NumberFormatException _) {
      return -1;
    }
  }
}
