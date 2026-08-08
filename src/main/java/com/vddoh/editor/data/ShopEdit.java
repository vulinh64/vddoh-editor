package com.vddoh.editor.data;

import java.util.List;
import lombok.Builder;

@Builder
public record ShopEdit(int shopId, int eventOffset, List<Integer> itemIds) {
  public ShopEdit {
    itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
  }
}
