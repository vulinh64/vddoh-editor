package com.vddoh.editor.service;

record ClassPatchSelection(
    boolean resistanceOverflow,
    boolean equipmentBonus,
    boolean physicalDamageCap,
    boolean highValueDisplay,
    boolean highValueGraphicDisplay,
    boolean victoryReward,
    boolean monsterRewardParser,
    boolean diagonalBackAttack) {

  boolean anyRequested() {
    return resistanceOverflow
        || equipmentBonus
        || physicalDamageCap
        || highValueDisplay
        || highValueGraphicDisplay
        || victoryReward
        || monsterRewardParser
        || diagonalBackAttack;
  }
}
