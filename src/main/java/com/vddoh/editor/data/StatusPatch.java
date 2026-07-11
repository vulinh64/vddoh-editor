package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record StatusPatch(int statusId, int duration, int expireChance, int icon) {}
