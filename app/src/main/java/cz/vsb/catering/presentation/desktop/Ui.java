package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.ConflictException;
import cz.vsb.catering.business.domain.NodeType;
import cz.vsb.catering.business.domain.ValidationException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javax.imageio.ImageIO;

/** Shared helpers of the desktop UI: alerts, error handling, Czech formatting, icons, screenshots. */
final class Ui {
    static final String CSS = Ui.class.getResource("app.css").toExternalForm();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. M. yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d. M. yyyy, H:mm");

    /** Seam for all modal dialogs; tests replace it. */
    static Prompter prompter = new Prompter.Dialogs();

    private Ui() {}

    static String date(LocalDate d) { return d == null ? "" : DATE.format(d); }

    static String date(LocalDateTime d) { return d == null ? "" : DATE.format(d); }

    static String dateTime(LocalDateTime d) { return d == null ? "" : DATE_TIME.format(d); }

    /** Czech plural: 1 položka, 2-4 položky, 5+ položek. */
    static String plural(long n, String one, String few, String many) {
        return n + " " + (n == 1 ? one : n >= 2 && n <= 4 ? few : many);
    }

    static Scene scene(javafx.scene.Parent root, double w, double h) {
        Scene scene = new Scene(root, w, h);
        scene.getStylesheets().add(CSS);
        return scene;
    }

    static void style(DialogPane pane) { pane.getStylesheets().add(CSS); }

    static void info(String message) { alert(Alert.AlertType.INFORMATION, "Informace", message); }

    static void warn(String message) { alert(Alert.AlertType.WARNING, "Upozornění", message); }

    static boolean confirm(String message) { return prompter.confirm(message); }

    static final String TECHNICAL_ERROR = "Operaci se nepodařilo provést. Zkontrolujte připojení k databázi a zkuste to znovu.";

    /** Business messages (conflict, validation) are shown as they are, anything else as a fixed text. */
    static String userMessage(Exception e) {
        if (e instanceof ConflictException || e instanceof ValidationException) return e.getMessage();
        e.printStackTrace();
        return TECHNICAL_ERROR;
    }

    /** Shows any exception from a service call as a Czech dialog. */
    static void error(Exception e) {
        if (e instanceof ConflictException) alert(Alert.AlertType.WARNING, "Konflikt", e.getMessage());
        else if (e instanceof ValidationException) alert(Alert.AlertType.WARNING, "Neplatné údaje", e.getMessage());
        else alert(Alert.AlertType.ERROR, "Chyba", userMessage(e));
    }

    private static void alert(Alert.AlertType type, String title, String message) {
        prompter.message(type, title, message);
    }

    static Label label(String text, String... styleClasses) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        return l;
    }

    static Region spacer() {
        Region r = new Region();
        javafx.scene.layout.HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    static final String TRASH = "M3 4h10v1H3zM6 2h4v2H6zM4 6h8l-1 9H5z";

    /** Small vector icon, so no image files or icon fonts are needed. */
    static SVGPath icon(String path) {
        SVGPath p = new SVGPath();
        p.setContent(path);
        p.getStyleClass().add("icon");
        return p;
    }

    static SVGPath icon(NodeType type) {
        return icon(switch (type) {
            case FOLDER -> "M1 3h5l2 2h7v9H1z";
            case MENU -> "M2 3h12v2H2zM2 7h12v2H2zM2 11h12v2H2z";
            case DOCUMENT -> "M3 1h7l3 3v11H3zM10 1v3h3";
        });
    }

    /** Field errors of one form: each field gets a red border and a message label below it. */
    static final class FieldErrors {
        private final Map<String, Region> fields = new LinkedHashMap<>();
        private final Map<String, Label> labels = new LinkedHashMap<>();

        /** Registers the field and returns it wrapped together with its (hidden) error label. */
        VBox add(String key, Region field) {
            Label error = label("", "error-text");
            error.setManaged(false);
            error.setVisible(false);
            fields.put(key, field);
            labels.put(key, error);
            return new VBox(2, field, error);
        }

        void clear() {
            fields.values().forEach(f -> f.getStyleClass().remove("field-error"));
            labels.values().forEach(l -> { l.setText(""); l.setVisible(false); l.setManaged(false); });
        }

        /** Highlights the fields named in the exception; returns false when none of them is in this form. */
        boolean show(ValidationException e) {
            clear();
            boolean any = false;
            for (var entry : e.getFieldErrors().entrySet()) {
                Region field = fields.get(entry.getKey());
                if (field == null) continue;
                field.getStyleClass().add("field-error");
                Label l = labels.get(entry.getKey());
                l.setText(entry.getValue());
                l.setVisible(true);
                l.setManaged(true);
                any = true;
            }
            return any;
        }
    }

    /** Writes a snapshot of the scene as PNG (dev-only screenshots, see DesktopApp). */
    static void savePng(Node root, Path file) {
        try {
            Files.createDirectories(file.getParent());
            ImageIO.write(toBufferedImage(root.getScene().snapshot(null)), "png", file.toFile());
            System.out.println("Screenshot: " + file);
        } catch (IOException e) {
            System.err.println("Screenshot failed: " + e.getMessage());
        }
    }

    static BufferedImage toBufferedImage(Image image) {
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) out.setRGB(x, y, reader.getArgb(x, y));
        return out;
    }
}
