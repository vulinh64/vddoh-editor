package com.vddoh.editor;

import static com.vddoh.editor.EditorSupport.joinParts;

import java.util.ArrayList;
import java.util.List;

final class PatchSummary {
  int cost;
  int damage;
  int status;
  int price;
  int icon;
  int hp;
  int resource;
  int duration;
  int expire;
  int heroStats;
  int heroSeeds;
  int heroResistOverflow;
  int monsterHeader;
  int monsterCoreStats;
  int monsterEffect;
  int talentAmount;
  int skipped;

  @Override
  public String toString() {
    List<String> parts = new ArrayList<>();
    if (cost != 0) {
      parts.add("cost=" + cost);
    }
    if (damage != 0) {
      parts.add("damage=" + damage);
    }
    if (status != 0) {
      parts.add("status=" + status);
    }
    if (price != 0) {
      parts.add("price=" + price);
    }
    if (icon != 0) {
      parts.add("icon=" + icon);
    }
    if (hp != 0) {
      parts.add("hp=" + hp);
    }
    if (resource != 0) {
      parts.add("resource=" + resource);
    }
    if (duration != 0) {
      parts.add("duration=" + duration);
    }
    if (expire != 0) {
      parts.add("expire=" + expire);
    }
    if (heroStats != 0) {
      parts.add("heroStats=" + heroStats);
    }
    if (heroSeeds != 0) {
      parts.add("heroCrit=" + heroSeeds);
    }
    if (heroResistOverflow != 0) {
      parts.add("heroResistOverflow=" + heroResistOverflow);
    }
    if (monsterHeader != 0) {
      parts.add("monsterHeader=" + monsterHeader);
    }
    if (monsterCoreStats != 0) {
      parts.add("monsterCoreStats=" + monsterCoreStats);
    }
    if (monsterEffect != 0) {
      parts.add("monsterEffect=" + monsterEffect);
    }
    if (talentAmount != 0) {
      parts.add("talentAmount=" + talentAmount);
    }
    parts.add("skipped=" + skipped);
    return joinParts(parts);
  }
}
