package rasporedcasova.motor;

import rasporedcasova.model.Dani;
import rasporedcasova.model.Cas;
import rasporedcasova.model.Pravila;
import rasporedcasova.model.Nastavnik;
import rasporedcasova.model.Zaduzenje;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Motor {
    public record Rezultat(List<String> napomene, String statusPoruke, int cvorovi, int mekaCena) {
    }

    private final Stanje stanje;

    public Motor(Stanje stanje) {
        this.stanje = stanje;
    }

    public Rezultat napravi() {
        return napravi("balanced");
    }

    public Rezultat napravi(String prioritet) {
        if (stanje.getOdeljenja().isEmpty()) {
            throw new IllegalStateException("Prvo unesite odeljenja.");
        }
        if (stanje.getNastavnici().isEmpty()) {
            throw new IllegalStateException("Prvo unesite nastavnike.");
        }
        if (stanje.getTipSkole().isEmpty()) {
            throw new IllegalStateException("Obavezno izaberite tip škole: 1-4, 5-8, srednja ili fakultet.");
        }

        stanje.srediRaspored();
        Resavac.Ishod reseno = new Resavac(stanje).resi(prioritet);
        stanje.setRaspored(reseno.raspored());
        stanje.srediRaspored();

        List<String> napomene = skupiNapomene(true, prioritet);
        String statusPoruke = napomene.isEmpty()
                ? "Raspored je uspešno napravljen."
                : "Raspored je napravljen. Ishod je na dnu rasporeda.";
        return new Rezultat(napomene, statusPoruke, reseno.cvorovi(), reseno.mekaCena());
    }

    public List<String> skupiNapomene() {
        return skupiNapomene(false, "balanced");
    }

    public List<String> skupiNapomene(boolean samoMeko) {
        return skupiNapomene(samoMeko, "balanced");
    }

    public List<String> skupiNapomene(boolean samoMeko, String prioritet) {
        List<String> napomene = new ArrayList<>();
        Map<String, Map<String, List<Cas>>> raspored = stanje.getRaspored();
        if (!samoMeko) {
            for (String odeljenje : stanje.getOdeljenja()) {
                for (String dan : Dani.SVI) {
                    Map<String, Integer> brojevi = new java.util.LinkedHashMap<>();
                    Map<String, Integer> maksimalni = new java.util.LinkedHashMap<>();
                    Map<String, String> imena = new java.util.LinkedHashMap<>();
                    List<Cas> casoviDana = raspored.get(odeljenje).get(dan);
                    for (int termin = 0; termin < casoviDana.size(); termin++) {
                        Cas cas = casoviDana.get(termin);
                        if (cas == null) {
                            continue;
                        }
                        String kljuc = cas.getPredmet().toLowerCase(Locale.ROOT);
                        imena.put(kljuc, cas.getPredmet());
                        maksimalni.put(kljuc, cas.getMaxDnevno());
                        brojevi.merge(kljuc, 1, Integer::sum);
                        Nastavnik nastavnik = stanje.getNastavnici().get(cas.getNastavnik());
                        if (nastavnik == null) {
                            napomene.add(cas.getNastavnik() + " nije u listi nastavnika.");
                        } else if (!nastavnik.getSlobodniDani().contains(dan)) {
                            napomene.add(cas.getNastavnik() + " predaje na dan kada nije dostupan ("
                                    + Dani.label(dan) + ").");
                        }
                        if (dupliCas(dan, termin, cas.getNastavnik(), stanje.smenaZa(odeljenje))) {
                            napomene.add(cas.getNastavnik() + " je u dva odeljenja u isto vreme: "
                                    + Dani.label(dan) + ", čas " + (termin + 1) + ".");
                        }
                    }
                    for (String kljuc : imena.keySet()) {
                        if (brojevi.get(kljuc) > maksimalni.get(kljuc)) {
                            napomene.add(odeljenje + " ima " + imena.get(kljuc) + " " + brojevi.get(kljuc)
                                    + " puta u " + Dani.label(dan) + ".");
                        }
                    }
                    if (!danJeOk(casoviDana)) {
                        napomene.add(odeljenje + " mora da počne od 1. časa, bez praznog časa, i da završi posle 5., 6. ili 7. časa ("
                                + Dani.label(dan) + ").");
                    }
                    if (!blokoviUparu(casoviDana)) {
                        napomene.add(odeljenje + " mora da ima blok-predmete (Tehničko, Likovno u 5. razredu) kao dva spojena časa ("
                                + Dani.label(dan) + ").");
                    }
                }
                if (Pravila.odPrvogCasa(stanje.getTipSkole())) {
                    int[] granice = Pravila.graniceDana(stanje.getTipSkole());
                    int nedeljniFond = 0;
                    Map<String, Integer> dayCounts = new java.util.LinkedHashMap<>();
                    for (String dan : Dani.SVI) {
                        int n = 0;
                        for (Cas cas : raspored.get(odeljenje).get(dan)) {
                            if (cas != null) {
                                n += 1;
                            }
                        }
                        dayCounts.put(dan, n);
                        nedeljniFond += n;
                    }
                    if (nedeljniFond >= 5 * granice[0]) {
                        for (Map.Entry<String, Integer> entry : dayCounts.entrySet()) {
                            if (entry.getValue() > 0 && entry.getValue() < granice[0]) {
                                napomene.add(odeljenje + " ima samo " + entry.getValue() + " časova u "
                                        + Dani.label(entry.getKey()) + "; školski dan mora trajati "
                                        + granice[0] + "–" + granice[1] + " časova.");
                            }
                        }
                    }
                }
            }
        }

        for (String nastavnik : stanje.getNastavnici().keySet()) {
            for (String dan : Dani.SVI) {
                for (String smena : stanje.korisceneSmene()) {
                    Set<Integer> termini = terminiNastavnika(raspored, nastavnik, dan, smena);
                    int prelomi = rupeTermina(termini);
                    boolean uciteljski = Pravila.razredna(stanje.getTipSkole())
                            && Pravila.ucitelj(stanje.getNastavnici().get(nastavnik));
                    String label = "popodne".equals(smena) ? "popodne" : "prepodne";
                    if (uciteljski && !termini.isEmpty() && !termini.contains(0)) {
                        napomene.add(nastavnik + " — " + Dani.label(dan) + ", " + label
                                + ": učiteljica mora da počne od 1. časa.");
                    }
                    if (uciteljski && prelomi > 0) {
                        napomene.add(nastavnik + " — " + Dani.label(dan) + ", " + label + ": "
                                + prelomi + " pauza. U 1–4 učiteljica mora da ima 0 pauza.");
                    } else if (!uciteljski && prelomi > 2) {
                        napomene.add(nastavnik + " — " + Dani.label(dan) + ", " + label + ": "
                                + prelomi + " pauze. Bolje je 0 ili 1 pauza.");
                    } else if (!uciteljski && prelomi == 2 && !"pauze".equals(prioritet)) {
                        napomene.add(nastavnik + " — " + Dani.label(dan) + ", " + label + ": "
                                + prelomi + " pauze. Bolje je 0 ili 1 pauza.");
                    }
                }
            }
            for (String smena : stanje.korisceneSmene()) {
                int usamljen = 0;
                List<String> posete = new ArrayList<>();
                for (String dan : Dani.SVI) {
                    if (!usamljenaSmena(raspored, nastavnik, dan, smena)) {
                        continue;
                    }
                    usamljen += 1;
                    String label = "popodne".equals(smena) ? "popodne" : "prepodne";
                    posete.add(Dani.label(dan) + ", " + label);
                }
                if (!"pauze".equals(prioritet)
                        && usamljen <= brojPrinudnih(nastavnik, smena, prioritet)) {
                    continue;
                }
                for (String poseta : posete) {
                    napomene.add(nastavnik + " — " + poseta
                            + ": dolazi samo zbog jednog časa. Bolje je bar 2, poželjno 3.");
                }
            }
        }
        if (stanje.rotacijaUkljucena() && Pravila.upareneSmene(stanje.getTipSkole())) {
            upozoriFondSmene(napomene);
        }
        return new ArrayList<>(new LinkedHashSet<>(napomene));
    }

    public boolean ostajeZbijeno(List<Cas> casoviDana, int termin) {
        List<Cas> simulated = new ArrayList<>(casoviDana);
        simulated.set(termin, new Cas("_", "_", 1, false));
        return danJeOk(simulated);
    }

    private boolean danJeOk(List<Cas> casoviDana) {
        int broj = 0;
        Integer prvi = null;
        Integer last = null;
        for (int termin = 0; termin < casoviDana.size(); termin++) {
            if (casoviDana.get(termin) == null) {
                continue;
            }
            broj += 1;
            if (prvi == null) {
                prvi = termin;
            }
            last = termin;
        }
        if (broj == 0) {
            return true;
        }
        if (Pravila.odPrvogCasa(stanje.getTipSkole()) && prvi != 0) {
            return false;
        }
        for (int termin = prvi; termin <= last; termin++) {
            if (casoviDana.get(termin) == null) {
                return false;
            }
        }
        int maxDuzina = Pravila.graniceDana(stanje.getTipSkole())[1];
        return broj <= maxDuzina;
    }

    private boolean blokoviUparu(List<Cas> casoviDana) {
        Map<String, List<Integer>> periodsBySubject = new java.util.LinkedHashMap<>();
        for (int termin = 0; termin < casoviDana.size(); termin++) {
            Cas cas = casoviDana.get(termin);
            if (cas == null) {
                continue;
            }
            if (!Pravila.jeBlok(
                    cas.getPredmet(), 0, cas.getMaxDnevno(), stanje.getTipSkole())) {
                continue;
            }
            String kljuc = cas.getPredmet().toLowerCase(Locale.ROOT);
            periodsBySubject.computeIfAbsent(kljuc, nista -> new ArrayList<>()).add(termin);
        }
        for (Map.Entry<String, List<Integer>> entry : periodsBySubject.entrySet()) {
            List<Integer> termini = entry.getValue();
            int singles = 0;
            int rb = 0;
            while (rb < termini.size()) {
                if (rb + 1 < termini.size() && termini.get(rb + 1) == termini.get(rb) + 1) {
                    rb += 2;
                } else {
                    singles += 1;
                    rb += 1;
                }
            }
            int dozvoljeno = Pravila.smePoredBloka(entry.getKey()) ? 1 : 0;
            if (singles > dozvoljeno) {
                return false;
            }
        }
        return true;
    }

    private void upozoriFondSmene(List<String> napomene) {
        Map<String, int[]> satiPredmeta = new java.util.LinkedHashMap<>();
        Map<String, Integer> subjectTeachers = new java.util.LinkedHashMap<>();
        for (Nastavnik nastavnik : stanje.getNastavnici().values()) {
            String predmet = jedanPredmet(nastavnik);
            if (predmet == null) {
                continue;
            }
            int[] sati = casoviPoSmeni(nastavnik);
            satiPredmeta.computeIfAbsent(predmet, nista -> new int[2])[0] += sati[0];
            satiPredmeta.get(predmet)[1] += sati[1];
            subjectTeachers.merge(predmet, 1, Integer::sum);
        }
        for (Nastavnik nastavnik : stanje.getNastavnici().values()) {
            int[] sati = casoviPoSmeni(nastavnik);
            int prepodne = sati[0];
            int popodne = sati[1];
            int diff = Math.abs(prepodne - popodne);
            if (diff <= 2) {
                continue;
            }
            String predmet = jedanPredmet(nastavnik);
            if (predmet != null) {
                int[] school = satiPredmeta.getOrDefault(predmet, new int[2]);
                int nastavnici = subjectTeachers.getOrDefault(predmet, 1);
                int schoolDiff = Math.abs(school[0] - school[1]);
                int bestPossible = (schoolDiff + nastavnici - 1) / Math.max(1, nastavnici);
                if (diff <= Math.max(2, bestPossible)) {
                    continue;
                }
            }
            String morningGrades = "srednja".equals(stanje.getTipSkole()) ? "I i III" : "5. i 6.";
            String afternoonGrades = "srednja".equals(stanje.getTipSkole()) ? "II i IV" : "7. i 8.";
            napomene.add(nastavnik.getIme() + " ima " + prepodne
                    + " časova u prepodnevnim odeljenjima (" + morningGrades + ") i " + popodne
                    + " u popodnevnim (" + afternoonGrades + "). Pri rotaciji smena fond treba da bude isti.");
        }
    }

    private static String jedanPredmet(Nastavnik nastavnik) {
        String predmet = null;
        for (Zaduzenje dodela : nastavnik.getDodele()) {
            if (predmet == null) {
                predmet = dodela.getPredmet();
            } else if (!predmet.equalsIgnoreCase(dodela.getPredmet())) {
                return null;
            }
        }
        return predmet;
    }

    private int[] casoviPoSmeni(Nastavnik nastavnik) {
        int prepodne = 0;
        int popodne = 0;
        for (Zaduzenje dodela : nastavnik.getDodele()) {
            int sati = dodela.getNedeljno();
            if ("popodne".equals(stanje.smenaZa(dodela.getOdeljenje()))) {
                popodne += sati;
            } else {
                prepodne += sati;
            }
        }
        return new int[] {prepodne, popodne};
    }

    private int brojPrinudnih(String imeNastavnika, String smena, String prioritet) {
        Nastavnik nastavnik = stanje.getNastavnici().get(imeNastavnika);
        if (nastavnik == null) {
            return 0;
        }
        int satiSmene = 0;
        int prinudniDani = 0;
        for (Zaduzenje dodela : nastavnik.getDodele()) {
            if (!smena.equals(stanje.smenaZa(dodela.getOdeljenje()))) {
                continue;
            }
            satiSmene += dodela.getNedeljno();
            if (dodela.getMaxDnevno() <= 1) {
                prinudniDani = Math.max(prinudniDani, dodela.getNedeljno());
            }
        }
        int ostatak = 0;
        if (prinudniDani >= 5) {
            ostatak = Math.max(0, prinudniDani - (satiSmene - prinudniDani));
        }
        if ("pauze".equals(prioritet)) {
            int limit = Pravila.limitPosete(
                    stanje.getTipSkole(), Pravila.ucitelj(nastavnik));
            if (limit < 1000 && satiSmene > 0) {
                ostatak = Math.max(ostatak, satiSmene % limit);
            }
        }
        return ostatak;
    }

    private List<String> usamljenePosete(
            Map<String, Map<String, List<Cas>>> raspored, String nastavnik) {
        List<String> posete = new ArrayList<>();
        for (String dan : Dani.SVI) {
            for (String smena : stanje.korisceneSmene()) {
                if (terminiNastavnika(raspored, nastavnik, dan, smena).size() == 1) {
                    String label = "popodne".equals(smena) ? "popodne" : "prepodne";
                    posete.add(Dani.label(dan) + ", " + label);
                }
            }
        }
        return posete;
    }

    private Set<Integer> terminiNastavnika(
            Map<String, Map<String, List<Cas>>> raspored, String nastavnik, String dan, String smena) {
        Set<Integer> termini = new LinkedHashSet<>();
        for (String odeljenje : stanje.getOdeljenja()) {
            if (!stanje.smenaZa(odeljenje).equals(smena)) {
                continue;
            }
            List<Cas> casoviDana = raspored.get(odeljenje).get(dan);
            for (int termin = 0; termin < casoviDana.size(); termin++) {
                Cas cas = casoviDana.get(termin);
                if (cas != null && cas.getNastavnik().equals(nastavnik)) {
                    termini.add(termin);
                }
            }
        }
        return termini;
    }

    private boolean usamljenaSmena(
            Map<String, Map<String, List<Cas>>> raspored, String nastavnik, String dan, String smena) {
        Set<Integer> termini = terminiNastavnika(raspored, nastavnik, dan, smena);
        if (termini.size() != 1) {
            return false;
        }
        int prepodne = maskaTermina(terminiNastavnika(raspored, nastavnik, dan, "prepodne"));
        int popodne = maskaTermina(terminiNastavnika(raspored, nastavnik, dan, "popodne"));
        if (Pravila.jednaPoseta(prepodne, popodne, stanje.getTerminaDnevno())) {
            return Integer.bitCount(prepodne) + Integer.bitCount(popodne) < 2;
        }
        return true;
    }

    private static int maskaTermina(Set<Integer> termini) {
        int maska = 0;
        for (int termin : termini) {
            maska |= 1 << termin;
        }
        return maska;
    }

    private int rupeTermina(Set<Integer> termini) {
        if (termini.size() < 2) {
            return 0;
        }
        List<Integer> poredjano = new ArrayList<>(termini);
        Collections.sort(poredjano);
        int gaps = 0;
        for (int i = 0; i < poredjano.size() - 1; i++) {
            gaps += Math.max(0, poredjano.get(i + 1) - poredjano.get(i) - 1);
        }
        return gaps;
    }

    public boolean dupliCas(String dan, int termin, String nastavnik, String smena) {
        int broj = 0;
        for (String odeljenje : stanje.getOdeljenja()) {
            if (!stanje.smenaZa(odeljenje).equals(smena)) {
                continue;
            }
            Cas cas = stanje.getRaspored().get(odeljenje).get(dan).get(termin);
            if (cas != null && cas.getNastavnik().equals(nastavnik)) {
                broj += 1;
            }
        }
        return broj > 1;
    }
}
