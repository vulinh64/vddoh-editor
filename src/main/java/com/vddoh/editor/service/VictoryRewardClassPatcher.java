package com.vddoh.editor.service;

import com.vddoh.editor.data.PatchSummary;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class VictoryRewardClassPatcher {

  private static final String ENGINE_CLASS_NAME = "j";
  private static final String HERO_CLASS_NAME = "g";
  private static final String HERO_ARRAY_FIELD = "a";
  private static final String HERO_ARRAY_FIELD_DESCRIPTOR = "[Lg;";
  private static final String PENDING_EXP_FIELD = "r";
  private static final String PENDING_FILAR_FIELD = "s";
  private static final String PENDING_REWARD_DESCRIPTOR = "S";
  private static final String HERO_AWARD_EXP_METHOD = "a";
  private static final String HERO_AWARD_EXP_DESCRIPTOR = "(IZ)V";

  private static final byte[] ORIGINAL =
      new byte[] {
        (byte) 0xb2,
        0x00,
        0x6d,
        0x1c,
        0x32,
        (byte) 0xb2,
        0x01,
        (byte) 0xfa,
        0x03,
        (byte) 0xb6,
        0x02,
        0x66
      };

  private static final byte[] PATCHED =
      new byte[] {
        (byte) 0xb2,
        0x00,
        0x6d,
        0x1c,
        0x32,
        (byte) 0xb2,
        0x01,
        (byte) 0xf7,
        0x03,
        (byte) 0xb6,
        0x02,
        0x66
      };

  public enum State {
    ORIGINAL,
    PATCHED,
    UNKNOWN
  }

  public static State state(byte[] data) {
    int original = countPattern(data, ORIGINAL);
    int patched = countPattern(data, PATCHED);
    int semanticOriginal = semanticMatches(data, PENDING_FILAR_FIELD);
    int semanticPatched = semanticMatches(data, PENDING_EXP_FIELD);
    if (patched == 1 && original == 0 && semanticPatched == 1 && semanticOriginal == 0) {
      return State.PATCHED;
    }
    if (original == 1 && patched == 0 && semanticOriginal == 1) {
      return State.ORIGINAL;
    }
    log.info(
        "Victory reward class patch state unknown; original={}, patched={}, semanticOriginal={}, semanticPatched={}",
        original,
        patched,
        semanticOriginal,
        semanticPatched);
    return State.UNKNOWN;
  }

  static PatchSummary patch(byte[] data) {
    PatchSummary summary = new PatchSummary();
    State state = state(data);
    log.info("Applying victory reward class patch; current state={}", state);
    if (state == State.PATCHED) {
      summary.incrementSkipped();
      log.info("Victory reward class patch skipped because class is already patched");
      return summary;
    }
    if (state != State.ORIGINAL) {
      throw new IllegalArgumentException("Unsupported j.class layout for victory reward patch");
    }
    int offset = indexOf(data);
    System.arraycopy(PATCHED, 0, data, offset, PATCHED.length);
    if (state(data) != State.PATCHED) {
      throw new IllegalStateException(
          "Victory reward patch did not produce the expected j.class bytecode");
    }
    summary.incrementVictoryExpReward();
    log.info("Victory reward class patch applied at byte offset {}", offset);
    return summary;
  }

  private static int semanticMatches(byte[] data, String rewardFieldName) {
    try {
      int matches = 0;
      for (MethodModel method : ClassPatchSupport.classModel(data).methods()) {
        List<Instruction> instructions = ClassPatchSupport.instructions(method);
        for (int i = 0; i + 5 < instructions.size(); i++) {
          if (isRewardRemainderAward(instructions, i, rewardFieldName)) {
            matches++;
          }
        }
      }
      return matches;
    } catch (RuntimeException | LinkageError _) {
      return 0;
    }
  }


  private static boolean isRewardRemainderAward(
      List<Instruction> instructions, int offset, String rewardFieldName) {
    return instructions.get(offset) instanceof FieldInstruction heroArray
        && isField(heroArray, HERO_CLASS_NAME, HERO_ARRAY_FIELD, HERO_ARRAY_FIELD_DESCRIPTOR)
        && instructions.get(offset + 2).opcode() == Opcode.AALOAD
        && instructions.get(offset + 3) instanceof FieldInstruction reward
        && isField(reward, ENGINE_CLASS_NAME, rewardFieldName, PENDING_REWARD_DESCRIPTOR)
        && instructions.get(offset + 4).opcode() == Opcode.ICONST_0
        && instructions.get(offset + 5) instanceof InvokeInstruction invoke
        && invoke.opcode() == Opcode.INVOKEVIRTUAL
        && HERO_CLASS_NAME.equals(invoke.owner().asInternalName())
        && HERO_AWARD_EXP_METHOD.equals(invoke.name().stringValue())
        && HERO_AWARD_EXP_DESCRIPTOR.equals(invoke.type().stringValue());
  }

  private static boolean isField(
      FieldInstruction instruction, String owner, String name, String descriptor) {
    return instruction.opcode() == Opcode.GETSTATIC
        && owner.equals(instruction.owner().asInternalName())
        && name.equals(instruction.name().stringValue())
        && descriptor.equals(instruction.type().stringValue());
  }

  private static int countPattern(byte[] data, byte[] pattern) {
    int count = 0;
    for (int i = 0; i <= data.length - pattern.length; i++) {
      if (matches(data, pattern, i)) {
        count++;
      }
    }
    return count;
  }

  private static int indexOf(byte[] data) {
    for (int i = 0; i <= data.length - VictoryRewardClassPatcher.ORIGINAL.length; i++) {
      if (matches(data, VictoryRewardClassPatcher.ORIGINAL, i)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean matches(byte[] data, byte[] pattern, int offset) {
    for (int i = 0; i < pattern.length; i++) {
      if (data[offset + i] != pattern[i]) {
        return false;
      }
    }
    return true;
  }
}
