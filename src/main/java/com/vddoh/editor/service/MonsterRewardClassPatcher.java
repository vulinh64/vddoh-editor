package com.vddoh.editor.service;

import com.vddoh.editor.data.PatchSummary;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class MonsterRewardClassPatcher {

  private static final int EXPECTED_SITES = 2;
  private static final String PARSER_METHOD = "f";
  private static final String PARSER_DESCRIPTOR = "(I)I";
  private static final String ENGINE_CLASS_NAME = "j";
  private static final String GAME_DATA_FIELD = "f";
  private static final String GAME_DATA_FIELD_DESCRIPTOR = "[B";
  private static final String MONSTER_CLASS_NAME = "b";
  private static final String MONSTER_EXP_FIELD = "b";
  private static final String MONSTER_FILAR_FIELD = "c";
  private static final String MONSTER_REWARD_DESCRIPTOR = "S";

  public enum State {
    ORIGINAL,
    PATCHED,
    UNKNOWN
  }

  public static State state(byte[] data) {
    try {
      ClassModel model = ClassFile.of().parse(data);
      int original = originalSites(model);
      int patched = patchedSites(model);
      if (patched == EXPECTED_SITES && original == 0) {
        return State.PATCHED;
      }
      if (original == EXPECTED_SITES && patched == 0) {
        return State.ORIGINAL;
      }
      log.info(
          "Monster reward parser class patch state unknown; originalSites={}, patchedSites={}",
          original,
          patched);
      return State.UNKNOWN;
    } catch (RuntimeException | LinkageError _) {
      return State.UNKNOWN;
    }
  }

  record Result(byte[] data, PatchSummary summary) {

    @Override
    public String toString() {
      return "Result{data=%s, summary=%s}".formatted(Arrays.toString(data), summary);
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Result result = (Result) o;
      return Objects.deepEquals(data, result.data) && Objects.equals(summary, result.summary);
    }

    @Override
    public int hashCode() {
      return Objects.hash(Arrays.hashCode(data), summary);
    }
  }

  static Result patch(byte[] data) {
    PatchSummary summary = new PatchSummary();
    State state = state(data);
    log.info("Applying monster reward parser class patch; current state={}", state);
    if (state == State.PATCHED) {
      summary.incrementSkipped();
      log.info("Monster reward parser class patch skipped because class is already patched");
      return new Result(data, summary);
    }
    if (state != State.ORIGINAL) {
      throw new IllegalArgumentException(
          "Unsupported j.class layout for monster reward parser patch");
    }

    ClassFile classFile = ClassFile.of();
    ClassModel model = classFile.parse(data);
    PatchCounter counter = new PatchCounter();
    byte[] patched =
        classFile.transformClass(
            model,
            java.lang.classfile.ClassTransform.transformingMethodBodies(
                MonsterRewardClassPatcher::isParserMethod,
                java.lang.classfile.CodeTransform.ofStateful(
                    () -> new MonsterRewardCodeTransform(counter))));

    if (counter.count() != EXPECTED_SITES || state(patched) != State.PATCHED) {
      throw new IllegalStateException(
          "Monster reward parser patch did not produce the expected j.class bytecode");
    }
    summary.setMonsterRewardParser(counter.count());
    log.info("Monster reward parser class patch applied at {} sites", counter.count());
    return new Result(patched, summary);
  }

  private static int originalSites(ClassModel model) {
    return countSites(model, false);
  }

  private static int patchedSites(ClassModel model) {
    return countSites(model, true);
  }

  private static int countSites(ClassModel model, boolean patched) {
    int matches = 0;
    for (MethodModel method : model.methods()) {
      if (!isParserMethod(method)) {
        continue;
      }
      List<Instruction> instructions = instructions(method);
      for (int i = 0; i < instructions.size(); i++) {
        if (patched ? isPatchedSite(instructions, i) : isOriginalSite(instructions, i)) {
          matches++;
        }
      }
    }
    return matches;
  }

  private static boolean isParserMethod(MethodModel method) {
    return PARSER_METHOD.equals(method.methodName().stringValue())
        && PARSER_DESCRIPTOR.equals(method.methodType().stringValue());
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

  private static boolean isOriginalSite(List<Instruction> instructions, int offset) {
    return isOriginalExpHighByteSite(instructions, offset)
        || isOriginalFilarLowByteSite(instructions, offset);
  }

  private static boolean isPatchedSite(List<Instruction> instructions, int offset) {
    return isPatchedExpHighByteSite(instructions, offset)
        || isPatchedFilarLowByteSite(instructions, offset);
  }

  private static boolean isOriginalExpHighByteSite(List<Instruction> instructions, int offset) {
    return offset + 4 < instructions.size()
        && isGameDataRead(instructions, offset)
        && instructions.get(offset + 1).opcode() == Opcode.ICONST_4
        && instructions.get(offset + 2).opcode() == Opcode.ISHL
        && instructions.get(offset + 3).opcode() == Opcode.I2S
        && isMonsterRewardField(instructions.get(offset + 4), Opcode.PUTFIELD, MONSTER_EXP_FIELD);
  }

  private static boolean isPatchedExpHighByteSite(List<Instruction> instructions, int offset) {
    return offset + 6 < instructions.size()
        && isGameDataRead(instructions, offset)
        && isPush255(instructions.get(offset + 1))
        && instructions.get(offset + 2).opcode() == Opcode.IAND
        && instructions.get(offset + 3).opcode() == Opcode.ICONST_4
        && instructions.get(offset + 4).opcode() == Opcode.ISHL
        && instructions.get(offset + 5).opcode() == Opcode.I2S
        && isMonsterRewardField(instructions.get(offset + 6), Opcode.PUTFIELD, MONSTER_EXP_FIELD);
  }

  private static boolean isOriginalFilarLowByteSite(List<Instruction> instructions, int offset) {
    return offset + 4 < instructions.size()
        && isGameDataRead(instructions, offset)
        && hasPendingFilarHighValue(instructions, offset)
        && instructions.get(offset + 1).opcode() == Opcode.I2S
        && instructions.get(offset + 2).opcode() == Opcode.IOR
        && instructions.get(offset + 3).opcode() == Opcode.I2S
        && isMonsterRewardField(instructions.get(offset + 4), Opcode.PUTFIELD, MONSTER_FILAR_FIELD);
  }

  private static boolean isPatchedFilarLowByteSite(List<Instruction> instructions, int offset) {
    return offset + 6 < instructions.size()
        && isGameDataRead(instructions, offset)
        && hasPendingFilarHighValue(instructions, offset)
        && isPush255(instructions.get(offset + 1))
        && instructions.get(offset + 2).opcode() == Opcode.IAND
        && instructions.get(offset + 3).opcode() == Opcode.I2S
        && instructions.get(offset + 4).opcode() == Opcode.IOR
        && instructions.get(offset + 5).opcode() == Opcode.I2S
        && isMonsterRewardField(instructions.get(offset + 6), Opcode.PUTFIELD, MONSTER_FILAR_FIELD);
  }

  private static boolean isGameDataRead(List<Instruction> instructions, int offset) {
    return offset >= 3
        && isGameDataField(instructions.get(offset - 3))
        && isLoadSlot(instructions.get(offset - 2), 0)
        && isIncrementSlot(instructions.get(offset - 1), 0)
        && instructions.get(offset).opcode() == Opcode.BALOAD;
  }

  private static boolean hasPendingFilarHighValue(List<Instruction> instructions, int offset) {
    return offset >= 4
        && isMonsterRewardField(instructions.get(offset - 4), Opcode.GETFIELD, MONSTER_FILAR_FIELD);
  }

  private static boolean isGameDataField(Instruction instruction) {
    return instruction instanceof FieldInstruction field
        && field.opcode() == Opcode.GETSTATIC
        && ENGINE_CLASS_NAME.equals(field.owner().asInternalName())
        && GAME_DATA_FIELD.equals(field.name().stringValue())
        && GAME_DATA_FIELD_DESCRIPTOR.equals(field.type().stringValue());
  }

  private static boolean isMonsterRewardField(
      Instruction instruction, Opcode opcode, String fieldName) {
    return instruction instanceof FieldInstruction field
        && field.opcode() == opcode
        && MONSTER_CLASS_NAME.equals(field.owner().asInternalName())
        && fieldName.equals(field.name().stringValue())
        && MONSTER_REWARD_DESCRIPTOR.equals(field.type().stringValue());
  }

  private static boolean isLoadSlot(Instruction instruction, int slot) {
    return instruction instanceof LoadInstruction load && load.slot() == slot;
  }

  private static boolean isIncrementSlot(Instruction instruction, int slot) {
    return instruction instanceof IncrementInstruction increment && increment.slot() == slot;
  }

  private static boolean isPush255(Instruction instruction) {
    return instruction instanceof ConstantInstruction constant
        && constant.constantValue() instanceof Integer value
        && value == 255;
  }

  private record PatchCounter(int[] value) {

    @Override
    public String toString() {
      return "PatchCounter{value=%s}".formatted(Arrays.toString(value));
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      PatchCounter that = (PatchCounter) o;
      return Objects.deepEquals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(value);
    }

    PatchCounter() {
      this(new int[1]);
    }

    void increment() {
      value[0]++;
    }

    int count() {
      return value[0];
    }
  }

  private static final class MonsterRewardCodeTransform
      implements java.lang.classfile.CodeTransform {
    private final PatchCounter counter;
    private final List<Instruction> recentInstructions = new ArrayList<>();
    private boolean expHighBytePending;
    private boolean filarLowBytePending;

    private MonsterRewardCodeTransform(PatchCounter counter) {
      this.counter = counter;
    }

    @Override
    public void accept(CodeBuilder builder, CodeElement element) {
      if (element instanceof Instruction instruction) {
        if (expHighBytePending && instruction.opcode() == Opcode.ICONST_4) {
          builder.sipush(255);
          builder.iand();
          counter.increment();
        } else if (filarLowBytePending && instruction.opcode() == Opcode.I2S) {
          builder.sipush(255);
          builder.iand();
          counter.increment();
        } else if (expHighBytePending || filarLowBytePending) {
          expHighBytePending = false;
          filarLowBytePending = false;
        }
      }

      builder.with(element);
      if (element instanceof Instruction instruction) {
        remember(instruction);
        expHighBytePending = isGameDataRead(recentInstructions, recentInstructions.size() - 1);
        filarLowBytePending =
            expHighBytePending
                && hasPendingFilarHighValue(recentInstructions, recentInstructions.size() - 1);
      } else {
        expHighBytePending = false;
        filarLowBytePending = false;
      }
    }

    private void remember(Instruction instruction) {
      recentInstructions.add(instruction);
      if (recentInstructions.size() > 6) {
        recentInstructions.removeFirst();
      }
    }
  }
}
