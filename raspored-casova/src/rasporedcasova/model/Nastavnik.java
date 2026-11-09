package rasporedcasova.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Nastavnik extends SaImenom {
    private final Set<String> slobodniDani;
    private final List<Zaduzenje> dodele;

    public Nastavnik(String ime) {
        this(ime, new LinkedHashSet<>(Dani.SVI), new ArrayList<>());
    }

    public Nastavnik(String ime, Set<String> slobodniDani, List<Zaduzenje> dodele) {
        super(ime);
        this.slobodniDani = new LinkedHashSet<>(slobodniDani);
        this.dodele = new ArrayList<>();
        for (Zaduzenje dodela : dodele) {
            this.dodele.add(dodela.kopija());
        }
    }

    public Nastavnik kopija() {
        return new Nastavnik(getIme(), slobodniDani, dodele);
    }

    public Set<String> getSlobodniDani() {
        return slobodniDani;
    }

    public List<Zaduzenje> getDodele() {
        return dodele;
    }
}
