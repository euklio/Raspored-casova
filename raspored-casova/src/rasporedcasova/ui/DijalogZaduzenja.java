package rasporedcasova.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import rasporedcasova.model.Zaduzenje;

import java.util.List;

public class DijalogZaduzenja extends Dialog<Zaduzenje> {
    public DijalogZaduzenja(List<String> odeljenja, Zaduzenje dodela) {
        setTitle(dodela == null ? "Dodaj predmet / odeljenje" : "Izmeni predmet / odeljenje");
        ComboBox<String> kutijaOdeljenja = new ComboBox<>(FXCollections.observableArrayList(odeljenja));
        kutijaOdeljenja.setPrefWidth(280);
        TextField poljePredmeta = new TextField();
        Spinner<Integer> countSpinner = new Spinner<>(1, 40, 1);
        Spinner<Integer> maxDaySpinner = new Spinner<>(1, 2, 1);
        countSpinner.setEditable(true);
        maxDaySpinner.setEditable(true);

        if (dodela != null) {
            kutijaOdeljenja.setValue(dodela.getOdeljenje());
            poljePredmeta.setText(dodela.getPredmet());
            countSpinner.getValueFactory().setValue(dodela.getNedeljno());
            maxDaySpinner.getValueFactory().setValue(dodela.getMaxDnevno());
        } else if (!odeljenja.isEmpty()) {
            kutijaOdeljenja.setValue(odeljenja.get(0));
        }

        GridPane mreza = new GridPane();
        mreza.setHgap(8);
        mreza.setVgap(8);
        mreza.setPadding(new Insets(12));
        mreza.add(new Label("Odeljenje"), 0, 0);
        mreza.add(kutijaOdeljenja, 1, 0);
        mreza.add(new Label("Predmet"), 0, 1);
        mreza.add(poljePredmeta, 1, 1);
        mreza.add(new Label("Časova nedeljno"), 0, 2);
        mreza.add(countSpinner, 1, 2);
        mreza.add(new Label("Najviše istog predmeta dnevno"), 0, 3);
        mreza.add(maxDaySpinner, 1, 3);
        Label mig = new Label("Stavite 2 samo za predmete koji smeju dva puta istog dana.");
        mig.getStyleClass().add("hint");
        mreza.add(mig, 0, 4, 2, 1);
        getDialogPane().setContent(mreza);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Tema.apply(this);

        Button ok = (Button) getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, dogadjaj -> {
            String odeljenje = kutijaOdeljenja.getValue() == null ? "" : kutijaOdeljenja.getValue().strip();
            String predmet = poljePredmeta.getText() == null ? "" : poljePredmeta.getText().strip();
            int nedeljniFond = countSpinner.getValue();
            int maxDnevno = maxDaySpinner.getValue();
            if (odeljenje.isEmpty() || predmet.isEmpty() || nedeljniFond < 1 || (maxDnevno != 1 && maxDnevno != 2)) {
                Poruke.greska(
                        "Neispravno zaduženje",
                        "Unesite odeljenje, predmet, pozitivan nedeljni fond i najviše 1 ili 2 časa dnevno.");
                dogadjaj.consume();
            }
        });

        setResultConverter(dugme -> {
            if (dugme != ButtonType.OK) {
                return null;
            }
            return new Zaduzenje(
                    kutijaOdeljenja.getValue().strip(),
                    poljePredmeta.getText().strip(),
                    countSpinner.getValue(),
                    maxDaySpinner.getValue());
        });
    }
}
