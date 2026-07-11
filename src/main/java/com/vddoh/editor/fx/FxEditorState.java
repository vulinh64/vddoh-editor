package com.vddoh.editor.fx;

import com.vddoh.editor.EditorWorkspace;
import com.vddoh.editor.HeroEdit;
import com.vddoh.editor.ItemEdit;
import com.vddoh.editor.MonsterEdit;
import com.vddoh.editor.SkillEdit;
import com.vddoh.editor.StatusEdit;
import com.vddoh.editor.TalentEdit;
import java.util.List;
import java.util.function.Supplier;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class FxEditorState {

  private final ObjectProperty<EditorWorkspace> workspace = new SimpleObjectProperty<>();
  private final StringProperty status = new SimpleStringProperty("Choose a VDDOH JAR to begin.");
  private final BooleanProperty patchResistanceOverflow = new SimpleBooleanProperty(false);
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

  public StringProperty statusProperty() {
    return status;
  }

  public void status(String message) {
    status.set(message);
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
