package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record MonsterArrayEntrySnapshot(
    String side,
    String type,
    int index,
    String target,
    int value,
    boolean editable,
    int max,
    String raw) {}
