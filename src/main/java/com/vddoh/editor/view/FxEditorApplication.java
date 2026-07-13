package com.vddoh.editor.view;

import com.vddoh.editor.view.heroes.FxHeroesView;
import com.vddoh.editor.view.items.FxItemsView;
import com.vddoh.editor.view.monsters.FxMonstersView;
import com.vddoh.editor.view.skills.FxSkillsView;
import com.vddoh.editor.view.statuses.FxStatusesView;
import com.vddoh.editor.view.talents.FxTalentsView;
import com.vddoh.editor.view.ui.FxCommandBar;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public final class FxEditorApplication extends Application {

  @Override
  public void start(Stage stage) {
    Path initialInputJar = chooseInitialInputJar();
    if (initialInputJar == null) {
      System.exit(0);
      return;
    }
    FxEditorState state = new FxEditorState();
    FxNavigation navigation = new FxNavigation();
    BorderPane root = new BorderPane();
    root.getStyleClass().add("app-root");
    FxCommandBar commandBar = new FxCommandBar(stage, state);
    root.setTop(commandBar);
    root.setCenter(sectionTabs(state, navigation));
    Label status = new Label();
    status.getStyleClass().add("status-bar");
    status.textProperty().bind(state.statusProperty());
    root.setBottom(status);

    Scene scene = new Scene(root, 1180, 760);
    scene
        .getStylesheets()
        .add(
            Objects.requireNonNull(FxEditorApplication.class.getResource("/editor.css"))
                .toExternalForm());
    stage.setTitle("VDDOH Data Editor");
    stage.setScene(scene);
    stage.show();
    commandBar.loadInitialInputJar(initialInputJar);
  }

  private static Path chooseInitialInputJar() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Choose VDDOH JAR");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR files", "*.jar"));
    File chosen = chooser.showOpenDialog(null);
    return chosen == null ? null : chosen.toPath();
  }

  private static TabPane sectionTabs(FxEditorState state, FxNavigation navigation) {
    TabPane tabs = new TabPane();
    Tab skills = new Tab("Skills", new FxSkillsView(state, navigation));
    Tab talents = new Tab("Talents", new FxTalentsView(state));
    Tab heroes = new Tab("Heroes", new FxHeroesView(state));
    Tab items = new Tab("Items", new FxItemsView(state, navigation));
    Tab monsters = new Tab("Monsters", new FxMonstersView(state));
    Tab statuses = new Tab("Statuses", new FxStatusesView(state));
    skills.setClosable(false);
    talents.setClosable(false);
    heroes.setClosable(false);
    items.setClosable(false);
    monsters.setClosable(false);
    statuses.setClosable(false);
    navigation
        .pendingSkillNavigationProperty()
        .addListener(
            (_, _, request) -> {
              if (request != null) {
                tabs.getSelectionModel().select(skills);
              }
            });
    tabs.getTabs().addAll(skills, talents, heroes, items, monsters, statuses);
    return tabs;
  }
}
