package rasporedcasova.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class RedCasa {
    private final StringProperty termin = new SimpleStringProperty();
    private final StringProperty ponedeljak = new SimpleStringProperty();
    private final StringProperty utorak = new SimpleStringProperty();
    private final StringProperty sreda = new SimpleStringProperty();
    private final StringProperty cetvrtak = new SimpleStringProperty();
    private final StringProperty petak = new SimpleStringProperty();

    public RedCasa(String termin, String ponedeljak, String utorak, String sreda, String cetvrtak, String petak) {
        this.termin.set(termin);
        this.ponedeljak.set(ponedeljak);
        this.utorak.set(utorak);
        this.sreda.set(sreda);
        this.cetvrtak.set(cetvrtak);
        this.petak.set(petak);
    }

    public StringProperty terminProperty() {
        return termin;
    }

    public StringProperty ponedeljakProperty() {
        return ponedeljak;
    }

    public StringProperty utorakProperty() {
        return utorak;
    }

    public StringProperty sredaProperty() {
        return sreda;
    }

    public StringProperty cetvrtakProperty() {
        return cetvrtak;
    }

    public StringProperty petakProperty() {
        return petak;
    }
}
