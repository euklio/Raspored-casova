package rasporedcasova.model;

public class Stavka {
    public final String nastavnik;
    public final String odeljenje;
    public final String predmet;
    public final int maxDnevno;

    public Stavka(String nastavnik, String odeljenje, String predmet, int maxDnevno) {
        this.nastavnik = nastavnik;
        this.odeljenje = odeljenje;
        this.predmet = predmet;
        this.maxDnevno = maxDnevno;
    }
}
