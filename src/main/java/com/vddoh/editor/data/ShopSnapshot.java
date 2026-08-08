package com.vddoh.editor.data;

import java.util.List;
import lombok.Builder;

@Builder
public record ShopSnapshot(int id, String name, int eventOffset, List<Integer> itemIds) {
  public ShopSnapshot {
    itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
  }
}
