package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record StatusSnapshot(
    int id, String name, int duration, int expireChance, int icon, String notes) {}
