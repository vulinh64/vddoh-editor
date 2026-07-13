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
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class EquipmentBonusClassPatcher {

  private static final int EXPECTED_SITES = 4;
  private static final String HERO_RECALC_METHOD = "b";
  private static final String HERO_RECALC_DESCRIPTOR = "()V";
  private static final String ITEM_CLASS_NAME = "k";
  private static final String ITEM_STAT_METHOD = "a";
  private static final String ITEM_STAT_DESCRIPTOR = "(I)I";
  private static final String ITEM_BYTE_D_FIELD = "d";
  private static final String ITEM_BYTE_D_DESCRIPTOR = "B";
  private static final int BYTE_D_SLOT = 8;
  private static final int NON_WEAPON_BONUS_LOCAL = 5;
  private static final int WEAPON_BONUS_LOCAL = 6;

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
          "Equipment bonus class patch state unknown; originalSites={}, patchedSites={}",
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
    log.info("Applying equipment bonus class patch; current state={}", state);
    if (state == State.PATCHED) {
      summary.incrementSkipped();
      log.info("Equipment bonus class patch skipped because class is already patched");
      return new Result(data, summary);
    }
    if (state != State.ORIGINAL) {
      throw new IllegalArgumentException(
          "Unsupported g.class layout for equipment bonus aggregation patch");
    }

    ClassFile classFile = ClassFile.of();
    ClassModel model = classFile.parse(data);
    PatchCounter counter = new PatchCounter();
    byte[] patched =
        classFile.transformClass(
            model,
            java.lang.classfile.ClassTransform.transformingMethodBodies(
                EquipmentBonusClassPatcher::isHeroRecalcMethod,
                java.lang.classfile.CodeTransform.ofStateful(
                    () -> new EquipmentBonusCodeTransform(counter))));

    if (counter.count() != EXPECTED_SITES || state(patched) != State.PATCHED) {
      throw new IllegalStateException(
          "Equipment bonus patch did not produce the expected g.class bytecode");
    }
    summary.setEquipmentBonusAggregation(counter.count());
    log.info("Equipment bonus class patch applied at {} sites", counter.count());
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
      if (!isHeroRecalcMethod(method)) {
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

  private static boolean isHeroRecalcMethod(MethodModel method) {
    return HERO_RECALC_METHOD.equals(method.methodName().stringValue())
        && HERO_RECALC_DESCRIPTOR.equals(method.methodType().stringValue());
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
    return offset + 1 < instructions.size()
        && isByteDValue(instructions, offset)
        && isTargetStore(instructions.get(offset + 1));
  }

  private static boolean isPatchedSite(List<Instruction> instructions, int offset) {
    if (offset + 3 >= instructions.size() || !isByteDValue(instructions, offset)) {
      return false;
    }
    Instruction store = instructions.get(offset + 3);
    return instructions.get(offset + 1) instanceof java.lang.classfile.instruction.LoadInstruction
        && instructions.get(offset + 2).opcode() == Opcode.IADD
        && isTargetStore(store)
        && localSlot(instructions.get(offset + 1)) == localSlot(store);
  }

  private static boolean isByteDValue(List<Instruction> instructions, int offset) {
    return isItemStatByteDValue(instructions, offset) || isDirectByteDValue(instructions, offset);
  }

  private static boolean isItemStatByteDValue(List<Instruction> instructions, int offset) {
    if (offset < 1 || !(instructions.get(offset) instanceof InvokeInstruction instruction)) {
      return false;
    }
    return isPushByteDSlot(instructions.get(offset - 1))
        && instruction.opcode() == Opcode.INVOKEVIRTUAL
        && ITEM_CLASS_NAME.equals(instruction.owner().asInternalName())
        && ITEM_STAT_METHOD.equals(instruction.name().stringValue())
        && ITEM_STAT_DESCRIPTOR.equals(instruction.type().stringValue());
  }

  private static boolean isDirectByteDValue(List<Instruction> instructions, int offset) {
    if (offset < 2
        || !(instructions.get(offset - 2) instanceof FieldInstruction fieldInstruction)) {
      return false;
    }
    return fieldInstruction.opcode() == Opcode.GETFIELD
        && ITEM_CLASS_NAME.equals(fieldInstruction.owner().asInternalName())
        && ITEM_BYTE_D_FIELD.equals(fieldInstruction.name().stringValue())
        && ITEM_BYTE_D_DESCRIPTOR.equals(fieldInstruction.type().stringValue())
        && isPush255(instructions.get(offset - 1))
        && instructions.get(offset).opcode() == Opcode.IAND;
  }

  private static boolean isPushByteDSlot(Instruction instruction) {
    return instruction instanceof ConstantInstruction constantInstruction
        && constantInstruction.constantValue() instanceof Integer value
        && value == BYTE_D_SLOT;
  }

  private static boolean isPush255(Instruction instruction) {
    return instruction instanceof ConstantInstruction constantInstruction
        && constantInstruction.constantValue() instanceof Integer value
        && value == 255;
  }

  private static boolean isTargetStore(Instruction instruction) {
    return instruction instanceof StoreInstruction
        && (localSlot(instruction) == NON_WEAPON_BONUS_LOCAL
            || localSlot(instruction) == WEAPON_BONUS_LOCAL);
  }

  private static int localSlot(Instruction instruction) {
    if (instruction instanceof StoreInstruction storeInstruction) {
      return storeInstruction.slot();
    }
    if (instruction instanceof java.lang.classfile.instruction.LoadInstruction loadInstruction) {
      return loadInstruction.slot();
    }
    return -1;
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

  private static final class EquipmentBonusCodeTransform
      implements java.lang.classfile.CodeTransform {
    private final PatchCounter counter;
    private final List<Instruction> recentInstructions = new ArrayList<>();
    private boolean byteDValuePending;

    private EquipmentBonusCodeTransform(PatchCounter counter) {
      this.counter = counter;
    }

    @Override
    public void accept(CodeBuilder builder, CodeElement element) {
      if (element instanceof Instruction instruction
          && byteDValuePending
          && isTargetStore(instruction)) {
        int slot = localSlot(instruction);
        builder.iload(slot);
        builder.iadd();
        builder.with(element);
        counter.increment();
        byteDValuePending = false;
        remember(instruction);
        return;
      }

      builder.with(element);
      if (element instanceof Instruction instruction) {
        remember(instruction);
        byteDValuePending = endsWithByteDValue();
      } else {
        byteDValuePending = false;
      }
    }

    private void remember(Instruction instruction) {
      recentInstructions.add(instruction);
      if (recentInstructions.size() > 3) {
        recentInstructions.removeFirst();
      }
    }

    private boolean endsWithByteDValue() {
      int offset = recentInstructions.size() - 1;
      return offset >= 0 && isByteDValue(recentInstructions, offset);
    }
  }
}
