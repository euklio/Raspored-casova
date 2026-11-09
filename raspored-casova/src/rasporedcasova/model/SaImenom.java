package rasporedcasova.model;

public abstract class SaImenom {
    private String ime;

    protected SaImenom(String ime) {
        this.ime = ime;
    }

    public String getIme() {
        return ime;
    }

    public void setIme(String ime) {
        this.ime = ime;
    }

    @Override
    public String toString() {
        return ime;
    }
}
