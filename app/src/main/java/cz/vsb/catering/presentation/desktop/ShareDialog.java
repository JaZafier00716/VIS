package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.ShareLink;
import cz.vsb.catering.business.service.SharingService;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** UC11 step 3: the new share link with a copy button, its validity and a QR code. */
class ShareDialog {
    final String url;
    final TextField urlField;
    final ImageView qr;
    final Label validity;
    private final Dialog<Void> dialog = new Dialog<>();

    ShareDialog(SharingService sharing, ShareLink link, String menuName) {
        this.url = sharing.shareUrl(link);
        dialog.setTitle("Sdílet se zákazníkem");
        dialog.setHeaderText("Nabídka je sdílena se zákazníkem");
        Ui.style(dialog.getDialogPane());
        Label intro = new Label("Revize " + link.getRevisionNo() + " menu „" + menuName
                + "“ je nyní uzamčena (stav SDÍLENÁ). Pošlete zákazníkovi odkaz nebo mu ukažte QR kód.");
        intro.setWrapText(true);
        urlField = new TextField(url);
        urlField.setEditable(false);
        HBox.setHgrow(urlField, Priority.ALWAYS);
        Button copy = new Button("Kopírovat");
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(url);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
            copy.setText("Zkopírováno");
        });
        qr = qrOrEmpty(url);
        long days = ShareLink.VALIDITY.toDays();
        validity = new Label("Platnost " + days + " dní, do " + Ui.date(link.getValidUntil()));
        VBox facts = new VBox(8, validity,
                new Label("Revize " + link.getRevisionNo() + " je jen pro čtení"));
        Label info = Ui.label("Předchozí odkazy na toto menu byly zneplatněny. Zákazník na nich uvidí odkaz na tuto nabídku.", "info");
        info.setWrapText(true);
        info.setMaxWidth(Double.MAX_VALUE);
        dialog.getDialogPane().setContent(new VBox(10, intro, Ui.label("Odkaz pro zákazníka", "h2"),
                new HBox(8, urlField, copy), new HBox(16, qr, facts), info));
        dialog.getDialogPane().setPrefWidth(560);
        ButtonType done = new ButtonType("Hotovo", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(done);
        dialog.getDialogPane().lookupButton(done).getStyleClass().add("primary");
    }

    /** A-39: a QR failure must not block the dialog; the link is still shown (empty image). */
    static ImageView qrOrEmpty(String url) {
        try {
            return new ImageView(QrCode.image(url, 180));
        } catch (Exception e) {
            return new ImageView();
        }
    }

    void show() { dialog.showAndWait(); }
}
