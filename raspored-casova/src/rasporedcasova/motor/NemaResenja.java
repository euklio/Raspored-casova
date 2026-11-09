package rasporedcasova.motor;

import java.util.ArrayList;
import java.util.List;

public class NemaResenja extends RuntimeException {
    private final List<String> razlozi;
    private final String vrsta;
    private final int cvorovi;
    private final int vracanja;

    public NemaResenja(String poruka, List<String> razlozi, String vrsta) {
        this(poruka, razlozi, vrsta, 0, 0);
    }

    public NemaResenja(String poruka, List<String> razlozi, String vrsta, int cvorovi, int vracanja) {
        super(format(poruka, razlozi));
        this.razlozi = razlozi == null ? List.of() : new ArrayList<>(razlozi);
        this.vrsta = vrsta;
        this.cvorovi = cvorovi;
        this.vracanja = vracanja;
    }

    private static String format(String poruka, List<String> razlozi) {
        if (razlozi == null || razlozi.isEmpty()) {
            return poruka;
        }
        return poruka + "\n\n" + String.join("\n", razlozi);
    }

    public List<String> getReasons() {
        return razlozi;
    }

    public String getKind() {
        return vrsta;
    }

    public int getNodes() {
        return cvorovi;
    }

    public int getBacktracks() {
        return vracanja;
    }
}
