package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record StatusEdit(int statusId, int duration, int expireChance, int icon) {}
