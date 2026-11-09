package rasporedcasova.model;

public class Zaduzenje {
    private String odeljenje;
    private String predmet;
    private int nedeljno;
    private int maxDnevno;

    public Zaduzenje(String odeljenje, String predmet, int nedeljno, int maxDnevno) {
        this.odeljenje = odeljenje;
        this.predmet = predmet;
        this.nedeljno = nedeljno;
        this.maxDnevno = maxDnevno;
    }

    public Zaduzenje kopija() {
        return new Zaduzenje(odeljenje, predmet, nedeljno, maxDnevno);
    }

    public String getOdeljenje() {
        return odeljenje;
    }

    public void setOdeljenje(String odeljenje) {
        this.odeljenje = odeljenje;
    }

    public String getPredmet() {
        return predmet;
    }

    public void setPredmet(String predmet) {
        this.predmet = predmet;
    }

    public int getNedeljno() {
        return nedeljno;
    }

    public void setNedeljno(int nedeljno) {
        this.nedeljno = nedeljno;
    }

    public int getMaxDnevno() {
        return maxDnevno;
    }

    public void setMaxDnevno(int maxDnevno) {
        this.maxDnevno = maxDnevno;
    }
}
