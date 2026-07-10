package com.vddoh.editor;

import java.io.Serializable;
import lombok.Builder;
import lombok.With;

@Builder
@With
record NamedRow(int id, String name, String notes) implements Serializable {

  static NamedRow of(int id, String name, String notes) {
    return NamedRow.builder().id(id).name(name).notes(notes).build();
  }
}
