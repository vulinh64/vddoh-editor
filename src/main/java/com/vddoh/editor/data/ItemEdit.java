package com.vddoh.editor.data;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record ItemEdit(int itemId, int price, int icon, int hpRestore, int resourceRestore) {}
