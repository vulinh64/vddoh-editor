package com.vddoh.editor.utils;

import com.vddoh.editor.data.*;
import com.vddoh.editor.service.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EditorSupport {

  private static final String PACKED_HERO_HEADER = "packed hero header";
  private static final String EQUIPMENT = "Equipment";
  private static final String PACKED_HERO_STATS = "packed hero stat";
  public static final String PACKED_STAT = "Packed Stat";
  public static final String ME_LIB_INTERNAL = "me-lib";
  public static final String CONSUMABLE_ROW = "Consumable";
  public static final String WEAPON_SIDE = "Weapon";
  private static final int[][] EQUIPMENT_FLAG_BITS = {
    {0, 8, 4},
    {0, 2, 1},
    {1, 0x80, 0x40},
    {1, 0x20, 0x10},
    {1, 8, 4},
    {1, 2, 1},
    {2, 0x80, 0x40},
    {2, 0x20, 0x10},
    {2, 8, 4},
    {2, 2, 1}
  };
  public static final String BYTE_D = "byte_d";
  public static final String ARMOR_SIDE = "Armor";
  public static final String SHORT_ARR_B_RAW = "short_arr_b";

  public static Path editorUserPath(String child) {
    return Path.of(System.getProperty("user.home"), ".vddoh-editor", child);
  }

  public static int skipDamageGroups(byte[] data, int n) {
    int count = u8(data[n++]);
    for (int i = 0; i < count; i++) {
      n += 1 + (data[n] & 0x7f);
    }
    return n;
  }

  public static int skipStatuses(byte[] data, int n) {
    int count = u8(data[n++]);
    for (int i = 0; i < count; i++) {
      int nameLen = data[n] & 0x1f;
      n += 1 + nameLen;
      boolean specialFlag = (data[n] & 0x80) != 0;
      n++;
      if (i > 0) {
        int flags = u8(data[n++]);
        if ((flags & 0x80) != 0) {
          n++;
        }
        if ((flags & 0x40) != 0) {
          n += 2;
        }
        if ((flags & 0x20) != 0) {
          n++;
        }
        n = GameDatStatusPatcher.getN(n, flags);
      }
      if (i == 0) {
        continue;
      }
      n++;
      n = GameDatStatusPatcher.getPacked(data, n, specialFlag);
    }
    return n;
  }

  public static int heroStartOffset(byte[] data) {
    return skipMonsters(data, monsterStartOffset(data));
  }

  public static int monsterStartOffset(byte[] data) {
    int n = 13 + u16(data, 11) * 5;
    n = skipDamageGroups(data, n);
    n = skipStatuses(data, n);
    return skipSkills(data, n);
  }

  public static int skipSkills(byte[] data, int n) {
    int skillCount = u8(data[n++]);
    for (int skillId = 0; skillId < skillCount; skillId++) {
      n = skipSkill(data, n);
    }
    return n;
  }

  private static int skipSkill(byte[] data, int n) {
    int nameLen = data[n] & 0x1f;
    n += 1 + nameLen;
    int header = u8(data[n++]);
    int levelCount = ((header >> 6) & 3) + 1;
    int inheritedFlags = header & 7;
    n++;
    int packedUsability = u8(data[n++]);
    inheritedFlags = inheritedFlags | ((packedUsability & 1) << 3);
    n += (packedUsability & 8) != 0 ? 1 : 0;
    n++;
    SkillPayload payload = skipBaseSkillPayload(data, n, inheritedFlags);
    return skipOverrideLevels(data, payload.nextOffset(), levelCount, payload);
  }

  private static SkillPayload skipBaseSkillPayload(byte[] data, int n, int inheritedFlags) {
    n += (inheritedFlags & 1) != 0 ? 2 : 0;
    int damageCount = u8(data[n++]);
    n += damageCount * 3;
    int statusCount = 0;
    if ((inheritedFlags & 8) != 0) {
      statusCount = u8(data[n++]);
      n += statusCount * 2;
    }
    n += 2;
    n += (inheritedFlags & 4) != 0 ? 2 : 0;
    n += (inheritedFlags & 2) != 0 ? 2 : 0;
    return new SkillPayload(n + 1, damageCount, statusCount);
  }

  private static int skipOverrideLevels(byte[] data, int n, int levelCount, SkillPayload payload) {
    for (int level = 1; level < levelCount; level++) {
      int overrideFlags = u8(data[n++]);
      int reuseFlags = u8(data[n++]);
      n = GameDatSkillPatcher.skipOverrideLevelPrefix(n, overrideFlags, reuseFlags);
      n = skipOverrideScalarBytes(n, overrideFlags, reuseFlags);
      n = skipOverrideDamageBytes(n, overrideFlags, reuseFlags, payload.damageCount());
      n = skipOverrideStatusBytes(n, overrideFlags, reuseFlags, payload.statusCount());
      n += 2;
    }
    return n;
  }

  private static int skipOverrideScalarBytes(int n, int overrideFlags, int reuseFlags) {
    n += (overrideFlags & 0x10) != 0 ? 1 : 0;
    n += (reuseFlags & 0x10) != 0 || (overrideFlags & 4) != 0 ? 1 : 0;
    n += (overrideFlags & 4) != 0 ? 1 : 0;
    return n;
  }

  private static int skipOverrideDamageBytes(
      int n, int overrideFlags, int reuseFlags, int damageCount) {
    if ((reuseFlags & 4) != 0) {
      return n + 1;
    }
    return (overrideFlags & 2) != 0 ? n + damageCount * 2 : n;
  }

  private static int skipOverrideStatusBytes(
      int n, int overrideFlags, int reuseFlags, int statusCount) {
    if ((reuseFlags & 2) != 0) {
      return n + 1;
    }
    return (overrideFlags & 1) != 0 ? n + statusCount : n;
  }

  public static int skipHeroes(byte[] data, int n) {
    int count = u8(data[n++]);
    for (int heroId = 0; heroId < count; heroId++) {
      int nameLen = data[n] & 0x7f;
      n += 1 + nameLen;
      n += 11;
      n += 3;
      n++;
      n++;
      int seedOffset = n;
      n += 3;
      n += 3;
      for (int slot = 0; slot < 10; slot++) {
        int equipped = equipmentFlag(data, seedOffset + 3, slot);
        if (equipped > 0) {
          n++;
        }
      }
      n = GameDatHeroPatcher.getN(data, n);
    }
    return n;
  }

  public static int skipMonsters(byte[] data, int n) {
    int count = u8(data[n++]);
    for (int i = 0; i < count; i++) {
      int nameLen = u8(data[n]);
      n += 1 + nameLen;
      n = GameDatMonsterPatcher.getN(data, n);
      n += 13;
    }
    return n;
  }

  public static int equipmentFlag(byte[] data, int offset, int slot) {
    if (slot < 0 || slot >= EQUIPMENT_FLAG_BITS.length) {
      return -1;
    }
    int[] bits = EQUIPMENT_FLAG_BITS[slot];
    int value = u8(data[offset + bits[0]]);
    if ((value & bits[1]) == 0) {
      return -1;
    }
    return (value & bits[2]) != 0 ? 1 : 0;
  }

  public static void writeHeroStats(
      byte[] data, int offset, int power, int spirit, int vitality, int agility) {
    data[offset] = checkedByte((power >> 16) & 0xff, "power curve");
    data[offset + 1] = checkedByte((spirit >> 16) & 0xff, "spirit curve");
    data[offset + 2] = checkedByte((vitality >> 16) & 0xff, "vitality curve");
    data[offset + 3] = checkedByte((agility >> 16) & 0xff, "agility curve");
    int p = power & 0x7fff;
    int s = spirit & 0x7fff;
    int v = vitality & 0x7fff;
    int a = agility & 0x7fff;
    data[offset + 4] = checkedByte(((p >> 7) & 0xfe) | ((p >> 6) & 1), PACKED_HERO_STATS);
    data[offset + 5] = checkedByte(((p & 0x3f) << 2) | ((s >> 13) & 3), PACKED_HERO_STATS);
    data[offset + 6] = checkedByte(((s >> 5) & 0xf8) | ((s >> 4) & 7), PACKED_HERO_STATS);
    data[offset + 7] = checkedByte(((s & 0x0f) << 4) | ((v >> 11) & 0x0f), PACKED_HERO_STATS);
    data[offset + 8] = checkedByte(((v >> 3) & 0xe0) | ((v >> 2) & 0x1f), PACKED_HERO_STATS);
    data[offset + 9] = checkedByte(((v & 3) << 6) | ((a >> 9) & 0x3f), PACKED_HERO_STATS);
    data[offset + 10] = checkedByte(((a >> 1) & 0x80) | (a & 0x7f), PACKED_HERO_STATS);
  }

  public static void writeHeroSeeds(
      byte[] data, int offset, int levelCap, int baseCritChance, int baseCritDamage) {
    int shortA = checked7Bit(levelCap, "level cap");
    int shortB =
        ((checkedByte(baseCritChance, "base crit chance") & 0xff) << 8)
            | (checkedByte(baseCritDamage, "base crit damage") & 0xff);
    data[offset] = checkedByte(((shortA >> 7) & 0xfe) | ((shortA >> 6) & 1), PACKED_HERO_HEADER);
    data[offset + 1] =
        checkedByte(((shortA & 0x3f) << 2) | ((shortB >> 13) & 3), PACKED_HERO_HEADER);
    data[offset + 2] =
        checkedByte(((shortB >> 5) & 0xf8) | ((shortB >> 4) & 7), PACKED_HERO_HEADER);
    data[offset + 3] = (byte) ((data[offset + 3] & 0x0f) | ((shortB & 0x0f) << 4));
  }

  public static void writeMonsterHeader(
      byte[] data, int offset, int experience, int filar, int deathValue) {
    if (experience < 0 || experience > 4095) {
      throw new IllegalArgumentException("monster EXP must be 0..4095");
    }
    if (filar < 0 || filar > 4095) {
      throw new IllegalArgumentException("monster Filar must be 0..4095");
    }
    if (deathValue < 0 || deathValue > 127) {
      throw new IllegalArgumentException("monster soul restore must be 0..127");
    }
    data[offset] = checkedByte((experience >>> 4) & 0xff, "monster EXP high");
    data[offset + 1] =
        checkedByte(
            ((experience & 0x0f) << 4) | ((filar >>> 8) & 0x0f), "packed monster EXP/Filar");
    data[offset + 2] = checkedByte(filar & 0xff, "monster Filar low");
    data[offset + 3] = checkedByte(deathValue, "monster soul restore");
  }

  public static String[] decodedNames(Object[] values, Method decode) {
    if (values == null) {
      return new String[0];
    }
    String[] names = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      names[i] = decodeName(values[i], 0, decode);
    }
    return names;
  }

  public static List<ItemEffectRow> decodeItemEffects(
      Object item, int category, String[] statusNames, String[] skillNames) {
    List<ItemEffectRow> rows = new ArrayList<>();
    appendRestoreEffect(rows, category, intValue(raw(item, 21)), "HP", "short_g");
    appendRestoreEffect(rows, category, intValue(raw(item, 22)), "Blood/Soul", "short_h");
    String packedStatSide =
        switch (category) {
          case 5 -> "Consumable stat boost";
          case 7 -> "Rune";
          default -> EQUIPMENT;
        };
    appendPackedItemStats(rows, item, packedStatSide, category);
    appendItemArrays(rows, item, category, statusNames);
    appendConsumableUseEffect(rows, item, category);
    appendLinkedSkill(rows, item, category, skillNames);
    appendWeaponDetails(rows, item, category);
    if (rows.isEmpty()) {
      rows.add(
          ItemEffectRow.of(
              "Info",
              "No decoded effects",
              StringUtils.EMPTY,
              StringUtils.EMPTY,
              StringUtils.EMPTY,
              StringUtils.EMPTY));
    }
    return rows;
  }

  private static void appendRestoreEffect(
      List<ItemEffectRow> rows, int category, int value, String target, String rawName) {
    if (value <= 0) {
      return;
    }
    boolean consumable = category == 5;
    String type = target.equals("HP") ? "HP effect" : "Resource effect";
    String equipmentType = target.equals("HP") ? "Restore HP" : "Restore Resource";
    rows.add(
        ItemEffectRow.of(
            consumable ? CONSUMABLE_ROW : EQUIPMENT,
            consumable ? type : equipmentType,
            target,
            String.valueOf(value),
            consumable ? "battle consumable" : StringUtils.EMPTY,
            rawName));
  }

  private static void appendPackedItemStats(
      List<ItemEffectRow> rows, Object item, String packedStatSide, int category) {
    appendPackedStat(rows, packedStatSide, 0, shortValue(raw(item, 8)), "short_c", category);
    appendPackedStat(rows, packedStatSide, 2, shortValue(raw(item, 9)), "short_d", category);
    appendPackedStat(rows, packedStatSide, 4, shortValue(raw(item, 10)), "short_e", category);
    appendPackedStat(rows, packedStatSide, 6, shortValue(raw(item, 11)), "short_f", category);
    int misc = u8(raw(item, 12));
    if (misc != 0) {
      rows.add(
          ItemEffectRow.of(
              packedStatSide,
              PACKED_STAT,
              ItemEffectLabel.packedStatTarget(category, 8, BYTE_D, statName(8)),
              String.valueOf(misc),
              ItemEffectLabel.packedStatExtra(category, 8, BYTE_D, StringUtils.EMPTY),
              BYTE_D));
    }
  }

  private static void appendItemArrays(
      List<ItemEffectRow> rows, Object item, int category, String[] statusNames) {
    if (category == 7) {
      appendRuneIntEffects(rows, WEAPON_SIDE, intArray(raw(item, 13)), "int_arr_a");
      appendRuneIntEffects(rows, ARMOR_SIDE, intArray(raw(item, 14)), "int_arr_b");
      appendRuneStatusEffects(rows, shortArray(raw(item, 16)), statusNames);
      return;
    }
    appendIntEffects(rows, "Equipment/Weapon", intArray(raw(item, 13)), "int_arr_a", category);
    appendIntEffects(rows, "Armor effect", intArray(raw(item, 14)), "int_arr_b", category);
    appendShortEffects(
        rows,
        category == 5 ? "Consumable status gate" : "Protection",
        shortArray(raw(item, 15)),
        statusNames,
        "short_arr_a",
        category);
    appendShortEffects(
        rows,
        shortArraySide(category),
        shortArray(raw(item, 16)),
        statusNames,
        SHORT_ARR_B_RAW,
        category);
  }

  private static String shortArraySide(int category) {
    return switch (category) {
      case 2 -> "Protection";
      case 5 -> "Consumable status effect";
      default -> "On hit / item use";
    };
  }

  private static void appendRuneIntEffects(
      List<ItemEffectRow> rows, String side, int[] values, String rawName) {
    for (int i = 0; values != null && i < values.length; i++) {
      int packed = values[i];
      int kind = (packed >> 16) & 0xff;
      int value = packed & 0xffff;
      boolean armor = ARMOR_SIDE.equals(side);
      boolean elemental = kind > 0 && kind <= 5;
      String target = runeElementName(kind);
      rows.add(
          ItemEffectRow.of(
              side,
              elemental ? (armor ? "Anti-element" : "Element damage") : "Flat stat/damage",
              armor && elemental ? "Anti-" + target.toLowerCase(Locale.ROOT) : target,
              String.valueOf(value),
              StringUtils.EMPTY,
              "%s[%d]=%d".formatted(rawName, i, packed)));
    }
  }

  private static void appendRuneStatusEffects(
      List<ItemEffectRow> rows, short[] values, String[] statusNames) {
    for (int i = 0; values != null && i < values.length; i++) {
      int packed = values[i] & 0xffff;
      int id = (packed >> 8) & 0xff;
      int rawChance = packed & 0xff;
      String status = runeStatusName(statusLabel(id, statusNames));
      rows.add(
          ItemEffectRow.of(
              WEAPON_SIDE,
              "Status chance",
              status,
              String.valueOf(rawChance / runeStatusChanceDivisor(status)),
              "%% chance, raw=%d".formatted(rawChance),
              "%s[%d]:chance".formatted(SHORT_ARR_B_RAW, i)));
      rows.add(
          ItemEffectRow.of(
              ARMOR_SIDE,
              "Status resistance",
              "Anti-" + status.toLowerCase(Locale.ROOT),
              String.valueOf(rawChance),
              "%",
              "%s[%d]:armor".formatted(SHORT_ARR_B_RAW, i)));
    }
  }

  private static String runeElementName(int id) {
    String displayName = DamageEffectKind.elementName(id);
    return displayName == null ? statName(id) : displayName;
  }

  private static String runeStatusName(String status) {
    return status;
  }

  private static int runeStatusChanceDivisor(String status) {
    return "Weak".equals(status) ? 15 : 5;
  }

  private static void appendConsumableUseEffect(
      List<ItemEffectRow> rows, Object item, int category) {
    int useEffect = category == 5 ? u8(raw(item, 32)) : 0;
    if (useEffect != 0) {
      rows.add(
          ItemEffectRow.of(
              CONSUMABLE_ROW,
              "Use visual/effect",
              "Effect ID",
              String.valueOf(useEffect),
              StringUtils.EMPTY,
              "byte_q"));
    }
  }

  private static void appendLinkedSkill(
      List<ItemEffectRow> rows, Object item, int category, String[] skillNames) {
    if (category != 9 && category != 10) {
      return;
    }
    int skillId = u8(raw(item, 30));
    int skillLevel = u8(raw(item, 31));
    rows.add(
        ItemEffectRow.of(
            slotLabel(category, u8(raw(item, 0)) & 0x0f),
            "Linked skill",
            skillNameForTalentLink(skillId + 1, skillNames),
            String.valueOf(skillLevel),
            "skill id=%d".formatted(skillId),
            "byte_o/byte_p"));
  }

  private static void appendWeaponDetails(List<ItemEffectRow> rows, Object item, int category) {
    if (category != 3) {
      return;
    }
    int weaponData = u8(raw(item, 18));
    int weaponReach = weaponData & 0x0f;
    int weaponMode = (weaponData >> 5) & 7;
    rows.add(
        ItemEffectRow.of(
            WEAPON_SIDE,
            "Reach",
            "Tiles",
            String.valueOf(weaponReach),
            "mode=" + weaponMode,
            "byte_f"));
    rows.add(
        ItemEffectRow.of(
            WEAPON_SIDE,
            "Animation",
            "Projectile/impact",
            "q=" + intValue(raw(item, 32)),
            "r=" + intValue(raw(item, 33)),
            "q/r"));
  }

  public static void appendPackedStat(
      List<ItemEffectRow> rows,
      String side,
      int baseStat,
      int packed,
      String rawName,
      int category) {
    int high = (packed >> 8) & 0xff;
    int low = packed & 0xff;
    if (high != 0) {
      appendPackedStatRow(rows, side, baseStat, high, "%s:hi".formatted(rawName), category);
    }
    if (low != 0) {
      appendPackedStatRow(rows, side, baseStat + 1, low, "%s:lo".formatted(rawName), category);
    }
  }

  private static void appendPackedStatRow(
      List<ItemEffectRow> rows, String side, int statId, int value, String rawName, int category) {
    String target = ItemEffectLabel.packedStatTarget(category, statId, rawName, statName(statId));
    if ("Rune".equals(side)) {
      rows.add(
          ItemEffectRow.of(
              WEAPON_SIDE, PACKED_STAT, target, String.valueOf(value), StringUtils.EMPTY, rawName));
      rows.add(
          ItemEffectRow.of(
              ARMOR_SIDE, PACKED_STAT, target, String.valueOf(value), StringUtils.EMPTY, rawName));
      return;
    }
    rows.add(
        ItemEffectRow.of(
            side, PACKED_STAT, target, String.valueOf(value), StringUtils.EMPTY, rawName));
  }

  public static void appendIntEffects(
      List<ItemEffectRow> rows, String side, int[] values, String rawName, int category) {
    for (int i = 0; values != null && i < values.length; i++) {
      int packed = values[i];
      int kind = (packed >> 16) & 0xff;
      int value = packed & 0xffff;
      rows.add(
          ItemEffectRow.of(
              side,
              ItemEffectLabel.intEffectType(category, kind, effectKind(kind)),
              ItemEffectLabel.intEffectTarget(category, kind, statName(kind)),
              String.valueOf(value),
              StringUtils.EMPTY,
              "%s[%d]=%d".formatted(rawName, i, packed)));
    }
  }

  public static void appendShortEffects(
      List<ItemEffectRow> rows,
      String side,
      short[] values,
      String[] statusNames,
      String rawName,
      int category) {
    for (int i = 0; values != null && i < values.length; i++) {
      int packed = values[i] & 0xffff;
      int id = (packed >> 8) & 0xff;
      int value = packed & 0xff;
      String status = statusLabel(id, statusNames);
      rows.add(
          ItemEffectRow.of(
              side,
              category == 2 ? "Status resistance" : "Status",
              ItemEffectLabel.statusTarget(category, status),
              String.valueOf(value),
              "%/value",
              rawName + "[" + i + "]=" + packed));
    }
  }

  public static String slotLabel(int category, int subtype) {
    return switch (category) {
      case 1 -> subtype == 0 ? "Ring" : "Neck";
      case 2 ->
          switch (subtype) {
            case 0 -> "Head";
            case 1 -> "Main Body Armor";
            case 4 -> "Boot";
            default -> "Armor subtype " + subtype;
          };
      case 3 -> "Main Weapon";
      case 5, 6 -> CONSUMABLE_ROW;
      case 7 -> "Runes";
      case 8 -> "Text/Special";
      case 9 -> "Battle-only Consumable";
      default -> "Special";
    };
  }

  public static String allowedClasses(byte[] allowed) {
    if (allowed == null || allowed.length == 0) {
      return "Any";
    }
    List<String> parts = new ArrayList<>();
    for (byte b : allowed) {
      parts.add(heroClassName(b & 0xff));
    }
    return joinParts(parts);
  }

  public static String heroClassName(int id) {
    return switch (id) {
      case 0 -> "Lara";
      case 1 -> "Vince";
      case 2 -> "Romus";
      case 3 -> "Manok";
      default -> "Class " + id;
    };
  }

  public static String itemNotes(int category, int subtype, int reach, int mode) {
    if (category == 3) {
      return "weapon: reach=" + reach + ", mode=" + mode;
    }
    if (category == 7) {
      return "rune/modifier: weapon effect + armor effect";
    }
    return "type=" + category + ", subtype=" + subtype;
  }

  public static String effectKind(int id) {
    if (id == 0) {
      return "Flat stat/damage";
    }
    if (id >= 9 && id <= 13) {
      return "Element/resistance";
    }
    if (id >= 14) {
      return "Status/resistance";
    }
    return "Modifier";
  }

  public static String skillNameForTalentLink(int skillUnlock, String[] skillNames) {
    int skillId = skillUnlock - 1;
    if (skillId < 0) {
      return StringUtils.EMPTY;
    }
    if (skillId >= skillNames.length) {
      return "Skill %d".formatted(skillId);
    }
    return skillNames[skillId];
  }

  public static String globalTalentName(int id) {
    return switch (id) {
      case 1 -> "Blood sucking / NPC resource gain";
      case 2 -> "Stealing tier";
      case 3 -> "Sharp senses";
      default -> "Global bonus %d".formatted(id);
    };
  }

  public static String resistanceTalentName(String talentName, int id) {
    String normalized =
        talentName == null ? StringUtils.EMPTY : talentName.toLowerCase(Locale.ROOT);
    if (normalized.contains("mental")) {
      return "Anti-sleep";
    }
    if (normalized.contains("poison")) {
      return "Anti-poison";
    }
    if (normalized.contains("magic eyes")) {
      return "Anti-blind";
    }
    if (normalized.contains("hard bones")) {
      return "Anti-blaze";
    }
    return "Resistance bonus " + id;
  }

  public static String heroBonusName(int id) {
    return switch (id) {
      case 1 -> "HP regen per turn";
      case 2 -> "Movement/zone bonus";
      case 3 -> "Critical chance %";
      case 4 -> "Critical damage bonus %";
      case 5 -> "Reflex/evasion";
      default -> "Hero bonus %d".formatted(id);
    };
  }

  public static String talentNotes(
      int heroBonus,
      int skillUnlock,
      String unlockedSkillName,
      int statusBonus,
      int resistanceBonus,
      int globalBonus,
      int currentLevel) {
    String prefix = currentLevel > 0 ? "current=%d; ".formatted(currentLevel) : StringUtils.EMPTY;

    if (skillUnlock > 0) {
      return "%shero talent unlocks castable skill %d%s."
          .formatted(
              prefix,
              skillUnlock - 1,
              unlockedSkillName.isEmpty()
                  ? StringUtils.EMPTY
                  : " (%s)".formatted(unlockedSkillName));
    }
    if (heroBonus == 3) {
      return prefix
          + "Find Weaknesses-like: adds amount percent critical chance per learned level.";
    }
    if (heroBonus == 4) {
      return prefix
          + "Deadly Might-like: adds amount percent critical damage bonus per learned level.";
    }
    if (heroBonus > 0) {
      return prefix + "hero-wide bonus id " + heroBonus + "; amount applies per learned level.";
    }
    if (statusBonus > 0) {
      return prefix + "status bonus id " + statusBonus + ".";
    }
    if (resistanceBonus > 0) {
      return prefix + "resistance bonus id " + resistanceBonus + ".";
    }
    if (globalBonus > 0) {
      return prefix + "global party bonus id " + globalBonus + ".";
    }
    return prefix + "amount applies per learned level; exact effect not named yet.";
  }

  public static String statName(int id) {
    String[] names = {
      "Strength/Power",
      "Spirit",
      "Vitality",
      "Speed",
      "Max HP",
      "Max Resource",
      "Move",
      "Regen",
      "Weapon Attack / Armor Defense",
      "Fire",
      "Frost",
      "Light",
      "Shadow",
      "Blood",
      "Status",
      "Poison",
      "Sleep",
      "Bleed",
      "Blind",
      "Silence",
      "Weak",
      "Frenzy",
      "Confuse",
      "Shackle",
      "Blaze",
      "Cold",
      "Fear"
    };
    return id >= 0 && id < names.length ? names[id] : "Stat " + id;
  }

  public static String statusLabel(int id, String[] statusNames) {
    return id >= 0 && id < statusNames.length ? statusDisplayName(statusNames[id]) : "Status " + id;
  }

  public static String statusDisplayName(String statusName) {
    return "Rigor".equals(statusName) ? "Instant Death" : statusName;
  }

  @SneakyThrows
  public static URLClassLoader selectedJarClassLoader(Path inputJar) {
    List<URL> urls = new ArrayList<>();
    urls.add(inputJar.toAbsolutePath().normalize().toUri().toURL());
    urls.add(EditorSupport.class.getProtectionDomain().getCodeSource().getLocation());
    addJavaMeLibraries(urls);
    log.info("Created isolated class loader for {} with {} URLs", inputJar, urls.size());
    return new URLClassLoader(urls.toArray(new URL[0]), null);
  }

  @SneakyThrows
  public static void addJavaMeLibraries(List<URL> urls) {
    Path javaMeLibraryDirectory = Path.of(ME_LIB_INTERNAL);
    if (!Files.isDirectory(javaMeLibraryDirectory)) {
      log.info(
          "Local Java ME library directory {} does not exist; using bundled classes",
          javaMeLibraryDirectory);
      return;
    }
    try (var paths = Files.list(javaMeLibraryDirectory)) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".jar"))
          .map(Path::toAbsolutePath)
          .map(Path::normalize)
          .map(EditorSupport::toUrl)
          .forEach(urls::add);
    }
  }

  @SneakyThrows
  public static URL toUrl(Path path) {
    return path.toUri().toURL();
  }

  public static byte[] readZipEntry(ZipInputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) >= 0) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  public static byte[] readJarEntry(Path inputJar, String entryName) throws IOException {
    try (ZipInputStream in = new ZipInputStream(Files.newInputStream(inputJar))) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        if (!entry.isDirectory() && entryName.equals(entry.getName())) {
          return readZipEntry(in);
        }
        in.closeEntry();
      }
    }
    throw new IOException("JAR does not contain " + entryName);
  }

  public static void replaceJarEntries(
      Path inputJar, Path outputJar, Map<String, byte[]> replacements) throws IOException {
    log.info("Replacing {} entries from {} into {}", replacements.size(), inputJar, outputJar);
    Set<String> seen = new HashSet<>();
    try (ZipInputStream in = new ZipInputStream(Files.newInputStream(inputJar));
        ZipOutputStream out =
            new ZipOutputStream(
                Files.newOutputStream(
                    outputJar, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        ZipEntry copy = new ZipEntry(entry.getName());
        copy.setTime(entry.getTime());
        out.putNextEntry(copy);
        byte[] replacement = replacements.get(entry.getName());
        if (replacement != null) {
          out.write(replacement);
          seen.add(entry.getName());
        } else {
          byte[] buffer = new byte[8192];
          int read;
          while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
          }
        }
        out.closeEntry();
        in.closeEntry();
      }
      for (Map.Entry<String, byte[]> replacement : replacements.entrySet()) {
        if (seen.contains(replacement.getKey())) {
          continue;
        }
        out.putNextEntry(new ZipEntry(replacement.getKey()));
        out.write(replacement.getValue());
        out.closeEntry();
      }
    }
    log.info("Finished writing patched JAR {}", outputJar);
  }

  @SneakyThrows
  public static Object raw(Object value, int ordinal) {
    int i = 0;
    for (Field field : value.getClass().getFields()) {
      if (!Modifier.isStatic(field.getModifiers()) && i++ == ordinal) {
        return field.get(value);
      }
    }
    return null;
  }

  @SneakyThrows
  public static Object[] staticArray(Class<?> owner, Class<?> component, int ordinal) {
    int seen = 0;
    for (Field field : owner.getFields()) {
      if (staticArrayField(field, component) && seen++ == ordinal) {
        return (Object[]) field.get(null);
      }
    }
    return new Object[0];
  }

  @SneakyThrows
  public static Object[] largerStaticArray(Class<?> owner, Class<?> component) {
    Object[] best = new Object[0];
    for (Field field : owner.getFields()) {
      Object[] value = staticArrayField(field, component) ? (Object[]) field.get(null) : null;
      if (longerThan(value, best)) {
        best = value;
      }
    }
    return best;
  }

  @SneakyThrows
  public static byte[][] staticByte2d(Class<?> owner, int ordinal) {
    int seen = 0;
    for (Field field : owner.getFields()) {
      if (staticByte2dField(field) && seen++ == ordinal) {
        return (byte[][]) field.get(null);
      }
    }
    return new byte[0][];
  }

  @SuppressWarnings("java:S3011")
  public static void setFirstStaticBoolean(Class<?> owner, boolean value)
      throws IllegalAccessException {
    for (Field field : owner.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) && field.getType() == Boolean.TYPE) {
        field.setBoolean(null, value);
        return;
      }
    }
  }

  @SneakyThrows
  public static String decodeName(Object value, int byteArrayOrdinal, Method decode) {
    int seen = 0;
    for (Field field : value.getClass().getFields()) {
      if (byteArrayField(field) && seen++ == byteArrayOrdinal) {
        return decodeBytes((byte[]) field.get(value), decode);
      }
    }
    return StringUtils.EMPTY;
  }

  @SneakyThrows
  public static String decodeBytes(byte[] encoded, Method decode) {
    return encoded == null ? StringUtils.EMPTY : (String) decode.invoke(null, encoded);
  }

  public static Object[] objectArray(Object value) {
    return value == null ? new Object[0] : (Object[]) value;
  }

  public static int[] intArray(Object value) {
    return value instanceof int[] arrInt ? arrInt : new int[0];
  }

  public static short[] shortArray(Object value) {
    return value instanceof short[] arrShort ? arrShort : new short[0];
  }

  public static short[] nullableShortArray(Object value) {
    return value instanceof short[] arrShort ? arrShort : null;
  }

  public static byte[] byteArray(Object value) {
    return value instanceof byte[] arrByte ? arrByte : null;
  }

  public static int u8(Object value) {
    return value instanceof Byte aByte ? aByte & 0xff : 0;
  }

  public static int intValue(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }

  public static int shortValue(Object value) {
    return value instanceof Number number ? number.intValue() & 0xffff : 0;
  }

  public static int u8(byte value) {
    return value & 0xff;
  }

  public static int u16(byte[] data, int offset) {
    return (u8(data[offset]) << 8) | u8(data[offset + 1]);
  }

  public static int signedChance(int raw) {
    return (raw & 0x80) != 0 ? -((-raw) & 0x7f) : raw & 0x7f;
  }

  public static void writeU16(byte[] data, int offset, int value) {
    if (value < 0 || value > 0xffff) {
      throw new IllegalArgumentException("damage must be 0..65535");
    }
    data[offset] = (byte) ((value >>> 8) & 0xff);
    data[offset + 1] = (byte) (value & 0xff);
  }

  public static int checked7Bit(int value, String label) {
    if (value < 0 || value > 127) {
      throw new IllegalArgumentException(label + " must be 0..127");
    }
    return value;
  }

  public static int checkedTalentMaxLevel(int value) {
    if (value < 1 || value > 4) {
      throw new IllegalArgumentException("talent max level must be 1..4");
    }
    return value;
  }

  public static byte checkedTalentLink(int value, String label) {
    if (value < 1 || value > 256) {
      throw new IllegalArgumentException(label + " must be 1..256 for an existing talent link");
    }
    return (byte) ((value - 1) & 0xff);
  }

  public static int checked4Bit(int value, String label) {
    if (value < 0 || value > 15) {
      throw new IllegalArgumentException(label + " must be 0..15");
    }
    return value;
  }

  public static int checkedRange(int value, int min, int max, String label) {
    if (value < min || value > max) {
      throw new IllegalArgumentException("%s must be %d..%d".formatted(label, min, max));
    }
    return value;
  }

  public static byte checkedByte(int value, String label) {
    if (value < 0 || value > 255) {
      throw new IllegalArgumentException(label + " must be 0..255");
    }
    return (byte) value;
  }

  public static byte encodeSignedChance(int chance) {
    if (chance < -127 || chance > 127) {
      throw new IllegalArgumentException("status chance must be -127..127");
    }
    return chance < 0 ? (byte) (-(-chance & 0x7f)) : (byte) (chance & 0x7f);
  }

  public static String joinParts(List<String> values) {
    return String.join(", ", values);
  }

  private static boolean staticArrayField(Field field, Class<?> component) {
    Class<?> type = field.getType();
    return Modifier.isStatic(field.getModifiers())
        && type.isArray()
        && type.getComponentType() == component;
  }

  private static boolean staticByte2dField(Field field) {
    Class<?> type = field.getType();
    return Modifier.isStatic(field.getModifiers())
        && type.isArray()
        && type.getComponentType().isArray()
        && type.getComponentType().getComponentType() == Byte.TYPE;
  }

  private static boolean byteArrayField(Field field) {
    Class<?> type = field.getType();
    return !Modifier.isStatic(field.getModifiers())
        && type.isArray()
        && type.getComponentType() == Byte.TYPE;
  }

  private static boolean longerThan(Object[] value, Object[] best) {
    return value != null && value.length > best.length;
  }

  private record SkillPayload(int nextOffset, int damageCount, int statusCount) {}
}
