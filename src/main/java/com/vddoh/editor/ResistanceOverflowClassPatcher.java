package com.vddoh.editor;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ResistanceOverflowClassPatcher {

  private static final List<Opcode> OPCODES =
      List.of(
          Opcode.ALOAD_0,
          Opcode.GETFIELD,
          Opcode.GETSTATIC,
          Opcode.BALOAD,
          Opcode.IFGE,
          Opcode.ALOAD_0,
          Opcode.GETFIELD,
          Opcode.GETSTATIC,
          Opcode.ICONST_0,
          Opcode.BASTORE,
          Opcode.GOTO);

  enum State {
    ORIGINAL,
    PATCHED,
    UNKNOWN
  }

  private static final byte[] ORIGINAL =
      new byte[] {
        0x2a,
        (byte) 0xb4,
        0x00,
        0x46,
        (byte) 0xb2,
        0x00,
        0x15,
        0x33,
        (byte) 0x9c,
        0x00,
        0x0f,
        0x2a,
        (byte) 0xb4,
        0x00,
        0x46,
        (byte) 0xb2,
        0x00,
        0x15,
        0x03,
        0x54,
        (byte) 0xa7,
        0x00,
        0x1a
      };
  private static final byte[] PATCHED =
      new byte[] {
        0x2a,
        (byte) 0xb4,
        0x00,
        0x46,
        (byte) 0xb2,
        0x00,
        0x15,
        0x33,
        (byte) 0x9c,
        0x00,
        0x0f,
        0x2a,
        (byte) 0xb4,
        0x00,
        0x46,
        (byte) 0xb2,
        0x00,
        0x15,
        0x10,
        0x64,
        0x54,
        0x00,
        0x00
      };

  static State state(byte[] data) {
    int original = countPattern(data, ORIGINAL);
    int patched = countPattern(data, PATCHED);
    if (patched == 1 && original == 0) {
      return State.PATCHED;
    }
    if (original == 1 && patched == 0 && semanticOriginalMatches(data) == 1) {
      return State.ORIGINAL;
    }
    return State.UNKNOWN;
  }

  static PatchSummary patch(byte[] data) {
    PatchSummary summary = new PatchSummary();
    State state = state(data);
    log.info("Applying resistance overflow class patch; current state={}", state);
    if (state == State.PATCHED) {
      summary.skipped++;
      log.info("Resistance overflow class patch skipped because class is already patched");
      return summary;
    }
    if (state != State.ORIGINAL) {
      throw new IllegalArgumentException(
          "Unsupported g.class layout for resistance overflow patch");
    }
    int offset = indexOf(data);
    System.arraycopy(PATCHED, 0, data, offset, PATCHED.length);
    if (state(data) != State.PATCHED) {
      throw new IllegalStateException(
          "Resistance overflow patch did not produce the expected g.class bytecode");
    }
    summary.heroResistOverflow++;
    log.info("Resistance overflow class patch applied at byte offset {}", offset);
    return summary;
  }

  private static int semanticOriginalMatches(byte[] data) {
    try {
      int matches = 0;
      for (MethodModel method : ClassFile.of().parse(data).methods()) {
        if (!"b".equals(method.methodName().stringValue())
            || !"()V".equals(method.methodType().stringValue())) {
          continue;
        }
        List<Instruction> instructions = instructions(method);
        for (int i = 0; i + 10 < instructions.size(); i++) {
          if (isOriginalClamp(instructions, i)) {
            matches++;
          }
        }
      }
      log.info("Semantic resistance clamp matches found: {}", matches);
      return matches;
    } catch (RuntimeException | LinkageError _) {
      return 0;
    }
  }

  private static List<Instruction> instructions(MethodModel method) {
    List<Instruction> instructions = new ArrayList<>();
    if (method.code().isEmpty()) {
      return instructions;
    }
    for (CodeElement element : method.code().orElseThrow()) {
      if (element instanceof Instruction instruction) {
        instructions.add(instruction);
      }
    }
    return instructions;
  }

  private static boolean isOriginalClamp(List<Instruction> instructions, int i) {
    if (!hasOpcodes(instructions, i)) {
      return false;
    }
    if (!(instructions.get(i + 1) instanceof FieldInstruction firstArray)
        || !(instructions.get(i + 6) instanceof FieldInstruction secondArray)
        || !(instructions.get(i + 2) instanceof FieldInstruction firstIndex)
        || !(instructions.get(i + 7) instanceof FieldInstruction secondIndex)) {
      return false;
    }
    return firstArray.field().equals(secondArray.field())
        && firstIndex.field().equals(secondIndex.field());
  }

  private static boolean hasOpcodes(List<Instruction> instructions, int offset) {
    if (offset + OPCODES.size() > instructions.size()) {
      return false;
    }
    for (int i = 0; i < OPCODES.size(); i++) {
      if (instructions.get(offset + i).opcode() != OPCODES.get(i)) {
        return false;
      }
    }
    return true;
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
    for (int i = 0; i <= data.length - ResistanceOverflowClassPatcher.ORIGINAL.length; i++) {
      if (matches(data, ResistanceOverflowClassPatcher.ORIGINAL, i)) {
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
