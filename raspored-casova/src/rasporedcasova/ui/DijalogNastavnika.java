package rasporedcasova.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import rasporedcasova.model.Dani;
import rasporedcasova.model.Nastavnik;
import rasporedcasova.model.Zaduzenje;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DijalogNastavnika extends Dialog<Nastavnik> {
    public DijalogNastavnika(List<String> odeljenja, Nastavnik nastavnik) {
        setTitle(nastavnik == null ? "Dodaj nastavnika" : "Izmeni nastavnika");
        setResizable(true);

        TextField poljeImena = new TextField(nastavnik == null ? "" : nastavnik.getIme());
        poljeImena.setPrefWidth(280);

        Map<String, CheckBox> dayBoxes = new LinkedHashMap<>();
        HBox daysBox = new HBox(14);
        VBox leftDays = new VBox(4);
        VBox rightDays = new VBox(4);
        int rb = 0;
        for (String dan : Dani.SVI) {
            CheckBox kutija = new CheckBox(Dani.label(dan));
            kutija.setSelected(nastavnik == null || nastavnik.getSlobodniDani().contains(dan));
            dayBoxes.put(dan, kutija);
            if (rb % 2 == 0) {
                leftDays.getChildren().add(kutija);
            } else {
                rightDays.getChildren().add(kutija);
            }
            rb++;
        }
        daysBox.getChildren().addAll(leftDays, rightDays);

        ObservableList<Zaduzenje> dodele = FXCollections.observableArrayList();
        if (nastavnik != null) {
            for (Zaduzenje dodela : nastavnik.getDodele()) {
                dodele.add(dodela.kopija());
            }
        }

        TableView<Zaduzenje> tabela = new TableView<>(dodele);
        tabela.setPrefHeight(220);
        TableColumn<Zaduzenje, String> classCol = new TableColumn<>("Odeljenje");
        classCol.setCellValueFactory(new PropertyValueFactory<>("odeljenje"));
        classCol.setPrefWidth(110);
        TableColumn<Zaduzenje, String> kolonaPredmeta = new TableColumn<>("Predmet");
        kolonaPredmeta.setCellValueFactory(new PropertyValueFactory<>("predmet"));
        kolonaPredmeta.setPrefWidth(180);
        TableColumn<Zaduzenje, Integer> kolonaFonda = new TableColumn<>("Nedeljno");
        kolonaFonda.setCellValueFactory(new PropertyValueFactory<>("nedeljno"));
        kolonaFonda.setPrefWidth(90);
        TableColumn<Zaduzenje, Integer> kolonaMax = new TableColumn<>("Max/dan");
        kolonaMax.setCellValueFactory(new PropertyValueFactory<>("maxDnevno"));
        kolonaMax.setPrefWidth(80);
        tabela.getColumns().add(classCol);
        tabela.getColumns().add(kolonaPredmeta);
        tabela.getColumns().add(kolonaFonda);
        tabela.getColumns().add(kolonaMax);
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabela.setOnMouseClicked(dogadjaj -> {
            if (dogadjaj.getClickCount() == 2) {
                izmeniDodelu(odeljenja, tabela, dodele);
            }
        });

        Button add = new Button("Dodaj");
        Button izmena = new Button("Izmeni");
        Button delete = new Button("Obriši");
        Tema.oznaci(add, "primary");
        Tema.oznaci(izmena, "secondary");
        Tema.oznaci(delete, "danger");
        add.setMaxWidth(Double.MAX_VALUE);
        izmena.setMaxWidth(Double.MAX_VALUE);
        delete.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(dogadjaj -> {
            DijalogZaduzenja dijalog = new DijalogZaduzenja(odeljenja, null);
            dijalog.showAndWait().ifPresent(dodela -> {
                dodele.add(dodela);
            });
        });
        izmena.setOnAction(dogadjaj -> izmeniDodelu(odeljenja, tabela, dodele));
        delete.setOnAction(dogadjaj -> {
            Zaduzenje izabrano = tabela.getSelectionModel().getSelectedItem();
            if (izabrano != null) {
                dodele.remove(izabrano);
            }
        });
        VBox buttons = new VBox(6, add, izmena, delete);
        HBox assignmentRow = new HBox(8, tabela, buttons);
        HBox.setHgrow(tabela, Priority.ALWAYS);

        GridPane mreza = new GridPane();
        mreza.setHgap(8);
        mreza.setVgap(10);
        mreza.setPadding(new Insets(12));
        mreza.add(new Label("Ime nastavnika"), 0, 0);
        mreza.add(poljeImena, 1, 0);
        mreza.add(new Label("Dostupni dani"), 0, 1);
        mreza.add(daysBox, 1, 1);
        mreza.add(new Label("Predmeti i odeljenja"), 0, 2);
        mreza.add(assignmentRow, 1, 2);
        getDialogPane().setContent(mreza);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setPrefWidth(640);
        Tema.apply(this);

        Button ok = (Button) getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, dogadjaj -> {
            String ime = poljeImena.getText() == null ? "" : poljeImena.getText().strip();
            if (ime.isEmpty()) {
                Poruke.greska("Nedostaje nastavnik", "Unesite ime nastavnika.");
                dogadjaj.consume();
                return;
            }
            boolean anyDay = dayBoxes.values().stream().anyMatch(CheckBox::isSelected);
            if (!anyDay) {
                Poruke.greska("Nedostaje dostupnost", "Izaberite bar jedan dostupan dan.");
                dogadjaj.consume();
            }
        });

        setResultConverter(dugme -> {
            if (dugme != ButtonType.OK) {
                return null;
            }
            Set<String> dani = new LinkedHashSet<>();
            for (Map.Entry<String, CheckBox> entry : dayBoxes.entrySet()) {
                if (entry.getValue().isSelected()) {
                    dani.add(entry.getKey());
                }
            }
            return new Nastavnik(poljeImena.getText().strip(), dani, new ArrayList<>(dodele));
        });
    }

    private void izmeniDodelu(
            List<String> odeljenja,
            TableView<Zaduzenje> tabela,
            ObservableList<Zaduzenje> dodele) {
        Zaduzenje izabrano = tabela.getSelectionModel().getSelectedItem();
        if (izabrano == null) {
            return;
        }
        int rb = dodele.indexOf(izabrano);
        DijalogZaduzenja dijalog = new DijalogZaduzenja(odeljenja, izabrano);
        dijalog.showAndWait().ifPresent(updated -> dodele.set(rb, updated));
    }
}
