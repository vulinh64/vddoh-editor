package com.vddoh.editor.service;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ClassPatchSupport {

  static ClassModel classModel(byte[] data) {
    return ClassFile.of().parse(data);
  }

  static SiteCounts siteCounts(
      byte[] data,
      ToIntFunction<ClassModel> originalCounter,
      ToIntFunction<ClassModel> patchedCounter) {
    ClassModel model = classModel(data);
    return new SiteCounts(originalCounter.applyAsInt(model), patchedCounter.applyAsInt(model));
  }

  static List<Instruction> instructions(MethodModel method) {
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

  record SiteCounts(int original, int patched) {
    boolean isPatched(int expectedSites) {
      return patched == expectedSites && original == 0;
    }

    boolean isOriginal(int expectedSites) {
      return original == expectedSites && patched == 0;
    }
  }
}
