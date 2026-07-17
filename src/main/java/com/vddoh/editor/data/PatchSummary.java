package com.vddoh.editor.data;

import static com.vddoh.editor.utils.EditorSupport.joinParts;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class PatchSummary {
  private int cost;
  private int damage;
  private int status;
  private int price;
  private int icon;
  private int hp;
  private int resource;
  private int duration;
  private int expire;
  private int heroStats;
  private int heroSeeds;
  private int heroResistOverflow;
  private int equipmentBonusAggregation;
  private int physicalDamageCap;
  private int highValueDisplay;
  private int highValueGraphicDisplay;
  private int victoryExpReward;
  private int monsterRewardParser;
  private int monsterHeader;
  private int monsterCoreStats;
  private int monsterEffect;
  private int talentAmount;
  private int skipped;

  public void incrementCost() {
    cost++;
  }

  public void incrementDamage() {
    damage++;
  }

  public void incrementStatus() {
    status++;
  }

  public void incrementPrice() {
    price++;
  }

  public void incrementIcon() {
    icon++;
  }

  public void incrementHp() {
    hp++;
  }

  public void incrementResource() {
    resource++;
  }

  public void incrementDuration() {
    duration++;
  }

  public void incrementExpire() {
    expire++;
  }

  public void incrementHeroStats() {
    heroStats++;
  }

  public void incrementHeroSeeds() {
    heroSeeds++;
  }

  public void incrementHeroResistOverflow() {
    heroResistOverflow++;
  }

  public void incrementVictoryExpReward() {
    victoryExpReward++;
  }

  public void incrementMonsterHeader() {
    monsterHeader++;
  }

  public void incrementMonsterCoreStats() {
    monsterCoreStats++;
  }

  public void incrementMonsterEffect() {
    monsterEffect++;
  }

  public void incrementTalentAmount() {
    talentAmount++;
  }

  public void incrementSkipped() {
    skipped++;
  }

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
        && equipmentBonusAggregation == 0
        && physicalDamageCap == 0
        && highValueDisplay == 0
        && highValueGraphicDisplay == 0
        && victoryExpReward == 0
        && monsterRewardParser == 0
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
    addPart(parts, "equipmentBonusAggregation", equipmentBonusAggregation);
    addPart(parts, "physicalDamageCap", physicalDamageCap);
    addPart(parts, "highValueDisplay", highValueDisplay);
    addPart(parts, "highValueGraphicDisplay", highValueGraphicDisplay);
    addPart(parts, "victoryExpReward", victoryExpReward);
    addPart(parts, "monsterRewardParser", monsterRewardParser);
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
