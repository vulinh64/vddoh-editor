package com.vddoh.editor.service;

import com.vddoh.editor.data.PatchSummary;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PhysicalDamageCapClassPatcher {

  private static final int EXPECTED_SITES = 1;
  private static final int DAMAGE_MASK = 1023;
  private static final int RAW_DAMAGE_MASK = 65535;
  private static final int DAMAGE_CAP = 999;
  private static final int FLAG_MASK = -65536;
  private static final String HERO_CLASS_NAME = "g";
  private static final String MONSTER_ACTION_CLASS_NAME = "f";
  private static final String BATTLE_UNIT_CLASS_NAME = "b";
  private static final String HERO_ACTION_METHOD = "a";
  private static final String HERO_ACTION_DESCRIPTOR = "(Lf;ZLb;ZZ)V";
  private static final String HERO_RESULT_FIELD = "v";
  private static final String HERO_RESULT_DESCRIPTOR = "I";
  private static final String BATTLE_UNIT_APPLY_DAMAGE_METHOD = "b";
  private static final String BATTLE_UNIT_APPLY_DAMAGE_DESCRIPTOR = "(II)V";

  public enum State {
    ORIGINAL,
    PATCHED,
    UNKNOWN
  }

  public static State state(byte[] data) {
    try {
      ClassModel model = ClassPatchSupport.classModel(data);
      int original = originalSites(model);
      int patched = patchedSites(model);
      int shortCasts = physicalResultShortCastSites(model);
      if (patched == EXPECTED_SITES && original == 0 && shortCasts == 0) {
        return State.PATCHED;
      }
      if ((original == EXPECTED_SITES && patched == 0)
          || (patched == EXPECTED_SITES && shortCasts > 0)) {
        return State.ORIGINAL;
      }
      log.info(
          "Physical damage cap class patch state unknown; originalSites={}, patchedSites={}, shortCasts={}",
          original,
          patched,
          shortCasts);
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
    log.info("Applying physical damage cap class patch; current state={}", state);
    if (state == State.PATCHED) {
      summary.incrementSkipped();
      log.info("Physical damage cap class patch skipped because class is already patched");
      return new Result(data, summary);
    }
    if (state != State.ORIGINAL) {
      throw new IllegalArgumentException(
          "Unsupported g.class layout for physical damage cap patch");
    }

    ClassFile classFile = ClassFile.of();
    ClassModel model = classFile.parse(data);
    PatchCounter counter = new PatchCounter();
    PatchCounter castCounter = new PatchCounter();
    byte[] patched =
        classFile.transformClass(
            model,
            java.lang.classfile.ClassTransform.transformingMethodBodies(
                PhysicalDamageCapClassPatcher::isHeroActionMethod,
                java.lang.classfile.CodeTransform.ofStateful(
                    () -> new PhysicalDamageCapCodeTransform(counter, castCounter))));

    State patchedState = state(patched);
    if (counter.count() != EXPECTED_SITES || patchedState != State.PATCHED) {
      throw new IllegalStateException(
          "Physical damage cap patch did not produce the expected g.class bytecode; counter=%d, casts=%d, state=%s"
              .formatted(counter.count(), castCounter.count(), patchedState));
    }
    summary.setPhysicalDamageCap(counter.count());
    log.info("Physical damage cap class patch applied at {} site", counter.count());
    return new Result(patched, summary);
  }

  static int capPhysicalDamageResult(int result) {
    int cappedResult = result;
    if ((cappedResult & RAW_DAMAGE_MASK) > DAMAGE_MASK) {
      cappedResult = (cappedResult & FLAG_MASK) | DAMAGE_MASK;
    }
    if ((cappedResult & RAW_DAMAGE_MASK) > DAMAGE_CAP) {
      cappedResult = (cappedResult & FLAG_MASK) | DAMAGE_CAP;
    }
    return cappedResult;
  }

  private static int originalSites(ClassModel model) {
    return countSites(model, false);
  }

  private static int patchedSites(ClassModel model) {
    return countSites(model, true);
  }

  private static int physicalResultShortCastSites(ClassModel model) {
    int matches = 0;
    for (MethodModel method : model.methods()) {
      if (!isHeroActionMethod(method)) {
        continue;
      }
      List<Instruction> instructions = ClassPatchSupport.instructions(method);
      for (int i = 0; i + 1 < instructions.size(); i++) {
        if (instructions.get(i).opcode() == Opcode.I2S
            && isHeroResultField(instructions.get(i + 1), Opcode.PUTFIELD)) {
          matches++;
        }
      }
    }
    return matches;
  }

  private static int countSites(ClassModel model, boolean patched) {
    int matches = 0;
    for (MethodModel method : model.methods()) {
      if (!isHeroActionMethod(method)) {
        continue;
      }
      List<Instruction> instructions = ClassPatchSupport.instructions(method);
      for (int i = 0; i < instructions.size(); i++) {
        if (patched ? isPatchedSite(instructions, i) : isOriginalSite(instructions, i)) {
          matches++;
        }
      }
    }
    return matches;
  }

  private static boolean isHeroActionMethod(MethodModel method) {
    return HERO_ACTION_METHOD.equals(method.methodName().stringValue())
        && HERO_ACTION_DESCRIPTOR.equals(method.methodType().stringValue());
  }


  private static boolean isOriginalSite(List<Instruction> instructions, int offset) {
    return isApplyPhysicalDamageCall(instructions, offset)
        && !hasCapImmediatelyBefore(instructions, offset);
  }

  private static boolean isPatchedSite(List<Instruction> instructions, int offset) {
    return isApplyPhysicalDamageCall(instructions, offset)
        && hasCapImmediatelyBefore(instructions, offset);
  }

  private static boolean isApplyPhysicalDamageCall(List<Instruction> instructions, int offset) {
    return offset + 20 < instructions.size()
        && isLoadSlot(instructions.get(offset), 3)
        && isLoadThis(instructions.get(offset + 1))
        && isHeroResultField(instructions.get(offset + 2), Opcode.GETFIELD)
        && isPush(instructions.get(offset + 3), DAMAGE_MASK)
        && instructions.get(offset + 4).opcode() == Opcode.IAND
        && isLoadSlot(instructions.get(offset + 5), 1)
        && isMonsterActionDeathValueCall(instructions.get(offset + 6))
        && isPush(instructions.get(offset + 7), 127)
        && instructions.get(offset + 8).opcode() == Opcode.IAND
        && instructions.get(offset + 9).opcode() == Opcode.IMUL
        && isPush(instructions.get(offset + 10), 100)
        && instructions.get(offset + 11).opcode() == Opcode.IDIV
        && isLoadSlot(instructions.get(offset + 12), 1)
        && isMonsterActionDeathValueCall(instructions.get(offset + 13))
        && isPush(instructions.get(offset + 14), 128)
        && instructions.get(offset + 15).opcode() == Opcode.IAND
        && instructions.get(offset + 16).opcode() == Opcode.IFEQ
        && isPush(instructions.get(offset + 17), 100)
        && instructions.get(offset + 19).opcode() == Opcode.ICONST_0
        && instructions.get(offset + 18).opcode() == Opcode.GOTO
        && instructions.get(offset + 19).opcode() == Opcode.ICONST_0
        && instructions.get(offset + 20) instanceof InvokeInstruction invoke
        && invoke.opcode() == Opcode.INVOKEVIRTUAL
        && BATTLE_UNIT_CLASS_NAME.equals(invoke.owner().asInternalName())
        && BATTLE_UNIT_APPLY_DAMAGE_METHOD.equals(invoke.name().stringValue())
        && BATTLE_UNIT_APPLY_DAMAGE_DESCRIPTOR.equals(invoke.type().stringValue());
  }

  private static boolean hasCapImmediatelyBefore(List<Instruction> instructions, int offset) {
    if (offset < 10
        || !isLoadThis(instructions.get(offset - 8))
        || !isLoadThis(instructions.get(offset - 7))
        || !isHeroResultField(instructions.get(offset - 6), Opcode.GETFIELD)
        || !isPush(instructions.get(offset - 5), FLAG_MASK)
        || instructions.get(offset - 4).opcode() != Opcode.IAND
        || !isPush(instructions.get(offset - 3), DAMAGE_CAP)
        || instructions.get(offset - 2).opcode() != Opcode.IOR
        || !isHeroResultField(instructions.get(offset - 1), Opcode.PUTFIELD)) {
      return false;
    }

    boolean rawDamageRead = false;
    boolean packedMaximumClamp = false;
    for (int index = Math.max(0, offset - 42); index < offset - 8; index++) {
      rawDamageRead |= isHeroResultRawDamageRead(instructions, index);
      packedMaximumClamp |= isPush(instructions.get(index), DAMAGE_MASK);
    }
    return rawDamageRead && packedMaximumClamp;
  }

  private static boolean isHeroResultRawDamageRead(List<Instruction> instructions, int offset) {
    return offset + 3 < instructions.size()
        && isLoadThis(instructions.get(offset))
        && isHeroResultField(instructions.get(offset + 1), Opcode.GETFIELD)
        && isPush(instructions.get(offset + 2), RAW_DAMAGE_MASK)
        && instructions.get(offset + 3).opcode() == Opcode.IAND;
  }

  private static boolean isLoadThis(Instruction instruction) {
    return isLoadSlot(instruction, 0);
  }

  private static boolean isLoadSlot(Instruction instruction, int slot) {
    return instruction instanceof LoadInstruction load && load.slot() == slot;
  }

  private static boolean isHeroResultField(Instruction instruction, Opcode opcode) {
    return instruction instanceof FieldInstruction field
        && field.opcode() == opcode
        && HERO_CLASS_NAME.equals(field.owner().asInternalName())
        && HERO_RESULT_FIELD.equals(field.name().stringValue())
        && HERO_RESULT_DESCRIPTOR.equals(field.type().stringValue());
  }

  private static boolean isPush(Instruction instruction, int value) {
    return instruction instanceof ConstantInstruction constant
        && constant.constantValue() instanceof Integer integer
        && integer == value;
  }

  private static boolean isMonsterActionDeathValueCall(Instruction instruction) {
    return instruction instanceof InvokeInstruction invoke
        && invoke.opcode() == Opcode.INVOKEVIRTUAL
        && MONSTER_ACTION_CLASS_NAME.equals(invoke.owner().asInternalName())
        && "b".equals(invoke.name().stringValue())
        && "()B".equals(invoke.type().stringValue());
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

  private static final class PhysicalDamageCapCodeTransform
      implements java.lang.classfile.CodeTransform {
    private final PatchCounter counter;
    private final PatchCounter castCounter;
    private final List<Instruction> recentInstructions = new ArrayList<>();

    private PhysicalDamageCapCodeTransform(PatchCounter counter, PatchCounter castCounter) {
      this.counter = counter;
      this.castCounter = castCounter;
    }

    @Override
    public void accept(CodeBuilder builder, CodeElement element) {
      if (element instanceof Instruction instruction) {
        if (isPhysicalResultShortCast(instruction)) {
          builder.nop();
          castCounter.increment();
          remember(instruction);
          return;
        }
        if (isDamageCallStart(instruction)) {
          emitCap(builder);
          counter.increment();
        }
        remember(instruction);
      }
      builder.with(element);
    }

    private boolean isDamageCallStart(Instruction instruction) {
      return isLoadSlot(instruction, 3)
          && (isOriginalDamageCallStart() || isLegacyCapDamageCallStart());
    }

    private boolean isOriginalDamageCallStart() {
      return recentInstructions.size() == 3
          && isLoadSlot(recentInstructions.get(0), 1)
          && isMonsterActionDeathValueCall(recentInstructions.get(1))
          && recentInstructions.get(2).opcode() == Opcode.IFEQ;
    }

    private boolean isLegacyCapDamageCallStart() {
      return recentInstructions.size() == 3
          && isPush(recentInstructions.get(0), DAMAGE_CAP)
          && recentInstructions.get(1).opcode() == Opcode.IOR
          && isHeroResultField(recentInstructions.get(2), Opcode.PUTFIELD);
    }

    private boolean isPhysicalResultShortCast(Instruction instruction) {
      return instruction.opcode() == Opcode.I2S
          && (isCriticalDamageResultShortCast() || isBaseDamageResultShortCast());
    }

    private boolean isCriticalDamageResultShortCast() {
      return recentInstructions.size() == 3
          && recentInstructions.get(0).opcode() == Opcode.IMUL
          && isPush(recentInstructions.get(1), 100)
          && recentInstructions.get(2).opcode() == Opcode.IDIV;
    }

    private boolean isBaseDamageResultShortCast() {
      return recentInstructions.size() == 3
          && recentInstructions.get(0).opcode() == Opcode.GOTO
          && isLoadThis(recentInstructions.get(1))
          && isHeroResultField(recentInstructions.get(2), Opcode.GETFIELD);
    }

    private static void emitCap(CodeBuilder builder) {
      Label withinDisplayRange = builder.newLabel();
      Label atOrBelowPackedMaximum = builder.newLabel();
      builder
          .aload(0)
          .getfield(
              java.lang.constant.ClassDesc.of(HERO_CLASS_NAME),
              HERO_RESULT_FIELD,
              java.lang.constant.ConstantDescs.CD_int)
          .ldc(RAW_DAMAGE_MASK)
          .iand()
          .sipush(DAMAGE_CAP)
          .if_icmple(withinDisplayRange)
          .aload(0)
          .getfield(
              java.lang.constant.ClassDesc.of(HERO_CLASS_NAME),
              HERO_RESULT_FIELD,
              java.lang.constant.ConstantDescs.CD_int)
          .ldc(RAW_DAMAGE_MASK)
          .iand()
          .sipush(DAMAGE_MASK)
          .if_icmple(atOrBelowPackedMaximum)
          .aload(0)
          .aload(0)
          .getfield(
              java.lang.constant.ClassDesc.of(HERO_CLASS_NAME),
              HERO_RESULT_FIELD,
              java.lang.constant.ConstantDescs.CD_int)
          .ldc(FLAG_MASK)
          .iand()
          .sipush(DAMAGE_MASK)
          .ior()
          .putfield(
              java.lang.constant.ClassDesc.of(HERO_CLASS_NAME),
              HERO_RESULT_FIELD,
              java.lang.constant.ConstantDescs.CD_int)
          .labelBinding(atOrBelowPackedMaximum)
          .aload(0)
          .aload(0)
          .getfield(
              java.lang.constant.ClassDesc.of(HERO_CLASS_NAME),
              HERO_RESULT_FIELD,
              java.lang.constant.ConstantDescs.CD_int)
          .ldc(FLAG_MASK)
          .iand()
          .sipush(DAMAGE_CAP)
          .ior()
          .putfield(
              java.lang.constant.ClassDesc.of(HERO_CLASS_NAME),
              HERO_RESULT_FIELD,
              java.lang.constant.ConstantDescs.CD_int)
          .labelBinding(withinDisplayRange);
    }

    private void remember(Instruction instruction) {
      recentInstructions.add(instruction);
      if (recentInstructions.size() > 3) {
        recentInstructions.removeFirst();
      }
    }
  }
}
