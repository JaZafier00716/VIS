package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.domain.AccessPolicy;
import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.NodeType;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ProjectStatus;
import cz.vsb.catering.business.domain.ProjectNode;
import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.User;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** D1 main window (UC02, UC19, UC17): project tree on the left, content of the active folder on the right. */
class MainWindow {
    private final AppContext ctx;
    private final User user;
    private final Stage stage;
    private CustomersWindow customersWindow;
    private CatalogWindow catalogWindow;
    final ComboBox<Project> projectBox = new ComboBox<>();
    private final Label status = Ui.label("", "badge", "badge-status");
    private final TreeView<ProjectNode> tree = new TreeView<>();
    final TableView<ProjectNode> table = new TableView<>();
    private final Label breadcrumb = Ui.label("", "muted");
    private final Label title = Ui.label("", "h1");
    private final Label summary = Ui.label("", "muted");
    private final GridPane detail = new GridPane();
    final MenuItem newFolder = new MenuItem("Složka", Ui.icon(NodeType.FOLDER));
    final MenuItem newMenu = new MenuItem("Menu", Ui.icon(NodeType.MENU));
    final MenuButton create = new MenuButton("+ Nový");
    final Button upload = new Button("Nahrát dokument");
    final Button customers = new Button("Zákazníci"), catalog = new Button("Katalog");
    final Button editProject = new Button("Upravit projekt");
    final TextField searchField = new TextField();
    final Button search = new Button("Hledat");
    final Button download = new Button("Stáhnout");
    final Button delete = new Button("Smazat", Ui.icon(Ui.TRASH));
    private Folder root;

    MainWindow(AppContext ctx, User user, Stage stage) {
        AccessPolicy.requireWorker(user);                                // A-17: the worker UI needs the WORKER role
        this.ctx = ctx;
        this.user = user;
        this.stage = stage;
        BorderPane pane = new BorderPane(content(), topBar(), null, null, side());
        stage.setTitle("Catering IS");
        stage.setScene(Ui.scene(pane, 1280, 760));
        projectBox.getItems().setAll(ctx.projects.findAllProjects());
        projectBox.valueProperty().addListener((o, a, p) -> showProject(p, null));
        if (!projectBox.getItems().isEmpty()) projectBox.getSelectionModel().selectFirst();
    }

    Stage stage() { return stage; }

    User user() { return user; }

    AppContext ctx() { return ctx; }

    private HBox topBar() {
        Button newProject = new Button("+ Nový projekt");
        newProject.setOnAction(e -> new NewProjectDialog(ctx.projects, user).show().ifPresent(this::projectCreated));
        String roles = user.getRoles().stream().map(Role::label).collect(Collectors.joining(" · "));
        customers.setOnAction(e -> {                                                          // UC05
            if (customersWindow == null || !customersWindow.stage().isShowing()) {
                customersWindow = new CustomersWindow(ctx.customers);
                customersWindow.stage().initOwner(stage);
            }
            customersWindow.stage().show();
            customersWindow.stage().toFront();
        });
        catalog.setOnAction(e -> {                                                            // UC06
            if (catalogWindow == null || !catalogWindow.stage().isShowing()) {
                catalogWindow = new CatalogWindow(ctx.catalog);
                catalogWindow.stage().initOwner(stage);
            }
            catalogWindow.stage().show();
            catalogWindow.stage().toFront();
        });
        HBox bar = new HBox(Ui.label("C", "logo"), Ui.label("Catering IS", "app-name"), newProject, customers, catalog,
                Ui.spacer());
        if (AccessPolicy.canUseAdminMode(user)) {            // UC17 only for an active administrator
            ToggleButton admin = new ToggleButton("Administrátorský režim");
            admin.setOnAction(e -> {
                if (!admin.isSelected()) return;
                UsersWindow users = new UsersWindow(ctx.users, user);
                users.stage().initOwner(stage);
                users.stage().addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, ev -> admin.setSelected(false));
                users.stage().show();
            });
            bar.getChildren().add(admin);
        }
        bar.getChildren().add(new VBox(Ui.label(user.getFullName(), "h2"), Ui.label(roles, "muted")));
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    private VBox side() {
        projectBox.setMaxWidth(Double.MAX_VALUE);
        tree.setShowRoot(true);
        tree.setCellFactory(t -> new javafx.scene.control.TreeCell<>() {
            @Override protected void updateItem(ProjectNode n, boolean empty) {
                super.updateItem(n, empty);
                setText(empty || n == null ? null : n.getName());
                setGraphic(empty || n == null ? null : Ui.icon(n.type()));
            }
        });
        tree.getSelectionModel().selectedItemProperty().addListener((o, a, item) -> {
            if (item == null) return;
            ProjectNode n = item.getValue();
            showFolder(n instanceof Folder f ? f : n.getParent());
        });
        tree.setOnMouseClicked(e -> {
            TreeItem<ProjectNode> item = tree.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && item != null && item.getValue() instanceof Menu m) openMenu(m);
        });
        VBox.setVgrow(tree, Priority.ALWAYS);
        detail.setHgap(16);
        detail.setVgap(4);
        searchField.setPromptText("Hledat projekt nebo zákazníka…");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setOnAction(e -> search.fire());
        search.setOnAction(e -> Ui.prompter.dialog(new ProjectSearchDialog(ctx.projects, searchField.getText()).dialog())
                .ifPresent(p -> selectProject(p.getId())));                                   // UC04
        editProject.setOnAction(e -> {                                                            // UC03
            if (projectBox.getValue() != null)
                Ui.prompter.dialog(new EditProjectDialog(ctx.projects, projectBox.getValue(), user).dialog())
                        .ifPresent(this::projectCreated);
        });
        VBox side = new VBox(new HBox(6, searchField, search), Ui.label("PROJEKT", "section"), projectBox,
                new HBox(8, status, Ui.spacer(), editProject), Ui.label("OBSAH PROJEKTU", "section"),
                tree, Ui.label("DETAIL PROJEKTU", "section"), detail);
        side.setPrefWidth(300);
        side.getStyleClass().add("side");
        return side;
    }

    private VBox content() {
        create.getStyleClass().add("primary");
        newFolder.setOnAction(e -> createNode("Nová složka", name -> ctx.documents.createFolder(activeFolder(), name)));
        newMenu.setOnAction(e -> createNode("Nové menu", name -> ctx.menus.createMenu(activeFolder(), name, user)));
        create.getItems().addAll(newFolder, newMenu);
        upload.setOnAction(e -> upload());
        download.setOnAction(e -> download());
        delete.getStyleClass().add("danger");
        delete.setOnAction(e -> delete());
        HBox toolbar = new HBox(8, create, upload, download, delete);

        TableColumn<ProjectNode, ProjectNode> name = new TableColumn<>("Název");
        name.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
        name.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(ProjectNode n, boolean empty) {
                super.updateItem(n, empty);
                setText(empty || n == null ? null : n.getName());
                setGraphic(empty || n == null ? null : Ui.icon(n.type()));
            }
        });
        name.setPrefWidth(380);
        table.getColumns().add(name);
        table.getColumns().add(column("Typ", 140, n -> n instanceof Document d ? typeOf(d) : n.type().label()));
        table.getColumns().add(column("Velikost", 120, n -> switch (n) {
            case Document d -> d.formattedSize();
            case Folder f -> Ui.plural(f.getChildren().size(), "položka", "položky", "položek");
            default -> "";
        }));
        table.getColumns().add(column("Vytvořeno", 130, n -> Ui.date(n.getCreatedAt())));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPlaceholder(new Label("Složka je prázdná."));
        table.getSelectionModel().getSelectedItems().addListener((ListChangeListener<ProjectNode>) c -> updateButtons());
        table.setRowFactory(t -> {
            var row = new javafx.scene.control.TableRow<ProjectNode>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() != 2 || row.getItem() == null) return;
                if (row.getItem() instanceof Folder f) selectInTree(f.getId());
                else if (row.getItem() instanceof Menu m) openMenu(m);
            });
            return row;
        });
        VBox.setVgrow(table, Priority.ALWAYS);
        updateButtons();
        VBox box = new VBox(breadcrumb, new HBox(12, title, summary), toolbar, table);
        box.getStyleClass().add("content");
        ((HBox) box.getChildren().get(1)).setAlignment(javafx.geometry.Pos.BASELINE_LEFT);
        return box;
    }

    private static TableColumn<ProjectNode, String> column(String title, double width,
                                                           java.util.function.Function<ProjectNode, String> value) {
        TableColumn<ProjectNode, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new SimpleStringProperty(value.apply(d.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    private static String typeOf(Document d) {
        int dot = d.getName().lastIndexOf('.');
        return dot > 0 ? d.getName().substring(dot + 1).toUpperCase() : "Dokument";
    }

    // ------------------------------------------------------------------ project and folder

    /** UC02 step 4: show the new project with its (empty) root folder. */
    void projectCreated(Project p) {                         // also after UC03 edit/archive
        projectBox.getItems().setAll(ctx.projects.findAllProjects());
        selectProject(p.getId());
    }

    void selectProject(long projectId) {
        if (projectBox.getItems().stream().noneMatch(p -> p.getId() == projectId)) {   // A-33: e.g. a search result
            try {
                projectBox.getItems().setAll(ctx.projects.findAllProjects());
            } catch (Exception e) {
                Ui.error(e);
                return;
            }
        }
        projectBox.getItems().stream().filter(p -> p.getId() == projectId).findFirst()
                .ifPresent(projectBox.getSelectionModel()::select);
    }

    private void showProject(Project p, Long folderId) {
        if (p == null) return;
        status.setText(p.getStatus().label().toUpperCase());
        status.getStyleClass().setAll("label", "badge", p.getStatus() == ProjectStatus.ARCHIVED ? "badge-archived" : "badge-status");
        detail.getChildren().clear();
        String[][] rows = {{"Zákazník", p.getCustomer() == null ? "" : p.getCustomer().displayName()},
                {"Datum akce", Ui.date(p.getEventDate())}, {"Místo", p.getEventPlace()},
                {"Hosté", String.valueOf(p.getGuestCount())}};
        for (int i = 0; i < rows.length; i++) {
            detail.add(Ui.label(rows[i][0], "muted"), 0, i);
            detail.add(new Label(rows[i][1]), 1, i);
        }
        try {
            root = ctx.projects.loadTree(p.getId());
        } catch (Exception e) {
            Ui.error(e);
            tree.setRoot(null);                             // A-34: never keep the previous project's tree
            table.getItems().clear();
            table.setUserData(null);
            create.setDisable(true);
            upload.setDisable(true);
            updateButtons();
            return;
        }
        tree.setRoot(treeItem(root));
        create.setDisable(p.isArchived());                  // UC03: an archived project is read-only
        upload.setDisable(p.isArchived());
        selectInTree(folderId == null ? root.getId() : folderId);
        updateButtons();
    }

    private TreeItem<ProjectNode> treeItem(ProjectNode node) {
        TreeItem<ProjectNode> item = new TreeItem<>(node);
        item.setExpanded(node.getParent() == null);
        if (node instanceof Folder f) f.getChildren().forEach(c -> item.getChildren().add(treeItem(c)));
        return item;
    }

    void selectInTree(long nodeId) {
        TreeItem<ProjectNode> item = findItem(tree.getRoot(), nodeId);
        if (item == null) item = tree.getRoot();
        for (TreeItem<ProjectNode> p = item.getParent(); p != null; p = p.getParent()) p.setExpanded(true);
        tree.getSelectionModel().select(item);
        showFolder((Folder) item.getValue());
    }

    private static TreeItem<ProjectNode> findItem(TreeItem<ProjectNode> item, long id) {
        if (item == null) return null;
        if (item.getValue().getId() != null && item.getValue().getId() == id) return item;
        for (TreeItem<ProjectNode> c : item.getChildren()) {
            TreeItem<ProjectNode> found = findItem(c, id);
            if (found != null) return found;
        }
        return null;
    }

    /** UC02 step 7: the active folder and its content (DocumentService.listContent). */
    private void showFolder(Folder folder) {
        if (folder == null || folder == table.getUserData()) return;
        List<ProjectNode> content;
        try {
            content = ctx.documents.listContent(folder);
        } catch (Exception e) {
            Ui.error(e);
            return;
        }
        table.setUserData(folder);
        table.getItems().setAll(content);
        breadcrumb.setText("⌂ " + folder.path());
        title.setText(folder.getName());
        long folders = content.stream().filter(n -> n instanceof Folder).count();
        long menus = content.stream().filter(n -> n instanceof Menu).count();
        summary.setText((folder.getParent() == null ? "kořenová složka · " : "")
                + Ui.plural(folders, "složka", "složky", "složek") + ", " + menus + " menu, "
                + Ui.plural(content.size() - folders - menus, "dokument", "dokumenty", "dokumentů"));
    }

    Folder activeFolder() { return (Folder) table.getUserData(); }

    /** Reloads the tree from the database and keeps the active folder. */
    private void reload() {
        Long folderId = activeFolder() == null ? null : activeFolder().getId();
        table.setUserData(null);
        showProject(projectBox.getValue(), folderId);
    }

    private void updateButtons() {
        List<ProjectNode> sel = table.getSelectionModel().getSelectedItems();
        boolean onlyDocs = !sel.isEmpty() && sel.stream().allMatch(n -> n instanceof Document);
        download.setDisable(!onlyDocs);                     // UC02 7.8: only documents can be downloaded
        delete.setDisable(sel.isEmpty() || projectBox.getValue() != null && projectBox.getValue().isArchived());
        download.setText(sel.isEmpty() ? "Stáhnout" : "Stáhnout (" + sel.size() + ")");
        delete.setText(sel.isEmpty() ? "Smazat" : "Smazat (" + sel.size() + ")");
    }

    // ------------------------------------------------------------------ actions

    private void createNode(String defaultName, java.util.function.Function<String, ? extends ProjectNode> create) {
        if (activeFolder() == null) return;
        Optional<String> name = Ui.prompter.text(defaultName, "Název:", defaultName);
        if (name.isEmpty()) return;
        try {
            UploadConflictDialog.createWithRename(activeFolder(), name.get(), create).ifPresent(n -> reload());
        } catch (Exception e) {
            Ui.error(e);
        }
    }

    private void upload() {
        if (activeFolder() == null) return;
        Path file = Ui.prompter.openFile(stage).orElse(null);
        if (file == null) return;
        try {
            UploadConflictDialog.createWithRename(activeFolder(), file.getFileName().toString(),
                    name -> ctx.documents.createDocument(activeFolder(), file, name)).ifPresent(n -> reload());
        } catch (Exception e) {
            Ui.error(e);                                     // 7.4: too large file etc.
        }
    }

    private void download() {
        List<Document> docs = table.getSelectionModel().getSelectedItems().stream().map(n -> (Document) n).toList();
        Path dir = Ui.prompter.chooseDirectory(stage).orElse(null);
        if (dir == null) return;                             // 7.10: cancelled, nothing downloaded
        try {
            int count = ctx.documents.downloadDocuments(docs, dir);
            Ui.info("Staženo " + Ui.plural(count, "dokument", "dokumenty", "dokumentů") + " do složky " + dir + ".");
        } catch (Exception e) {
            Ui.error(e);
        }
    }

    /** UC19: confirmation, then all or nothing; the service gives the reason when it refuses. */
    private void delete() {
        List<ProjectNode> sel = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (sel.isEmpty() || !Ui.prompter.confirmDelete(sel)) return;
        try {
            ctx.documents.deleteNodes(sel);
        } catch (Exception e) {
            Ui.error(e);
        }
        reload();
    }

    MenuEditorWindow openMenu(Menu menu) {
        try {
            MenuEditorWindow editor = new MenuEditorWindow(ctx, user, menu, projectBox.getValue());
            editor.stage().initOwner(stage);
            editor.stage().addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, e -> reload());
            editor.onTreeChanged = this::reload;
            editor.stage().show();
            return editor;
        } catch (Exception e) {
            Ui.error(e);
            return null;
        }
    }

    /** First menu with this name in the active project (used by the screenshot mode). */
    Optional<Menu> findMenu(String name) {
        return find(root, name);
    }

    private static Optional<Menu> find(Folder folder, String name) {
        for (ProjectNode n : folder.getChildren()) {
            if (n instanceof Menu m && m.getName().equals(name)) return Optional.of(m);
            if (n instanceof Folder f) {
                Optional<Menu> m = find(f, name);
                if (m.isPresent()) return m;
            }
        }
        return Optional.empty();
    }
}
