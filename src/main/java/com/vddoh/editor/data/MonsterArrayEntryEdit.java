package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record MonsterArrayEntryEdit(String raw, int value) {}
