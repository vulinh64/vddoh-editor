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
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/** Treats a hero positioned diagonally behind a monster as a back attack. */
@Slf4j
public final class DiagonalBackAttackClassPatcher {

  private static final int EXPECTED_SITES = 1;
  private static final String BATTLE_UNIT_CLASS = "b";
  private static final String HERO_CLASS = "g";
  private static final String HERO_ATTACK_METHOD = "a";
  private static final String HERO_ATTACK_DESCRIPTOR = "(I)V";
  private static final String DIRECTION_FIELD = "b";
  private static final String BYTE_DESCRIPTOR = "B";
  private static final String BATTLE_X_FIELD = "d";
  private static final String BATTLE_Y_FIELD = "e";
  private static final String HERO_X_FIELD = "a";
  private static final String HERO_Y_FIELD = "b";
  private static final String INT_DESCRIPTOR = "I";
  private static final int HERO_FEET_OFFSET = 16;

  public enum State {
    ORIGINAL,
    PATCHED,
    UNKNOWN
  }

  public static State state(byte[] data) {
    try {
      ClassPatchSupport.SiteCounts sites =
          ClassPatchSupport.siteCounts(
              data,
              DiagonalBackAttackClassPatcher::originalSites,
              DiagonalBackAttackClassPatcher::patchedSites);
      if (sites.isPatched(EXPECTED_SITES)) {
        return State.PATCHED;
      }
      if (sites.isOriginal(EXPECTED_SITES)) {
        return State.ORIGINAL;
      }
      log.info(
          "Diagonal back-attack patch state unknown; originalSites={}, patchedSites={}",
          sites.original(),
          sites.patched());
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
    log.info("Applying diagonal back-attack class patch; current state={}", state);
    if (state == State.PATCHED) {
      summary.incrementSkipped();
      return new Result(data, summary);
    }
    if (state != State.ORIGINAL) {
      throw new IllegalArgumentException("Unsupported b.class layout for diagonal back-attack patch");
    }

    ClassFile classFile = ClassFile.of();
    ClassModel model = classFile.parse(data);
    PatchCounter counter = new PatchCounter();
    byte[] patched =
        classFile.transformClass(
            model,
            java.lang.classfile.ClassTransform.transformingMethodBodies(
                DiagonalBackAttackClassPatcher::isHeroAttackMethod,
                java.lang.classfile.CodeTransform.ofStateful(
                    () -> new DiagonalBackAttackCodeTransform(counter))));
    State patchedState = state(patched);
    if (counter.count() != EXPECTED_SITES || patchedState != State.PATCHED) {
      throw new IllegalStateException(
          "Diagonal back-attack patch did not produce the expected b.class bytecode; counter=%d, state=%s"
              .formatted(counter.count(), patchedState));
    }
    summary.setDiagonalBackAttack(counter.count());
    log.info("Diagonal back-attack class patch applied at {} site", counter.count());
    return new Result(patched, summary);
  }

  static boolean isBehind(int direction, int battleX, int battleY, int heroX, int heroY) {
    return switch (direction) {
      case 1 -> heroX < battleX;
      case 4 -> heroX > battleX;
      case 2 -> heroY + HERO_FEET_OFFSET < battleY;
      case 8 -> heroY + HERO_FEET_OFFSET > battleY;
      default -> false;
    };
  }

  private static int originalSites(ClassModel model) {
    return countSites(model, Opcode.IF_ICMPNE);
  }

  private static int patchedSites(ClassModel model) {
    return countSites(model, Opcode.IF_ICMPEQ);
  }

  private static int countSites(ClassModel model, Opcode comparison) {
    int matches = 0;
    for (MethodModel method : model.methods()) {
      if (!isHeroAttackMethod(method)) {
        continue;
      }
      List<Instruction> instructions = ClassPatchSupport.instructions(method);
      for (int i = 0; i + 3 < instructions.size(); i++) {
        if (isLoadSlot(instructions.get(i), 2)
            && isHeroDirectionField(instructions.get(i + 1))
            && isLoadThis(instructions.get(i + 2))
            && isBattleDirectionField(instructions.get(i + 3))) {
          if (i + 4 < instructions.size() && instructions.get(i + 4).opcode() == comparison) {
            matches++;
          }
        }
      }
    }
    return matches;
  }

  private static boolean isHeroAttackMethod(MethodModel method) {
    return HERO_ATTACK_METHOD.equals(method.methodName().stringValue())
        && HERO_ATTACK_DESCRIPTOR.equals(method.methodType().stringValue());
  }

  private static boolean isLoadSlot(Instruction instruction, int slot) {
    return instruction instanceof LoadInstruction load && load.slot() == slot;
  }

  private static boolean isLoadThis(Instruction instruction) {
    return isLoadSlot(instruction, 0);
  }

  private static boolean isHeroDirectionField(Instruction instruction) {
    return isField(instruction, Opcode.GETFIELD, HERO_CLASS, "f", BYTE_DESCRIPTOR);
  }

  private static boolean isBattleDirectionField(Instruction instruction) {
    return isField(instruction, Opcode.GETFIELD, BATTLE_UNIT_CLASS, DIRECTION_FIELD, BYTE_DESCRIPTOR);
  }

  private static boolean isField(
      Instruction instruction, Opcode opcode, String owner, String name, String descriptor) {
    return instruction instanceof FieldInstruction field
        && field.opcode() == opcode
        && owner.equals(field.owner().asInternalName())
        && name.equals(field.name().stringValue())
        && descriptor.equals(field.type().stringValue());
  }

  private record PatchCounter(int[] value) {

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

  private static final class DiagonalBackAttackCodeTransform
      implements java.lang.classfile.CodeTransform {
    private final PatchCounter counter;
    private final List<Instruction> recentInstructions = new ArrayList<>();
    private Label directBack;
    private boolean bindDirectBack;

    private DiagonalBackAttackCodeTransform(PatchCounter counter) {
      this.counter = counter;
    }

    @Override
    public void accept(CodeBuilder builder, CodeElement element) {
      if (bindDirectBack) {
        builder.labelBinding(directBack);
        bindDirectBack = false;
      }
      if (element instanceof Instruction instruction) {
        if (isOriginalDirectionComparison(instruction)) {
          BranchInstruction comparison = (BranchInstruction) instruction;
          directBack = builder.newLabel();
          builder.if_icmpeq(directBack);
          emitDiagonalBackCheck(builder, comparison.target());
          bindDirectBack = true;
          counter.increment();
          return;
        }
        remember(instruction);
      }
      builder.with(element);
    }

    private boolean isOriginalDirectionComparison(Instruction instruction) {
      return instruction.opcode() == Opcode.IF_ICMPNE
          && recentInstructions.size() == 4
          && isLoadSlot(recentInstructions.get(0), 2)
          && isHeroDirectionField(recentInstructions.get(1))
          && isLoadThis(recentInstructions.get(2))
          && isBattleDirectionField(recentInstructions.get(3));
    }

    private static void emitDiagonalBackCheck(CodeBuilder builder, Label vanillaSideOrFront) {
      Label checkLeft = builder.newLabel();
      Label checkDown = builder.newLabel();
      Label checkUp = builder.newLabel();
      Label back = builder.newLabel();
      builder
          .aload(0)
          .getfield(java.lang.constant.ClassDesc.of(BATTLE_UNIT_CLASS), DIRECTION_FIELD, java.lang.constant.ConstantDescs.CD_byte)
          .iconst_1()
          .if_icmpne(checkLeft)
          .aload(2)
          .getfield(java.lang.constant.ClassDesc.of(HERO_CLASS), HERO_X_FIELD, java.lang.constant.ConstantDescs.CD_int)
          .aload(0)
          .getfield(java.lang.constant.ClassDesc.of(BATTLE_UNIT_CLASS), BATTLE_X_FIELD, java.lang.constant.ConstantDescs.CD_int)
          .if_icmplt(back)
          .goto_(vanillaSideOrFront)
          .labelBinding(checkLeft)
          .aload(0)
          .getfield(java.lang.constant.ClassDesc.of(BATTLE_UNIT_CLASS), DIRECTION_FIELD, java.lang.constant.ConstantDescs.CD_byte)
          .iconst_4()
          .if_icmpne(checkDown)
          .aload(2)
          .getfield(java.lang.constant.ClassDesc.of(HERO_CLASS), HERO_X_FIELD, java.lang.constant.ConstantDescs.CD_int)
          .aload(0)
          .getfield(java.lang.constant.ClassDesc.of(BATTLE_UNIT_CLASS), BATTLE_X_FIELD, java.lang.constant.ConstantDescs.CD_int)
          .if_icmpgt(back)
          .goto_(vanillaSideOrFront)
          .labelBinding(checkDown)
          .aload(0)
          .getfield(java.lang.constant.ClassDesc.of(BATTLE_UNIT_CLASS), DIRECTION_FIELD, java.lang.constant.ConstantDescs.CD_byte)
          .iconst_2()
          .if_icmpne(checkUp)
          .aload(2)
          .getfield(java.lang.constant.ClassDesc.of(HERO_CLASS), HERO_Y_FIELD, java.lang.constant.ConstantDescs.CD_int)
          .bipush(HERO_FEET_OFFSET)
          .iadd()
          .aload(0)
          .getfield(java.lang.constant.ClassDesc.of(BATTLE_UNIT_CLASS), BATTLE_Y_FIELD, java.lang.constant.ConstantDescs.CD_int)
          .if_icmplt(back)
          .goto_(vanillaSideOrFront)
          .labelBinding(checkUp)
          .aload(0)
          .getfield(java.lang.constant.ClassDesc.of(BATTLE_UNIT_CLASS), DIRECTION_FIELD, java.lang.constant.ConstantDescs.CD_byte)
          .bipush(8)
          .if_icmpne(vanillaSideOrFront)
          .aload(2)
          .getfield(java.lang.constant.ClassDesc.of(HERO_CLASS), HERO_Y_FIELD, java.lang.constant.ConstantDescs.CD_int)
          .bipush(HERO_FEET_OFFSET)
          .iadd()
          .aload(0)
          .getfield(java.lang.constant.ClassDesc.of(BATTLE_UNIT_CLASS), BATTLE_Y_FIELD, java.lang.constant.ConstantDescs.CD_int)
          .if_icmpgt(back)
          .goto_(vanillaSideOrFront)
          .labelBinding(back)
          .iconst_1()
          .istore(3)
          .iconst_1()
          .istore(4)
          .goto_(vanillaSideOrFront);
    }

    private void remember(Instruction instruction) {
      recentInstructions.add(instruction);
      if (recentInstructions.size() > 4) {
        recentInstructions.removeFirst();
      }
    }
  }
}
