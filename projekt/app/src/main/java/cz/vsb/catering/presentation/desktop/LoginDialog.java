package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.service.AuthService;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** UC01 Přihlásit se. The dialog stays open until the login succeeds or is cancelled. */
class LoginDialog {
    private final AuthService auth;

    LoginDialog(AuthService auth) { this.auth = auth; }

    Optional<User> show() {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Catering IS · Přihlášení");
        dialog.setHeaderText("Přihlášení do Catering IS");
        Ui.style(dialog.getDialogPane());
        TextField username = new TextField();
        PasswordField password = new PasswordField();
        Label error = Ui.label("", "error-text");
        dialog.getDialogPane().setContent(new VBox(6, new Label("Uživatelské jméno"), username,
                new Label("Heslo"), password, error));
        ButtonType login = new ButtonType("Přihlásit", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(login, new ButtonType("Zrušit", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.getDialogPane().lookupButton(login).getStyleClass().add("primary");

        User[] result = new User[1];
        dialog.getDialogPane().lookupButton(login).addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                result[0] = auth.loginDesktop(username.getText(), password.getText());
            } catch (Exception e) {
                error.setText(Ui.userMessage(e));
                password.clear();
                ev.consume();                                 // keep the dialog open
            }
        });
        dialog.setResultConverter(b -> b == login ? result[0] : null);
        username.requestFocus();
        return dialog.showAndWait();
    }
}
