package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.ConflictException;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.service.SharingService;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** UC13 Zpracovat komentáře zákazníka for one revision. Closing the window early keeps the rest (7.1). */
class CommentsWindow {
    private final SharingService sharing;
    private final User user;
    private final MenuEditorWindow editor;
    private final long menuId;
    private final int revisionNo;
    private final Stage stage = new Stage();
    final ListView<Comment> list = new ListView<>();
    private final Label author = Ui.label("", "h2"), meta = Ui.label("", "muted"), body = new Label();
    final TextArea note = new TextArea();
    private final Label counter = Ui.label("", "muted"), openCount = new Label();
    final Button refresh = new Button("Obnovit");
    final Button resolve = new Button("Označit jako vyřízený"), editMenu = new Button("Upravit menu podle komentáře");

    CommentsWindow(SharingService sharing, User user, MenuEditorWindow editor, long menuId, int revisionNo, String menuName) {
        this.sharing = sharing;
        this.user = user;
        this.editor = editor;
        this.menuId = menuId;
        this.revisionNo = revisionNo;

        list.setCellFactory(l -> new ListCell<>() {
            @Override protected void updateItem(Comment c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) { setGraphic(null); return; }
                Label state = c.isResolved() ? Ui.label("VYŘÍZENÝ", "badge", "badge-working") : Ui.label("NEVYŘÍZENÝ", "badge", "badge-open");
                VBox box = new VBox(3, new HBox(8, state, Ui.spacer(), Ui.label(Ui.date(c.getCreatedAt()), "muted")),
                        Ui.label(target(c), "h2"), new Label(c.getAuthorName() + ": " + c.getBody()));
                if (c.isResolved())
                    box.getChildren().add(Ui.label("Vyřídil " + (c.getResolvedByName() == null ? "" : c.getResolvedByName())
                            + " " + Ui.date(c.getResolvedAt()) + (c.getResolutionNote() == null ? "" : " – " + c.getResolutionNote()), "muted"));
                box.setMaxWidth(360);
                ((Label) box.getChildren().get(2)).setWrapText(true);
                setGraphic(box);
            }
        });
        list.setPrefWidth(400);
        list.getSelectionModel().selectedItemProperty().addListener((o, a, c) -> showDetail(c));

        note.setTextFormatter(new TextFormatter<String>(ch ->                  // UC13 step 3: at most 200 characters
                ch.getControlNewText().length() <= Comment.MAX_RESOLUTION_NOTE ? ch : null));
        note.textProperty().addListener((o, a, t) -> counter.setText(t.length() + " / " + Comment.MAX_RESOLUTION_NOTE));
        counter.setText("0 / " + Comment.MAX_RESOLUTION_NOTE);
        note.setPrefRowCount(3);
        note.setWrapText(true);
        body.setWrapText(true);
        body.getStyleClass().add("card");
        body.setMaxWidth(Double.MAX_VALUE);
        Label info = Ui.label("Vyžaduje komentář změnu menu? Zvolte „Upravit menu podle komentáře“. Otevře se nejnovější revize "
                + "se zvýrazněnou položkou (u sdílené revize jako pracovní kopie). Po uložení se sem vrátíte s předvyplněnou poznámkou.", "info");
        info.setWrapText(true);
        VBox detail = new VBox(10, author, meta, body, info, Ui.label("Poznámka k vyřízení", "h2"), note,
                new HBox(Ui.label("Poznámku uvidí ostatní pracovníci u vyřízeného komentáře.", "muted"), Ui.spacer(), counter));
        detail.getStyleClass().add("content");

        Button close = new Button("Zavřít");
        close.setOnAction(e -> stage.hide());
        resolve.getStyleClass().add("primary");
        resolve.setOnAction(e -> resolve());
        editMenu.setOnAction(e -> editMenu());
        refresh.setOnAction(e -> {                                             // new customer comments without reopening
            Comment sel = list.getSelectionModel().getSelectedItem();
            reload(sel == null ? null : sel.getId());
        });
        HBox bottom = new HBox(10, openCount, Ui.spacer(), refresh, close, editMenu, resolve);
        bottom.getStyleClass().add("top-bar");
        VBox header = new VBox(4, Ui.label("Komentáře zákazníka", "h1"), Ui.label(menuName + " · rev. " + revisionNo, "muted"));
        header.getStyleClass().add("top-bar");

        stage.setTitle("Catering IS · Komentáře zákazníka");
        stage.setScene(Ui.scene(new BorderPane(detail, header, null, bottom, list), 1100, 620));
        reload(null);
    }

    Stage stage() { return stage; }

    private static String target(Comment c) { return c.isOnItem() ? c.getProductName() : "Celé menu"; }

    /** UC13 step 2 (and 4.3): unresolved first; keeps the selection on the given comment if possible. */
    private void reload(Long selectId) {
        List<Comment> comments = sharing.listComments(menuId, revisionNo);
        list.getItems().setAll(comments);
        long open = comments.stream().filter(c -> !c.isResolved()).count();
        openCount.setText("Nevyřízené komentáře: " + open);
        Comment toSelect = comments.stream().filter(c -> c.getId().equals(selectId)).findFirst()
                .orElse(comments.stream().filter(c -> !c.isResolved()).findFirst().orElse(null));
        if (toSelect != null) list.getSelectionModel().select(toSelect);
        else showDetail(null);
    }

    private void showDetail(Comment c) {
        note.clear();
        author.setText(c == null ? "Vyberte komentář" : c.getAuthorName());
        meta.setText(c == null ? "" : Ui.dateTime(c.getCreatedAt()) + " · " + (c.isOnItem() ? "k položce " + c.getProductName() : "k celému menu"));
        body.setText(c == null ? "" : c.getBody());
        boolean open = c != null && !c.isResolved() && !editor.readOnly;     // UC03: archived = read-only
        resolve.setDisable(!open);
        editMenu.setDisable(!open);
        note.setDisable(!open);
        if (c != null && c.isResolved() && c.getResolutionNote() != null) note.setText(c.getResolutionNote());
    }

    /** UC13 steps 3-7 and 4.1-4.5. */
    private void resolve() {
        Comment c = list.getSelectionModel().getSelectedItem();
        if (c == null) return;
        try {
            sharing.resolveComment(c.getId(), note.getText(), user);
        } catch (ConflictException e) {
            Ui.error(e);                                                      // 4.2 who and when
        } catch (Exception e) {
            Ui.error(e);
            return;
        }
        reload(null);
        editor.refreshCommentCount();                                         // step 6
        if (list.getItems().stream().allMatch(Comment::isResolved)) Ui.info("Všechny komentáře jsou vyřízeny.");
    }

    /** UC13 3.1-3.5: edit in the editor; after the save the note is pre-filled with the revision number. */
    private void editMenu() {
        Comment c = list.getSelectionModel().getSelectedItem();
        if (c == null) return;
        Long id = c.getId();
        editor.editForComment(c.getProductId(), revisionNo -> {
            reload(id);
            note.setText("Upraveno v revizi č. " + revisionNo);
            stage.toFront();
        });
    }
}
