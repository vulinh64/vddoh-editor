package com.vddoh.editor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.swing.JOptionPane;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EditorSupport {

  private static final String PACKED_HERO_HEADER = "packed hero header";
  private static final String EQUIPMENT = "Equipment";
  private static final String RUNE_EQUIPMENT = "Rune/Equipment";
  private static final String PACKED_HERO_STATS = "packed hero stat";
  public static final String PACKED_STAT = "Packed Stat";
  public static final String ME_LIB_INTERNAL = "me-lib";
  public static final String CONSUMABLE_ROW = "Consumable";
  public static final String WEAPON_SIDE = "Weapon";

  static Path editorUserPath(String child) {
    return Path.of(System.getProperty("user.home"), ".vddoh-editor", child);
  }

  static void appendNamed(
      List<NamedRow> out, Object[] values, int nameOrdinal, Method decode, String notes) {
    for (int i = 0; values != null && i < values.length; i++) {
      out.add(NamedRow.of(i, decodeName(values[i], nameOrdinal, decode), notes));
    }
  }

  static int skipDamageGroups(byte[] data, int n) {
    int count = u8(data[n++]);
    for (int i = 0; i < count; i++) {
      n += 1 + (data[n] & 0x7f);
    }
    return n;
  }

  static int skipStatuses(byte[] data, int n) {
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

  static int heroStartOffset(byte[] data) {
    return skipMonsters(data, monsterStartOffset(data));
  }

  static int monsterStartOffset(byte[] data) {
    int n = 13 + u16(data, 11) * 5;
    n = skipDamageGroups(data, n);
    n = skipStatuses(data, n);
    return skipSkills(data, n);
  }

  static int skipSkills(byte[] data, int n) {
    int skillCount = u8(data[n++]);
    for (int skillId = 0; skillId < skillCount; skillId++) {
      int nameLen = data[n] & 0x1f;
      n += 1 + nameLen;
      int header = u8(data[n++]);
      int levelCount = ((header >> 6) & 3) + 1;
      int inheritedFlags = header & 7;
      n++;
      int packedUsability = u8(data[n++]);
      inheritedFlags = inheritedFlags | ((packedUsability & 1) << 3);
      if ((packedUsability & 8) != 0) {
        n++;
      }
      n++;
      if ((inheritedFlags & 1) != 0) {
        n += 2;
      }
      int damageCount = u8(data[n++]);
      n += damageCount * 3;
      int statusCount = 0;
      if ((inheritedFlags & 8) != 0) {
        statusCount = u8(data[n++]);
        n += statusCount * 2;
      }
      n += 2;
      if ((inheritedFlags & 4) != 0) {
        n += 2;
      }
      if ((inheritedFlags & 2) != 0) {
        n += 2;
      }
      n++;
      for (int level = 1; level < levelCount; level++) {
        int overrideFlags = u8(data[n++]);
        int reuseFlags = u8(data[n++]);
        n = GameDatSkillPatcher.skipOverrideLevelPrefix(n, overrideFlags, reuseFlags);
        if ((overrideFlags & 0x10) != 0) {
          n++;
        }
        if ((reuseFlags & 0x10) != 0 || (overrideFlags & 4) != 0) {
          n++;
        }
        if ((overrideFlags & 4) != 0) {
          n++;
        }
        if ((reuseFlags & 4) != 0) {
          n++;
        } else if ((overrideFlags & 2) != 0) {
          n += damageCount * 2;
        }
        if ((reuseFlags & 2) != 0) {
          n++;
        } else if ((overrideFlags & 1) != 0) {
          n += statusCount;
        }
        n += 2;
      }
    }
    return n;
  }

  static int skipHeroes(byte[] data, int n) {
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

  static int skipMonsters(byte[] data, int n) {
    int count = u8(data[n++]);
    for (int i = 0; i < count; i++) {
      int nameLen = u8(data[n]);
      n += 1 + nameLen;
      n = GameDatMonsterPatcher.getN(data, n);
      n += 13;
    }
    return n;
  }

  static int equipmentFlag(byte[] data, int offset, int slot) {
    return switch (slot) {
      case 0 -> (data[offset] & 8) != 0 ? ((data[offset] & 4) != 0 ? 1 : 0) : -1;
      case 1 -> (data[offset] & 2) != 0 ? ((data[offset] & 1) != 0 ? 1 : 0) : -1;
      case 2 -> (data[offset + 1] & 0x80) != 0 ? ((data[offset + 1] & 0x40) != 0 ? 1 : 0) : -1;
      case 3 -> (data[offset + 1] & 0x20) != 0 ? ((data[offset + 1] & 0x10) != 0 ? 1 : 0) : -1;
      case 4 -> (data[offset + 1] & 8) != 0 ? ((data[offset + 1] & 4) != 0 ? 1 : 0) : -1;
      case 5 -> (data[offset + 1] & 2) != 0 ? ((data[offset + 1] & 1) != 0 ? 1 : 0) : -1;
      case 6 -> (data[offset + 2] & 0x80) != 0 ? ((data[offset + 2] & 0x40) != 0 ? 1 : 0) : -1;
      case 7 -> (data[offset + 2] & 0x20) != 0 ? ((data[offset + 2] & 0x10) != 0 ? 1 : 0) : -1;
      case 8 -> (data[offset + 2] & 8) != 0 ? ((data[offset + 2] & 4) != 0 ? 1 : 0) : -1;
      case 9 -> (data[offset + 2] & 2) != 0 ? ((data[offset + 2] & 1) != 0 ? 1 : 0) : -1;
      default -> -1;
    };
  }

  static void writeHeroStats(
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

  static void writeHeroSeeds(
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

  static void writeMonsterHeader(
      byte[] data, int offset, int experience, int filar, int deathValue) {
    if (experience < 0 || experience > 4095) {
      throw new IllegalArgumentException("monster EXP must be 0..4095");
    }
    if (filar < 0 || filar > 4095) {
      throw new IllegalArgumentException("monster Filar must be 0..4095");
    }
    if (deathValue < 0 || deathValue > 127) {
      throw new IllegalArgumentException("monster death value must be 0..127");
    }
    data[offset] = checkedByte((experience >>> 4) & 0xff, "monster EXP high");
    data[offset + 1] =
        checkedByte(
            ((experience & 0x0f) << 4) | ((filar >>> 8) & 0x0f), "packed monster EXP/Filar");
    data[offset + 2] = checkedByte(filar & 0xff, "monster Filar low");
    data[offset + 3] = checkedByte(deathValue, "monster death value");
  }

  static String[] decodedNames(Object[] values, Method decode) {
    if (values == null) {
      return new String[0];
    }
    String[] names = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      names[i] = decodeName(values[i], 0, decode);
    }
    return names;
  }

  static List<ItemEffectRow> decodeItemEffects(
      Object item, int category, String[] statusNames, String[] skillNames) {
    List<ItemEffectRow> rows = new ArrayList<>();
    int hpRestore = intValue(raw(item, 21));
    int resourceRestore = intValue(raw(item, 22));
    if (hpRestore > 0) {
      rows.add(
          ItemEffectRow.of(
              category == 5 ? CONSUMABLE_ROW : EQUIPMENT,
              category == 5 ? "HP effect" : "Restore HP",
              "HP",
              String.valueOf(hpRestore),
              category == 5 ? "battle consumable" : StringUtils.EMPTY,
              "short_g"));
    }
    if (resourceRestore > 0) {
      rows.add(
          ItemEffectRow.of(
              category == 5 ? CONSUMABLE_ROW : EQUIPMENT,
              category == 5 ? "Resource effect" : "Restore Resource",
              "Blood/Soul",
              String.valueOf(resourceRestore),
              category == 5 ? "battle consumable" : StringUtils.EMPTY,
              "short_h"));
    }

    String packedStatSide =
        switch (category) {
          case 5 -> "Consumable stat boost";
          case 7 -> RUNE_EQUIPMENT;
          default -> EQUIPMENT;
        };
    appendPackedStat(rows, packedStatSide, 0, shortValue(raw(item, 8)), "short_c");
    appendPackedStat(rows, packedStatSide, 2, shortValue(raw(item, 9)), "short_d");
    appendPackedStat(rows, packedStatSide, 4, shortValue(raw(item, 10)), "short_e");
    appendPackedStat(rows, packedStatSide, 6, shortValue(raw(item, 11)), "short_f");
    int misc = u8(raw(item, 12));
    if (misc != 0) {
      rows.add(
          ItemEffectRow.of(
              packedStatSide,
              PACKED_STAT,
              statName(8),
              String.valueOf(misc),
              StringUtils.EMPTY,
              "byte_d"));
    }

    appendIntEffects(
        rows,
        category == 7 ? "Weapon effect" : "Equipment/Weapon",
        intArray(raw(item, 13)),
        "int_arr_a");
    appendIntEffects(rows, "Armor effect", intArray(raw(item, 14)), "int_arr_b");
    appendShortEffects(
        rows,
        category == 5 ? "Consumable status gate" : "Protection",
        shortArray(raw(item, 15)),
        statusNames,
        "short_arr_a");
    appendShortEffects(
        rows,
        category == 5 ? "Consumable status effect" : "On hit / item use",
        shortArray(raw(item, 16)),
        statusNames,
        "short_arr_b");

    if (category == 5) {
      int useEffect = u8(raw(item, 32));
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

    if (category == 9 || category == 10) {
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

    if (category == 3) {
      int weaponReach = u8(raw(item, 18)) & 0x0f;
      int weaponMode = (u8(raw(item, 18)) >> 5) & 7;
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

  static void appendPackedStat(
      List<ItemEffectRow> rows, String side, int baseStat, int packed, String rawName) {
    int high = (packed >> 8) & 0xff;
    int low = packed & 0xff;
    if (high != 0) {
      rows.add(
          ItemEffectRow.of(
              side,
              PACKED_STAT,
              statName(baseStat),
              String.valueOf(high),
              StringUtils.EMPTY,
              "%s:hi".formatted(rawName)));
    }
    if (low != 0) {
      rows.add(
          ItemEffectRow.of(
              side,
              PACKED_STAT,
              statName(baseStat + 1),
              String.valueOf(low),
              StringUtils.EMPTY,
              "%s:lo".formatted(rawName)));
    }
  }

  static void appendIntEffects(
      List<ItemEffectRow> rows, String side, int[] values, String rawName) {
    for (int i = 0; values != null && i < values.length; i++) {
      int packed = values[i];
      int kind = (packed >> 16) & 0xff;
      int value = packed & 0xffff;
      rows.add(
          ItemEffectRow.of(
              side,
              effectKind(kind),
              statName(kind),
              String.valueOf(value),
              StringUtils.EMPTY,
              "%s[%d]=%d".formatted(rawName, i, packed)));
    }
  }

  static void appendShortEffects(
      List<ItemEffectRow> rows, String side, short[] values, String[] statusNames, String rawName) {
    for (int i = 0; values != null && i < values.length; i++) {
      int packed = values[i] & 0xffff;
      int id = (packed >> 8) & 0xff;
      int value = packed & 0xff;
      rows.add(
          ItemEffectRow.of(
              side,
              "Status",
              statusLabel(id, statusNames),
              String.valueOf(value),
              "%/value",
              rawName + "[" + i + "]=" + packed));
    }
  }

  static String slotLabel(int category, int subtype) {
    switch (category) {
      case 1:
        return subtype == 0 ? "Ring" : "Neck";
      case 2:
        if (subtype == 0) {
          return "Main Body Armor";
        }
        if (subtype == 1) {
          return "Head";
        }
        if (subtype == 4) {
          return "Boot";
        }
        return "Armor subtype " + subtype;
      case 3:
        return "Main Weapon";
      case 4:
        return "Special";
      case 5:
        return CONSUMABLE_ROW;
      case 6:
        return CONSUMABLE_ROW;
      case 7:
        return "Runes";
      case 8:
        return "Text/Special";
      case 9:
        return "Battle-only Consumable";
      case 10:
        return "Special";
      case 12:
        return "Special";
      default:
        return "Special";
    }
  }

  static String allowedClasses(byte[] allowed) {
    if (allowed == null || allowed.length == 0) {
      return "Any";
    }
    List<String> parts = new ArrayList<>();
    for (byte b : allowed) {
      parts.add(heroClassName(b & 0xff));
    }
    return joinParts(parts);
  }

  static String heroClassName(int id) {
    return switch (id) {
      case 0 -> "Lara";
      case 1 -> "Vince";
      case 2 -> "Romus";
      case 3 -> "Manok";
      default -> "Class " + id;
    };
  }

  static String itemNotes(int category, int subtype, int reach, int mode) {
    if (category == 3) {
      return "weapon: reach=" + reach + ", mode=" + mode;
    }
    if (category == 7) {
      return "rune/modifier: weapon effect + armor effect";
    }
    return "type=" + category + ", subtype=" + subtype;
  }

  static String effectKind(int id) {
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

  static String skillNameForTalentLink(int skillUnlock, String[] skillNames) {
    int skillId = skillUnlock - 1;
    if (skillId < 0) {
      return StringUtils.EMPTY;
    }
    if (skillId >= skillNames.length) {
      return "Skill %d".formatted(skillId);
    }
    return skillNames[skillId];
  }

  static String globalTalentName(int id) {
    return switch (id) {
      case 1 -> "Blood sucking / NPC resource gain";
      case 2 -> "Stealing tier";
      case 3 -> "Sharp senses";
      default -> "Global bonus %d".formatted(id);
    };
  }

  static String resistanceTalentName(String talentName, int id) {
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

  static String heroBonusName(int id) {
    return switch (id) {
      case 1 -> "HP regen per turn";
      case 2 -> "Movement/zone bonus";
      case 3 -> "Critical chance %";
      case 4 -> "Critical damage bonus %";
      case 5 -> "Reflex/evasion";
      default -> "Hero bonus %d".formatted(id);
    };
  }

  static String talentNotes(
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

  static String statName(int id) {
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
      "Enfeeble",
      "Frenzy",
      "Confuse",
      "Shackle",
      "Blaze",
      "Cold",
      "Fear"
    };
    return id >= 0 && id < names.length ? names[id] : "Stat " + id;
  }

  static String statusLabel(int id, String[] statusNames) {
    return id >= 0 && id < statusNames.length ? statusNames[id] : "Status " + id;
  }

  @SneakyThrows
  static URLClassLoader selectedJarClassLoader(Path inputJar) {
    List<URL> urls = new ArrayList<>();
    urls.add(inputJar.toAbsolutePath().normalize().toUri().toURL());
    urls.add(EditorSupport.class.getProtectionDomain().getCodeSource().getLocation());
    addJavaMeLibraries(urls);
    log.info("Created isolated class loader for {} with {} URLs", inputJar, urls.size());
    return new URLClassLoader(urls.toArray(new URL[0]), null);
  }

  @SneakyThrows
  static void addJavaMeLibraries(List<URL> urls) {
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
  static URL toUrl(Path path) {
    return path.toUri().toURL();
  }

  static byte[] readZipEntry(ZipInputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) >= 0) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  static byte[] readJarEntry(Path inputJar, String entryName) throws IOException {
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

  static void replaceJarEntries(Path inputJar, Path outputJar, Map<String, byte[]> replacements)
      throws IOException {
    log.info("Replacing {} entries from {} into {}", replacements.size(), inputJar, outputJar);
    Set<String> seen = new HashSet<>();
    try (ZipInputStream in = new ZipInputStream(Files.newInputStream(inputJar));
        ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(outputJar))) {
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
  static Object raw(Object value, int ordinal) {
    int i = 0;
    for (Field field : value.getClass().getFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      if (i++ == ordinal) {
        return field.get(value);
      }
    }
    return null;
  }

  @SneakyThrows
  static Object[] staticArray(Class<?> owner, Class<?> component, int ordinal) {
    int seen = 0;
    for (Field field : owner.getFields()) {
      if (!Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      Class<?> type = field.getType();
      if (!type.isArray() || type.getComponentType() != component) {
        continue;
      }
      if (seen++ == ordinal) {
        return (Object[]) field.get(null);
      }
    }
    return new Object[0];
  }

  @SneakyThrows
  static Object[] largerStaticArray(Class<?> owner, Class<?> component) {
    Object[] best = new Object[0];
    for (Field field : owner.getFields()) {
      if (!Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      Class<?> type = field.getType();
      if (!type.isArray() || type.getComponentType() != component) {
        continue;
      }
      Object[] value = (Object[]) field.get(null);
      if (value != null && value.length > best.length) {
        best = value;
      }
    }
    return best;
  }

  @SneakyThrows
  static byte[][] staticByte2d(Class<?> owner, int ordinal) {
    int seen = 0;
    for (Field field : owner.getFields()) {
      if (!Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      Class<?> type = field.getType();
      if (!type.isArray()
          || !type.getComponentType().isArray()
          || type.getComponentType().getComponentType() != Byte.TYPE) {
        continue;
      }
      if (seen++ == ordinal) {
        return (byte[][]) field.get(null);
      }
    }
    return new byte[0][];
  }

  @SuppressWarnings("java:S3011")
  static void setFirstStaticBoolean(Class<?> owner, boolean value) throws IllegalAccessException {
    for (Field field : owner.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) && field.getType() == Boolean.TYPE) {
        field.setBoolean(null, value);
        return;
      }
    }
  }

  @SneakyThrows
  static String decodeName(Object value, int byteArrayOrdinal, Method decode) {
    int seen = 0;
    for (Field field : value.getClass().getFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      if (!field.getType().isArray() || field.getType().getComponentType() != Byte.TYPE) {
        continue;
      }
      if (seen++ == byteArrayOrdinal) {
        return decodeBytes((byte[]) field.get(value), decode);
      }
    }
    return StringUtils.EMPTY;
  }

  @SneakyThrows
  static String decodeBytes(byte[] encoded, Method decode) {
    return encoded == null ? StringUtils.EMPTY : (String) decode.invoke(null, encoded);
  }

  static Object[] objectArray(Object value) {
    return value == null ? new Object[0] : (Object[]) value;
  }

  static int[] intArray(Object value) {
    return value instanceof int[] arrInt ? arrInt : new int[0];
  }

  static short[] shortArray(Object value) {
    return value instanceof short[] arrShort ? arrShort : new short[0];
  }

  static short[] nullableShortArray(Object value) {
    return value instanceof short[] arrShort ? arrShort : null;
  }

  static byte[] byteArray(Object value) {
    return value instanceof byte[] arrByte ? arrByte : null;
  }

  static int u8(Object value) {
    return value instanceof Byte aByte ? aByte & 0xff : 0;
  }

  static int intValue(Object value) {
    return value instanceof Number number ? number.intValue() & 0xffff : 0;
  }

  static int shortValue(Object value) {
    return value instanceof Number number ? number.intValue() & 0xffff : 0;
  }

  static int u8(byte value) {
    return value & 0xff;
  }

  static int u16(byte[] data, int offset) {
    return (u8(data[offset]) << 8) | u8(data[offset + 1]);
  }

  static int signedChance(int raw) {
    return (raw & 0x80) != 0 ? -((-raw) & 0x7f) : raw & 0x7f;
  }

  static void writeU16(byte[] data, int offset, int value) {
    if (value < 0 || value > 0xffff) {
      throw new IllegalArgumentException("damage must be 0..65535");
    }
    data[offset] = (byte) ((value >>> 8) & 0xff);
    data[offset + 1] = (byte) (value & 0xff);
  }

  static int checked7Bit(int value, String label) {
    if (value < 0 || value > 127) {
      throw new IllegalArgumentException(label + " must be 0..127");
    }
    return value;
  }

  static int checkedTalentMaxLevel(int value) {
    if (value < 1 || value > 4) {
      throw new IllegalArgumentException("talent max level must be 1..4");
    }
    return value;
  }

  static byte checkedTalentLink(int value, String label) {
    if (value < 1 || value > 256) {
      throw new IllegalArgumentException(label + " must be 1..256 for an existing talent link");
    }
    return (byte) ((value - 1) & 0xff);
  }

  static int checked4Bit(int value, String label) {
    if (value < 0 || value > 15) {
      throw new IllegalArgumentException(label + " must be 0..15");
    }
    return value;
  }

  static byte checkedByte(int value, String label) {
    if (value < 0 || value > 255) {
      throw new IllegalArgumentException(label + " must be 0..255");
    }
    return (byte) value;
  }

  static byte encodeSignedChance(int chance) {
    if (chance < -127 || chance > 127) {
      throw new IllegalArgumentException("status chance must be -127..127");
    }
    return chance < 0 ? (byte) (-(-chance & 0x7f)) : (byte) (chance & 0x7f);
  }

  static String joinLines(List<String> values) {
    return String.join("\n", values);
  }

  static String joinParts(List<String> values) {
    return String.join(", ", values);
  }

  static void showError(java.awt.Component parent, Exception ex) {
    log.error("Editor operation failed", ex);
    JOptionPane.showMessageDialog(parent, ex.toString(), "Error", JOptionPane.ERROR_MESSAGE);
  }
}
