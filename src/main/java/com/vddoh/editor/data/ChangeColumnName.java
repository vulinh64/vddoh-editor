package com.vddoh.editor.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChangeColumnName {
  AMOUNT("Amount"),
  COST("Cost"),
  DAMAGE("Damage"),
  DAMAGE_TYPE("Damage Type"),
  CRIT_CHANCE("Crit %"),
  CRIT_DAMAGE("Crit Dmg"),
  DECODED_EFFECT_VALUE("Decoded Effect Value"),
  DETAIL_VALUE("Detail Value"),
  DURATION("Duration"),
  EFFECT("Effect"),
  EXP("EXP"),
  EXPIRE_CHANCE("Expire Chance"),
  FILAR("Filar"),
  GLOBAL_ID("Global ID"),
  HERO_EFFECT("Hero Effect"),
  ICON("Icon"),
  LEVEL_CAP("Level Cap"),
  MAX("Max"),
  PRICE("Price"),
  RESIST_ID("Resist ID"),
  RUNE_SLOTS("Rune Slots"),
  SOUL_RESTORE("Soul Restore"),
  SPD("SPD"),
  SPD_CURVE("SPD Curve"),
  SPD_START("SPD Start"),
  SPD_TARGET("SPD Target"),
  SPI("SPI"),
  SPI_CURVE("SPI Curve"),
  SPI_START("SPI Start"),
  SPI_TARGET("SPI Target"),
  STATUS_ID("Status ID"),
  STR("STR"),
  STR_CURVE("STR Curve"),
  STR_START("STR Start"),
  STR_TARGET("STR Target"),
  UNLOCK_REF("Unlock Ref"),
  VALUE("Value"),
  VIT("VIT"),
  VIT_CURVE("VIT Curve"),
  VIT_START("VIT Start"),
  VIT_TARGET("VIT Target");

  private final String label;
}
