package rasporedcasova.ui;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Tema {
    private Tema() {
    }

    public static String stil() {
        URL resource = Tema.class.getResource("app.css");
        if (resource != null) {
            return resource.toExternalForm();
        }
        Path[] putanje = {
                Path.of("src", "rasporedcasova", "ui", "app.css"),
                Path.of("out", "rasporedcasova", "ui", "app.css")
        };
        for (Path putanja : putanje) {
            if (Files.isRegularFile(putanja)) {
                return putanja.toAbsolutePath().toUri().toString();
            }
        }
        return null;
    }

    public static void apply(Scene scena) {
        String css = stil();
        if (css != null && !scena.getStylesheets().contains(css)) {
            scena.getStylesheets().add(css);
        }
    }

    public static void apply(DialogPane panel) {
        String css = stil();
        if (css != null && !panel.getStylesheets().contains(css)) {
            panel.getStylesheets().add(css);
        }
        if (!panel.getStyleClass().contains("app-dialog")) {
            panel.getStyleClass().add("app-dialog");
        }
    }

    public static void apply(Dialog<?> dijalog) {
        apply(dijalog.getDialogPane());
        Button ok = (Button) dijalog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK);
        if (ok != null) {
            oznaci(ok, "primary");
            ok.setText("U redu");
        }
        Button cancel = (Button) dijalog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.CANCEL);
        if (cancel != null) {
            cancel.setText("Otkaži");
        }
    }

    public static void oznaci(Button dugme, String styleClass) {
        if (dugme != null && !dugme.getStyleClass().contains(styleClass)) {
            dugme.getStyleClass().add(styleClass);
        }
    }
}
