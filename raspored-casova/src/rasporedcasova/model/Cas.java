package rasporedcasova.model;

public class Cas {
    private String predmet;
    private String nastavnik;
    private int maxDnevno;
    private boolean zakljucan;

    public Cas(String predmet, String nastavnik, int maxDnevno, boolean zakljucan) {
        this.predmet = predmet;
        this.nastavnik = nastavnik;
        this.maxDnevno = maxDnevno;
        this.zakljucan = zakljucan;
    }

    public Cas kopija() {
        return new Cas(predmet, nastavnik, maxDnevno, zakljucan);
    }

    public String zaOdeljenje() {
        String prefiks = zakljucan ? "[L] " : "";
        return prefiks + predmet + " - " + nastavnik;
    }

    public String zaNastavnika(String odeljenje) {
        String prefiks = zakljucan ? "[L] " : "";
        return prefiks + odeljenje + " - " + predmet;
    }

    public String getPredmet() {
        return predmet;
    }

    public void setPredmet(String predmet) {
        this.predmet = predmet;
    }

    public String getNastavnik() {
        return nastavnik;
    }

    public void setNastavnik(String nastavnik) {
        this.nastavnik = nastavnik;
    }

    public int getMaxDnevno() {
        return maxDnevno;
    }

    public void setMaxDnevno(int maxDnevno) {
        this.maxDnevno = maxDnevno;
    }

    public boolean jeZakljucan() {
        return zakljucan;
    }

    public void setZakljucan(boolean zakljucan) {
        this.zakljucan = zakljucan;
    }
}
