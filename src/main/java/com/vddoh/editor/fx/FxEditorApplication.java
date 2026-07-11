package com.vddoh.editor.fx;

import com.vddoh.editor.fx.heroes.FxHeroesView;
import com.vddoh.editor.fx.items.FxItemsView;
import com.vddoh.editor.fx.monsters.FxMonstersView;
import com.vddoh.editor.fx.skills.FxSkillsView;
import com.vddoh.editor.fx.statuses.FxStatusesView;
import com.vddoh.editor.fx.talents.FxTalentsView;
import com.vddoh.editor.fx.ui.FxCommandBar;
import java.util.Objects;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public final class FxEditorApplication extends Application {

  @Override
  public void start(Stage stage) {
    FxEditorState state = new FxEditorState();
    FxNavigation navigation = new FxNavigation();
    BorderPane root = new BorderPane();
    root.getStyleClass().add("app-root");
    root.setTop(new FxCommandBar(stage, state));
    root.setCenter(sectionTabs(state, navigation));
    Label status = new Label();
    status.getStyleClass().add("status-bar");
    status.textProperty().bind(state.statusProperty());
    root.setBottom(status);

    Scene scene = new Scene(root, 1180, 760);
    scene
        .getStylesheets()
        .add(
            Objects.requireNonNull(
                    FxEditorApplication.class.getResource("/com/vddoh/editor/fx/editor.css"))
                .toExternalForm());
    stage.setTitle("VDDOH Data Editor FX - Phase 1");
    stage.setScene(scene);
    stage.show();
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
