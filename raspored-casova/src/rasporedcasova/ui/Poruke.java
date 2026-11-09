package rasporedcasova.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public final class Poruke {
    private Poruke() {
    }

    public static void greska(String naslov, String poruka) {
        show(Alert.AlertType.ERROR, naslov, poruka);
    }

    public static void obavestenje(String naslov, String poruka) {
        show(Alert.AlertType.INFORMATION, naslov, poruka);
    }

    public static void upozorenje(String naslov, String poruka) {
        show(Alert.AlertType.WARNING, naslov, poruka);
    }

    public static boolean pitaj(String naslov, String poruka) {
        Alert alert = build(Alert.AlertType.CONFIRMATION, naslov, poruka);
        var rezultat = alert.showAndWait();
        return rezultat.isPresent() && rezultat.get() == ButtonType.OK;
    }

    private static void show(Alert.AlertType tip, String naslov, String poruka) {
        build(tip, naslov, poruka).showAndWait();
    }

    private static Alert build(Alert.AlertType tip, String naslov, String poruka) {
        Alert alert = new Alert(tip);
        alert.setTitle(naslov);
        alert.setHeaderText(null);
        alert.setContentText(poruka);
        Tema.apply(alert);
        return alert;
    }
}
