package rasporedcasova.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import rasporedcasova.model.Cas;

import java.util.List;

public class DijalogCasa extends Dialog<DijalogCasa.IzborCasa> {
    public record IzborCasa(String odeljenje, Cas cas) {
    }

    public DijalogCasa(
            List<String> odeljenja, Cas cas, String imeNastavnika, String imeOdeljenja) {
        this(odeljenja, cas, imeNastavnika, imeOdeljenja, List.of());
    }

    public DijalogCasa(
            List<String> odeljenja,
            Cas cas,
            String imeNastavnika,
            String imeOdeljenja,
            List<String> imenaNastavnika) {
        setTitle("Dodaj / izmeni čas");
        ComboBox<String> kutijaOdeljenja = new ComboBox<>(FXCollections.observableArrayList(odeljenja));
        kutijaOdeljenja.setPrefWidth(280);
        ComboBox<String> kutijaNastavnika = new ComboBox<>(FXCollections.observableArrayList(imenaNastavnika));
        kutijaNastavnika.setPrefWidth(280);
        if (imeNastavnika != null && !imeNastavnika.isEmpty()) {
            if (!imenaNastavnika.contains(imeNastavnika)) {
                kutijaNastavnika.getItems().add(0, imeNastavnika);
            }
            kutijaNastavnika.setValue(imeNastavnika);
        } else if (!imenaNastavnika.isEmpty()) {
            kutijaNastavnika.setValue(imenaNastavnika.get(0));
        }
        boolean izaberiNastavnika = imenaNastavnika != null && !imenaNastavnika.isEmpty();
        TextField poljePredmeta = new TextField(cas == null ? "" : cas.getPredmet());
        Spinner<Integer> maxDaySpinner = new Spinner<>(1, 2, cas == null ? 1 : cas.getMaxDnevno());
        maxDaySpinner.setEditable(true);
        CheckBox kutijaKatanca = new CheckBox("Zaključaj ovaj ručni čas da ga generator ne pomeri");
        kutijaKatanca.setSelected(cas == null || cas.jeZakljucan());
        if (imeOdeljenja != null && !imeOdeljenja.isEmpty()) {
            kutijaOdeljenja.setValue(imeOdeljenja);
        } else if (!odeljenja.isEmpty()) {
            kutijaOdeljenja.setValue(odeljenja.get(0));
        }

        GridPane mreza = new GridPane();
        mreza.setHgap(8);
        mreza.setVgap(8);
        mreza.setPadding(new Insets(12));
        int red = 0;
        if (izaberiNastavnika) {
            mreza.add(new Label("Nastavnik"), 0, red);
            mreza.add(kutijaNastavnika, 1, red);
        } else {
            mreza.add(new Label("Nastavnik: " + imeNastavnika), 0, red, 2, 1);
        }
        red++;
        mreza.add(new Label("Odeljenje"), 0, red);
        mreza.add(kutijaOdeljenja, 1, red);
        red++;
        mreza.add(new Label("Predmet"), 0, red);
        mreza.add(poljePredmeta, 1, red);
        red++;
        mreza.add(new Label("Najviše istog predmeta dnevno"), 0, red);
        mreza.add(maxDaySpinner, 1, red);
        red++;
        mreza.add(kutijaKatanca, 0, red, 2, 1);
        getDialogPane().setContent(mreza);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Tema.apply(this);

        Button ok = (Button) getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, dogadjaj -> {
            String odeljenje = kutijaOdeljenja.getValue() == null ? "" : kutijaOdeljenja.getValue().strip();
            String predmet = poljePredmeta.getText() == null ? "" : poljePredmeta.getText().strip();
            String izabranIzListe = izaberiNastavnika
                    ? (kutijaNastavnika.getValue() == null ? "" : kutijaNastavnika.getValue().strip())
                    : (imeNastavnika == null ? "" : imeNastavnika.strip());
            int maxDnevno = maxDaySpinner.getValue();
            if (odeljenje.isEmpty() || predmet.isEmpty() || izabranIzListe.isEmpty()
                    || (maxDnevno != 1 && maxDnevno != 2)) {
                Poruke.greska(
                        "Neispravan čas",
                        "Unesite nastavnika, odeljenje, predmet i najviše 1 ili 2 časa dnevno.");
                dogadjaj.consume();
            }
        });

        setResultConverter(dugme -> {
            if (dugme != ButtonType.OK) {
                return null;
            }
            String izabranIzListe = izaberiNastavnika
                    ? kutijaNastavnika.getValue().strip()
                    : imeNastavnika.strip();
            Cas created = new Cas(
                    poljePredmeta.getText().strip(),
                    izabranIzListe,
                    maxDaySpinner.getValue(),
                    kutijaKatanca.isSelected());
            return new IzborCasa(kutijaOdeljenja.getValue().strip(), created);
        });
    }
}
