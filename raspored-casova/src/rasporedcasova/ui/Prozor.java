package rasporedcasova.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import rasporedcasova.motor.NemaResenja;
import rasporedcasova.motor.Ucitavanje;
import rasporedcasova.motor.Motor;
import rasporedcasova.motor.Stanje;
import rasporedcasova.json.Json;
import rasporedcasova.model.Dani;
import rasporedcasova.model.Cas;
import rasporedcasova.model.Pravila;
import rasporedcasova.model.Nastavnik;
import rasporedcasova.model.Zaduzenje;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class Prozor extends Application {
    private final Stanje stanje = new Stanje();
    private final Motor motor = new Motor(stanje);

    private Label natpisStatusa;
    private ListView<String> listaOdeljenja;
    private static final String PRIKAZ_NASTAVNIK = "Nastavnik";
    private static final String PRIKAZ_ODELJENJE = "Odeljenje";
    private static final String PRIKAZ_SVA = "Sva odeljenja";

    private TableView<RedNastavnika> tabelaNastavnika;
    private ComboBox<String> kutijaPrikaza;
    private ComboBox<String> kutijaNastavnika;
    private ComboBox<String> kutijaOdeljenja;
    private Label natpisNastavnika;
    private Label natpisOdeljenja;
    private Spinner<Integer> brojacOdeljenja;
    private Spinner<Integer> brojacNastavnika;
    private Spinner<Integer> brojacTermina;
    private ComboBox<String> kutijaTipa;
    private static final String PRIORITET_SREDNJE = "Uravnotežen";
    private static final String PRIORITET_UCENIK = "Dobar za decu";
    private static final String PRIORITET_NASTAVNIK = "Dobar za nastavnike";
    private static final String DVE_SMENE = "Dve smene";
    private static final String MOD_JEDNA_SMENA = "Jedna smena";

    private ComboBox<String> kutijaPrioriteta;
    private ComboBox<String> kutijaSmene;
    private VBox karticaIshoda;
    private VBox listaIshoda;
    private Label uvodIshoda;
    private TableView<RedCasa> tabelaPrepodne;
    private TableView<RedCasa> tabelaPopodne;
    private TableView<RedCasa> tabelaOdeljenja;
    private Label naslovTabele;
    private Label naslovPrepodne;
    private HBox tabeleNastavnika;
    private VBox karticaPrepodne;
    private VBox karticaPopodne;
    private VBox panelOdeljenja;
    private VBox kutijaSvih;
    private ScrollPane skrolSvih;
    private TextArea tekstUvoza;
    private String izabranNastavnik = "";
    private String izabranoOdeljenje = "";
    private Celija celijaMeni;
    private boolean generating;

    @Override
    public void start(Stage bina) {
        bina.setTitle("Raspored časova");
        bina.setMinWidth(1280);
        bina.setMinHeight(800);

        Label kicker = new Label("ŠKOLSKI RASPORED");
        kicker.getStyleClass().add("app-kicker");
        Label naslov = new Label("Raspored časova");
        naslov.getStyleClass().add("app-title");
        VBox brandText = new VBox(2, kicker, naslov);
        Region oznaci = new Region();
        oznaci.getStyleClass().add("brand-mark");
        HBox brand = new HBox(12, oznaci, brandText);
        brand.setAlignment(Pos.CENTER_LEFT);

        natpisStatusa = new Label("Kreirajte odeljenja i nastavnike da biste počeli.");
        natpisStatusa.getStyleClass().add("status-chip");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox headerInner = new HBox(16, brand, spacer, natpisStatusa);
        headerInner.getStyleClass().add("app-header-inner");
        headerInner.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(headerInner);
        header.getStyleClass().add("app-header");

        TabPane tabovi = new TabPane();
        tabovi.getStyleClass().add("app-tabs");
        tabovi.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab setupTab = new Tab("1. Podešavanje", tabPodesavanja());
        Tab importTab = new Tab("2. JSON uvoz", tabUvoz(bina));
        Tab scheduleTab = new Tab("3. Raspored", tabRaspored());
        tabovi.getTabs().addAll(setupTab, importTab, scheduleTab);

        BorderPane koren = new BorderPane();
        koren.getStyleClass().add("app-root");
        koren.setTop(header);
        koren.setCenter(tabovi);
        BorderPane.setMargin(tabovi, new Insets(8, 20, 16, 20));

        Scene scena = new Scene(koren, 1280, 800);
        Tema.apply(scena);
        bina.setScene(scena);
        bina.setMaximized(true);
        bina.show();
    }

    private VBox kartica(javafx.scene.Node... children) {
        VBox kutija = new VBox(10, children);
        kutija.getStyleClass().add("card");
        return kutija;
    }

    private Label mig(String tekst) {
        Label label = new Label(tekst);
        label.getStyleClass().add("hint");
        label.setWrapText(true);
        return label;
    }

    private Label naslovSekcije(String tekst) {
        Label label = new Label(tekst);
        label.getStyleClass().add("section-title");
        return label;
    }

    private HBox tabPodesavanja() {
        brojacOdeljenja = new Spinner<>(1, 80, 3);
        brojacOdeljenja.setPrefWidth(110);
        Button napraviRazrede = new Button("Napravi odeljenja");
        Tema.oznaci(napraviRazrede, "primary");
        napraviRazrede.setOnAction(dogadjaj -> napraviOdeljenja());
        HBox classControls = new HBox(8, new Label("Broj odeljenja"), brojacOdeljenja, napraviRazrede);
        classControls.setAlignment(Pos.CENTER_LEFT);

        listaOdeljenja = new ListView<>();
        VBox.setVgrow(listaOdeljenja, Priority.ALWAYS);
        Button preimenuj = new Button("Preimenuj");
        Button obrisiRazred = new Button("Obriši");
        Tema.oznaci(preimenuj, "secondary");
        Tema.oznaci(obrisiRazred, "danger");
        preimenuj.setOnAction(dogadjaj -> preimenujOdeljenje());
        obrisiRazred.setOnAction(dogadjaj -> obrisiOdeljenje());
        HBox classButtons = new HBox(6, preimenuj, obrisiRazred);

        kutijaTipa = new ComboBox<>(FXCollections.observableArrayList(
                Pravila.nazivTipa(""),
                Pravila.nazivTipa("1-4"),
                Pravila.nazivTipa("5-8"),
                Pravila.nazivTipa("srednja"),
                Pravila.nazivTipa("fakultet")));
        kutijaTipa.setValue(Pravila.nazivTipa(""));
        kutijaTipa.setPrefWidth(280);
        kutijaTipa.setOnAction(dogadjaj -> tipIzKutije(true));
        HBox schoolTypeRow = new HBox(8, new Label("Tip škole"), kutijaTipa);
        schoolTypeRow.setAlignment(Pos.CENTER_LEFT);

        kutijaSmene = new ComboBox<>(FXCollections.observableArrayList(DVE_SMENE, MOD_JEDNA_SMENA));
        kutijaSmene.setValue(DVE_SMENE);
        kutijaSmene.setPrefWidth(280);
        kutijaSmene.setOnAction(dogadjaj -> smenaIzKutije());
        HBox shiftModeRow = new HBox(8, new Label("Smene"), kutijaSmene);
        shiftModeRow.setAlignment(Pos.CENTER_LEFT);

        listaOdeljenja.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String stavka, boolean empty) {
                super.updateItem(stavka, empty);
                if (empty || stavka == null) {
                    setText(null);
                    return;
                }
                setText(stavka + sufiksSmene(stavka));
            }
        });

        VBox levo = kartica(
                naslovSekcije("Odeljenja"),
                mig("Prvo napravite odeljenja, zatim ih dodelite nastavnicima. Isti primer radi i sa dve smene i sa jednom smenom za celu školu."),
                schoolTypeRow,
                shiftModeRow,
                classControls,
                listaOdeljenja,
                classButtons);
        HBox.setHgrow(levo, Priority.ALWAYS);

        brojacNastavnika = new Spinner<>(1, 100, 5);
        brojacNastavnika.setPrefWidth(110);
        Button napraviNastavnike = new Button("Napravi prazne nastavnike");
        napraviNastavnike.setOnAction(dogadjaj -> napraviNastavnike());
        HBox teacherControls = new HBox(8, new Label("Broj nastavnika"), brojacNastavnika, napraviNastavnike);
        teacherControls.setAlignment(Pos.CENTER_LEFT);

        tabelaNastavnika = new TableView<>();
        kolonaNastavnika("Nastavnik", "ime", 150);
        kolonaNastavnika("Predmeti", "predmeti", 210);
        kolonaNastavnika("Odeljenja", "razredi", 170);
        TableColumn<RedNastavnika, Integer> kolonaNedeljno = new TableColumn<>("Nedeljno");
        kolonaNedeljno.setCellValueFactory(new PropertyValueFactory<>("nedeljnoFond"));
        kolonaNedeljno.setPrefWidth(75);
        tabelaNastavnika.getColumns().add(kolonaNedeljno);
        kolonaNastavnika("Dostupni dani", "dani", 160);
        tabelaNastavnika.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabelaNastavnika.setOnMouseClicked(dogadjaj -> {
            if (dogadjaj.getClickCount() == 2) {
                izmeniNastavnika();
            }
        });
        VBox.setVgrow(tabelaNastavnika, Priority.ALWAYS);

        Button add = new Button("Dodaj");
        Button izmena = new Button("Izmeni");
        Button obrisiNastavnika = new Button("Obriši");
        Tema.oznaci(add, "primary");
        Tema.oznaci(izmena, "secondary");
        Tema.oznaci(obrisiNastavnika, "danger");
        add.setOnAction(dogadjaj -> dodajNastavnika());
        izmena.setOnAction(dogadjaj -> izmeniNastavnika());
        obrisiNastavnika.setOnAction(dogadjaj -> obrisiNastavnika());
        HBox teacherButtons = new HBox(6, add, izmena, obrisiNastavnika);

        VBox desno = kartica(
                naslovSekcije("Nastavnici, predmeti, odeljenja"),
                mig("Svaki nastavnik ima dostupne dane, predmete, odeljenja i fond časova nedeljno."),
                teacherControls,
                tabelaNastavnika,
                teacherButtons);
        HBox.setHgrow(desno, Priority.ALWAYS);

        HBox koren = new HBox(16, levo, desno);
        koren.setPadding(new Insets(14));
        return koren;
    }

    private void kolonaNastavnika(String naslov, String property, int width) {
        TableColumn<RedNastavnika, String> kolona = new TableColumn<>(naslov);
        kolona.setCellValueFactory(new PropertyValueFactory<>(property));
        kolona.setPrefWidth(width);
        tabelaNastavnika.getColumns().add(kolona);
    }

    private VBox tabUvoz(Stage bina) {
        Button dugmeUvoz = new Button("Uvezi JSON fajl");
        Tema.oznaci(dugmeUvoz, "secondary");
        dugmeUvoz.setOnAction(dogadjaj -> uveziJson(bina));
        Button dugmePrimeni = new Button("Napravi raspored");
        Tema.oznaci(dugmePrimeni, "primary");
        dugmePrimeni.setOnAction(dogadjaj -> uveziNalep());
        HBox actions = new HBox(8, dugmePrimeni, dugmeUvoz);
        BorderPane vrh = new BorderPane();
        vrh.setLeft(naslovSekcije("Zalepite JSON, pa napravite raspored"));
        vrh.setRight(actions);

        Button elementary = new Button("1–4");
        elementary.setOnAction(dogadjaj -> ucitajPrimer("os_1_4.json"));
        Button elementaryEighteen = new Button("1–4 (18 odeljenja)");
        elementaryEighteen.setOnAction(dogadjaj -> ucitajPrimer("os_1_4_18_odeljenja.json"));
        Button os58 = new Button("5–8");
        os58.setOnAction(dogadjaj -> ucitajPrimer("os_5_8.json"));
        Button highSchool = new Button("Srednja škola");
        highSchool.setOnAction(dogadjaj -> ucitajPrimer("srednja.json"));
        Button highSchoolSixteen = new Button("Srednja (16 odeljenja)");
        highSchoolSixteen.setOnAction(dogadjaj -> ucitajPrimer("srednja_16_odeljenja.json"));
        Button highSchoolSeventeen = new Button("Srednja (17 odeljenja)");
        highSchoolSeventeen.setOnAction(dogadjaj -> ucitajPrimer("srednja_17_odeljenja.json"));
        Button os58sixteen = new Button("5–8 (16 odeljenja)");
        os58sixteen.setOnAction(dogadjaj -> ucitajPrimer("os_5_8_16_odeljenja.json"));
        Button os58seventeen = new Button("5–8 (17 odeljenja)");
        os58seventeen.setOnAction(dogadjaj -> ucitajPrimer("os_5_8_17_odeljenja.json"));
        Button clear = new Button("Obriši");
        Tema.oznaci(elementary, "chip");
        Tema.oznaci(elementaryEighteen, "chip");
        Tema.oznaci(os58, "chip");
        Tema.oznaci(highSchool, "chip");
        Tema.oznaci(highSchoolSixteen, "chip");
        Tema.oznaci(highSchoolSeventeen, "chip");
        Tema.oznaci(os58sixteen, "chip");
        Tema.oznaci(os58seventeen, "chip");
        Tema.oznaci(clear, "chip");
        Tema.oznaci(clear, "danger");
        clear.setOnAction(dogadjaj -> tekstUvoza.clear());
        FlowPane primeri = new FlowPane(6, 6);
        primeri.setAlignment(Pos.CENTER_LEFT);
        primeri.getChildren().addAll(
                new Label("Učitaj primer:"),
                elementary,
                elementaryEighteen,
                os58,
                os58sixteen,
                os58seventeen,
                highSchool,
                highSchoolSixteen,
                highSchoolSeventeen,
                clear);

        tekstUvoza = new TextArea("Ovde zalepite JSON, pa kliknite Napravi raspored.");
        tekstUvoza.setWrapText(true);
        tekstUvoza.getStyleClass().add("json-editor");
        VBox.setVgrow(tekstUvoza, Priority.ALWAYS);

        VBox kartica = kartica(
                vrh,
                mig("Ctrl+V zamenjuje ceo okvir. Zatim kliknite Napravi raspored. Šabloni 1–4, 5–8 i srednja su prazni. Veliki primeri: 1–4 sa 18 odeljenja, 5–8 sa 16 ili 17 odeljenja i gimnazija (društveni + prirodni smer) sa 16 ili 17 odeljenja."),
                primeri,
                tekstUvoza);
        VBox.setVgrow(kartica, Priority.ALWAYS);
        VBox koren = new VBox(kartica);
        koren.setPadding(new Insets(14));
        return koren;
    }

    private VBox tabRaspored() {
        kutijaPrikaza = new ComboBox<>(FXCollections.observableArrayList(
                PRIKAZ_NASTAVNIK, PRIKAZ_ODELJENJE, PRIKAZ_SVA));
        kutijaPrikaza.setValue(PRIKAZ_NASTAVNIK);
        kutijaPrikaza.setPrefWidth(180);
        kutijaPrikaza.setOnAction(dogadjaj -> osveziMrezu());

        natpisNastavnika = new Label("Nastavnik");
        kutijaNastavnika = new ComboBox<>();
        kutijaNastavnika.setPrefWidth(240);
        kutijaNastavnika.setOnAction(dogadjaj -> {
            izabranNastavnik = kutijaNastavnika.getValue() == null ? "" : kutijaNastavnika.getValue();
            osveziMrezu();
        });
        natpisOdeljenja = new Label("Odeljenje");
        kutijaOdeljenja = new ComboBox<>();
        kutijaOdeljenja.setPrefWidth(180);
        kutijaOdeljenja.setOnAction(dogadjaj -> {
            izabranoOdeljenje = kutijaOdeljenja.getValue() == null ? "" : kutijaOdeljenja.getValue();
            osveziMrezu();
        });
        brojacTermina = new Spinner<>(1, 16, stanje.getTerminaDnevno());
        brojacTermina.setPrefWidth(90);
        brojacTermina.valueProperty().addListener((obs, old, vrednost) -> {
            stanje.setTerminaDnevno(vrednost);
            osveziMrezu();
        });

        kutijaPrioriteta = new ComboBox<>(FXCollections.observableArrayList(
                PRIORITET_SREDNJE, PRIORITET_UCENIK, PRIORITET_NASTAVNIK));
        kutijaPrioriteta.setValue(PRIORITET_NASTAVNIK);
        kutijaPrioriteta.setPrefWidth(200);

        Button napravi = new Button("Napravi raspored");
        Tema.oznaci(napravi, "primary");
        napravi.setOnAction(dogadjaj -> napraviRaspored());
        Button clearUnlocked = new Button("Obriši otključano");
        Tema.oznaci(clearUnlocked, "secondary");
        clearUnlocked.setOnAction(dogadjaj -> obrisiOtkljucane());
        Button clearAll = new Button("Obriši sve");
        Tema.oznaci(clearAll, "danger");
        clearAll.setOnAction(dogadjaj -> obrisiSveCasove());

        HBox toolbar = new HBox(
                8,
                new Label("Prikaz"),
                kutijaPrikaza,
                natpisNastavnika,
                kutijaNastavnika,
                natpisOdeljenja,
                kutijaOdeljenja,
                new Label("Časova dnevno"),
                brojacTermina,
                new Label("Prioritet"),
                kutijaPrioriteta,
                napravi,
                clearUnlocked,
                clearAll);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        VBox toolbarCard = new VBox(10, toolbar);
        toolbarCard.getStyleClass().addAll("card", "toolbar-card");

        listaIshoda = new VBox(8);
        listaIshoda.getStyleClass().add("work-notes-list");
        ScrollPane notesScroll = new ScrollPane(listaIshoda);
        notesScroll.setFitToWidth(true);
        notesScroll.setMaxHeight(280);
        notesScroll.getStyleClass().add("work-notes-scroll");
        Label notesTitle = naslovSekcije("Ishod:");
        uvodIshoda = mig("Raspored je ispravan. Svaka stavka ispod je želja koja nije u potpunosti ispunjena:");
        karticaIshoda = new VBox(10, notesTitle, uvodIshoda, notesScroll);
        karticaIshoda.getStyleClass().addAll("card", "work-notes");
        karticaIshoda.setVisible(false);
        karticaIshoda.setManaged(false);
        VBox.setVgrow(karticaIshoda, Priority.NEVER);

        tabelaPrepodne = tabelaRasporeda("prepodne");
        tabelaPopodne = tabelaRasporeda("popodne");
        VBox.setVgrow(tabelaPrepodne, Priority.ALWAYS);
        VBox.setVgrow(tabelaPopodne, Priority.ALWAYS);

        naslovPrepodne = naslovSekcije("Prepodnevna smena");
        karticaPrepodne = kartica(naslovPrepodne, tabelaPrepodne);
        karticaPopodne = kartica(naslovSekcije("Poslepodnevna smena"), tabelaPopodne);
        VBox.setVgrow(karticaPrepodne, Priority.ALWAYS);
        VBox.setVgrow(karticaPopodne, Priority.ALWAYS);
        tabeleNastavnika = new HBox(12, karticaPrepodne, karticaPopodne);
        HBox.setHgrow(karticaPrepodne, Priority.ALWAYS);
        HBox.setHgrow(karticaPopodne, Priority.ALWAYS);
        VBox.setVgrow(tabeleNastavnika, Priority.ALWAYS);

        naslovTabele = naslovSekcije("Odeljenje");
        tabelaOdeljenja = tabelaRasporeda("odeljenje:");
        VBox.setVgrow(tabelaOdeljenja, Priority.ALWAYS);
        panelOdeljenja = kartica(naslovTabele, tabelaOdeljenja);
        VBox.setVgrow(panelOdeljenja, Priority.ALWAYS);

        kutijaSvih = new VBox(12);
        skrolSvih = new ScrollPane(kutijaSvih);
        skrolSvih.setFitToWidth(true);
        skrolSvih.getStyleClass().add("all-classes-scroll");
        VBox.setVgrow(skrolSvih, Priority.ALWAYS);

        VBox koren = new VBox(10, toolbarCard, tabeleNastavnika, panelOdeljenja, skrolSvih, karticaIshoda);
        koren.setPadding(new Insets(14));
        osveziPrikaz();
        return koren;
    }

    private TableView<RedCasa> tabelaRasporeda(String userData) {
        TableView<RedCasa> tabela = new TableView<>();
        tabela.setUserData(userData);
        tabela.getStyleClass().add("schedule-table");
        tabela.setPlaceholder(new Label("Nema časova u ovom prikazu"));
        tabela.getSelectionModel().setCellSelectionEnabled(true);
        TableColumn<RedCasa, String> kolonaTermina = new TableColumn<>("Čas");
        kolonaTermina.setCellValueFactory(podaci -> podaci.getValue().terminProperty());
        kolonaTermina.setPrefWidth(72);
        kolonaTermina.setCellFactory(kolona -> new TableCell<>() {
            @Override
            protected void updateItem(String stavka, boolean empty) {
                super.updateItem(stavka, empty);
                getStyleClass().remove("period-cell");
                if (empty || stavka == null) {
                    setText(null);
                    return;
                }
                setText(stavka);
                getStyleClass().add("period-cell");
                setAlignment(Pos.CENTER);
            }
        });
        tabela.getColumns().add(kolonaTermina);
        kolonaDana(tabela, Dani.label("Monday"), RedCasa::ponedeljakProperty);
        kolonaDana(tabela, Dani.label("Tuesday"), RedCasa::utorakProperty);
        kolonaDana(tabela, Dani.label("Wednesday"), RedCasa::sredaProperty);
        kolonaDana(tabela, Dani.label("Thursday"), RedCasa::cetvrtakProperty);
        kolonaDana(tabela, Dani.label("Friday"), RedCasa::petakProperty);
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        MenuItem editItem = new MenuItem("Dodaj / izmeni čas");
        MenuItem deleteItem = new MenuItem("Obriši čas ovde");
        MenuItem lockItem = new MenuItem("Zaključaj / otključaj čas");
        ContextMenu menu = new ContextMenu(editItem, deleteItem, lockItem);
        editItem.setOnAction(dogadjaj -> {
            if (celijaMeni != null) {
                izmeniCeliju(celijaMeni);
            }
        });
        deleteItem.setOnAction(dogadjaj -> obrisiCeliju());
        lockItem.setOnAction(dogadjaj -> zakljucajCeliju());

        tabela.setOnMouseClicked(dogadjaj -> {
            Celija celija = celijaOdKlika(tabela);
            if (celija == null) {
                return;
            }
            if (dogadjaj.getButton() == MouseButton.SECONDARY) {
                celijaMeni = celija;
                menu.show(tabela, dogadjaj.getScreenX(), dogadjaj.getScreenY());
            } else {
                menu.hide();
                if (dogadjaj.getClickCount() == 2) {
                    izmeniCeliju(celija);
                }
            }
        });
        return tabela;
    }

    private void kolonaDana(
            TableView<RedCasa> tabela,
            String naslov,
            java.util.function.Function<RedCasa, javafx.beans.property.StringProperty> getter) {
        TableColumn<RedCasa, String> kolona = new TableColumn<>(naslov);
        kolona.setCellValueFactory(podaci -> getter.apply(podaci.getValue()));
        kolona.setPrefWidth(200);
        kolona.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String stavka, boolean empty) {
                super.updateItem(stavka, empty);
                if (empty || stavka == null || stavka.isBlank()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label pilula = new Label(stavka);
                pilula.getStyleClass().add("lesson-pill");
                if (stavka.startsWith("[L]")) {
                    pilula.getStyleClass().add("lesson-pill-locked");
                }
                pilula.setWrapText(true);
                pilula.setMaxWidth(Double.MAX_VALUE);
                setText(null);
                setGraphic(pilula);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });
        tabela.getColumns().add(kolona);
    }

    private void napraviOdeljenja() {
        int broj = Math.max(1, brojacOdeljenja.getValue());
        stanje.getOdeljenja().clear();
        for (int rb = 0; rb < broj; rb++) {
            stanje.getOdeljenja().add("Odeljenje " + (rb + 1));
        }
        stanje.srediRaspored();
        osveziOdeljenja();
        natpisStatusa.setText("Napravljeno je " + broj + " odeljenja.");
    }

    private void osveziOdeljenja() {
        listaOdeljenja.setItems(FXCollections.observableArrayList(stanje.getOdeljenja()));
        osveziIzbor();
        osveziNastavnike();
        osveziMrezu();
    }

    private void preimenujOdeljenje() {
        String staroIme = listaOdeljenja.getSelectionModel().getSelectedItem();
        if (staroIme == null) {
            return;
        }
        TextInputDialog dijalog = new TextInputDialog(staroIme);
        dijalog.setTitle("Preimenuj odeljenje");
        dijalog.setHeaderText(null);
        dijalog.setContentText("Naziv odeljenja:");
        Tema.apply(dijalog);
        Optional<String> rezultat = dijalog.showAndWait();
        if (rezultat.isEmpty()) {
            return;
        }
        String newName = rezultat.get().strip();
        if (newName.isEmpty()) {
            return;
        }
        if (stanje.getOdeljenja().contains(newName) && !newName.equals(staroIme)) {
            Poruke.greska("Duplikat odeljenja", "Odeljenje sa tim imenom već postoji.");
            return;
        }
        stanje.preimenujOdeljenje(staroIme, newName);
        osveziOdeljenja();
    }

    private void obrisiOdeljenje() {
        String odeljenje = listaOdeljenja.getSelectionModel().getSelectedItem();
        if (odeljenje == null) {
            return;
        }
        if (!Poruke.pitaj("Obriši odeljenje", "Obrisati " + odeljenje + " i njegove časove?")) {
            return;
        }
        stanje.obrisiOdeljenje(odeljenje);
        osveziOdeljenja();
    }

    private void napraviNastavnike() {
        int broj = Math.max(1, brojacNastavnika.getValue());
        stanje.getNastavnici().clear();
        for (int rb = 0; rb < broj; rb++) {
            String ime = "Nastavnik " + (rb + 1);
            stanje.getNastavnici().put(ime, new Nastavnik(ime));
        }
        osveziNastavnike();
        natpisStatusa.setText("Napravljeno je " + broj + " praznih nastavnika.");
    }

    private void dodajNastavnika() {
        if (stanje.getOdeljenja().isEmpty()) {
            Poruke.obavestenje("Potrebna odeljenja", "Prvo napravite bar jedno odeljenje.");
            return;
        }
        DijalogNastavnika dijalog = new DijalogNastavnika(stanje.getOdeljenja(), null);
        Optional<Nastavnik> rezultat = dijalog.showAndWait();
        if (rezultat.isEmpty()) {
            return;
        }
        Nastavnik nastavnik = rezultat.get();
        if (stanje.getNastavnici().containsKey(nastavnik.getIme())) {
            Poruke.greska("Duplikat nastavnika", "Nastavnik sa tim imenom već postoji.");
            return;
        }
        stanje.getNastavnici().put(nastavnik.getIme(), nastavnik);
        osveziNastavnike();
    }

    private void izmeniNastavnika() {
        RedNastavnika izabrano = tabelaNastavnika.getSelectionModel().getSelectedItem();
        if (izabrano == null) {
            return;
        }
        String staroIme = izabrano.getIme();
        DijalogNastavnika dijalog = new DijalogNastavnika(stanje.getOdeljenja(), stanje.getNastavnici().get(staroIme));
        Optional<Nastavnik> rezultat = dijalog.showAndWait();
        if (rezultat.isEmpty()) {
            return;
        }
        Nastavnik noviNastavnik = rezultat.get();
        if (!noviNastavnik.getIme().equals(staroIme) && stanje.getNastavnici().containsKey(noviNastavnik.getIme())) {
            Poruke.greska("Duplikat nastavnika", "Nastavnik sa tim imenom već postoji.");
            return;
        }
        stanje.getNastavnici().remove(staroIme);
        stanje.getNastavnici().put(noviNastavnik.getIme(), noviNastavnik);
        stanje.preimenujNastavnikaURasporedu(staroIme, noviNastavnik.getIme());
        osveziNastavnike();
        osveziMrezu();
    }

    private void obrisiNastavnika() {
        RedNastavnika izabrano = tabelaNastavnika.getSelectionModel().getSelectedItem();
        if (izabrano == null) {
            return;
        }
        String ime = izabrano.getIme();
        if (!Poruke.pitaj("Obriši nastavnika", "Obrisati " + ime + "?")) {
            return;
        }
        stanje.getNastavnici().remove(ime);
        stanje.skloniNastavnikaIzRasporeda(ime);
        osveziNastavnike();
        osveziMrezu();
    }

    private void osveziNastavnike() {
        List<RedNastavnika> redovi = new ArrayList<>();
        List<Nastavnik> nastavnici = new ArrayList<>(stanje.getNastavnici().values());
        nastavnici.sort((levo, desno) -> levo.getIme().compareToIgnoreCase(desno.getIme()));
        for (Nastavnik nastavnik : nastavnici) {
            String predmeti = nastavnik.getDodele().stream()
                    .map(Zaduzenje::getPredmet)
                    .collect(Collectors.toCollection(TreeSet::new))
                    .stream()
                    .collect(Collectors.joining(", "));
            String razredi = nastavnik.getDodele().stream()
                    .map(Zaduzenje::getOdeljenje)
                    .collect(Collectors.toCollection(TreeSet::new))
                    .stream()
                    .collect(Collectors.joining(", "));
            int nedeljniFond = nastavnik.getDodele().stream().mapToInt(Zaduzenje::getNedeljno).sum();
            String dani = Dani.SVI.stream()
                    .filter(nastavnik.getSlobodniDani()::contains)
                    .map(Dani::kratko)
                    .collect(Collectors.joining(", "));
            redovi.add(new RedNastavnika(
                    nastavnik.getIme(),
                    predmeti.isEmpty() ? "-" : predmeti,
                    razredi.isEmpty() ? "-" : razredi,
                    nedeljniFond,
                    dani));
        }
        tabelaNastavnika.setItems(FXCollections.observableArrayList(redovi));

        List<String> imena = new ArrayList<>(stanje.getNastavnici().keySet());
        imena.sort(String::compareToIgnoreCase);
        if (kutijaNastavnika != null) {
            var akcija = kutijaNastavnika.getOnAction();
            kutijaNastavnika.setOnAction(null);
            kutijaNastavnika.setItems(FXCollections.observableArrayList(imena));
            if (!imena.isEmpty() && !imena.contains(izabranNastavnik)) {
                izabranNastavnik = imena.get(0);
                kutijaNastavnika.setValue(izabranNastavnik);
            } else if (imena.isEmpty()) {
                izabranNastavnik = "";
                kutijaNastavnika.setValue(null);
            } else {
                kutijaNastavnika.setValue(izabranNastavnik);
            }
            kutijaNastavnika.setOnAction(akcija);
        }
    }

    private void uveziJson(Stage bina) {
        FileChooser birac = new FileChooser();
        birac.setTitle("Uvezi JSON rasporeda");
        birac.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON fajlovi", "*.json"));
        File fajl = birac.showOpenDialog(bina);
        if (fajl == null) {
            return;
        }
        try {
            String sirovo = Files.readString(fajl.toPath(), StandardCharsets.UTF_8);
            tekstUvoza.setText(sirovo);
            primeniJson(sirovo);
        } catch (Exception izuzetak) {
            Poruke.greska("Uvoz nije uspeo", izuzetak.getMessage());
        }
    }

    private void uveziNalep() {
        try {
            primeniJson(tekstUvoza.getText());
            napraviRaspored();
        } catch (Exception izuzetak) {
            Poruke.greska("Uvoz nije uspeo", izuzetak.getMessage());
        }
    }

    private void ucitajPrimer(String filename) {
        ucitajPutanju(nadjiPrimer("tests" + File.separator + filename), filename);
    }

    private void ucitajPutanju(Path putanja, String label) {
        if (putanja == null || !Files.isRegularFile(putanja)) {
            Poruke.greska("Primer nije pronađen", "Nema fajla " + label);
            return;
        }
        try {
            tekstUvoza.setText(Files.readString(putanja, StandardCharsets.UTF_8));
            tekstUvoza.positionCaret(0);
        } catch (Exception izuzetak) {
            Poruke.greska("Primer nije pronađen", izuzetak.getMessage());
        }
    }

    private Path nadjiPrimer(String relative) {
        Path cwd = Path.of(System.getProperty("user.dir")).resolve(relative);
        if (Files.isRegularFile(cwd)) {
            return cwd;
        }
        Path local = Path.of(relative);
        return Files.isRegularFile(local) ? local : null;
    }

    private void primeniJson(String sirovo) throws Exception {
        String ocisceno = sirovo == null ? "" : sirovo.strip();
        if (ocisceno.startsWith("```")) {
            String[] redoviTeksta = ocisceno.split("\\R");
            int start = 1;
            int kraj = redoviTeksta.length;
            if (kraj > 0 && redoviTeksta[kraj - 1].strip().equals("```")) {
                kraj--;
            }
            ocisceno = String.join("\n", java.util.Arrays.copyOfRange(redoviTeksta, start, kraj)).strip();
        }
        Ucitavanje.ucitajPodatke(stanje, Json.kaoMapa(Json.procitaj(ocisceno)));
        brojacTermina.getValueFactory().setValue(stanje.getTerminaDnevno());
        kutijaTipa.setOnAction(null);
        kutijaTipa.setValue(Pravila.nazivTipa(stanje.getTipSkole()));
        kutijaTipa.setOnAction(dogadjaj -> tipIzKutije(true));
        uskladiSmenu();
        osveziOdeljenja();
        osveziNastavnike();
        osveziMrezu();
        natpisStatusa.setText("JSON podešavanje je uvezeno.");
    }

    private void tipIzKutije(boolean updatePeriods) {
        String label = kutijaTipa.getValue();
        String kljuc = "";
        for (String tip : Pravila.TIPOVI_SKOLE) {
            if (Pravila.nazivTipa(tip).equals(label)) {
                kljuc = tip;
                break;
            }
        }
        stanje.setTipSkole(kljuc);
        if (updatePeriods && !kljuc.isEmpty()) {
            int termini = Pravila.terminiZaTip(kljuc);
            stanje.setTerminaDnevno(termini);
            if (brojacTermina != null) {
                brojacTermina.getValueFactory().setValue(termini);
            }
        }
        osveziMrezu();
    }

    private void smenaIzKutije() {
        if (kutijaSmene == null) {
            return;
        }
        boolean oneShift = MOD_JEDNA_SMENA.equals(kutijaSmene.getValue());
        if (oneShift == stanje.jednaSmenaUkljucena()) {
            return;
        }
        stanje.setJednaSmena(oneShift);
        osveziOdeljenja();
        osveziMrezu();
    }

    private void uskladiSmenu() {
        if (kutijaSmene == null) {
            return;
        }
        kutijaSmene.setOnAction(null);
        kutijaSmene.setValue(stanje.jednaSmenaUkljucena() ? MOD_JEDNA_SMENA : DVE_SMENE);
        kutijaSmene.setOnAction(dogadjaj -> smenaIzKutije());
    }

    private String sufiksSmene(String odeljenje) {
        if (stanje.jednaSmenaUkljucena()) {
            return "";
        }
        return "  ·  " + Pravila.nazivSmene(stanje.sacuvanaSmena(odeljenje));
    }

    private void osveziMrezu() {
        stanje.srediRaspored();
        if (brojacTermina != null && brojacTermina.getValue() != stanje.getTerminaDnevno()) {
            brojacTermina.getValueFactory().setValue(stanje.getTerminaDnevno());
        }
        osveziIzbor();
        osveziPrikaz();
        if (naslovPrepodne != null) {
            naslovPrepodne.setText(stanje.jednaSmenaUkljucena() ? "Raspored" : "Prepodnevna smena");
        }
        postaviVidljivo(karticaPopodne, !stanje.jednaSmenaUkljucena());
        if (tabelaPrepodne != null) {
            tabelaPrepodne.setItems(redoviSmene("prepodne"));
        }
        if (tabelaPopodne != null) {
            tabelaPopodne.setItems(redoviSmene("popodne"));
        }
        if (tabelaOdeljenja != null) {
            tabelaOdeljenja.setUserData("odeljenje:" + izabranoOdeljenje);
            tabelaOdeljenja.setItems(redoviOdeljenja(izabranoOdeljenje));
            if (naslovTabele != null) {
                if (izabranoOdeljenje.isEmpty()) {
                    naslovTabele.setText("Odeljenje");
                } else {
                    naslovTabele.setText(izabranoOdeljenje + sufiksSmene(izabranoOdeljenje));
                }
            }
        }
        sveTabele();
        if (listaOdeljenja != null) {
            listaOdeljenja.refresh();
        }
    }

    private void osveziIzbor() {
        if (kutijaOdeljenja == null) {
            return;
        }
        List<String> odeljenja = new ArrayList<>(stanje.getOdeljenja());
        var akcija = kutijaOdeljenja.getOnAction();
        kutijaOdeljenja.setOnAction(null);
        kutijaOdeljenja.setItems(FXCollections.observableArrayList(odeljenja));
        if (!odeljenja.isEmpty() && !odeljenja.contains(izabranoOdeljenje)) {
            izabranoOdeljenje = odeljenja.get(0);
        } else if (odeljenja.isEmpty()) {
            izabranoOdeljenje = "";
        }
        kutijaOdeljenja.setValue(izabranoOdeljenje.isEmpty() ? null : izabranoOdeljenje);
        kutijaOdeljenja.setOnAction(akcija);
    }

    private void osveziPrikaz() {
        String nacin = trenutniPrikaz();
        boolean teacherView = PRIKAZ_NASTAVNIK.equals(nacin);
        boolean oneClass = PRIKAZ_ODELJENJE.equals(nacin);
        boolean allClasses = PRIKAZ_SVA.equals(nacin);
        postaviVidljivo(natpisNastavnika, teacherView);
        postaviVidljivo(kutijaNastavnika, teacherView);
        postaviVidljivo(natpisOdeljenja, oneClass);
        postaviVidljivo(kutijaOdeljenja, oneClass);
        postaviVidljivo(tabeleNastavnika, teacherView);
        postaviVidljivo(panelOdeljenja, oneClass);
        postaviVidljivo(skrolSvih, allClasses);
    }

    private String trenutniPrikaz() {
        if (kutijaPrikaza == null || kutijaPrikaza.getValue() == null) {
            return PRIKAZ_NASTAVNIK;
        }
        return kutijaPrikaza.getValue();
    }

    private void postaviVidljivo(javafx.scene.Node node, boolean shown) {
        if (node == null) {
            return;
        }
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private void sveTabele() {
        if (kutijaSvih == null) {
            return;
        }
        kutijaSvih.getChildren().clear();
        if (!PRIKAZ_SVA.equals(trenutniPrikaz())) {
            return;
        }
        if (stanje.getOdeljenja().isEmpty()) {
            kutijaSvih.getChildren().add(mig("Nema odeljenja. Prvo ih kreirajte u kartici Podešavanje."));
            return;
        }
        for (String odeljenje : stanje.getOdeljenja()) {
            Label naslovSekcije = naslovSekcije(odeljenje + sufiksSmene(odeljenje));
            TableView<RedCasa> tabela = tabelaRasporeda("odeljenje:" + odeljenje);
            tabela.setItems(redoviOdeljenja(odeljenje));
            tabela.setFixedCellSize(54);
            tabela.setPrefHeight(56 + Math.max(1, stanje.getTerminaDnevno()) * 54);
            VBox kartica = kartica(naslovSekcije, tabela);
            kutijaSvih.getChildren().add(kartica);
        }
    }

    private ObservableList<RedCasa> redoviSmene(String smena) {
        ObservableList<RedCasa> redovi = FXCollections.observableArrayList();
        int termini = stanje.getTerminaDnevno();
        String nastavnik = izabranNastavnik;
        for (int termin = 0; termin < termini; termin++) {
            List<String> natpisi = new ArrayList<>();
            natpisi.add(String.valueOf(termin + 1));
            for (String dan : Dani.SVI) {
                List<String> celija = new ArrayList<>();
                if (nastavnik != null && !nastavnik.isEmpty()) {
                    for (String odeljenje : stanje.getOdeljenja()) {
                        if (!stanje.smenaZa(odeljenje).equals(smena)) {
                            continue;
                        }
                        Cas cas = stanje.getRaspored().get(odeljenje).get(dan).get(termin);
                        if (cas != null && cas.getNastavnik().equals(nastavnik)) {
                            celija.add(cas.zaNastavnika(odeljenje));
                        }
                    }
                }
                natpisi.add(String.join(" | ", celija));
            }
            redovi.add(new RedCasa(
                    natpisi.get(0), natpisi.get(1), natpisi.get(2), natpisi.get(3), natpisi.get(4), natpisi.get(5)));
        }
        return redovi;
    }

    private ObservableList<RedCasa> redoviOdeljenja(String odeljenje) {
        ObservableList<RedCasa> redovi = FXCollections.observableArrayList();
        int termini = stanje.getTerminaDnevno();
        for (int termin = 0; termin < termini; termin++) {
            List<String> natpisi = new ArrayList<>();
            natpisi.add(String.valueOf(termin + 1));
            for (String dan : Dani.SVI) {
                String tekst = "";
                if (odeljenje != null && !odeljenje.isEmpty() && stanje.getRaspored().containsKey(odeljenje)) {
                    Cas cas = stanje.getRaspored().get(odeljenje).get(dan).get(termin);
                    if (cas != null) {
                        tekst = cas.zaOdeljenje();
                    }
                }
                natpisi.add(tekst);
            }
            redovi.add(new RedCasa(
                    natpisi.get(0), natpisi.get(1), natpisi.get(2), natpisi.get(3), natpisi.get(4), natpisi.get(5)));
        }
        return redovi;
    }

    private Celija celijaOdKlika(TableView<RedCasa> tabela) {
        if (tabela.getSelectionModel().getSelectedCells().isEmpty()) {
            return null;
        }
        TablePosition<?, ?> position = tabela.getSelectionModel().getSelectedCells().get(0);
        int kolona = position.getColumn();
        int termin = position.getRow();
        if (kolona < 1 || kolona > Dani.SVI.size() || termin < 0) {
            return null;
        }
        String dan = Dani.SVI.get(kolona - 1);
        String oznaka = tabela.getUserData() == null ? "prepodne" : String.valueOf(tabela.getUserData());
        if (oznaka.startsWith("odeljenje:")) {
            String odeljenje = oznaka.substring("odeljenje:".length());
            if (odeljenje.isEmpty()) {
                odeljenje = izabranoOdeljenje;
            }
            if (odeljenje == null || odeljenje.isEmpty() || !stanje.getRaspored().containsKey(odeljenje)) {
                return null;
            }
            Cas cas = stanje.getRaspored().get(odeljenje).get(dan).get(termin);
            String nastavnik = cas == null ? "" : cas.getNastavnik();
            return new Celija(nastavnik, dan, termin, stanje.smenaZa(odeljenje), odeljenje);
        }
        if (izabranNastavnik == null || izabranNastavnik.isEmpty()) {
            return null;
        }
        return new Celija(izabranNastavnik, dan, termin, oznaka, "");
    }

    private List<Map.Entry<String, Cas>> casoviNa(String nastavnik, String dan, int termin, String smena) {
        List<Map.Entry<String, Cas>> matches = new ArrayList<>();
        for (String odeljenje : stanje.getOdeljenja()) {
            if (smena != null && !stanje.smenaZa(odeljenje).equals(smena)) {
                continue;
            }
            Cas cas = stanje.getRaspored().get(odeljenje).get(dan).get(termin);
            if (cas != null && cas.getNastavnik().equals(nastavnik)) {
                matches.add(Map.entry(odeljenje, cas));
            }
        }
        return matches;
    }

    private void obrisiCeliju() {
        if (celijaMeni == null) {
            return;
        }
        String odeljenje = odeljenjeCelije(celijaMeni);
        if (odeljenje == null) {
            return;
        }
        stanje.getRaspored().get(odeljenje).get(celijaMeni.dan).set(celijaMeni.termin, null);
        osveziMrezu();
    }

    private void zakljucajCeliju() {
        if (celijaMeni == null) {
            return;
        }
        String odeljenje = odeljenjeCelije(celijaMeni);
        if (odeljenje == null) {
            return;
        }
        Cas cas = stanje.getRaspored().get(odeljenje).get(celijaMeni.dan).get(celijaMeni.termin);
        if (cas == null) {
            return;
        }
        cas.setZakljucan(!cas.jeZakljucan());
        osveziMrezu();
    }

    private String odeljenjeCelije(Celija celija) {
        if (celija.odeljenje != null && !celija.odeljenje.isEmpty()) {
            return celija.odeljenje;
        }
        List<Map.Entry<String, Cas>> matches =
                casoviNa(celija.nastavnik, celija.dan, celija.termin, celija.smena);
        if (matches.isEmpty()) {
            return null;
        }
        return matches.get(0).getKey();
    }

    private void izmeniCeliju(Celija celija) {
        if (celija == null || stanje.getOdeljenja().isEmpty()) {
            return;
        }
        String nastavnik = celija.nastavnik == null ? "" : celija.nastavnik;
        String dan = celija.dan;
        int termin = celija.termin;
        String smena = celija.smena;
        String migOdeljenja = celija.odeljenje == null ? "" : celija.odeljenje;
        List<Map.Entry<String, Cas>> matches = nastavnik.isEmpty()
                ? List.of()
                : casoviNa(nastavnik, dan, termin, smena);
        if (!migOdeljenja.isEmpty() && stanje.getRaspored().containsKey(migOdeljenja)) {
            Cas inSection = stanje.getRaspored().get(migOdeljenja).get(dan).get(termin);
            if (inSection != null) {
                matches = List.of(Map.entry(migOdeljenja, inSection));
                nastavnik = inSection.getNastavnik();
            }
        }
        String existingSection = matches.isEmpty() ? migOdeljenja : matches.get(0).getKey();
        Cas existing = matches.isEmpty() ? null : matches.get(0).getValue();
        List<String> odeljenja = new ArrayList<>();
        for (String ime : stanje.getOdeljenja()) {
            if (smena == null || smena.isEmpty() || stanje.smenaZa(ime).equals(smena)) {
                odeljenja.add(ime);
            }
        }
        if (odeljenja.isEmpty()) {
            odeljenja.addAll(stanje.getOdeljenja());
        }
        List<String> imenaNastavnika = new ArrayList<>(stanje.getNastavnici().keySet());
        imenaNastavnika.sort(String::compareToIgnoreCase);
        boolean izaberiNastavnika = nastavnik.isEmpty() || !stanje.getNastavnici().containsKey(nastavnik);
        if (izaberiNastavnika && imenaNastavnika.isEmpty()) {
            Poruke.obavestenje("Potrebni nastavnici", "Prvo unesite nastavnike.");
            return;
        }
        DijalogCasa dijalog = new DijalogCasa(
                odeljenja,
                existing,
                izaberiNastavnika ? "" : nastavnik,
                existingSection,
                izaberiNastavnika ? imenaNastavnika : List.of());
        Optional<DijalogCasa.IzborCasa> rezultat = dijalog.showAndWait();
        if (rezultat.isEmpty()) {
            return;
        }
        String odeljenje = rezultat.get().odeljenje();
        Cas cas = rezultat.get().cas();
        Cas sad = stanje.getRaspored().get(odeljenje).get(dan).get(termin);
        if (sad != null && sad != existing) {
            if (!Poruke.pitaj("Zameni čas", odeljenje + " već ima čas u ovom terminu. Zameniti ga?")) {
                return;
            }
        }
        if (!matches.isEmpty()) {
            String oldSection = matches.get(0).getKey();
            stanje.getRaspored().get(oldSection).get(dan).set(termin, null);
        }
        if (!motor.ostajeZbijeno(stanje.getRaspored().get(odeljenje).get(dan), termin)) {
            if (!matches.isEmpty()) {
                stanje.getRaspored().get(matches.get(0).getKey()).get(dan).set(termin, matches.get(0).getValue());
            }
            Poruke.greska(
                    "Rupa u danu učenika",
                    "Ovo bi napravilo prazan čas u toku dana. Učenici ne mogu da imaju rupe.");
            return;
        }
        stanje.getRaspored().get(odeljenje).get(dan).set(termin, cas);
        osveziMrezu();
    }

    private void obrisiOtkljucane() {
        for (Map<String, List<Cas>> sectionSchedule : stanje.getRaspored().values()) {
            for (String dan : Dani.SVI) {
                List<Cas> termini = sectionSchedule.get(dan);
                for (int rb = 0; rb < termini.size(); rb++) {
                    Cas cas = termini.get(rb);
                    if (cas != null && !cas.jeZakljucan()) {
                        termini.set(rb, null);
                    }
                }
            }
        }
        osveziMrezu();
        natpisStatusa.setText("Obrisani su generisani časovi; zaključani ručni časovi su ostali.");
    }

    private void obrisiSveCasove() {
        if (!Poruke.pitaj("Obriši sve", "Obrisati sve časove, uključujući zaključane?")) {
            return;
        }
        for (Map<String, List<Cas>> sectionSchedule : stanje.getRaspored().values()) {
            for (String dan : Dani.SVI) {
                List<Cas> termini = sectionSchedule.get(dan);
                for (int rb = 0; rb < termini.size(); rb++) {
                    termini.set(rb, null);
                }
            }
        }
        osveziMrezu();
        natpisStatusa.setText("Ceo raspored je obrisan.");
    }

    private void napraviRaspored() {
        if (generating) {
            return;
        }
        if (stanje.getOdeljenja().isEmpty()) {
            Poruke.obavestenje("Potrebna odeljenja", "Prvo unesite odeljenja.");
            return;
        }
        if (stanje.getNastavnici().isEmpty()) {
            Poruke.obavestenje("Potrebni nastavnici", "Prvo unesite nastavnike.");
            return;
        }
        if (stanje.getTipSkole().isEmpty()) {
            Poruke.obavestenje("Tip škole", "Izaberite tip škole (1–4, 5–8, srednja ili fakultet) pre generisanja.");
            return;
        }
        generating = true;
        String prioritet = kodPrioriteta();
        natpisStatusa.setText(statusGenerisanja(prioritet));
        sakrijIshod();
        Thread worker = new Thread(() -> {
            try {
                Motor.Rezultat rezultat = motor.napravi(prioritet);
                Platform.runLater(() -> {
                    generating = false;
                    brojacTermina.getValueFactory().setValue(stanje.getTerminaDnevno());
                    osveziMrezu();
                    natpisStatusa.setText(rezultat.statusPoruke());
                    prikaziIshod(rezultat.napomene());
                });
            } catch (NemaResenja izuzetak) {
                Platform.runLater(() -> {
                    generating = false;
                    natpisStatusa.setText("Nema ispravnog rasporeda.");
                    sakrijIshod();
                    Poruke.greska("Nema ispravnog rasporeda", izuzetak.getMessage());
                });
            } catch (IllegalStateException izuzetak) {
                Platform.runLater(() -> {
                    generating = false;
                    natpisStatusa.setText(izuzetak.getMessage());
                    Poruke.obavestenje("Nema časova", izuzetak.getMessage());
                });
            } catch (RuntimeException izuzetak) {
                Platform.runLater(() -> {
                    generating = false;
                    natpisStatusa.setText("Generisanje nije uspelo.");
                    Poruke.greska("Pravljenje rasporeda nije uspelo", izuzetak.getMessage());
                });
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private String kodPrioriteta() {
        String label = kutijaPrioriteta == null ? PRIORITET_NASTAVNIK : kutijaPrioriteta.getValue();
        if (PRIORITET_UCENIK.equals(label)) {
            return "student";
        }
        if (PRIORITET_NASTAVNIK.equals(label)) {
            return "pauze";
        }
        return "balanced";
    }

    private String statusGenerisanja(String kod) {
        return switch (kod) {
            case "student" -> "Generisanje rasporeda koji je dobar za decu...";
            case "pauze" -> "Generisanje rasporeda koji je dobar za nastavnike...";
            default -> "Generisanje uravnoteženog rasporeda...";
        };
    }

    private void sakrijIshod() {
        if (karticaIshoda == null || listaIshoda == null) {
            return;
        }
        listaIshoda.getChildren().clear();
        karticaIshoda.setVisible(false);
        karticaIshoda.setManaged(false);
    }

    private void prikaziIshod(List<String> napomene) {
        if (karticaIshoda == null || listaIshoda == null) {
            return;
        }
        listaIshoda.getChildren().clear();
        if (napomene == null || napomene.isEmpty()) {
            if (uvodIshoda != null) {
                uvodIshoda.setText("Raspored je ispravan. Nema napomena za ovaj način sastavljanja.");
            }
            karticaIshoda.setVisible(true);
            karticaIshoda.setManaged(true);
            return;
        }
        if (uvodIshoda != null) {
            uvodIshoda.setText("Raspored je ispravan. Svaka stavka ispod je želja koja nije u potpunosti ispunjena:");
        }
        int rb = 1;
        for (String beleska : napomene) {
            if (beleska == null || beleska.isBlank()) {
                continue;
            }
            Label red = new Label(rb + ".  " + beleska.strip());
            red.getStyleClass().add("work-notes-item");
            red.setWrapText(true);
            red.setMaxWidth(Double.MAX_VALUE);
            listaIshoda.getChildren().add(red);
            rb += 1;
        }
        boolean hasNotes = !listaIshoda.getChildren().isEmpty();
        karticaIshoda.setVisible(hasNotes);
        karticaIshoda.setManaged(hasNotes);
    }

    private record Celija(String nastavnik, String dan, int termin, String smena, String odeljenje) {
    }
}
