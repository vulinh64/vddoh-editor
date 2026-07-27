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
import java.lang.classfile.instruction.LoadInstruction;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class HighValueDisplayClassPatcher {

  private static final int EXPECTED_SITES = 1;
  private static final int FORMAT_WIDTH_3_DIGITS = 1000;
  private static final int DISPLAY_CAP = 999;
  private static final String DISPLAY_CAP_TEXT = "999+";
  private static final String FORMAT_METHOD = "a";
  private static final String FORMAT_DESCRIPTOR = "(II)Ljava/lang/String;";

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
              HighValueDisplayClassPatcher::originalSites,
              HighValueDisplayClassPatcher::patchedSites);
      if (sites.isPatched(EXPECTED_SITES)) {
        return State.PATCHED;
      }
      if (sites.isOriginal(EXPECTED_SITES)) {
        return State.ORIGINAL;
      }
      log.info(
          "High-value display class patch state unknown; originalSites={}, patchedSites={}",
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
    log.info("Applying high-value display class patch; current state={}", state);
    if (state == State.PATCHED) {
      summary.incrementSkipped();
      log.info("High-value display class patch skipped because class is already patched");
      return new Result(data, summary);
    }
    if (state != State.ORIGINAL) {
      throw new IllegalArgumentException("Unsupported j.class layout for high-value display patch");
    }

    ClassFile classFile = ClassFile.of();
    ClassModel model = classFile.parse(data);
    PatchCounter counter = new PatchCounter();
    byte[] patched =
        classFile.transformClass(
            model,
            java.lang.classfile.ClassTransform.transformingMethodBodies(
                HighValueDisplayClassPatcher::isFormatMethod,
                java.lang.classfile.CodeTransform.ofStateful(
                    () -> new HighValueDisplayCodeTransform(counter))));

    State patchedState = state(patched);
    if (counter.count() != EXPECTED_SITES || patchedState != State.PATCHED) {
      throw new IllegalStateException(
          "High-value display patch did not produce the expected j.class bytecode; counter=%d, state=%s"
              .formatted(counter.count(), patchedState));
    }
    summary.setHighValueDisplay(counter.count());
    log.info("High-value display class patch applied at {} site", counter.count());
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
      if (!isFormatMethod(method)) {
        continue;
      }
      List<Instruction> instructions = ClassPatchSupport.instructions(method);
      if (patched ? isPatchedFormatter(instructions) : isOriginalFormatter(instructions)) {
        matches++;
      }
    }
    return matches;
  }

  private static boolean isFormatMethod(MethodModel method) {
    return FORMAT_METHOD.equals(method.methodName().stringValue())
        && FORMAT_DESCRIPTOR.equals(method.methodType().stringValue());
  }


  private static boolean isOriginalFormatter(List<Instruction> instructions) {
    return instructions.size() >= 5
        && !isPatchedFormatter(instructions)
        && isStringConstant(instructions.get(0), "")
        && instructions.get(1).opcode() == Opcode.ASTORE_2
        && isLoadSlot(instructions.get(2), 1)
        && isPush(instructions.get(3), 10)
        && instructions.get(4).opcode() == Opcode.IDIV;
  }

  private static boolean isPatchedFormatter(List<Instruction> instructions) {
    return instructions.size() >= 8
        && isLoadSlot(instructions.get(0), 1)
        && isPush(instructions.get(1), FORMAT_WIDTH_3_DIGITS)
        && instructions.get(2).opcode() == Opcode.IF_ICMPNE
        && isLoadSlot(instructions.get(3), 0)
        && isPush(instructions.get(4), DISPLAY_CAP)
        && instructions.get(5).opcode() == Opcode.IF_ICMPLE
        && isStringConstant(instructions.get(6), DISPLAY_CAP_TEXT)
        && instructions.get(7).opcode() == Opcode.ARETURN;
  }

  private static boolean isLoadSlot(Instruction instruction, int slot) {
    return instruction instanceof LoadInstruction load && load.slot() == slot;
  }

  private static boolean isPush(Instruction instruction, int value) {
    return instruction instanceof ConstantInstruction constant
        && constant.constantValue() instanceof Integer integer
        && integer == value;
  }

  private static boolean isStringConstant(Instruction instruction, String value) {
    return instruction instanceof ConstantInstruction constant
        && value.equals(constant.constantValue());
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

  private static final class HighValueDisplayCodeTransform
      implements java.lang.classfile.CodeTransform {
    private final PatchCounter counter;
    private boolean emitted;

    private HighValueDisplayCodeTransform(PatchCounter counter) {
      this.counter = counter;
    }

    @Override
    public void accept(CodeBuilder builder, CodeElement element) {
      if (!emitted && element instanceof Instruction) {
        emitHighValueGuard(builder);
        emitted = true;
        counter.increment();
      }
      builder.with(element);
    }

    private static void emitHighValueGuard(CodeBuilder builder) {
      Label vanilla = builder.newLabel();
      builder
          .iload(1)
          .sipush(FORMAT_WIDTH_3_DIGITS)
          .if_icmpne(vanilla)
          .iload(0)
          .sipush(DISPLAY_CAP)
          .if_icmple(vanilla)
          .ldc(DISPLAY_CAP_TEXT)
          .areturn()
          .labelBinding(vanilla);
    }
  }
}
