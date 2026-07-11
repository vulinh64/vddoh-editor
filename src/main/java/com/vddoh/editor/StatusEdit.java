package com.vddoh.editor;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record StatusEdit(int statusId, int duration, int expireChance, int icon) {}
