package com.vddoh.editor.data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.Builder;
import lombok.With;
import org.apache.commons.lang3.StringUtils;

@Builder
@With
public record ChangeLogEntry(
    LocalDateTime timestamp,
    EditorTabName tabName,
    int entryId,
    String entryName,
    ChangeColumnName columnName,
    String oldValue,
    String newValue) {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public ChangeLogEntry {
    timestamp = timestamp == null ? LocalDateTime.now() : timestamp;
    entryName = entryName == null ? StringUtils.EMPTY : entryName;
    oldValue = oldValue == null ? StringUtils.EMPTY : oldValue;
    newValue = newValue == null ? StringUtils.EMPTY : newValue;
  }

  public String formattedTimestamp() {
    return timestamp.format(FORMATTER);
  }

  public String summary() {
    return "%s edit tab %s, entry ID %d, entry name %s, edit column name %s, old value %s, change to %s"
        .formatted(
            formattedTimestamp(),
            tabName == null ? StringUtils.EMPTY : tabName.getLabel(),
            entryId,
            entryName,
            columnName == null ? StringUtils.EMPTY : columnName.getLabel(),
            oldValue,
            newValue);
  }
}
