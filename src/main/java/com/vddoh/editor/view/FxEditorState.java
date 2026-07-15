package com.vddoh.editor.view;

import com.vddoh.editor.data.ChangeColumnName;
import com.vddoh.editor.data.ChangeLogEntry;
import com.vddoh.editor.data.EditorTabName;
import com.vddoh.editor.data.EditorWorkspace;
import com.vddoh.editor.data.HeroEdit;
import com.vddoh.editor.data.ItemEdit;
import com.vddoh.editor.data.MonsterEdit;
import com.vddoh.editor.data.SkillEdit;
import com.vddoh.editor.data.StatusEdit;
import com.vddoh.editor.data.TalentEdit;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public final class FxEditorState {

  private final ObjectProperty<EditorWorkspace> workspace = new SimpleObjectProperty<>();
  private final ObjectProperty<Path> outputJar = new SimpleObjectProperty<>();
  private final ObservableList<ChangeLogEntry> changeLog = FXCollections.observableArrayList();
  private final StringProperty status = new SimpleStringProperty("Choose a VDDOH JAR to begin.");
  private final BooleanProperty patchResistanceOverflow = new SimpleBooleanProperty(false);
  private final BooleanProperty patchEquipmentBonus = new SimpleBooleanProperty(false);
  private final BooleanProperty patchPhysicalDamageCap = new SimpleBooleanProperty(false);
  private final BooleanProperty patchHighValueDisplay = new SimpleBooleanProperty(false);
  private final BooleanProperty patchHighValueGraphicDisplay = new SimpleBooleanProperty(false);
  private final BooleanProperty patchVictoryReward = new SimpleBooleanProperty(false);
  private final BooleanProperty patchMonsterRewardParser = new SimpleBooleanProperty(false);
  private Supplier<List<SkillEdit>> skillEdits = List::of;
  private Supplier<List<TalentEdit>> talentEdits = List::of;
  private Supplier<List<HeroEdit>> heroEdits = List::of;
  private Supplier<List<ItemEdit>> itemEdits = List::of;
  private Supplier<List<MonsterEdit>> monsterEdits = List::of;
  private Supplier<List<StatusEdit>> statusEdits = List::of;

  public ObjectProperty<EditorWorkspace> workspaceProperty() {
    return workspace;
  }

  public EditorWorkspace workspace() {
    return workspace.get();
  }

  public void workspace(EditorWorkspace workspace) {
    this.workspace.set(workspace);
  }

  public Path outputJar() {
    return outputJar.get();
  }

  public void outputJar(Path outputJar) {
    this.outputJar.set(outputJar);
  }

  public EditorWorkspace buildWorkspace() {
    EditorWorkspace current = workspace();
    Path output = outputJar();
    return current == null || output == null ? current : current.withOutputJar(output);
  }

  public StringProperty statusProperty() {
    return status;
  }

  public void status(String message) {
    status.set(message);
  }

  public ObservableList<ChangeLogEntry> changeLog() {
    return changeLog;
  }

  public void clearChangeLog() {
    changeLog.clear();
  }

  public void recordChange(
      EditorTabName tabName,
      int entryId,
      String entryName,
      ChangeColumnName columnName,
      Object oldValue,
      Object newValue) {
    String oldText = displayValue(oldValue);
    String newText = displayValue(newValue);
    if (oldText.equals(newText)) {
      return;
    }
    ChangeLogEntry entry =
        ChangeLogEntry.builder()
            .tabName(tabName)
            .entryId(entryId)
            .entryName(entryName)
            .columnName(columnName)
            .oldValue(oldText)
            .newValue(newText)
            .build();
    changeLog.add(entry);
    log.info(entry.summary());
  }

  private static String displayValue(Object value) {
    return value == null ? StringUtils.EMPTY : String.valueOf(value);
  }

  public BooleanProperty patchResistanceOverflowProperty() {
    return patchResistanceOverflow;
  }

  public boolean patchResistanceOverflow() {
    return patchResistanceOverflow.get();
  }

  public void patchResistanceOverflow(boolean patchResistanceOverflow) {
    this.patchResistanceOverflow.set(patchResistanceOverflow);
  }

  public BooleanProperty patchEquipmentBonusProperty() {
    return patchEquipmentBonus;
  }

  public boolean patchEquipmentBonus() {
    return patchEquipmentBonus.get();
  }

  public void patchEquipmentBonus(boolean patchEquipmentBonus) {
    this.patchEquipmentBonus.set(patchEquipmentBonus);
  }

  public BooleanProperty patchVictoryRewardProperty() {
    return patchVictoryReward;
  }

  public BooleanProperty patchPhysicalDamageCapProperty() {
    return patchPhysicalDamageCap;
  }

  public boolean patchPhysicalDamageCap() {
    return patchPhysicalDamageCap.get();
  }

  public void patchPhysicalDamageCap(boolean patchPhysicalDamageCap) {
    this.patchPhysicalDamageCap.set(patchPhysicalDamageCap);
  }

  public BooleanProperty patchHighValueDisplayProperty() {
    return patchHighValueDisplay;
  }

  public boolean patchHighValueDisplay() {
    return patchHighValueDisplay.get();
  }

  public void patchHighValueDisplay(boolean patchHighValueDisplay) {
    this.patchHighValueDisplay.set(patchHighValueDisplay);
  }

  public BooleanProperty patchHighValueGraphicDisplayProperty() {
    return patchHighValueGraphicDisplay;
  }

  public boolean patchHighValueGraphicDisplay() {
    return patchHighValueGraphicDisplay.get();
  }

  public void patchHighValueGraphicDisplay(boolean patchHighValueGraphicDisplay) {
    this.patchHighValueGraphicDisplay.set(patchHighValueGraphicDisplay);
  }

  public boolean patchVictoryReward() {
    return patchVictoryReward.get();
  }

  public void patchVictoryReward(boolean patchVictoryReward) {
    this.patchVictoryReward.set(patchVictoryReward);
  }

  public BooleanProperty patchMonsterRewardParserProperty() {
    return patchMonsterRewardParser;
  }

  public boolean patchMonsterRewardParser() {
    return patchMonsterRewardParser.get();
  }

  public void patchMonsterRewardParser(boolean patchMonsterRewardParser) {
    this.patchMonsterRewardParser.set(patchMonsterRewardParser);
  }

  public void skillEditsSupplier(Supplier<List<SkillEdit>> skillEdits) {
    this.skillEdits = safeSupplier(skillEdits);
  }

  public List<SkillEdit> skillEdits() {
    return safeList(skillEdits);
  }

  public void talentEditsSupplier(Supplier<List<TalentEdit>> talentEdits) {
    this.talentEdits = safeSupplier(talentEdits);
  }

  public List<TalentEdit> talentEdits() {
    return safeList(talentEdits);
  }

  public void heroEditsSupplier(Supplier<List<HeroEdit>> heroEdits) {
    this.heroEdits = safeSupplier(heroEdits);
  }

  public List<HeroEdit> heroEdits() {
    return safeList(heroEdits);
  }

  public void itemEditsSupplier(Supplier<List<ItemEdit>> itemEdits) {
    this.itemEdits = safeSupplier(itemEdits);
  }

  public List<ItemEdit> itemEdits() {
    return safeList(itemEdits);
  }

  public void monsterEditsSupplier(Supplier<List<MonsterEdit>> monsterEdits) {
    this.monsterEdits = safeSupplier(monsterEdits);
  }

  public List<MonsterEdit> monsterEdits() {
    return safeList(monsterEdits);
  }

  public void statusEditsSupplier(Supplier<List<StatusEdit>> statusEdits) {
    this.statusEdits = safeSupplier(statusEdits);
  }

  public List<StatusEdit> statusEdits() {
    return safeList(statusEdits);
  }

  private static <T> Supplier<List<T>> safeSupplier(Supplier<List<T>> supplier) {
    return supplier == null ? List::of : supplier;
  }

  private static <T> List<T> safeList(Supplier<List<T>> supplier) {
    List<T> values = supplier.get();
    return values == null ? List.of() : values;
  }
}
