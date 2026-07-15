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
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class HighValueGraphicDisplayClassPatcher {

  private static final int EXPECTED_HELPER_SITES = 1;
  private static final int EXPECTED_INLINE_DIGIT_SITES = 6;
  private static final int PACKED_VALUE_SLOT = 4;
  private static final int DISPLAY_ENABLED_SLOT = 5;
  private static final int PARTY_INDEX_SLOT = 2;
  private static final int UNSIGNED_SHORT_MASK = 65535;
  private static final int DISPLAY_CAP = 999;
  private static final int HIGH_WORD_MASK = -65536;
  private static final int DISPLAY_CAP_HIGH_WORD = DISPLAY_CAP << 16;
  private static final String GRAPHIC_BAR_METHOD = "a";
  private static final String GRAPHIC_BAR_DESCRIPTOR =
      "(Ljavax/microedition/lcdui/Graphics;IIZIZZZII)V";
  private static final String GRAPHICS_CLASS_NAME = "javax/microedition/lcdui/Graphics";
  private static final String SET_CLIP_METHOD = "setClip";
  private static final String SET_CLIP_DESCRIPTOR = "(IIII)V";
  private static final String HERO_CLASS_NAME = "g";
  private static final String HERO_PARTY_FIELD = "a";
  private static final String HERO_ARRAY_DESCRIPTOR = "[Lg;";
  private static final String HERO_HEALTH_FIELD = "l";
  private static final String HERO_RESOURCE_FIELD = "m";
  private static final String HERO_PACKED_VALUE_DESCRIPTOR = "I";
  private static final String JAVA_LANG_MATH_CLASS_NAME = "java/lang/Math";
  private static final String MATH_MIN_METHOD = "min";
  private static final String MATH_MIN_DESCRIPTOR = "(II)I";
  private static final ClassDesc MATH_CLASS = ClassDesc.of("java.lang.Math");
  private static final MethodTypeDesc MATH_MIN_TYPE =
      MethodTypeDesc.ofDescriptor(MATH_MIN_DESCRIPTOR);

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
      int expectedSites = expectedSites();
      if (patched == expectedSites && original == 0) {
        return State.PATCHED;
      }
      if (original == expectedSites && patched == 0) {
        return State.ORIGINAL;
      }
      log.info(
          "High-value graphic display class patch state unknown; originalSites={}, patchedSites={}",
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
    log.info("Applying high-value graphic display class patch; current state={}", state);
    if (state == State.PATCHED) {
      summary.incrementSkipped();
      log.info("High-value graphic display class patch skipped because class is already patched");
      return new Result(data, summary);
    }
    if (state != State.ORIGINAL) {
      throw new IllegalArgumentException(
          "Unsupported j.class layout for high-value graphic display patch");
    }

    ClassFile classFile = ClassFile.of();
    ClassModel model = classFile.parse(data);
    PatchCounter counter = new PatchCounter();
    byte[] patched =
        classFile.transformClass(
            model,
            java.lang.classfile.ClassTransform.transformingMethodBodies(
                _ -> true,
                java.lang.classfile.CodeTransform.ofStateful(
                    () -> new HighValueGraphicDisplayCodeTransform(counter))));

    State patchedState = state(patched);
    if (counter.count() != expectedSites() || patchedState != State.PATCHED) {
      ClassModel patchedModel = classFile.parse(patched);
      throw new IllegalStateException(
          "High-value graphic display patch did not produce the expected j.class bytecode; counter=%d, state=%s, originalSites=%d, patchedSites=%d"
              .formatted(
                  counter.count(),
                  patchedState,
                  originalSites(patchedModel),
                  patchedSites(patchedModel)));
    }
    summary.setHighValueGraphicDisplay(counter.count());
    log.info("High-value graphic display class patch applied at {} site", counter.count());
    return new Result(patched, summary);
  }

  private static int expectedSites() {
    return EXPECTED_HELPER_SITES + EXPECTED_INLINE_DIGIT_SITES;
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
      if (!isGraphicBarMethod(method)) {
        List<Instruction> instructions = instructions(method);
        matches += countInlineDigitSites(instructions, patched);
      } else {
        List<Instruction> instructions = instructions(method);
        if (patched
            ? isPatchedGraphicDisplay(instructions)
            : isOriginalGraphicDisplay(instructions)) {
          matches++;
        }
      }
    }
    return matches;
  }

  private static boolean isGraphicBarMethod(MethodModel method) {
    return GRAPHIC_BAR_METHOD.equals(method.methodName().stringValue())
        && GRAPHIC_BAR_DESCRIPTOR.equals(method.methodType().stringValue());
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

  private static boolean isOriginalGraphicDisplay(List<Instruction> instructions) {
    return !isPatchedGraphicDisplay(instructions)
        && countCurrentHundredsDigitSites(instructions) == 1;
  }

  private static boolean isPatchedGraphicDisplay(List<Instruction> instructions) {
    for (int i = 0; i + 23 < instructions.size(); i++) {
      if (isClampStart(instructions, i)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isClampStart(List<Instruction> instructions, int offset) {
    return isLoadSlot(instructions.get(offset), DISPLAY_ENABLED_SLOT)
        && instructions.get(offset + 1).opcode() == Opcode.IFEQ
        && isLoadSlot(instructions.get(offset + 2), PACKED_VALUE_SLOT)
        && isPush(instructions.get(offset + 3), UNSIGNED_SHORT_MASK)
        && instructions.get(offset + 4).opcode() == Opcode.IAND
        && isPush(instructions.get(offset + 5), DISPLAY_CAP)
        && instructions.get(offset + 6).opcode() == Opcode.IF_ICMPLE
        && isLoadSlot(instructions.get(offset + 7), PACKED_VALUE_SLOT)
        && isPush(instructions.get(offset + 8), HIGH_WORD_MASK)
        && instructions.get(offset + 9).opcode() == Opcode.IAND
        && isPush(instructions.get(offset + 10), DISPLAY_CAP)
        && instructions.get(offset + 11).opcode() == Opcode.IOR
        && instructions.get(offset + 12).opcode() == Opcode.ISTORE
        && isLoadSlot(instructions.get(offset + 13), PACKED_VALUE_SLOT)
        && instructions.get(offset + 14).opcode() == Opcode.BIPUSH
        && instructions.get(offset + 15).opcode() == Opcode.ISHR
        && isPush(instructions.get(offset + 16), UNSIGNED_SHORT_MASK)
        && instructions.get(offset + 17).opcode() == Opcode.IAND
        && isPush(instructions.get(offset + 18), DISPLAY_CAP)
        && instructions.get(offset + 19).opcode() == Opcode.IF_ICMPLE
        && isLoadSlot(instructions.get(offset + 20), PACKED_VALUE_SLOT)
        && isPush(instructions.get(offset + 21), UNSIGNED_SHORT_MASK)
        && instructions.get(offset + 22).opcode() == Opcode.IAND
        && isPush(instructions.get(offset + 23), DISPLAY_CAP_HIGH_WORD);
  }

  private static int countCurrentHundredsDigitSites(List<Instruction> instructions) {
    int matches = 0;
    for (int i = 0; i + 8 < instructions.size(); i++) {
      if (isLoadSlot(instructions.get(i), PACKED_VALUE_SLOT)
          && isPush(instructions.get(i + 1), UNSIGNED_SHORT_MASK)
          && instructions.get(i + 2).opcode() == Opcode.IAND
          && isPush(instructions.get(i + 3), 100)
          && instructions.get(i + 4).opcode() == Opcode.IDIV
          && isPush(instructions.get(i + 5), 10)
          && instructions.get(i + 6).opcode() == Opcode.IREM
          && instructions.get(i + 7).opcode() == Opcode.IMUL
          && instructions.get(i + 8).opcode() == Opcode.IADD) {
        matches++;
      }
    }
    return matches;
  }

  private static int countInlineDigitSites(List<Instruction> instructions, boolean patched) {
    int matches = 0;
    for (int i = 0; i + 6 < instructions.size(); i++) {
      if (patched
          ? isPatchedInlineDigitSite(instructions, i)
          : isOriginalInlineDigitSite(instructions, i)) {
        matches++;
      }
    }
    return matches;
  }

  private static boolean isOriginalInlineDigitSite(List<Instruction> instructions, int offset) {
    return isInlineHeroValueRead(instructions, offset)
        && isPush(instructions.get(offset + 4), UNSIGNED_SHORT_MASK)
        && instructions.get(offset + 5).opcode() == Opcode.IAND
        && (isPush(instructions.get(offset + 6), 100) || isPush(instructions.get(offset + 6), 10))
        && !isMathMinCall(instructions.get(offset + 6));
  }

  private static boolean isPatchedInlineDigitSite(List<Instruction> instructions, int offset) {
    return isInlineHeroValueRead(instructions, offset)
        && isPush(instructions.get(offset + 4), UNSIGNED_SHORT_MASK)
        && instructions.get(offset + 5).opcode() == Opcode.IAND
        && isPush(instructions.get(offset + 6), DISPLAY_CAP)
        && isMathMinCall(instructions.get(offset + 7));
  }

  private static boolean isInlineHeroValueRead(List<Instruction> instructions, int offset) {
    return offset + 3 < instructions.size()
        && isHeroPartyField(instructions.get(offset))
        && isLoadSlot(instructions.get(offset + 1), PARTY_INDEX_SLOT)
        && instructions.get(offset + 2).opcode() == Opcode.AALOAD
        && isHeroHealthOrResourceField(instructions.get(offset + 3));
  }

  private static boolean isLoadSlot(Instruction instruction, int slot) {
    return instruction instanceof LoadInstruction load && load.slot() == slot;
  }

  private static boolean isPush(Instruction instruction, int value) {
    return instruction instanceof ConstantInstruction constant
        && constant.constantValue() instanceof Integer integer
        && integer == value;
  }

  private static boolean isSetClip(Instruction instruction) {
    return instruction instanceof InvokeInstruction invoke
        && invoke.opcode() == Opcode.INVOKEVIRTUAL
        && GRAPHICS_CLASS_NAME.equals(invoke.owner().asInternalName())
        && SET_CLIP_METHOD.equals(invoke.name().stringValue())
        && SET_CLIP_DESCRIPTOR.equals(invoke.type().stringValue());
  }

  private static boolean isHeroPartyField(Instruction instruction) {
    return instruction instanceof FieldInstruction field
        && field.opcode() == Opcode.GETSTATIC
        && HERO_CLASS_NAME.equals(field.owner().asInternalName())
        && HERO_PARTY_FIELD.equals(field.name().stringValue())
        && HERO_ARRAY_DESCRIPTOR.equals(field.type().stringValue());
  }

  private static boolean isHeroHealthOrResourceField(Instruction instruction) {
    return instruction instanceof FieldInstruction field
        && field.opcode() == Opcode.GETFIELD
        && HERO_CLASS_NAME.equals(field.owner().asInternalName())
        && (HERO_HEALTH_FIELD.equals(field.name().stringValue())
            || HERO_RESOURCE_FIELD.equals(field.name().stringValue()))
        && HERO_PACKED_VALUE_DESCRIPTOR.equals(field.type().stringValue());
  }

  private static boolean isMathMinCall(Instruction instruction) {
    return instruction instanceof InvokeInstruction invoke
        && invoke.opcode() == Opcode.INVOKESTATIC
        && JAVA_LANG_MATH_CLASS_NAME.equals(invoke.owner().asInternalName())
        && MATH_MIN_METHOD.equals(invoke.name().stringValue())
        && MATH_MIN_DESCRIPTOR.equals(invoke.type().stringValue());
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

  private static final class HighValueGraphicDisplayCodeTransform
      implements java.lang.classfile.CodeTransform {
    private final PatchCounter counter;
    private final List<Instruction> recentInstructions = new ArrayList<>();
    private boolean digitClipJustSet;
    private boolean inlineDigitValueJustMasked;

    private HighValueGraphicDisplayCodeTransform(PatchCounter counter) {
      this.counter = counter;
    }

    @Override
    public void accept(CodeBuilder builder, CodeElement element) {
      if (element instanceof Instruction instruction && digitClipJustSet) {
        emitDigitDisplayClamp(builder);
        counter.increment();
        digitClipJustSet = false;
      } else if (element instanceof Instruction instruction && inlineDigitValueJustMasked) {
        builder.sipush(DISPLAY_CAP);
        builder.invokestatic(MATH_CLASS, MATH_MIN_METHOD, MATH_MIN_TYPE);
        counter.increment();
        inlineDigitValueJustMasked = false;
      }

      builder.with(element);
      if (element instanceof Instruction instruction) {
        remember(instruction);
        digitClipJustSet = isDigitClipSetup(recentInstructions);
        inlineDigitValueJustMasked = isInlineDigitMask(recentInstructions);
      } else {
        digitClipJustSet = false;
        inlineDigitValueJustMasked = false;
      }
    }

    private static boolean isDigitClipSetup(List<Instruction> instructions) {
      int size = instructions.size();
      return size >= 12
          && isSetClip(instructions.get(size - 1))
          && isPush(instructions.get(size - 2), 11)
          && isPush(instructions.get(size - 3), 21)
          && instructions.get(size - 4).opcode() == Opcode.ISUB
          && isPush(instructions.get(size - 5), 12)
          && instructions.get(size - 6).opcode() == Opcode.IADD
          && isPush(instructions.get(size - 7), 6)
          && isLoadSlot(instructions.get(size - 8), 2)
          && instructions.get(size - 9).opcode() == Opcode.IADD
          && isPush(instructions.get(size - 10), 16)
          && isLoadSlot(instructions.get(size - 11), 1);
    }

    private static boolean isInlineDigitMask(List<Instruction> instructions) {
      int size = instructions.size();
      return size >= 6
          && isInlineHeroValueRead(instructions, size - 6)
          && isPush(instructions.get(size - 2), UNSIGNED_SHORT_MASK)
          && instructions.get(size - 1).opcode() == Opcode.IAND;
    }

    private void remember(Instruction instruction) {
      recentInstructions.add(instruction);
      if (recentInstructions.size() > 16) {
        recentInstructions.removeFirst();
      }
    }

    private static void emitDigitDisplayClamp(CodeBuilder builder) {
      Label skipClamp = builder.newLabel();
      Label currentOk = builder.newLabel();
      Label maxOk = builder.newLabel();
      builder
          .iload(DISPLAY_ENABLED_SLOT)
          .ifeq(skipClamp)
          .iload(PACKED_VALUE_SLOT)
          .ldc(UNSIGNED_SHORT_MASK)
          .iand()
          .sipush(DISPLAY_CAP)
          .if_icmple(currentOk)
          .iload(PACKED_VALUE_SLOT)
          .ldc(HIGH_WORD_MASK)
          .iand()
          .sipush(DISPLAY_CAP)
          .ior()
          .istore(PACKED_VALUE_SLOT)
          .labelBinding(currentOk)
          .iload(PACKED_VALUE_SLOT)
          .bipush(16)
          .ishr()
          .ldc(UNSIGNED_SHORT_MASK)
          .iand()
          .sipush(DISPLAY_CAP)
          .if_icmple(maxOk)
          .iload(PACKED_VALUE_SLOT)
          .ldc(UNSIGNED_SHORT_MASK)
          .iand()
          .ldc(DISPLAY_CAP_HIGH_WORD)
          .ior()
          .istore(PACKED_VALUE_SLOT)
          .labelBinding(maxOk)
          .labelBinding(skipClamp);
    }
  }
}
