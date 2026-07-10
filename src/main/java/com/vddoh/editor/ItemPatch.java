package com.vddoh.editor;

import lombok.Builder;
import lombok.With;

@Builder
@With
record ItemPatch(int itemId, int price, int icon, int hpRestore, int resourceRestore) {}
