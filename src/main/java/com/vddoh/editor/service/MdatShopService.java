package com.vddoh.editor.service;

import com.vddoh.editor.data.ItemSnapshot;
import com.vddoh.editor.data.ShopEdit;
import com.vddoh.editor.data.ShopSnapshot;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Parses and rewrites the confirmed length-prefixed consumable shop events in {@code m.dat}. */
public final class MdatShopService {
  private static final int SHOP_OPCODE = 0x10;
  private static final int SHOP_HEADER_A = 0x30;
  private static final int SHOP_HEADER_B = 0x07;
  private static final Set<Integer> CHILDREN_STOCK_IDS =
      Set.of(6, 7, 8, 9, 10, 13, 14, 24, 25, 26, 28);

  private MdatShopService() {}

  public static boolean isChildrenShopItem(int itemId) {
    return CHILDREN_STOCK_IDS.contains(itemId);
  }

  public static List<ShopSnapshot> parse(byte[] data, List<ItemSnapshot> items) {
    Map<Integer, ItemSnapshot> itemsById =
        items.stream().collect(Collectors.toUnmodifiableMap(ItemSnapshot::id, item -> item));
    List<ShopSnapshot> shops = new ArrayList<>();
    for (int offset = 0; offset + 4 <= data.length; offset++) {
      int length = u8(data[offset]);
      if (length < 4 || offset + length >= data.length || isNotShopEvent(data, offset)) {
        continue;
      }
      List<Integer> stock = new ArrayList<>();
      boolean childrenShop = true;
      for (int index = offset + 4; index <= offset + length; index++) {
        int itemId = u8(data[index]);
        stock.add(itemId);
        if (!itemsById.containsKey(itemId) || !CHILDREN_STOCK_IDS.contains(itemId)) {
          childrenShop = false;
          break;
        }
      }
      if (childrenShop) {
        shops.add(
            ShopSnapshot.builder()
                .id(shops.size())
                .name(shopName(stock, offset))
                .eventOffset(offset)
                .itemIds(stock)
                .build());
      }
    }
    return List.copyOf(shops);
  }

  public static byte[] patch(byte[] original, List<ShopEdit> edits) {
    List<ShopEdit> ordered = edits.stream().sorted(Comparator.comparingInt(ShopEdit::eventOffset).reversed()).toList();
    byte[] patched = original;
    for (ShopEdit edit : ordered) {
      validate(edit, patched);
      patched = replace(patched, edit.eventOffset(), originalLength(patched, edit.eventOffset()), encode(edit));
    }
    return patched;
  }

  private static boolean isNotShopEvent(byte[] data, int offset) {
    return u8(data[offset + 1]) != SHOP_OPCODE
            || u8(data[offset + 2]) != SHOP_HEADER_A
            || u8(data[offset + 3]) != SHOP_HEADER_B;
  }

  private static void validate(ShopEdit edit, byte[] data) {
    int offset = edit.eventOffset();
    if (offset < 0 || offset + 4 > data.length || isNotShopEvent(data, offset)) {
      throw new IllegalArgumentException("Unknown shop event layout at m.dat offset " + offset);
    }
    if (edit.itemIds().isEmpty() || edit.itemIds().size() > 252) {
      throw new IllegalArgumentException("Shop stock must contain 1..252 items.");
    }
    for (int itemId : edit.itemIds()) {
      if (!isChildrenShopItem(itemId)) {
        throw new IllegalArgumentException("This tab accepts only the confirmed Children of Apocalypse shop items.");
      }
    }
  }

  private static int originalLength(byte[] data, int offset) {
    return u8(data[offset]) + 1;
  }

  private static byte[] encode(ShopEdit edit) {
    ByteArrayOutputStream encoded = new ByteArrayOutputStream(edit.itemIds().size() + 4);
    encoded.write(edit.itemIds().size() + 3);
    encoded.write(SHOP_OPCODE);
    encoded.write(SHOP_HEADER_A);
    encoded.write(SHOP_HEADER_B);
    edit.itemIds().forEach(encoded::write);
    return encoded.toByteArray();
  }

  private static byte[] replace(byte[] data, int offset, int length, byte[] replacement) {
    byte[] result = new byte[data.length - length + replacement.length];
    System.arraycopy(data, 0, result, 0, offset);
    System.arraycopy(replacement, 0, result, offset, replacement.length);
    System.arraycopy(data, offset + length, result, offset + replacement.length, data.length - offset - length);
    return result;
  }

  private static String shopName(List<Integer> stock, int offset) {
    Set<Integer> ids = Set.copyOf(stock);
    if (ids.equals(Set.of(6, 7, 24, 25, 10))) return "Lord Craft shop";
    if (ids.contains(14) && ids.contains(13) && stock.size() == 7) return "Sephyrot cave shop (Mysterious Caves quest)";
    if (ids.contains(28)) return "Gadanis shop (Might potion stock)";
    if (ids.contains(13) && stock.size() == 8) return "Gadanis shop (pre-Mysterious Potions)";
    return "Children shop @ m.dat 0x%X".formatted(offset);
  }

  private static int u8(byte value) { return Byte.toUnsignedInt(value); }
}
