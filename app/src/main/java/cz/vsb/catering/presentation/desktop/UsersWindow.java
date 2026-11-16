package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.service.UserService;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** UC16 (in the admin mode of UC17): list of users, a form for a new user and for editing the selected one. */
class UsersWindow {
    private final UserService users;
    private final User admin;
    private final Stage stage = new Stage();
    final TableView<User> table = new TableView<>();
    private final Label formTitle = Ui.label("", "h2");
    final TextField username = new TextField(), fullName = new TextField(), email = new TextField();
    final PasswordField password = new PasswordField();
    private final Label passwordLabel = new Label();
    final Map<Role, CheckBox> roles = new EnumMap<>(Role.class);
    final CheckBox active = new CheckBox("Účet je aktivní (neaktivní uživatel se nepřihlásí)");
    private final Ui.FieldErrors errors = new Ui.FieldErrors();
    final Button save = new Button("Uložit");
    private User editing;                                              // null = new user

    UsersWindow(UserService users, User admin) {
        this.users = users;
        this.admin = admin;
        Button exit = new Button("Ukončit režim administrátora");
        exit.setOnAction(e -> stage.hide());
        HBox adminBar = new HBox(new Label("Administrátorský režim"), Ui.spacer(), exit);
        adminBar.getStyleClass().add("admin-bar");

        table.setPlaceholder(new Label("Žádní uživatelé."));
        table.getColumns().add(col("Uživatel", 200, u -> u.getFullName() + " (" + u.getUsername() + ")"));
        table.getColumns().add(col("E-mail", 220, User::getEmail));
        table.getColumns().add(col("Role", 220, u -> u.getRoles().stream().map(Role::label).collect(Collectors.joining(", "))));
        table.getColumns().add(col("Stav", 90, u -> u.isActive() ? "Aktivní" : "Neaktivní"));
        table.getSelectionModel().selectedItemProperty().addListener((o, a, u) -> { if (u != null) fill(u); });
        VBox.setVgrow(table, Priority.ALWAYS);
        Button newUser = new Button("+ Nový uživatel");
        newUser.setOnAction(e -> { table.getSelectionModel().clearSelection(); fill(null); });
        VBox left = new VBox(10, new HBox(Ui.label("Uživatelé a oprávnění", "h1"), Ui.spacer(), newUser), table);
        left.getStyleClass().add("content");

        VBox roleBox = new VBox(4);
        for (Role r : new Role[]{Role.WORKER, Role.MANAGER, Role.ADMIN}) {
            roles.put(r, new CheckBox(r.label()));
            roleBox.getChildren().add(roles.get(r));
        }
        Button cancel = new Button("Zrušit");
        save.getStyleClass().add("primary");
        save.setOnAction(e -> save());
        cancel.setOnAction(e -> fill(editing));
        VBox form = new VBox(6, formTitle, new Label("Uživatelské jméno"), errors.add("username", username),
                new Label("Celé jméno"), errors.add("fullName", fullName), new Label("E-mail"), errors.add("email", email),
                new Label("Role"), errors.add("roles", roleBox),
                passwordLabel, errors.add("password", password), active, new HBox(8, Ui.spacer(), cancel, save));
        form.getStyleClass().add("card");
        form.setPrefWidth(340);
        VBox right = new VBox(form);
        right.getStyleClass().add("content");

        stage.setTitle("Catering IS · Administrace");
        stage.setScene(Ui.scene(new BorderPane(left, adminBar, right, null, null), 1150, 640));
        reload(null);
        fill(null);
    }

    Stage stage() { return stage; }

    private static TableColumn<User, String> col(String title, double width, Function<User, String> value) {
        TableColumn<User, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new SimpleStringProperty(value.apply(d.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    /** UC16 step 2 and 8. */
    private void reload(Long selectId) {
        try {
            table.getItems().setAll(users.findAllUsers(admin));
        } catch (Exception e) {
            Ui.error(e);
        }
        table.getItems().stream().filter(u -> u.getId().equals(selectId)).findFirst()
                .ifPresent(table.getSelectionModel()::select);
    }

    /** Puts the original values into the form (also used to revert after a refused change, 7.3). */
    private void fill(User u) {
        editing = u;
        errors.clear();
        formTitle.setText(u == null ? "Nový uživatel" : "Upravit uživatele");
        username.setText(u == null ? "" : u.getUsername());
        fullName.setText(u == null ? "" : u.getFullName());
        email.setText(u == null ? "" : u.getEmail());
        for (TextField f : new TextField[]{username, fullName, email}) f.setDisable(u != null);
        password.clear();
        password.setPromptText(u == null ? "" : "Ponechte prázdné, pokud se nemění");
        passwordLabel.setText(u == null ? "Počáteční heslo (nejméně 8 znaků)" : "Nastavit nové heslo (nejméně 8 znaků)");
        roles.forEach((r, box) -> box.setSelected(u == null ? r == Role.WORKER : u.hasRole(r)));
        active.setSelected(u == null || u.isActive());
        active.setDisable(u == null);
    }

    private Set<Role> selectedRoles() {
        Set<Role> set = EnumSet.noneOf(Role.class);
        roles.forEach((r, box) -> { if (box.isSelected()) set.add(r); });
        return set;
    }

    /** UC16 steps 3-7 with 4.1-4.3 and 7.1-7.3. */
    private void save() {
        boolean isNew = editing == null;
        try {
            User saved = isNew
                    ? users.createUser(admin, username.getText(), fullName.getText(), email.getText(), password.getText(), selectedRoles())
                    : users.updateUser(admin, editing.getId(), selectedRoles(), active.isSelected(), password.getText());
            reload(saved.getId());
            Ui.info(isNew ? "Uživatel byl uložen." : "Změny byly uloženy.");
        } catch (ValidationException e) {
            boolean lastAdmin = editing != null && e.getFieldErrors().containsKey("roles") && !selectedRoles().isEmpty();
            if (lastAdmin) {
                Ui.warn(e.getMessage());
                fill(editing);                                         // 7.3 original values back
            } else if (!errors.show(e)) Ui.error(e);
        } catch (Exception e) {
            Ui.error(e);
        }
    }
}
