package rasporedcasova.ui;

public class RedNastavnika {
    private final String ime;
    private final String predmeti;
    private final String razredi;
    private final int nedeljniFond;
    private final String dani;

    public RedNastavnika(String ime, String predmeti, String razredi, int nedeljniFond, String dani) {
        this.ime = ime;
        this.predmeti = predmeti;
        this.razredi = razredi;
        this.nedeljniFond = nedeljniFond;
        this.dani = dani;
    }

    public String getIme() {
        return ime;
    }

    public String getPredmeti() {
        return predmeti;
    }

    public String getRazredi() {
        return razredi;
    }

    public int getNedeljnoFond() {
        return nedeljniFond;
    }

    public String getDani() {
        return dani;
    }
}
