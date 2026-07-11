package com.vddoh.editor.data;

import static com.vddoh.editor.utils.EditorSupport.joinParts;

import java.util.ArrayList;
import java.util.List;

public final class PatchSummary {
  public int cost;
  public int damage;
  public int status;
  public int price;
  public int icon;
  public int hp;
  public int resource;
  public int duration;
  public int expire;
  public int heroStats;
  public int heroSeeds;
  public int heroResistOverflow;
  public int monsterHeader;
  public int monsterCoreStats;
  public int monsterEffect;
  public int talentAmount;
  public int skipped;

  public boolean onlySkipped() {
    return cost == 0
        && damage == 0
        && status == 0
        && price == 0
        && icon == 0
        && hp == 0
        && resource == 0
        && duration == 0
        && expire == 0
        && heroStats == 0
        && heroSeeds == 0
        && heroResistOverflow == 0
        && monsterHeader == 0
        && monsterCoreStats == 0
        && monsterEffect == 0
        && talentAmount == 0;
  }

  @Override
  public String toString() {
    List<String> parts = new ArrayList<>();
    addPart(parts, "cost", cost);
    addPart(parts, "damage", damage);
    addPart(parts, "status", status);
    addPart(parts, "price", price);
    addPart(parts, "icon", icon);
    addPart(parts, "hp", hp);
    addPart(parts, "resource", resource);
    addPart(parts, "duration", duration);
    addPart(parts, "expire", expire);
    addPart(parts, "heroStats", heroStats);
    addPart(parts, "heroCrit", heroSeeds);
    addPart(parts, "heroResistOverflow", heroResistOverflow);
    addPart(parts, "monsterHeader", monsterHeader);
    addPart(parts, "monsterCoreStats", monsterCoreStats);
    addPart(parts, "monsterEffect", monsterEffect);
    addPart(parts, "talentAmount", talentAmount);
    parts.add("skipped=" + skipped);
    return joinParts(parts);
  }

  private static void addPart(List<String> parts, String label, int value) {
    if (value != 0) {
      parts.add(label + "=" + value);
    }
  }
}
