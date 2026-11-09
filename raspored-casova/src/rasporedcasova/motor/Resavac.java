package rasporedcasova.motor;

import rasporedcasova.model.Dani;
import rasporedcasova.model.Cas;
import rasporedcasova.model.Pravila;
import rasporedcasova.model.Nastavnik;
import rasporedcasova.model.Zaduzenje;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class Resavac {
    public record Ishod(
            Map<String, Map<String, List<Cas>>> raspored,
            int mekaCena,
            int cvorovi,
            int vracanja,
            boolean viseResenja,
            int neiskoriscena,
            String prioritet) {
    }

    private static final class Komad {
        final String nastavnik;
        final String odeljenje;
        final String predmet;
        final String kljucPredmeta;
        final int maxDnevno;
        final int trajanje;

        int nastavnikRb;
        int odeljenjeRb;
        int smenaRb;
        int predmetRb;

        Komad(String nastavnik, String odeljenje, String predmet, String kljucPredmeta, int maxDnevno, int trajanje) {
            this.nastavnik = nastavnik;
            this.odeljenje = odeljenje;
            this.predmet = predmet;
            this.kljucPredmeta = kljucPredmeta;
            this.maxDnevno = maxDnevno;
            this.trajanje = trajanje;
        }
    }

    private record Mesto(String dan, int termin) {
    }

    private final Stanje stanje;
    private int termini;
    private int limitCvorova;
    private long rok;
    private final List<Komad> komadi = new ArrayList<>();
    private List<Mesto>[] domene = prazneDomene();
    private final Map<Integer, Mesto> dodela = new LinkedHashMap<>();
    private boolean[] dodeljen = new boolean[0];
    private final Map<String, Integer> indeksOdeljenja = new HashMap<>();
    private final Map<String, Integer> indeksNastavnika = new HashMap<>();
    private final Map<String, Integer> indeksDana = new HashMap<>();
    private final Map<String, Integer> indeksSmene = new HashMap<>();
    private final Map<String, Integer> indeksPredmeta = new HashMap<>();
    private String[] imenaOdeljenja = new String[0];
    private String[] imenaNastavnika = new String[0];
    private String[] imenaSmena = new String[0];
    private int[][] zauzetoOdeljenje = new int[0][0];
    private int[][][] zauzetoNastavnik = new int[0][0][0];
    private int[][][] brojPredmeta = new int[0][0][0];
    private String[][][] predmetUDanu = new String[0][0][0];
    private boolean[][] slobodanDan = new boolean[0][0];
    private int[] preostaloNastavnik = new int[0];
    private int[][] preostaloSmena = new int[0][0];
    private int[] preostaloOdeljenje = new int[0];
    private int[] teretDana = new int[0];
    private int[] parRb = new int[0];
    private boolean[] mozeRaspodela = new boolean[0];
    private boolean[] daljeRaspodela = new boolean[0];
    private int nDays;
    private final Map<String, Map<String, Integer>> fondSmene = new HashMap<>();
    private final Map<String, Map<String, Integer>> limitSmene = new HashMap<>();
    private Map<String, Map<String, List<Cas>>> zakljucanRaspored = new LinkedHashMap<>();
    private int[][] susedi = new int[0][0];
    private boolean danOdPrvog;
    private boolean razrednaNastava;
    private boolean[] oznakaUcitelja = new boolean[0];
    private int minDan;
    private int maxDan;
    private boolean pauzeVazne;
    private boolean opustiPauze;
    public int cvorovi;
    public int vracanja;
    private boolean isteklo;
    private String profil = "balanced";
    private final Map<Integer, Mesto> najDodela = new LinkedHashMap<>();
    private int najCenaDodele = Integer.MAX_VALUE;
    private boolean nasaoKomplet;
    private int soRedosleda;
    private int najKaljenje = Integer.MAX_VALUE;
    private boolean tabuPotez;
    private boolean bezKasnog;

    public Resavac(Stanje stanje) {
        this(stanje, 400_000, 15.0);
    }

    public Resavac(Stanje stanje, int limitCvorova, double timeLimitSeconds) {
        this.stanje = stanje;
        this.termini = Math.max(1, stanje.getTerminaDnevno());
        this.limitCvorova = limitCvorova;
        this.rok = System.nanoTime() + (long) (timeLimitSeconds * 1_000_000_000L);
    }

    @SuppressWarnings("unchecked")
    private static List<Mesto>[] prazneDomene() {
        return new List[0];
    }

    private void sloziIndekse() {
        indeksOdeljenja.clear();
        indeksNastavnika.clear();
        indeksDana.clear();
        indeksSmene.clear();
        indeksPredmeta.clear();
        imenaOdeljenja = stanje.getOdeljenja().toArray(String[]::new);
        for (int i = 0; i < imenaOdeljenja.length; i++) {
            indeksOdeljenja.put(imenaOdeljenja[i], i);
        }
        imenaNastavnika = stanje.getNastavnici().keySet().toArray(String[]::new);
        for (int i = 0; i < imenaNastavnika.length; i++) {
            indeksNastavnika.put(imenaNastavnika[i], i);
        }
        for (int i = 0; i < Dani.SVI.size(); i++) {
            indeksDana.put(Dani.SVI.get(i), i);
        }
        imenaSmena = stanje.korisceneSmene().toArray(String[]::new);
        for (int i = 0; i < imenaSmena.length; i++) {
            indeksSmene.put(imenaSmena[i], i);
        }
        int[] granice = Pravila.graniceDana(stanje.getTipSkole());
        danOdPrvog = Pravila.odPrvogCasa(stanje.getTipSkole());
        razrednaNastava = Pravila.razredna(stanje.getTipSkole());
        oznakaUcitelja = new boolean[imenaNastavnika.length];
        if (razrednaNastava) {
            for (int i = 0; i < imenaNastavnika.length; i++) {
                Nastavnik nastavnik = stanje.getNastavnici().get(imenaNastavnika[i]);
                oznakaUcitelja[i] = Pravila.ucitelj(nastavnik);
            }
        }
        maxDan = Math.min(granice[1], termini);
        minDan = Math.min(granice[0], maxDan);
        kljuceviPredmeta();
        nDays = Dani.SVI.size();
        zauzetoOdeljenje = new int[imenaOdeljenja.length][nDays];
        zauzetoNastavnik = new int[imenaNastavnika.length][nDays][Math.max(1, imenaSmena.length)];
        brojPredmeta = new int[imenaOdeljenja.length][nDays][Math.max(1, indeksPredmeta.size())];
        predmetUDanu = new String[imenaOdeljenja.length][nDays][termini];
        slobodanDan = new boolean[imenaNastavnika.length][nDays];
        preostaloNastavnik = new int[imenaNastavnika.length];
        preostaloSmena = new int[imenaNastavnika.length][Math.max(1, imenaSmena.length)];
        preostaloOdeljenje = new int[imenaOdeljenja.length];
        teretDana = new int[nDays];
        sloziParove();
        int distCap = Math.max(64, termini * nDays + 1);
        mozeRaspodela = new boolean[distCap];
        daljeRaspodela = new boolean[distCap];
    }

    private void kljuceviPredmeta() {
        for (Nastavnik nastavnik : stanje.getNastavnici().values()) {
            for (Zaduzenje dodela : nastavnik.getDodele()) {
                ugradiPredmet(dodela.getPredmet().toLowerCase(Locale.ROOT));
            }
        }
        for (String odeljenje : stanje.getOdeljenja()) {
            for (String dan : Dani.SVI) {
                List<Cas> celije = stanje.getRaspored().getOrDefault(odeljenje, Map.of()).getOrDefault(dan, List.of());
                for (Cas cas : celije) {
                    if (cas != null) {
                        ugradiPredmet(cas.getPredmet().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
    }

    private int ugradiPredmet(String kljuc) {
        Integer existing = indeksPredmeta.get(kljuc);
        if (existing != null) {
            return existing;
        }
        int id = indeksPredmeta.size();
        indeksPredmeta.put(kljuc, id);
        return id;
    }

    private void sloziParove() {
        parRb = new int[imenaOdeljenja.length];
        Arrays.fill(parRb, -1);
        if (!stanje.rotacijaUkljucena() || !Pravila.upareneSmene(stanje.getTipSkole())) {
            return;
        }
        for (int i = 0; i < imenaOdeljenja.length; i++) {
            String par = Pravila.parOdeljenje(imenaOdeljenja[i]);
            if (par == null) {
                continue;
            }
            Integer idx = indeksOdeljenja.get(par);
            if (idx != null) {
                parRb[i] = idx;
            }
        }
    }

    private int danIndeks(String dan) {
        return indeksDana.getOrDefault(dan, 0);
    }

    private int smenaIndeks(String odeljenje) {
        return indeksOdeljenja.getOrDefault(odeljenje, 0);
    }

    private int bitovi(int termin, int trajanje) {
        return ((1 << trajanje) - 1) << termin;
    }

    private void indeksirajKomade() {
        for (Komad komad : komadi) {
            komad.nastavnikRb = indeksNastavnika.getOrDefault(komad.nastavnik, 0);
            komad.odeljenjeRb = indeksOdeljenja.getOrDefault(komad.odeljenje, 0);
            komad.smenaRb = indeksSmene.getOrDefault(stanje.smenaZa(komad.odeljenje), 0);
            komad.predmetRb = indeksPredmeta.getOrDefault(komad.kljucPredmeta, 0);
        }
    }

    private int occMin(int maska) {
        return Integer.numberOfTrailingZeros(maska);
    }

    private int occMax(int maska) {
        return 31 - Integer.numberOfLeadingZeros(maska);
    }

    private int brojRupa(int maska) {
        int broj = Integer.bitCount(maska);
        if (broj < 2) {
            return 0;
        }
        return occMax(maska) - occMin(maska) + 1 - broj;
    }

    private int maskaNastavnika(String nastavnik, String dan, String smena) {
        Integer nastavnikRb = indeksNastavnika.get(nastavnik);
        Integer smenaRb = indeksSmene.get(smena);
        if (nastavnikRb == null || smenaRb == null) {
            return 0;
        }
        return zauzetoNastavnik[nastavnikRb][danIndeks(dan)][smenaRb];
    }

    public Ishod resi(String requestedProfile) {
        profil = List.of("student", "balanced", "pauze").contains(requestedProfile)
                ? requestedProfile
                : "balanced";
        pauzeVazne = "pauze".equals(profil) || "balanced".equals(profil);
        zakljucanRaspored = kopijaZakljucanog();
        sloziIndekse();
        zauzmiZakljucano();
        sloziKomade();
        dodeljen = new boolean[komadi.size()];
        indeksirajKomade();

        if (komadi.isEmpty() && !imaZaduzenja()) {
            throw new IllegalStateException("Dodajte zaduženja nastavnicima pre generisanja.");
        }

        List<String> razlozi = provera();
        if (!razlozi.isEmpty()) {
            throw new NemaResenja(
                    "Nema ispravnog rasporeda za trenutni broj časova, zaključane časove i zaduženja.",
                    razlozi,
                    "unsat_proven");
        }

        pocetneDomene();
        for (int id = 0; id < komadi.size(); id++) {
            if (domene[id].isEmpty()) {
                Komad komad = komadi.get(id);
                throw new NemaResenja(
                        "Nema ispravnog rasporeda za trenutni broj časova, zaključane časove i zaduženja.",
                        List.of(komad.nastavnik + " / " + komad.predmet + " / " + komad.odeljenje
                                + " nema slobodan termin (dan, čas)."),
                        "unsat_proven");
            }
        }

        sloziSusede();

        int brojKomada = komadi.size();
        if (brojKomada > 400) {
            limitCvorova = Math.max(limitCvorova, 12_000_000);
            rok = System.nanoTime() + 90_000_000_000L;
        } else if (brojKomada > 200) {
            limitCvorova = Math.max(limitCvorova, 2_500_000);
            rok = System.nanoTime() + 90_000_000_000L;
        } else if (brojKomada > 80) {
            limitCvorova = Math.max(limitCvorova, 400_000);
            rok = System.nanoTime() + 25_000_000_000L;
        }
        if (stanje.jednaSmenaUkljucena() && brojKomada > 200) {
            limitCvorova = Math.max(limitCvorova, 20_000_000);
            rok = System.nanoTime() + 60_000_000_000L;
        }
        if (stanje.jednaSmenaUkljucena() && stanje.getOdeljenja().size() >= 17) {
            pauzeVazne = false;
            opustiPauze = true;
        }

        najDodela.clear();
        najCenaDodele = Integer.MAX_VALUE;
        nasaoKomplet = false;

        if (razrednaNastava) {
            int posejano = semePredmetni();
            if (posejano > 0 && !popuniUcitelja()) {
                skloniUcitelja();
            }
            osveziDomene();
        } else if (stanje.jednaSmenaUkljucena() && stanje.getOdeljenja().size() < 17) {
            semeZbijeno();
            if (!sviKomadi()) {
                skloniSve();
            }
            osveziDomene();
        }

        if (!komadi.isEmpty()) {
            boolean found = traziDokNeNadje();
            if (razrednaNastava && !najDodela.isEmpty()) {
                vratiDodelu(najDodela);
                found = true;
            }
            if (!found) {
                String vrsta = isteklo ? "search_exhausted" : "unsat_proven";
                String poruka = isteklo
                        ? "Pretraga je istekla pre nego što je pronađen ispravan raspored. Smanjite zaključane časove ili povećajte broj časova dnevno."
                        : "Nema ispravnog rasporeda za trenutni broj časova, zaključane časove i zaduženja.";
                throw new NemaResenja(
                        poruka,
                        List.of("Nije pronađena raspodela preostalih časova koja poštuje tvrda pravila."),
                        vrsta,
                        cvorovi,
                        vracanja);
            }
        }

        Map<Integer, Mesto> classroomSnapshot = razrednaNastava ? snimiDodelu() : Map.of();
        if (razrednaNastava) {
            sabijPocetak();
        }
        pauzeVazne = "pauze".equals(profil) || "balanced".equals(profil);
        opustiPauze = "pauze".equals(profil);
        int faultsBeforeSearch = greskeUcitelja();
        Map<Integer, Mesto> snimakBezGreske = razrednaNastava && faultsBeforeSearch == 0
                ? snimiDodelu()
                : classroomSnapshot;
        lokalno();
        if (razrednaNastava && greskeUcitelja() > faultsBeforeSearch && !snimakBezGreske.isEmpty()) {
            vratiDodelu(snimakBezGreske);
        }
        if (razrednaNastava) {
            sabijPocetak();
            dotegliUcitelje();
            sabijPocetak();
        }
        popraviJedanCas();
        sabijIzbezive();
        if ("pauze".equals(profil)) {
            spojiUsamljene();
        }
        if (razrednaNastava) {
            Map<Integer, Mesto> afterWork = snimiDodelu();
            int usamljen = brojIzbezivih();
            sabijPocetak();
            if (greskeUcitelja() > 0
                    || ("pauze".equals(profil) && brojIzbezivih() > usamljen)) {
                vratiDodelu(afterWork);
            }
            if (greskeUcitelja() > 0 && !snimakBezGreske.isEmpty()) {
                vratiDodelu(snimakBezGreske);
            }
        }
        Map<String, Map<String, List<Cas>>> chosen = sloziRaspored();
        int unused = brojPraznih(chosen);
        return new Ishod(
                chosen,
                mekaCena(),
                cvorovi,
                vracanja,
                unused >= 10 || (vracanja <= 2 && brojKomada >= 8),
                unused,
                profil);
    }

    private boolean istekloVreme() {
        return cvorovi >= limitCvorova || System.nanoTime() > rok;
    }

    private boolean imaZaduzenja() {
        for (Nastavnik nastavnik : stanje.getNastavnici().values()) {
            if (!nastavnik.getDodele().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<String> provera() {
        List<String> razlozi = new ArrayList<>();
        proveriZakljucaneNastavnike(razlozi);
        proveriZakljucano(razlozi);

        for (Nastavnik nastavnik : stanje.getNastavnici().values()) {
            for (Zaduzenje dodela : nastavnik.getDodele()) {
                int zakljucan = brojZakljucanih(nastavnik.getIme(), dodela);
                int preostalo = Math.max(0, dodela.getNedeljno() - zakljucan);
                int maxDnevno = Pravila.jeBlok(dodela, stanje.getTipSkole())
                        ? 2
                        : dodela.getMaxDnevno();
                int kapacitet = nastavnik.getSlobodniDani().size() * maxDnevno;
                if (preostalo > kapacitet) {
                    razlozi.add(nastavnik.getIme() + " / " + dodela.getPredmet() + " / " + dodela.getOdeljenje()
                            + " treba još " + preostalo + " čas(ova), a u dostupnim danima staje samo "
                            + kapacitet + " (najviše " + dodela.getMaxDnevno() + " dnevno).");
                }
            }
        }
        if (razrednaNastava) {
            upozoriPredmetne(razlozi);
        }
        proveriBlokove(razlozi);

        for (String odeljenje : stanje.getOdeljenja()) {
            int odeljenjeRb = smenaIndeks(odeljenje);
            int empty = 0;
            int obavezneRupe = 0;
            for (int d = 0; d < nDays; d++) {
                int zauzeto = zauzetoOdeljenje[odeljenjeRb][d];
                empty += termini - Integer.bitCount(zauzeto);
                obavezneRupe += maskaZakljucanog(zauzeto);
            }
            int potrebno = preostaloOdeljenje[odeljenjeRb];
            if (potrebno > empty) {
                razlozi.add(odeljenje + " treba još " + potrebno + " čas(ova), a praznih polja ima " + empty + ".");
            }
            if (potrebno < obavezneRupe) {
                razlozi.add(odeljenje + " ima " + obavezneRupe
                        + " praznina između zaključanih časova koje mora da popuni, a preostalo je samo "
                        + potrebno + " čas(ova).");
            }
            if (!duzineOk(odeljenjeRb, "Monday", zauzetoOdeljenje[odeljenjeRb][danIndeks("Monday")], potrebno)) {
                razlozi.add(odeljenje + " ne može da ima školski dan od " + minDan + "–" + maxDan
                        + " časova koji počinje od 1. časa sa ovim nedeljnim fondom.");
            }
        }

        for (Nastavnik nastavnik : stanje.getNastavnici().values()) {
            int nastavnikRb = indeksNastavnika.getOrDefault(nastavnik.getIme(), 0);
            int potrebno = preostaloNastavnik[nastavnikRb];
            int kapacitet = 0;
            for (String dan : nastavnik.getSlobodniDani()) {
                int d = danIndeks(dan);
                for (int smenaRb = 0; smenaRb < imenaSmena.length; smenaRb++) {
                    String smena = imenaSmena[smenaRb];
                    int busy = zauzetoNastavnik[nastavnikRb][d][smenaRb];
                    for (int termin = 0; termin < termini; termin++) {
                        if ((busy & (1 << termin)) != 0) {
                            continue;
                        }
                        boolean hasEmptyClass = false;
                        for (int odeljenjeRb = 0; odeljenjeRb < imenaOdeljenja.length; odeljenjeRb++) {
                            if (!stanje.smenaZa(imenaOdeljenja[odeljenjeRb]).equals(smena)) {
                                continue;
                            }
                            if ((zauzetoOdeljenje[odeljenjeRb][d] & (1 << termin)) == 0) {
                                hasEmptyClass = true;
                                break;
                            }
                        }
                        if (hasEmptyClass) {
                            kapacitet += 1;
                        }
                    }
                }
            }
            if (potrebno > kapacitet) {
                razlozi.add(nastavnik.getIme() + " ima još " + potrebno
                        + " čas(ova), a slobodnih termina " + kapacitet + ".");
            }
        }

        return razlozi.stream().distinct().toList();
    }

    private void proveriBlokove(List<String> razlozi) {
        int smene = stanje.jednaSmenaUkljucena() ? 1 : Math.max(1, imenaSmena.length);
        int mestaDnevno = termini / 2;
        if (mestaDnevno <= 0) {
            return;
        }
        int limit = nDays * mestaDnevno * smene;
        Map<String, Integer> blocks = new LinkedHashMap<>();
        for (Komad komad : komadi) {
            if (komad.trajanje >= 2) {
                blocks.merge(komad.nastavnik, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : blocks.entrySet()) {
            if (entry.getValue() <= limit) {
                continue;
            }
            String where = smene == 1 ? "jednoj smeni" : "obe smene";
            razlozi.add(entry.getKey() + " ima " + entry.getValue()
                    + " spojena bloka, a u " + where + " staje najviše " + limit
                    + " (po " + mestaDnevno + " na dan).");
        }
    }

    private void upozoriPredmetne(List<String> razlozi) {
        int suffixSlots = Dani.SVI.size() * Math.max(1, maxDan - minDan + 1);
        Map<String, Map<String, Integer>> sati = new LinkedHashMap<>();
        for (Nastavnik nastavnik : stanje.getNastavnici().values()) {
            if (Pravila.ucitelj(nastavnik)) {
                continue;
            }
            for (Zaduzenje dodela : nastavnik.getDodele()) {
                sati.computeIfAbsent(nastavnik.getIme(), nista -> new LinkedHashMap<>())
                        .merge(stanje.smenaZa(dodela.getOdeljenje()), dodela.getNedeljno(), Integer::sum);
            }
        }
        for (Map.Entry<String, Map<String, Integer>> teacherHours : sati.entrySet()) {
            for (Map.Entry<String, Integer> satiSmene : teacherHours.getValue().entrySet()) {
                if (satiSmene.getValue() > suffixSlots) {
                    razlozi.add(teacherHours.getKey() + " ima " + satiSmene.getValue()
                            + " časova u jednoj smeni. Za 1–4 (učiteljica od 1. časa, 0 pauza) predmetni nastavnik "
                            + "staje u najviše " + suffixSlots + " časova po smeni. Dodajte još jednog nastavnika.");
                }
            }
        }
    }

    private void proveriZakljucaneNastavnike(List<String> razlozi) {
        for (String odeljenje : stanje.getOdeljenja()) {
            for (String dan : Dani.SVI) {
                List<Cas> celije = zakljucanRaspored.get(odeljenje).get(dan);
                for (int termin = 0; termin < celije.size(); termin++) {
                    Cas cas = celije.get(termin);
                    if (cas == null) {
                        continue;
                    }
                    if (!stanje.getNastavnici().containsKey(cas.getNastavnik())) {
                        razlozi.add(cas.getNastavnik() + " nije u listi nastavnika.");
                        continue;
                    }
                    if (!slobodanDan[indeksNastavnika.get(cas.getNastavnik())][danIndeks(dan)]) {
                        razlozi.add(cas.getNastavnik() + " ima zaključan čas na dan kada nije dostupan (" + dan + ").");
                    }
                }
            }
        }
    }

    private void proveriZakljucano(List<String> razlozi) {
        for (String dan : Dani.SVI) {
            for (int termin = 0; termin < termini; termin++) {
                Set<String> vidjeno = new HashSet<>();
                for (String odeljenje : stanje.getOdeljenja()) {
                    Cas cas = zakljucanRaspored.get(odeljenje).get(dan).get(termin);
                    if (cas == null) {
                        continue;
                    }
                    if (!vidjeno.add(cas.getNastavnik())) {
                        razlozi.add(cas.getNastavnik() + " je zaključan u dva odeljenja u isto vreme: "
                                + dan + ", čas " + (termin + 1) + ".");
                    }
                    if (Pravila.odPrvogCasa(stanje.getTipSkole())) {
                        int maxDuzina = Pravila.graniceDana(stanje.getTipSkole())[1];
                        if (termin >= maxDuzina) {
                            razlozi.add(odeljenje + " ima zaključan čas posle " + maxDuzina
                                    + ". časa (" + dan + "). U osnovnoj i srednjoj dan ide od 1. do najviše "
                                    + maxDuzina + ". časa.");
                        }
                    }
                }
            }
        }

        for (String odeljenje : stanje.getOdeljenja()) {
            for (String dan : Dani.SVI) {
                Map<String, Integer> brojevi = new HashMap<>();
                Map<String, Integer> maksimalni = new HashMap<>();
                Map<String, String> natpisi = new HashMap<>();
                for (Cas cas : zakljucanRaspored.get(odeljenje).get(dan)) {
                    if (cas == null) {
                        continue;
                    }
                    String kljuc = cas.getPredmet().toLowerCase(Locale.ROOT);
                    brojevi.merge(kljuc, 1, Integer::sum);
                    maksimalni.merge(kljuc, cas.getMaxDnevno(), Math::min);
                    natpisi.put(kljuc, cas.getPredmet());
                }
                for (Map.Entry<String, Integer> entry : brojevi.entrySet()) {
                    if (entry.getValue() > maksimalni.getOrDefault(entry.getKey(), 1)) {
                        razlozi.add(odeljenje + " ima zaključan predmet " + natpisi.getOrDefault(entry.getKey(), entry.getKey())
                                + " " + entry.getValue() + " puta u " + dan + ".");
                    }
                }
            }
        }
    }

    private int maskaZakljucanog(int zauzeto) {
        if (zauzeto == 0) {
            return 0;
        }
        int start = danOdPrvog ? 0 : occMin(zauzeto);
        int max = occMax(zauzeto);
        int rupe = 0;
        for (int termin = start; termin <= max; termin++) {
            if ((zauzeto & (1 << termin)) == 0) {
                rupe += 1;
            }
        }
        return rupe;
    }

    private void sloziKomade() {
        komadi.clear();
        fondSmene.clear();
        limitSmene.clear();
        Arrays.fill(preostaloNastavnik, 0);
        for (int[] red : preostaloSmena) {
            Arrays.fill(red, 0);
        }
        Arrays.fill(preostaloOdeljenje, 0);
        for (Nastavnik nastavnik : stanje.getNastavnici().values()) {
            int nastavnikRb = indeksNastavnika.getOrDefault(nastavnik.getIme(), 0);
            for (Zaduzenje dodela : nastavnik.getDodele()) {
                int preostalo = Math.max(0, dodela.getNedeljno() - brojZakljucanih(nastavnik.getIme(), dodela));
                boolean block = Pravila.jeBlok(dodela, stanje.getTipSkole());
                int maxDnevno = block ? 2 : dodela.getMaxDnevno();
                String smena = stanje.smenaZa(dodela.getOdeljenje());
                dodajUgnjezdeno(limitSmene, nastavnik.getIme(), smena, maxDnevno);
                dodajUgnjezdeno(fondSmene, nastavnik.getIme(), smena, brojZakljucanih(nastavnik.getIme(), dodela));
                Integer odeljenjeRb = indeksOdeljenja.get(dodela.getOdeljenje());
                while (preostalo > 0) {
                    int trajanje = (block && preostalo >= 2) ? 2 : 1;
                    komadi.add(new Komad(
                            nastavnik.getIme(),
                            dodela.getOdeljenje(),
                            dodela.getPredmet(),
                            dodela.getPredmet().toLowerCase(Locale.ROOT),
                            maxDnevno,
                            trajanje));
                    preostaloNastavnik[nastavnikRb] += trajanje;
                    preostaloSmena[nastavnikRb][indeksSmene.getOrDefault(smena, 0)] += trajanje;
                    dodajUgnjezdeno(fondSmene, nastavnik.getIme(), smena, trajanje);
                    if (odeljenjeRb != null) {
                        preostaloOdeljenje[odeljenjeRb] += trajanje;
                    }
                    preostalo -= trajanje;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void pocetneDomene() {
        domene = new List[komadi.size()];
        for (int id = 0; id < komadi.size(); id++) {
            domene[id] = dozvoljenaMesta(id);
        }
    }

    private void sloziSusede() {
        susedi = new int[komadi.size()][];
        Map<String, List<Integer>> byTeacher = new HashMap<>();
        Map<String, List<Integer>> bySection = new HashMap<>();
        for (int id = 0; id < komadi.size(); id++) {
            Komad komad = komadi.get(id);
            byTeacher.computeIfAbsent(komad.nastavnik, nista -> new ArrayList<>()).add(id);
            bySection.computeIfAbsent(komad.odeljenje, nista -> new ArrayList<>()).add(id);
        }
        for (int id = 0; id < komadi.size(); id++) {
            Komad komad = komadi.get(id);
            Set<Integer> vidjeno = new LinkedHashSet<>();
            for (int onaj : byTeacher.getOrDefault(komad.nastavnik, List.of())) {
                if (onaj != id) {
                    vidjeno.add(onaj);
                }
            }
            for (int onaj : bySection.getOrDefault(komad.odeljenje, List.of())) {
                if (onaj != id) {
                    vidjeno.add(onaj);
                }
            }
            int[] idjevi = new int[vidjeno.size()];
            int rb = 0;
            for (int onaj : vidjeno) {
                idjevi[rb++] = onaj;
            }
            susedi[id] = idjevi;
        }
    }

    private List<Mesto> dozvoljenaMesta(int id) {
        List<Mesto> mesta = new ArrayList<>();
        for (String dan : Dani.SVI) {
            for (int termin = 0; termin < termini; termin++) {
                if (sme(id, dan, termin)) {
                    mesta.add(new Mesto(dan, termin));
                }
            }
        }
        return mesta;
    }

    private boolean traziDokNeNadje() {
        if (trazi()) {
            return true;
        }
        if (!isteklo || razrednaNastava || !trebaJosJednom()) {
            return false;
        }
        skloniSve();
        osveziDomene();
        pauzeVazne = false;
        opustiPauze = true;
        soRedosleda = 11;
        isteklo = false;
        cvorovi = 0;
        limitCvorova = Math.max(2_500_000, Math.min(limitCvorova, 8_000_000));
        rok = System.nanoTime() + 30_000_000_000L;
        return trazi();
    }

    private boolean trebaJosJednom() {
        return stanje.jednaSmenaUkljucena() || stanje.getOdeljenja().size() >= 17 || komadi.size() > 400;
    }

    private boolean trazi() {
        if (istekloVreme()) {
            if (razrednaNastava && !najDodela.isEmpty()) {
                return true;
            }
            isteklo = true;
            return false;
        }
        Integer id = izaberiSlobodan();
        if (id == null) {
            return sacuvajResenje();
        }
        List<Mesto> values = poredjanaMesta(id);
        List<Mesto>[] savedDomains = snimakDomena();
        for (Mesto mesto : values) {
            cvorovi += 1;
            if (istekloVreme()) {
                vratiDomene(savedDomains);
                if (razrednaNastava && !najDodela.isEmpty()) {
                    return true;
                }
                isteklo = true;
                return false;
            }
            if (!sme(id, mesto.dan, mesto.termin)) {
                continue;
            }
            stavi(id, mesto.dan, mesto.termin);
            if (unapred(id) && trazi()) {
                return true;
            }
            skloni(id);
            vratiDomene(savedDomains);
            vracanja += 1;
            if (isteklo) {
                return false;
            }
        }
        return false;
    }

    private boolean sacuvajResenje() {
        int rang = greskeUcitelja() * 100_000 + mekaCena();
        if (najDodela.isEmpty() || rang < najCenaDodele) {
            najCenaDodele = rang;
            najDodela.clear();
            najDodela.putAll(dodela);
        }
        if (!razrednaNastava) {
            return true;
        }
        if (greskeUcitelja() == 0 && cvorovi == 0) {
            return true;
        }
        if (!nasaoKomplet) {
            nasaoKomplet = true;
            rok = Math.min(rok, System.nanoTime() + 8_000_000_000L);
            return false;
        }
        return greskeUcitelja() == 0 && cvorovi > komadi.size() * 4;
    }

    private Integer izaberiSlobodan() {
        Integer najbolji = null;
        int najRang = Integer.MAX_VALUE;
        int najVelicina = Integer.MAX_VALUE;
        int bestDegree = -1;
        for (int id = 0; id < komadi.size(); id++) {
            if (dodeljen[id]) {
                continue;
            }
            Komad komad = komadi.get(id);
            int size = domene[id].size();
            int rang;
            if (razrednaNastava) {

                rang = oznakaUcitelja[komad.nastavnikRb] ? 2 : 0;
            } else {
                rang = komad.trajanje == 2 ? 1 : 2;
            }
            int degree = preostaloNastavnik[komad.nastavnikRb] + preostaloOdeljenje[komad.odeljenjeRb];
            if (rang < najRang || (rang == najRang && size < najVelicina)
                    || (rang == najRang && size == najVelicina && degree > bestDegree)) {
                najbolji = id;
                najRang = rang;
                najVelicina = size;
                bestDegree = degree;
            }
        }
        return najbolji;
    }

    private List<Mesto> poredjanaMesta(int id) {
        List<Mesto> mesta = domene[id];
        List<int[]> redosled = new ArrayList<>();
        for (int rb = 0; rb < mesta.size(); rb++) {
            Mesto mesto = mesta.get(rb);
            redosled.add(new int[] {mekaOcena(id, mesto.dan, mesto.termin), rb});
        }
        redosled.sort(Comparator.comparingInt((int[] red) -> red[0]).thenComparingInt(red -> red[1]));
        List<Mesto> ranked = new ArrayList<>();
        for (int[] red : redosled) {
            ranked.add(mesta.get(red[1]));
        }
        return ranked;
    }

    private int mekaOcena(int id, String dan, int termin) {
        Komad komad = komadi.get(id);
        int d = danIndeks(dan);
        int maskaNastavnika = zauzetoNastavnik[komad.nastavnikRb][d][komad.smenaRb];
        int teacherLoad = Integer.bitCount(maskaNastavnika);
        int classLoad = Integer.bitCount(zauzetoOdeljenje[komad.odeljenjeRb][d]);
        int posle = maskaNastavnika | bitovi(termin, komad.trajanje);
        int gapDelta = brojRupa(posle) - brojRupa(maskaNastavnika);

        int lcv = 0;
        if (komadi.size() <= 80) {
            for (int idOnog = 0; idOnog < komadi.size(); idOnog++) {
                if (idOnog == id || dodeljen[idOnog]) {
                    continue;
                }
                Komad onaj = komadi.get(idOnog);
                if (onaj.nastavnikRb != komad.nastavnikRb && onaj.odeljenjeRb != komad.odeljenjeRb) {
                    continue;
                }
                for (Mesto mesto : domene[idOnog]) {
                    if (mesto.dan.equals(dan) && mesto.termin == termin) {
                        lcv += 1;
                        break;
                    }
                }
            }
        }

        int gapWeight = ("pauze".equals(profil) || stanje.jednaSmenaUkljucena()) ? 50
                : ("balanced".equals(profil) ? 18 : 4);
        int ocenaUcitelja = 0;
        if (oznakaUcitelja[komad.nastavnikRb]) {
            int nextPrefix = Integer.numberOfTrailingZeros(~maskaNastavnika);
            if (termin != nextPrefix) {
                ocenaUcitelja += 120;
            }
            if ((maskaNastavnika & 1) == 0 && termin != 0) {
                ocenaUcitelja += 240;
            }
            ocenaUcitelja += gapDelta * 80;
        } else if (razrednaNastava) {
            ocenaUcitelja -= termin * 25;
            if (termin == maxDan - 1 && preostaloPeti(komad.odeljenjeRb) <= 0) {
                ocenaUcitelja += 90;
            }
        }
        int salt = 0;
        if (soRedosleda != 0) {
            salt = (id * 31 + termin * 7 + d * 13 + soRedosleda) & 7;
        }
        return termin + teacherLoad * 4 + classLoad * 3 + gapDelta * gapWeight + lcv
                + ocenaUcitelja + ocenaOgledala(id, dan, termin) + salt;
    }

    private int ocenaOgledala(int id, String dan, int termin) {
        if (parRb.length == 0) {
            return 0;
        }
        Komad komad = komadi.get(id);
        int rbPara = parRb[komad.odeljenjeRb];
        if (rbPara < 0) {
            return 0;
        }
        int d = danIndeks(dan);
        int match = 0;
        int empty = 0;
        int neslaganje = 0;
        for (int offset = 0; offset < komad.trajanje; offset++) {
            int slotPeriod = termin + offset;
            if (slotPeriod >= termini) {
                neslaganje += 1;
                continue;
            }
            String kljucPara = predmetUDanu[rbPara][d][slotPeriod];
            if (kljucPara == null) {
                empty += 1;
            } else if (kljucPara.equals(komad.kljucPredmeta)) {
                match += 1;
            } else {
                neslaganje += 1;
            }
        }
        return neslaganje * 8 - match * 30 - empty * 6;
    }

    private boolean unapred(int placedId) {
        for (int id : susedi[placedId]) {
            if (dodeljen[id]) {
                continue;
            }
            List<Mesto> filtered = new ArrayList<>();
            for (Mesto mesto : domene[id]) {
                if (sme(id, mesto.dan, mesto.termin)) {
                    filtered.add(mesto);
                }
            }
            domene[id] = filtered;
            if (filtered.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean sme(int id, String dan, int termin) {
        Komad komad = komadi.get(id);
        int d = danIndeks(dan);
        if (termin < 0 || termin + komad.trajanje > termini) {
            return false;
        }
        if (!slobodanDan[komad.nastavnikRb][d]) {
            return false;
        }
        int maska = bitovi(termin, komad.trajanje);
        if ((zauzetoOdeljenje[komad.odeljenjeRb][d] & maska) != 0) {
            return false;
        }
        if ((zauzetoNastavnik[komad.nastavnikRb][d][komad.smenaRb] & maska) != 0) {
            return false;
        }
        if (brojPredmeta[komad.odeljenjeRb][d][komad.predmetRb] + komad.trajanje > komad.maxDnevno) {
            return false;
        }
        if (!zbijenoOk(komad.odeljenjeRb, d, termin, komad.trajanje)) {
            return false;
        }
        if (razrednaNastava && !oznakaUcitelja[komad.nastavnikRb]) {
            if (termin < minDan - 2) {
                return false;
            }
            if (termin == minDan - 2
                    && (zauzetoOdeljenje[komad.odeljenjeRb][d] & (1 << (termin + 1))) == 0) {
                return false;
            }
        }
        return pauzeOk(komad.nastavnikRb, d, komad.smenaRb, termin, komad.trajanje);
    }

    private int neizbezniUsamljeni(String nastavnik, String smena) {
        int ukupno = ugnjezdeno(fondSmene, nastavnik, smena);
        if (ukupno <= 0) {
            return 0;
        }
        if (ukupno == 1) {
            return 1;
        }
        int limit = Math.min(termini, Math.max(1, ugnjezdeno(limitSmene, nastavnik, smena)));
        Integer nastavnikRb = indeksNastavnika.get(nastavnik);
        boolean uciteljski = nastavnikRb != null && oznakaUcitelja[nastavnikRb];
        limit = Math.min(limit, Pravila.limitPosete(stanje.getTipSkole(), uciteljski));
        if (limit <= 1) {
            return ukupno;
        }
        if (limit == 2) {
            return ukupno % 2;
        }
        return 0;
    }

    private boolean zbijenoOk(int odeljenjeRb, int danRb, int termin, int trajanje) {
        if (danOdPrvog && (termin < 0 || termin + trajanje > maxDan)) {
            return false;
        }
        int zauzeto = zauzetoOdeljenje[odeljenjeRb][danRb] | bitovi(termin, trajanje);
        int min = occMin(zauzeto);
        int max = occMax(zauzeto);
        if (danOdPrvog && max >= maxDan) {
            return false;
        }
        int start = danOdPrvog ? 0 : min;
        int rupe = 0;
        for (int p = start; p <= max; p++) {
            if ((zauzeto & (1 << p)) == 0) {
                rupe += 1;
            }
        }
        int preostaloPosle = preostaloOdeljenje[odeljenjeRb] - trajanje;
        if (rupe > preostaloPosle) {
            return false;
        }
        return duzineOk(odeljenjeRb, Dani.SVI.get(danRb), zauzeto, preostaloPosle);
    }

    private boolean pauzeOk(int nastavnikRb, int danRb, int smenaRb, int termin, int trajanje) {
        int zauzeto = zauzetoNastavnik[nastavnikRb][danRb][smenaRb] | bitovi(termin, trajanje);
        int rupe = brojRupa(zauzeto);
        if (oznakaUcitelja[nastavnikRb]) {
            return rupe == 0 && (zauzeto & 1) != 0;
        }
        if (!pauzeVazne) {
            return true;
        }
        int preostaloPosle = preostaloSmena[nastavnikRb][smenaRb] - trajanje;
        int fillable = Math.min(Math.max(0, preostaloPosle), rupe);

        int dozvoljeno = stanje.jednaSmenaUkljucena() ? 3 : 1;
        if (opustiPauze && "pauze".equals(profil)) {
            dozvoljeno = Math.max(dozvoljeno, 3);
        }
        return (rupe - fillable) <= dozvoljeno;
    }

    private int rezervaPauza() {
        return "pauze".equals(profil) ? Pravila.dozvolaPauza(imenaNastavnika.length) : 0;
    }

    private boolean duzineOk(
            int odeljenjeRb, String extraDay, int extraOccupied, int preostaloPosle) {
        int extraDi = danIndeks(extraDay);
        int potrebneRupe = 0;
        int zbirTereta = 0;
        for (int d = 0; d < nDays; d++) {
            int zauzeto = d == extraDi ? extraOccupied : zauzetoOdeljenje[odeljenjeRb][d];
            if (zauzeto == 0) {
                teretDana[d] = 0;
                continue;
            }
            int maxP = occMax(zauzeto);
            int broj = Integer.bitCount(zauzeto);
            if (danOdPrvog) {
                if (maxP >= maxDan) {
                    return false;
                }
                int raspon = maxP + 1;
                potrebneRupe += raspon - broj;
                teretDana[d] = raspon;
            } else {
                teretDana[d] = broj;
            }
            zbirTereta += teretDana[d];
        }
        if (potrebneRupe > preostaloPosle) {
            return false;
        }
        int levo = preostaloPosle - potrebneRupe;
        if (levo == 0) {
            boolean requireMin = danOdPrvog && zbirTereta >= 5 * minDan;
            int floor = requireMin ? minDan : 1;
            for (int i = 0; i < nDays; i++) {
                int teret = teretDana[i];
                if (teret == 0) {
                    continue;
                }
                if (teret < floor || teret > maxDan) {
                    return false;
                }
            }
            return true;
        }
        int ukupno = zbirTereta + levo;
        boolean requireMin = danOdPrvog && ukupno >= 5 * minDan;
        return mozeRasporediti(levo, requireMin ? minDan : 1, maxDan);
    }

    private boolean mozeRasporediti(int levo, int minLen, int maxDuzina) {
        boolean[] moguce;
        boolean[] next;
        if (levo < mozeRaspodela.length && levo < daljeRaspodela.length) {
            moguce = mozeRaspodela;
            next = daljeRaspodela;
            Arrays.fill(moguce, 0, levo + 1, false);
        } else {
            moguce = new boolean[levo + 1];
            next = new boolean[levo + 1];
        }
        moguce[0] = true;
        for (int i = 0; i < nDays; i++) {
            int teret = teretDana[i];
            Arrays.fill(next, 0, levo + 1, false);
            if (teret == 0) {
                for (int potroseno = 0; potroseno <= levo; potroseno++) {
                    if (!moguce[potroseno]) {
                        continue;
                    }
                    next[potroseno] = true;
                    for (int add = minLen; add <= maxDuzina && potroseno + add <= levo; add++) {
                        next[potroseno + add] = true;
                    }
                }
            } else {
                int from = Math.max(0, minLen - teret);
                int to = maxDuzina - teret;
                if (to < from) {
                    return false;
                }
                for (int potroseno = 0; potroseno <= levo; potroseno++) {
                    if (!moguce[potroseno]) {
                        continue;
                    }
                    for (int add = from; add <= to && potroseno + add <= levo; add++) {
                        next[potroseno + add] = true;
                    }
                }
            }
            boolean[] swap = moguce;
            moguce = next;
            next = swap;
        }
        return moguce[levo];
    }

    private void stavi(int id, String dan, int termin) {
        Komad komad = komadi.get(id);
        int d = danIndeks(dan);
        dodela.put(id, new Mesto(dan, termin));
        dodeljen[id] = true;
        int maska = bitovi(termin, komad.trajanje);
        zauzetoOdeljenje[komad.odeljenjeRb][d] |= maska;
        zauzetoNastavnik[komad.nastavnikRb][d][komad.smenaRb] |= maska;
        brojPredmeta[komad.odeljenjeRb][d][komad.predmetRb] += komad.trajanje;
        for (int offset = 0; offset < komad.trajanje; offset++) {
            predmetUDanu[komad.odeljenjeRb][d][termin + offset] = komad.kljucPredmeta;
        }
        preostaloNastavnik[komad.nastavnikRb] -= komad.trajanje;
        preostaloSmena[komad.nastavnikRb][komad.smenaRb] -= komad.trajanje;
        preostaloOdeljenje[komad.odeljenjeRb] -= komad.trajanje;
    }

    private void skloni(int id) {
        Mesto stavljen = dodela.remove(id);
        if (stavljen == null) {
            return;
        }
        dodeljen[id] = false;
        Komad komad = komadi.get(id);
        int d = danIndeks(stavljen.dan);
        int maska = bitovi(stavljen.termin, komad.trajanje);
        zauzetoOdeljenje[komad.odeljenjeRb][d] &= ~maska;
        zauzetoNastavnik[komad.nastavnikRb][d][komad.smenaRb] &= ~maska;
        brojPredmeta[komad.odeljenjeRb][d][komad.predmetRb] -= komad.trajanje;
        for (int offset = 0; offset < komad.trajanje; offset++) {
            predmetUDanu[komad.odeljenjeRb][d][stavljen.termin + offset] = null;
        }
        preostaloNastavnik[komad.nastavnikRb] += komad.trajanje;
        preostaloSmena[komad.nastavnikRb][komad.smenaRb] += komad.trajanje;
        preostaloOdeljenje[komad.odeljenjeRb] += komad.trajanje;
    }

    private void lokalno() {
        if (komadi.isEmpty() || dodela.isEmpty()) {
            return;
        }
        boolean fokusNastavnika = "pauze".equals(profil);
        if ((komadi.size() > 60 && !razrednaNastava) || fokusNastavnika) {
            smanjiUsamljene();
        }
        boolean krupno = komadi.size() > 60 && !razrednaNastava;
        pohlepno(
                fokusNastavnika ? 2 : (krupno ? 2 : 3),
                fokusNastavnika ? 2_000_000_000L : (krupno ? 5_000_000_000L : 3_000_000_000L));
        kaljenje(fokusNastavnika
                ? 5_000_000_000L
                : (krupno ? 12_000_000_000L : (razrednaNastava ? 7_000_000_000L : 6_000_000_000L)));
        if (fokusNastavnika) {
            sabijIzbezive();
        }
    }

    private void pohlepno(int maxRounds, long budzetNano) {
        boolean krupno = komadi.size() > 60 && !razrednaNastava;
        int najbolji = mekaCena();
        long doKada = System.nanoTime() + budzetNano;
        for (int round = 0; round < maxRounds; round++) {
            if (System.nanoTime() > doKada) {
                return;
            }
            boolean bolje = false;
            for (int id = 0; id < komadi.size(); id++) {
                Integer pomeren = boljePomeranjem(id, najbolji);
                if (pomeren != null) {
                    najbolji = pomeren;
                    bolje = true;
                }
            }
            for (int i = 0; i < komadi.size(); i++) {
                Komad levo = komadi.get(i);
                for (int j = i + 1; j < komadi.size(); j++) {
                    Komad desno = komadi.get(j);
                    if (krupno && !levo.odeljenje.equals(desno.odeljenje) && !levo.nastavnik.equals(desno.nastavnik)) {
                        continue;
                    }
                    Integer zamenjeno = boljeZamenom(i, j, najbolji);
                    if (zamenjeno != null) {
                        najbolji = zamenjeno;
                        bolje = true;
                    }
                }
            }
            if (!bolje) {
                return;
            }
        }
    }

    private void kaljenje(long budzetNano) {
        if (dodela.isEmpty()) {
            return;
        }
        Random slucajno = new Random(31L * komadi.size() + 20260920L);
        Map<Integer, Mesto> najbolji = snimiDodelu();
        int najOcena = metaOcena();
        najKaljenje = najOcena;
        int sad = najOcena;
        double temperatura = Math.max(60.0, najOcena / 80.0);
        double pocetnaTemp = temperatura;
        double cooling = 0.997;
        int tabuRok = Math.max(18, Math.min(72, komadi.size() / 8));
        int odNajboljeg = 0;
        Map<Long, Integer> tabu = new HashMap<>();
        double[] tezina = "pauze".equals(profil)
                ? new double[] {1.0, 1.1, 2.8, 1.8, razrednaNastava ? 0.15 : (imaParove() ? 0.6 : 0.1)}
                : razrednaNastava
                        ? new double[] {1.1, 1.0, 2.4, 1.3, 0.15}
                        : new double[] {1.2, 1.2, 2.0, 1.4, imaParove() ? 1.3 : 0.1};
        long doKada = System.nanoTime() + budzetNano;
        int krug = 0;
        while (System.nanoTime() < doKada) {
            krug += 1;
            tabuPotez = false;
            int potez = izaberiPotez(tezina, slucajno);
            int pre = sad;
            boolean applied = switch (potez) {
                case 1 -> kaljenjeZameni(slucajno, tabu, krug, tabuRok, temperatura);
                case 2 -> kaljenjeSpoji(slucajno, tabu, krug, tabuRok, temperatura);
                case 3 -> kaljenjeZatvori(slucajno, tabu, krug, tabuRok, temperatura);
                case 4 -> kaljenjeOgledalo(slucajno, tabu, krug, tabuRok, temperatura);
                default -> kaljenjePomeri(slucajno, tabu, krug, tabuRok, temperatura);
            };
            if (!applied) {
                tezina[potez] = Math.max(0.12, tezina[potez] * 0.96);
                temperatura *= cooling;
                continue;
            }
            sad = metaOcena();
            if (sad < pre) {
                tezina[potez] += 0.35;
            } else if (sad == pre) {
                tezina[potez] += 0.04;
            } else {
                tezina[potez] = Math.max(0.12, tezina[potez] * 0.98);
            }
            if (sad < najOcena) {
                najOcena = sad;
                najKaljenje = najOcena;
                najbolji = snimiDodelu();
                odNajboljeg = 0;
            } else if (++odNajboljeg >= 480) {
                temperatura = Math.min(pocetnaTemp, Math.max(8.0, temperatura * 2.5));
                odNajboljeg = 0;
            }
            temperatura *= cooling;
            if ((krug & 255) == 0) {
                int trenutno = krug;
                tabu.entrySet().removeIf(entry -> entry.getValue() < trenutno);
            }
        }
        if (metaOcena() != najOcena) {
            vratiDodelu(najbolji);
        }
    }

    private int metaOcena() {
        int ocena = mekaCena();
        if (razrednaNastava) {
            ocena += greskeUcitelja() * 50_000;
        }
        if (danOdPrvog) {
            ocena += greskeUcenika() * 800;
        }
        return ocena;
    }

    private boolean imaParove() {
        for (int par : parRb) {
            if (par >= 0) {
                return true;
            }
        }
        return false;
    }

    private int izaberiPotez(double[] tezina, Random slucajno) {
        double sum = 0;
        for (double vrednost : tezina) {
            sum += vrednost;
        }
        double izbor = slucajno.nextDouble() * sum;
        double vidjeno = 0;
        for (int i = 0; i < tezina.length; i++) {
            vidjeno += tezina[i];
            if (izbor <= vidjeno) {
                return i;
            }
        }
        return tezina.length - 1;
    }

    private boolean prihvati(int pre, int posle, double temperatura, Random slucajno) {
        if (razrednaNastava && greskeUcitelja() > 0) {
            return false;
        }
        if (tabuPotez) {
            return posle < najKaljenje;
        }
        if (posle <= pre) {
            return true;
        }
        return slucajno.nextDouble() < Math.exp(-(posle - pre) / Math.max(0.75, temperatura));
    }

    private int greskeUcenika() {
        if (!danOdPrvog) {
            return 0;
        }
        int greske = 0;
        for (int s = 0; s < imenaOdeljenja.length; s++) {
            int nedeljniFond = 0;
            int[] brojevi = new int[nDays];
            for (int d = 0; d < nDays; d++) {
                int zauzeca = zauzetoOdeljenje[s][d];
                int broj = Integer.bitCount(zauzeca);
                brojevi[d] = broj;
                nedeljniFond += broj;
                if (broj == 0) {
                    continue;
                }
                if ((zauzeca & 1) == 0) {
                    greske += 3;
                }
                if (occMax(zauzeca) >= maxDan) {
                    greske += 3;
                }
                int rupe = occMax(zauzeca) + 1 - broj;
                if ((zauzeca & 1) != 0) {
                    rupe = occMax(zauzeca) + 1 - broj;
                } else {
                    rupe = occMax(zauzeca) - occMin(zauzeca) + 1 - broj;
                }
                greske += Math.max(0, rupe);
            }
            if (nedeljniFond >= 5 * minDan) {
                for (int broj : brojevi) {
                    if (broj > 0 && broj < minDan) {
                        greske += 4;
                    }
                }
            }
        }
        return greske;
    }

    private long tabuKljuc(int id, String dan, int termin) {
        return ((long) id << 32) | ((long) danIndeks(dan) << 8) | termin;
    }

    private boolean jeTabu(Map<Long, Integer> tabu, int krug, int id, String dan, int termin) {
        Integer doKada = tabu.get(tabuKljuc(id, dan, termin));
        return doKada != null && doKada > krug;
    }

    private void oznaciTabu(Map<Long, Integer> tabu, int krug, int tabuRok, int id, Mesto polaziste) {
        tabu.put(tabuKljuc(id, polaziste.dan, polaziste.termin), krug + tabuRok);
    }

    private int izaberiKomad(Random slucajno) {
        if (!razrednaNastava && "pauze".equals(profil) && slucajno.nextDouble() < 0.7) {
            int[] idjevi = dodela.keySet().stream().mapToInt(Integer::intValue).toArray();
            for (int attempt = 0; attempt < 28 && idjevi.length > 0; attempt++) {
                int id = idjevi[slucajno.nextInt(idjevi.length)];
                if (posetaJeLos(id)) {
                    return id;
                }
            }
        }
        if (razrednaNastava && slucajno.nextDouble() < 0.72) {
            List<Integer> predmetniNastavnici = new ArrayList<>();
            for (int id : dodela.keySet()) {
                if (!oznakaUcitelja[komadi.get(id).nastavnikRb]) {
                    predmetniNastavnici.add(id);
                }
            }
            if (!predmetniNastavnici.isEmpty()) {
                return predmetniNastavnici.get(slucajno.nextInt(predmetniNastavnici.size()));
            }
        }
        int[] idjevi = dodela.keySet().stream().mapToInt(Integer::intValue).toArray();
        return idjevi[slucajno.nextInt(idjevi.length)];
    }

    private boolean posetaJeLos(int id) {
        Komad komad = komadi.get(id);
        for (int d = 0; d < nDays; d++) {
            int maska = zauzetoNastavnik[komad.nastavnikRb][d][komad.smenaRb];
            int broj = Integer.bitCount(maska);
            if (broj == 1 || brojRupa(maska) >= 2) {
                return true;
            }
        }
        return false;
    }

    private boolean kaljenjePomeri(Random slucajno, Map<Long, Integer> tabu, int krug, int tabuRok, double temperatura) {
        int id = izaberiKomad(slucajno);
        Mesto polaziste = dodela.get(id);
        if (polaziste == null) {
            return false;
        }
        int pre = metaOcena();
        skloni(id);
        List<Mesto> opcije = new ArrayList<>();
        List<Mesto> tabuOpcije = new ArrayList<>();
        for (String dan : Dani.SVI) {
            for (int termin = 0; termin < termini; termin++) {
                if (dan.equals(polaziste.dan) && termin == polaziste.termin) {
                    continue;
                }
                if (!sme(id, dan, termin)) {
                    continue;
                }
                if (jeTabu(tabu, krug, id, dan, termin)) {
                    tabuOpcije.add(new Mesto(dan, termin));
                } else {
                    opcije.add(new Mesto(dan, termin));
                }
            }
        }
        if (opcije.isEmpty()) {
            opcije = tabuOpcije;
            tabuPotez = !opcije.isEmpty();
        }
        if (opcije.isEmpty()) {
            stavi(id, polaziste.dan, polaziste.termin);
            return false;
        }
        Mesto odrediste = opcije.get(slucajno.nextInt(opcije.size()));
        stavi(id, odrediste.dan, odrediste.termin);
        int posle = metaOcena();
        if (prihvati(pre, posle, temperatura, slucajno)) {
            oznaciTabu(tabu, krug, tabuRok, id, polaziste);
            return true;
        }
        skloni(id);
        stavi(id, polaziste.dan, polaziste.termin);
        return false;
    }

    private boolean kaljenjeZameni(Random slucajno, Map<Long, Integer> tabu, int krug, int tabuRok, double temperatura) {
        int levo = izaberiKomad(slucajno);
        int desno = izaberiKomad(slucajno);
        if (levo == desno) {
            return false;
        }
        Komad leftToken = komadi.get(levo);
        Komad rightToken = komadi.get(desno);
        if (!leftToken.odeljenje.equals(rightToken.odeljenje) && !leftToken.nastavnik.equals(rightToken.nastavnik)
                && slucajno.nextDouble() < 0.7) {
            return false;
        }
        Mesto levoMesto = dodela.get(levo);
        Mesto desnoMesto = dodela.get(desno);
        if (levoMesto == null || desnoMesto == null) {
            return false;
        }
        tabuPotez = jeTabu(tabu, krug, levo, desnoMesto.dan, desnoMesto.termin)
                || jeTabu(tabu, krug, desno, levoMesto.dan, levoMesto.termin);
        int pre = metaOcena();
        skloni(levo);
        skloni(desno);
        if (!probajPar(levo, desnoMesto.dan, desnoMesto.termin, desno, levoMesto.dan, levoMesto.termin)) {
            stavi(levo, levoMesto.dan, levoMesto.termin);
            stavi(desno, desnoMesto.dan, desnoMesto.termin);
            return false;
        }
        int posle = metaOcena();
        if (prihvati(pre, posle, temperatura, slucajno)) {
            oznaciTabu(tabu, krug, tabuRok, levo, levoMesto);
            oznaciTabu(tabu, krug, tabuRok, desno, desnoMesto);
            return true;
        }
        skloni(levo);
        skloni(desno);
        stavi(levo, levoMesto.dan, levoMesto.termin);
        stavi(desno, desnoMesto.dan, desnoMesto.termin);
        return false;
    }

    private boolean kaljenjeSpoji(Random slucajno, Map<Long, Integer> tabu, int krug, int tabuRok, double temperatura) {
        List<Integer> usamljen = new ArrayList<>();
        for (Map.Entry<Integer, Mesto> entry : dodela.entrySet()) {
            Komad komad = komadi.get(entry.getKey());
            if (Integer.bitCount(zauzetoNastavnik[komad.nastavnikRb][danIndeks(entry.getValue().dan)][komad.smenaRb]) == 1) {
                usamljen.add(entry.getKey());
            }
        }
        if (usamljen.isEmpty()) {
            return false;
        }
        int idPredmetnog = usamljen.get(slucajno.nextInt(usamljen.size()));
        Komad predmetniCas = komadi.get(idPredmetnog);
        Mesto mestoPredmetnog = dodela.get(idPredmetnog);
        List<Integer> parovi = new ArrayList<>();
        for (Map.Entry<Integer, Mesto> entry : dodela.entrySet()) {
            Komad onaj = komadi.get(entry.getKey());
            if (entry.getKey() == idPredmetnog || onaj.odeljenjeRb != predmetniCas.odeljenjeRb) {
                continue;
            }
            if (entry.getValue().dan.equals(mestoPredmetnog.dan)) {
                continue;
            }
            parovi.add(entry.getKey());
        }
        if (parovi.isEmpty()) {
            return kaljenjePomeri(slucajno, tabu, krug, tabuRok, temperatura);
        }
        parovi.sort(Comparator.comparingInt(id -> dodela.get(id).termin).reversed());
        int idOnog = parovi.get(0);
        if (parovi.size() > 1 && slucajno.nextDouble() < 0.4) {
            idOnog = parovi.get(slucajno.nextInt(Math.min(3, parovi.size())));
        }
        Mesto mestoDrugog = dodela.get(idOnog);
        boolean teacherAlreadyThere =
                Integer.bitCount(zauzetoNastavnik[predmetniCas.nastavnikRb][danIndeks(mestoDrugog.dan)][predmetniCas.smenaRb]) > 0;
        if (!teacherAlreadyThere && slucajno.nextDouble() < 0.45) {
            return false;
        }
        tabuPotez = jeTabu(tabu, krug, idPredmetnog, mestoDrugog.dan, mestoDrugog.termin)
                || jeTabu(tabu, krug, idOnog, mestoPredmetnog.dan, mestoPredmetnog.termin);
        int pre = metaOcena();
        skloni(idPredmetnog);
        skloni(idOnog);
        boolean stavljen = false;
        if (sme(idOnog, mestoPredmetnog.dan, mestoPredmetnog.termin)) {
            stavi(idOnog, mestoPredmetnog.dan, mestoPredmetnog.termin);
            int[] sufiks = {maxDan - 1, minDan - 1, mestoDrugog.termin};
            for (int termin : sufiks) {
                if (sme(idPredmetnog, mestoDrugog.dan, termin)) {
                    stavi(idPredmetnog, mestoDrugog.dan, termin);
                    stavljen = true;
                    break;
                }
            }
            if (!stavljen) {
                skloni(idOnog);
            }
        }
        if (!stavljen) {
            stavi(idPredmetnog, mestoPredmetnog.dan, mestoPredmetnog.termin);
            stavi(idOnog, mestoDrugog.dan, mestoDrugog.termin);
            return false;
        }
        int posle = metaOcena();
        if (prihvati(pre, posle, temperatura, slucajno)) {
            oznaciTabu(tabu, krug, tabuRok, idPredmetnog, mestoPredmetnog);
            oznaciTabu(tabu, krug, tabuRok, idOnog, mestoDrugog);
            return true;
        }
        skloni(idPredmetnog);
        skloni(idOnog);
        stavi(idPredmetnog, mestoPredmetnog.dan, mestoPredmetnog.termin);
        stavi(idOnog, mestoDrugog.dan, mestoDrugog.termin);
        return false;
    }

    private boolean kaljenjeZatvori(Random slucajno, Map<Long, Integer> tabu, int krug, int tabuRok, double temperatura) {
        List<int[]> saRupom = new ArrayList<>();
        for (int t = 0; t < imenaNastavnika.length; t++) {
            for (int d = 0; d < nDays; d++) {
                for (int sh = 0; sh < imenaSmena.length; sh++) {
                    if (brojRupa(zauzetoNastavnik[t][d][sh]) > 0) {
                        saRupom.add(new int[] {t, d, sh});
                    }
                }
            }
        }
        if (saRupom.isEmpty()) {
            return false;
        }
        int[] meta = saRupom.get(slucajno.nextInt(saRupom.size()));
        List<Integer> svoje = new ArrayList<>();
        for (Map.Entry<Integer, Mesto> entry : dodela.entrySet()) {
            Komad komad = komadi.get(entry.getKey());
            if (komad.nastavnikRb == meta[0] && komad.smenaRb == meta[2]) {
                svoje.add(entry.getKey());
            }
        }
        if (svoje.isEmpty()) {
            return false;
        }
        int id = svoje.get(slucajno.nextInt(svoje.size()));
        Mesto polaziste = dodela.get(id);
        String dan = Dani.SVI.get(meta[1]);
        int pre = metaOcena();
        skloni(id);
        List<Mesto> opcije = new ArrayList<>();
        List<Mesto> tabuOpcije = new ArrayList<>();
        for (int termin = 0; termin < termini; termin++) {
            if (!sme(id, dan, termin)) {
                continue;
            }
            if (jeTabu(tabu, krug, id, dan, termin)) {
                tabuOpcije.add(new Mesto(dan, termin));
            } else {
                opcije.add(new Mesto(dan, termin));
            }
        }
        if (opcije.isEmpty()) {
            opcije = tabuOpcije;
            tabuPotez = !opcije.isEmpty();
        }
        if (opcije.isEmpty()) {
            stavi(id, polaziste.dan, polaziste.termin);
            return false;
        }
        Mesto odrediste = opcije.get(slucajno.nextInt(opcije.size()));
        stavi(id, odrediste.dan, odrediste.termin);
        int posle = metaOcena();
        if (prihvati(pre, posle, temperatura, slucajno)) {
            oznaciTabu(tabu, krug, tabuRok, id, polaziste);
            return true;
        }
        skloni(id);
        stavi(id, polaziste.dan, polaziste.termin);
        return false;
    }

    private boolean kaljenjeOgledalo(Random slucajno, Map<Long, Integer> tabu, int krug, int tabuRok, double temperatura) {
        if (!imaParove()) {
            return false;
        }
        int id = izaberiKomad(slucajno);
        Komad komad = komadi.get(id);
        int rbPara = parRb[komad.odeljenjeRb];
        if (rbPara < 0) {
            return false;
        }
        Mesto polaziste = dodela.get(id);
        List<Mesto> opcije = new ArrayList<>();
        List<Mesto> tabuOpcije = new ArrayList<>();
        for (int d = 0; d < nDays; d++) {
            for (int termin = 0; termin < termini; termin++) {
                String kljucPara = predmetUDanu[rbPara][d][termin];
                if (kljucPara == null || !kljucPara.equals(komad.kljucPredmeta)) {
                    continue;
                }
                String dan = Dani.SVI.get(d);
                if (jeTabu(tabu, krug, id, dan, termin)) {
                    tabuOpcije.add(new Mesto(dan, termin));
                } else {
                    opcije.add(new Mesto(dan, termin));
                }
            }
        }
        if (opcije.isEmpty()) {
            opcije = tabuOpcije;
            tabuPotez = !opcije.isEmpty();
        }
        if (opcije.isEmpty()) {
            return false;
        }
        Mesto odrediste = opcije.get(slucajno.nextInt(opcije.size()));
        if (odrediste.dan.equals(polaziste.dan) && odrediste.termin == polaziste.termin) {
            return false;
        }
        int pre = metaOcena();
        skloni(id);
        if (!sme(id, odrediste.dan, odrediste.termin)) {
            stavi(id, polaziste.dan, polaziste.termin);
            return false;
        }
        stavi(id, odrediste.dan, odrediste.termin);
        int posle = metaOcena();
        if (prihvati(pre, posle, temperatura, slucajno)) {
            oznaciTabu(tabu, krug, tabuRok, id, polaziste);
            return true;
        }
        skloni(id);
        stavi(id, polaziste.dan, polaziste.termin);
        return false;
    }

    private int greskeUcitelja() {
        int greske = 0;
        for (int t = 0; t < imenaNastavnika.length; t++) {
            if (!oznakaUcitelja[t]) {
                continue;
            }
            for (int d = 0; d < nDays; d++) {
                for (int sh = 0; sh < imenaSmena.length; sh++) {
                    int maska = zauzetoNastavnik[t][d][sh];
                    int broj = Integer.bitCount(maska);
                    if (broj == 0) {
                        continue;
                    }
                    if ((maska & 1) == 0) {
                        greske += 1;
                    }
                    greske += brojRupa(maska);
                }
            }
        }
        return greske;
    }

    private Map<Integer, Mesto> snimiDodelu() {
        return new LinkedHashMap<>(dodela);
    }

    private void vratiDodelu(Map<Integer, Mesto> sacuvano) {
        List<Integer> sad = new ArrayList<>(dodela.keySet());
        for (int id : sad) {
            skloni(id);
        }
        for (Map.Entry<Integer, Mesto> entry : sacuvano.entrySet()) {
            Mesto mesto = entry.getValue();
            stavi(entry.getKey(), mesto.dan, mesto.termin);
        }
    }

    private void osveziDomene() {
        for (int id = 0; id < komadi.size(); id++) {
            if (!dodeljen[id]) {
                domene[id] = dozvoljenaMesta(id);
            }
        }
    }

    private boolean sviKomadi() {
        for (int id = 0; id < komadi.size(); id++) {
            if (!dodeljen[id]) {
                return false;
            }
        }
        return true;
    }

    private void skloniSve() {
        List<Integer> posejano = new ArrayList<>();
        for (int id = 0; id < komadi.size(); id++) {
            if (dodeljen[id]) {
                posejano.add(id);
            }
        }
        for (int id : posejano) {
            skloni(id);
        }
    }

    private int preostaloPeti(int odeljenjeRb) {
        int occupiedHours = 0;
        int fives = 0;
        for (int d = 0; d < nDays; d++) {
            int zauzeca = zauzetoOdeljenje[odeljenjeRb][d];
            int broj = Integer.bitCount(zauzeca);
            occupiedHours += broj;
            if (broj > 0 && occMax(zauzeca) >= maxDan - 1) {
                fives += 1;
            }
        }
        int ukupno = occupiedHours + preostaloOdeljenje[odeljenjeRb];
        int potrebno = Math.max(0, ukupno - minDan * nDays);
        return potrebno - fives;
    }

    private int semeZbijeno() {
        List<Integer> redNastavnika = new ArrayList<>();
        for (int nastavnikRb = 0; nastavnikRb < imenaNastavnika.length; nastavnikRb++) {
            if (preostaloNastavnik[nastavnikRb] > 0) {
                redNastavnika.add(nastavnikRb);
            }
        }
        boolean[] hasBlock = new boolean[imenaNastavnika.length];
        for (Komad komad : komadi) {
            if (komad.trajanje >= 2) {
                hasBlock[komad.nastavnikRb] = true;
            }
        }
        int earlyCap = nDays * Math.max(1, maxDan - 1);
        redNastavnika.sort(Comparator
                .comparingInt((Integer t) -> hasBlock[t] ? 0 : 1)
                .thenComparingInt(t -> preostaloNastavnik[t] > earlyCap ? 1 : 0)
                .thenComparing(Comparator.comparingInt((Integer t) -> preostaloNastavnik[t]).reversed()));
        int stavljen = 0;
        int redniBroj = 0;
        bezKasnog = true;
        for (int nastavnikRb : redNastavnika) {
            semeNastavnik(nastavnikRb, redniBroj);
            redniBroj += 1;
        }
        bezKasnog = false;
        redniBroj = 0;
        for (int nastavnikRb : redNastavnika) {
            if (semeNastavnik(nastavnikRb, redniBroj)) {
                stavljen += 1;
            } else {
                skloniNastavnika(nastavnikRb);
            }
            redniBroj += 1;
        }
        return stavljen;
    }

    private boolean semeNastavnik(int nastavnikRb, int redniBroj) {
        List<Integer> idjevi = new ArrayList<>();
        for (int id = 0; id < komadi.size(); id++) {
            if (!dodeljen[id] && komadi.get(id).nastavnikRb == nastavnikRb) {
                idjevi.add(id);
            }
        }
        int[] kopija = new int[idjevi.size()];
        Map<Integer, Integer> seenSection = new HashMap<>();
        for (int i = 0; i < idjevi.size(); i++) {
            int odeljenjeRb = komadi.get(idjevi.get(i)).odeljenjeRb;
            kopija[i] = seenSection.merge(odeljenjeRb, 1, Integer::sum);
        }
        List<Integer> redosled = new ArrayList<>();
        for (int i = 0; i < idjevi.size(); i++) {
            redosled.add(i);
        }
        int rotation = imenaOdeljenja.length == 0 ? 0 : (redniBroj * 4) % imenaOdeljenja.length;
        redosled.sort(Comparator
                .comparingInt((Integer i) -> komadi.get(idjevi.get(i)).trajanje).reversed()
                .thenComparingInt(i -> kopija[i])
                .thenComparingInt(i -> {
                    int odeljenjeRb = komadi.get(idjevi.get(i)).odeljenjeRb;
                    return (odeljenjeRb - rotation + imenaOdeljenja.length) % imenaOdeljenja.length;
                }));
        List<Integer> sorted = new ArrayList<>();
        for (int i : redosled) {
            sorted.add(idjevi.get(i));
        }
        for (int id : sorted) {
            if (!staviZbijeno(id)) {
                return false;
            }
        }
        return true;
    }

    private boolean staviZbijeno(int id) {
        Komad komad = komadi.get(id);
        String najDan = null;
        int najTermin = Integer.MAX_VALUE;
        int bestLoad = Integer.MAX_VALUE;
        for (int d = 0; d < nDays; d++) {
            String dan = Dani.SVI.get(d);
            int teret = Integer.bitCount(zauzetoNastavnik[komad.nastavnikRb][d][komad.smenaRb]);
            for (int termin = 0; termin < termini; termin++) {
                if (bezKasnog && termin + komad.trajanje >= maxDan) {
                    continue;
                }
                if (!sme(id, dan, termin)) {
                    continue;
                }
                if (termin < najTermin || (termin == najTermin && teret < bestLoad)) {
                    najDan = dan;
                    najTermin = termin;
                    bestLoad = teret;
                }
            }
        }
        if (najDan == null) {
            return false;
        }
        stavi(id, najDan, najTermin);
        return true;
    }

    private int semePredmetni() {
        List<Integer> redNastavnika = new ArrayList<>();
        for (int t = 0; t < imenaNastavnika.length; t++) {
            if (!oznakaUcitelja[t] && preostaloNastavnik[t] > 0) {
                redNastavnika.add(t);
            }
        }
        redNastavnika.sort(Comparator.comparingInt((Integer t) -> preostaloNastavnik[t]).reversed());
        int stavljen = 0;
        for (int nastavnikRb : redNastavnika) {
            if (!semeJednog(nastavnikRb)) {
                skloniPredmetne();
                return 0;
            }
            stavljen += 1;
        }
        int specAssigned = 0;
        for (int id = 0; id < komadi.size(); id++) {
            if (dodeljen[id] && !oznakaUcitelja[komadi.get(id).nastavnikRb]) {
                specAssigned += 1;
            }
        }
        return specAssigned;
    }

    private void skloniPredmetne() {
        List<Integer> idjevi = new ArrayList<>();
        for (int id = 0; id < komadi.size(); id++) {
            if (dodeljen[id] && !oznakaUcitelja[komadi.get(id).nastavnikRb]) {
                idjevi.add(id);
            }
        }
        for (int id : idjevi) {
            skloni(id);
        }
    }

    private boolean semeJednog(int nastavnikRb) {
        Map<Integer, List<Integer>> bySection = new LinkedHashMap<>();
        for (int id = 0; id < komadi.size(); id++) {
            if (dodeljen[id] || komadi.get(id).nastavnikRb != nastavnikRb) {
                continue;
            }
            bySection.computeIfAbsent(komadi.get(id).odeljenjeRb, nista -> new ArrayList<>()).add(id);
        }
        boolean pairedClasses = !bySection.isEmpty();
        for (List<Integer> komadiOdeljenja : bySection.values()) {
            if (komadiOdeljenja.size() != 2) {
                pairedClasses = false;
                break;
            }
        }
        int cycle = pairedClasses && bySection.size() >= 2 && bySection.size() <= nDays
                ? bySection.size()
                : nDays;
        int sectionNo = 0;
        for (List<Integer> komadiOdeljenja : bySection.values()) {
            for (int sat = 0; sat < komadiOdeljenja.size(); sat++) {
                int id = komadiOdeljenja.get(sat);
                int danRb = (sectionNo + sat) % cycle;
                int termin = sat == 0 ? maxDan - 1 : minDan - 1;
                if (sme(id, Dani.SVI.get(danRb), termin)) {
                    stavi(id, Dani.SVI.get(danRb), termin);
                } else if (sme(id, Dani.SVI.get(danRb), minDan - 1)) {
                    stavi(id, Dani.SVI.get(danRb), minDan - 1);
                } else if (!staviBiloGde(id)) {
                    skloniNastavnika(nastavnikRb);
                    return semeRezerva(nastavnikRb);
                }
            }
            sectionNo += 1;
        }
        return true;
    }

    private void skloniNastavnika(int nastavnikRb) {
        List<Integer> idjevi = new ArrayList<>();
        for (int id = 0; id < komadi.size(); id++) {
            if (dodeljen[id] && komadi.get(id).nastavnikRb == nastavnikRb) {
                idjevi.add(id);
            }
        }
        for (int id : idjevi) {
            skloni(id);
        }
    }

    private boolean semeRezerva(int nastavnikRb) {
        List<Integer> idjevi = new ArrayList<>();
        for (int id = 0; id < komadi.size(); id++) {
            if (!dodeljen[id] && komadi.get(id).nastavnikRb == nastavnikRb) {
                idjevi.add(id);
            }
        }
        for (int offset = 0; offset < nDays; offset++) {
            staviPar(idjevi, Dani.SVI.get((nastavnikRb + offset) % nDays));
        }
        for (int offset = 0; offset < nDays; offset++) {
            staviJedan(idjevi, Dani.SVI.get((nastavnikRb + offset + 1) % nDays));
        }
        for (int id : idjevi) {
            if (!dodeljen[id] && !staviBiloGde(id)) {
                return false;
            }
        }
        return true;
    }

    private boolean staviPar(List<Integer> idjevi, String dan) {
        List<Integer> slobodno = new ArrayList<>();
        for (int id : idjevi) {
            if (!dodeljen[id]) {
                slobodno.add(id);
            }
        }
        int kasno = maxDan - 1;
        int early = minDan - 1;
        for (int i = 0; i < slobodno.size(); i++) {
            for (int j = 0; j < slobodno.size(); j++) {
                if (i == j) {
                    continue;
                }
                int prvi = slobodno.get(i);
                int drugi = slobodno.get(j);
                if (komadi.get(prvi).odeljenjeRb == komadi.get(drugi).odeljenjeRb) {
                    continue;
                }
                if (!sme(prvi, dan, kasno)) {
                    continue;
                }
                stavi(prvi, dan, kasno);
                if (sme(drugi, dan, early)) {
                    stavi(drugi, dan, early);
                    return true;
                }
                skloni(prvi);
            }
        }
        return false;
    }

    private boolean staviJedan(List<Integer> idjevi, String dan) {
        int[] termini = {minDan - 1, maxDan - 1};
        for (int id : idjevi) {
            if (dodeljen[id]) {
                continue;
            }
            for (int termin : termini) {
                if (termin == maxDan - 1 && preostaloPeti(komadi.get(id).odeljenjeRb) <= 0) {
                    continue;
                }
                if (sme(id, dan, termin)) {
                    stavi(id, dan, termin);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean staviBiloGde(int id) {
        int[] termini = {minDan - 1, maxDan - 1};
        for (String dan : Dani.SVI) {
            for (int termin : termini) {
                if (sme(id, dan, termin)) {
                    stavi(id, dan, termin);
                    return true;
                }
            }
        }
        return false;
    }

    private void skloniUcitelja() {
        List<Integer> idjevi = new ArrayList<>();
        for (int id = 0; id < komadi.size(); id++) {
            if (dodeljen[id] && oznakaUcitelja[komadi.get(id).nastavnikRb]) {
                idjevi.add(id);
            }
        }
        for (int id : idjevi) {
            skloni(id);
        }
    }

    private boolean popuniUcitelja() {
        for (int odeljenjeRb = 0; odeljenjeRb < imenaOdeljenja.length; odeljenjeRb++) {
            if (!popuniOdeljenje(odeljenjeRb)) {
                skloniUcitelja();
                return false;
            }
        }
        return true;
    }

    private boolean popuniOdeljenje(int odeljenjeRb) {
        List<Integer> grupa = new ArrayList<>();
        for (int id = 0; id < komadi.size(); id++) {
            if (!dodeljen[id] && komadi.get(id).odeljenjeRb == odeljenjeRb
                    && oznakaUcitelja[komadi.get(id).nastavnikRb]) {
                grupa.add(id);
            }
        }
        grupa.sort(Comparator.comparingInt((Integer id) -> preostaloPoPredmetu(id)).reversed());

        for (int danRb = 0; danRb < nDays; danRb++) {
            int zauzeca = zauzetoOdeljenje[odeljenjeRb][danRb];
            if (zauzeca == 0) {
                continue;
            }
            int specMin = occMin(zauzeca);
            String dan = Dani.SVI.get(danRb);
            for (int termin = 0; termin < specMin; termin++) {
                if ((zauzeca & (1 << termin)) != 0) {
                    continue;
                }
                Integer id = uzmiUciteljski(grupa, dan, termin);
                if (id == null) {
                    return false;
                }
                stavi(id, dan, termin);
                grupa.remove(id);
            }
        }

        List<Integer> prazniDani = new ArrayList<>();
        for (int danRb = 0; danRb < nDays; danRb++) {
            if (zauzetoOdeljenje[odeljenjeRb][danRb] == 0) {
                prazniDani.add(danRb);
            }
        }
        int visak = grupa.size() - minDan * prazniDani.size();
        if (visak < 0 || visak > prazniDani.size()) {
            return false;
        }
        for (int danRb : prazniDani) {
            int length = minDan + (visak > 0 ? 1 : 0);
            if (visak > 0) {
                visak -= 1;
            }
            String dan = Dani.SVI.get(danRb);
            for (int termin = 0; termin < length; termin++) {
                Integer id = uzmiUciteljski(grupa, dan, termin);
                if (id == null) {
                    return false;
                }
                stavi(id, dan, termin);
                grupa.remove(id);
            }
        }
        return grupa.isEmpty();
    }

    private int preostaloPoPredmetu(int id) {
        Komad komad = komadi.get(id);
        int levo = 0;
        for (int onaj = 0; onaj < komadi.size(); onaj++) {
            if (!dodeljen[onaj] && komadi.get(onaj).odeljenjeRb == komad.odeljenjeRb
                    && komadi.get(onaj).predmetRb == komad.predmetRb) {
                levo += 1;
            }
        }
        return levo;
    }

    private Integer uzmiUciteljski(List<Integer> grupa, String dan, int termin) {
        grupa.sort(Comparator.comparingInt(this::preostaloPoPredmetu).reversed());
        for (int id : grupa) {
            if (sme(id, dan, termin)) {
                return id;
            }
        }
        return null;
    }

    private void sabijPocetak() {
        for (int odeljenjeRb = 0; odeljenjeRb < imenaOdeljenja.length; odeljenjeRb++) {
            for (int danRb = 0; danRb < nDays; danRb++) {
                sabijDan(odeljenjeRb, danRb);
            }
        }
    }

    private void sabijDan(int odeljenjeRb, int danRb) {
        String dan = Dani.SVI.get(danRb);
        List<Integer> idjevi = new ArrayList<>();
        for (Map.Entry<Integer, Mesto> entry : dodela.entrySet()) {
            Komad komad = komadi.get(entry.getKey());
            if (komad.odeljenjeRb == odeljenjeRb && entry.getValue().dan.equals(dan)) {
                idjevi.add(entry.getKey());
            }
        }
        if (idjevi.isEmpty()) {
            return;
        }
        idjevi.sort(Comparator.comparingInt(id -> dodela.get(id).termin));
        List<Integer> uciteljski = new ArrayList<>();
        List<Integer> predmetniNastavnici = new ArrayList<>();
        for (int id : idjevi) {
            if (oznakaUcitelja[komadi.get(id).nastavnikRb]) {
                uciteljski.add(id);
            } else {
                predmetniNastavnici.add(id);
            }
        }
        boolean packed = true;
        int ocekivano = 0;
        List<Integer> desired = new ArrayList<>(uciteljski);
        desired.addAll(predmetniNastavnici);
        for (int id : desired) {
            if (dodela.get(id).termin != ocekivano) {
                packed = false;
                break;
            }
            ocekivano += komadi.get(id).trajanje;
        }
        if (packed) {
            return;
        }

        Map<Integer, Mesto> sacuvano = new LinkedHashMap<>();
        for (int id : idjevi) {
            sacuvano.put(id, dodela.get(id));
        }
        for (int id : idjevi) {
            skloni(id);
        }
        if (staviNiz(uciteljski, predmetniNastavnici, dan)) {
            return;
        }
        if (predmetniNastavnici.size() > 1) {
            List<Integer> reversed = new ArrayList<>(predmetniNastavnici);
            Collections.reverse(reversed);
            if (staviNiz(uciteljski, reversed, dan)) {
                return;
            }
        }
        for (int id : idjevi) {
            Mesto mesto = sacuvano.get(id);
            stavi(id, mesto.dan, mesto.termin);
        }
    }

    private boolean staviNiz(List<Integer> uciteljski, List<Integer> predmetniNastavnici, String dan) {
        List<Integer> sequence = new ArrayList<>(uciteljski);
        sequence.addAll(predmetniNastavnici);
        List<Integer> stavljen = new ArrayList<>();
        int termin = 0;
        for (int id : sequence) {
            if (!sme(id, dan, termin)) {
                for (int i = stavljen.size() - 1; i >= 0; i--) {
                    skloni(stavljen.get(i));
                }
                return false;
            }
            stavi(id, dan, termin);
            stavljen.add(id);
            termin += komadi.get(id).trajanje;
        }
        return true;
    }

    private void dotegliUcitelje() {
        if (komadi.isEmpty()) {
            return;
        }
        long doKada = System.nanoTime() + ("pauze".equals(profil) ? 3_000_000_000L : 12_000_000_000L);
        int najbolji = mekaCena();
        for (int round = 0; round < 40; round++) {
            if (System.nanoTime() > doKada) {
                return;
            }
            boolean bolje = false;
            for (int id = 0; id < komadi.size(); id++) {
                Integer pomeren = boljePomeranjem(id, najbolji);
                if (pomeren != null) {
                    najbolji = pomeren;
                    bolje = true;
                }
            }
            for (int i = 0; i < komadi.size(); i++) {
                Komad levo = komadi.get(i);
                for (int j = i + 1; j < komadi.size(); j++) {
                    Komad desno = komadi.get(j);
                    if (!levo.odeljenje.equals(desno.odeljenje) && !levo.nastavnik.equals(desno.nastavnik)) {
                        continue;
                    }
                    Integer zamenjeno = boljeZamenom(i, j, najbolji);
                    if (zamenjeno != null) {
                        najbolji = zamenjeno;
                        bolje = true;
                    }
                }
            }
            if (!bolje) {
                return;
            }
        }
    }

    private void smanjiUsamljene() {
        long doKada = System.nanoTime() + ("pauze".equals(profil)
                ? 2_000_000_000L
                : (komadi.size() > 60 ? 12L : 3L) * 1_000_000_000L);
        int preCasa = greskeUcitelja();
        int preRasporeda = greskeUcenika();
        for (int round = 0; round < 10; round++) {
            if (System.nanoTime() > doKada) {
                return;
            }
            int najbolji = brojIzbezivih();
            if (najbolji == 0) {
                return;
            }
            boolean bolje = false;
            outer:
            for (int levoId = 0; levoId < komadi.size(); levoId++) {
                if (!dodela.containsKey(levoId)) {
                    continue;
                }
                Komad levo = komadi.get(levoId);
                for (int desnoId = levoId + 1; desnoId < komadi.size(); desnoId++) {
                    Komad desno = komadi.get(desnoId);
                    if (!levo.odeljenje.equals(desno.odeljenje) || !dodela.containsKey(desnoId)) {
                        continue;
                    }
                    Mesto levoMesto = dodela.get(levoId);
                    Mesto desnoMesto = dodela.get(desnoId);
                    if (levoMesto.dan.equals(desnoMesto.dan)) {
                        continue;
                    }
                    skloni(levoId);
                    skloni(desnoId);
                    if (probajPar(levoId, desnoMesto.dan, desnoMesto.termin, desnoId, levoMesto.dan, levoMesto.termin)) {
                        int trenutno = brojIzbezivih();
                        if (trenutno < najbolji
                                && greskeUcitelja() <= preCasa
                                && greskeUcenika() <= preRasporeda) {
                            bolje = true;
                            najbolji = trenutno;
                            continue outer;
                        }
                        skloni(levoId);
                        skloni(desnoId);
                    }
                    stavi(levoId, levoMesto.dan, levoMesto.termin);
                    stavi(desnoId, desnoMesto.dan, desnoMesto.termin);
                }
            }
            if (!bolje) {
                return;
            }
        }
    }

    private void popraviJedanCas() {
        long doKada = System.nanoTime() + ("pauze".equals(profil) ? 2_000_000_000L : 12_000_000_000L);
        for (int round = 0; round < 12; round++) {
            List<String[]> usamljen = poseteJednogCasa();
            if (usamljen.isEmpty()) {
                return;
            }
            boolean fixed = false;
            for (String[] poseta : usamljen) {
                if (System.nanoTime() > doKada) {
                    return;
                }
                if (!usamljenSeMoze(poseta[0], poseta[2])) {
                    continue;
                }
                if (pomeriUzBlok(poseta[0], poseta[1], poseta[2])
                        || dovuciPratioca(poseta[0], poseta[1], poseta[2])
                        || pomeriUsamljeni(poseta[0], poseta[1], poseta[2])
                        || popuniUsamljenDan(poseta[0], poseta[1], poseta[2])
                        || zameniUsamljeni(poseta[0], poseta[1], poseta[2])
                        || rotirajUsamljeni(poseta[0], poseta[1], poseta[2])) {
                    fixed = true;
                }
            }
            if (!fixed) {
                return;
            }
        }
    }

    private Integer tokenAt(int odeljenjeRb, String dan, int termin) {
        for (Map.Entry<Integer, Mesto> entry : dodela.entrySet()) {
            Mesto mesto = entry.getValue();
            if (!mesto.dan.equals(dan)) {
                continue;
            }
            Komad komad = komadi.get(entry.getKey());
            if (komad.odeljenjeRb != odeljenjeRb) {
                continue;
            }
            if (mesto.termin == termin || (komad.trajanje == 2 && mesto.termin + 1 == termin)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean pomeriUzBlok(String nastavnik, String dan, String smena) {
        Integer sam = dodeljenNa(nastavnik, dan, smena);
        if (sam == null) {
            return false;
        }
        Komad usamljenKomad = komadi.get(sam);
        Mesto polaziste = dodela.get(sam);
        int osnova = brojIzbezivih();
        int preRasporeda = greskeUcenika();
        int preCasa = greskeUcitelja();
        for (String meta : Dani.SVI) {
            if (meta.equals(dan)) {
                continue;
            }
            int destMask = zauzetoNastavnik[usamljenKomad.nastavnikRb][danIndeks(meta)][usamljenKomad.smenaRb];
            if (destMask == 0) {
                continue;
            }
            int dozvoljeneRupe = limitRupa();
            for (int termin = 0; termin < termini; termin++) {
                if ((destMask & (1 << termin)) != 0) {
                    continue;
                }
                if (brojRupa(destMask | bitovi(termin, usamljenKomad.trajanje)) > dozvoljeneRupe) {
                    continue;
                }
                Integer stanar = tokenAt(usamljenKomad.odeljenjeRb, meta, termin);
                if (stanar != null && stanar.equals(sam)) {
                    continue;
                }
                if (stanar == null) {
                    skloni(sam);
                    if (sme(sam, meta, termin)) {
                        stavi(sam, meta, termin);
                        if (spajanjeOk(osnova, preRasporeda, preCasa)) {
                            return true;
                        }
                        skloni(sam);
                    }
                    stavi(sam, polaziste.dan, polaziste.termin);
                    continue;
                }
                Mesto occupantSlot = dodela.get(stanar);
                skloni(sam);
                skloni(stanar);
                boolean ok = sme(stanar, polaziste.dan, polaziste.termin) && sme(sam, meta, termin);
                if (ok) {
                    stavi(stanar, polaziste.dan, polaziste.termin);
                    stavi(sam, meta, termin);
                    if (spajanjeOk(osnova, preRasporeda, preCasa)) {
                        return true;
                    }
                    skloni(sam);
                    skloni(stanar);
                }
                stavi(sam, polaziste.dan, polaziste.termin);
                stavi(stanar, occupantSlot.dan, occupantSlot.termin);
            }
        }
        return false;
    }

    private boolean dovuciPratioca(String nastavnik, String dan, String smena) {
        Integer sam = dodeljenNa(nastavnik, dan, smena);
        if (sam == null) {
            return false;
        }
        Komad usamljenKomad = komadi.get(sam);
        Mesto polaziste = dodela.get(sam);
        int osnova = brojIzbezivih();
        int preRasporeda = greskeUcenika();
        int destMask = zauzetoNastavnik[usamljenKomad.nastavnikRb][danIndeks(dan)][usamljenKomad.smenaRb];
        List<Integer> companions = new ArrayList<>();
        for (Map.Entry<Integer, Mesto> entry : dodela.entrySet()) {
            Komad komad = komadi.get(entry.getKey());
            if (entry.getKey() == sam || komad.nastavnikRb != usamljenKomad.nastavnikRb
                    || komad.smenaRb != usamljenKomad.smenaRb) {
                continue;
            }
            if (entry.getValue().dan.equals(dan)) {
                continue;
            }
            if (Integer.bitCount(zauzetoNastavnik[komad.nastavnikRb][danIndeks(entry.getValue().dan)][komad.smenaRb]) < 3) {
                continue;
            }
            companions.add(entry.getKey());
        }
        for (int pratilac : companions) {
            Mesto izvor = dodela.get(pratilac);
            int odeljenjeRb = komadi.get(pratilac).odeljenjeRb;
            int sourceOcc = zauzetoOdeljenje[odeljenjeRb][danIndeks(izvor.dan)];
            if (occMax(sourceOcc) != izvor.termin + komadi.get(pratilac).trajanje - 1) {
                continue;
            }
            for (int termin = 0; termin < termini; termin++) {
                if (brojRupa(destMask | bitovi(termin, komadi.get(pratilac).trajanje)) > 1) {
                    continue;
                }
                skloni(pratilac);
                if (sme(pratilac, dan, termin)) {
                    stavi(pratilac, dan, termin);
                    if (greskeUcenika() <= preRasporeda && brojIzbezivih() < osnova) {
                        return true;
                    }
                    skloni(pratilac);
                }
                stavi(pratilac, izvor.dan, izvor.termin);
            }
        }
        return false;
    }

    private int brojIzbezivih() {
        int broj = 0;
        for (String[] poseta : poseteJednogCasa()) {
            if (usamljenSeMoze(poseta[0], poseta[2])) {
                broj += 1;
            }
        }
        return broj;
    }

    private void sabijIzbezive() {
        long doKada = System.nanoTime() + ("pauze".equals(profil) ? 2_000_000_000L : 8_000_000_000L);
        for (int round = 0; round < 16; round++) {
            if (System.nanoTime() > doKada) {
                return;
            }
            int najbolji = brojIzbezivih();
            if (najbolji == 0) {
                return;
            }
            boolean bolje = false;
            for (String[] poseta : new ArrayList<>(poseteJednogCasa())) {
                if (System.nanoTime() > doKada) {
                    return;
                }
                if (!usamljenSeMoze(poseta[0], poseta[2])) {
                    continue;
                }
                if (mostSmena(poseta[0], poseta[1], poseta[2])
                        || pokrijTanakDan(poseta[0], poseta[1], poseta[2])
                        || pomeriUzBlok(poseta[0], poseta[1], poseta[2])
                        || dovuciPratioca(poseta[0], poseta[1], poseta[2])
                        || rotirajUsamljeni(poseta[0], poseta[1], poseta[2])
                        || zameniUsamljeni(poseta[0], poseta[1], poseta[2])
                        || pomeriUsamljeni(poseta[0], poseta[1], poseta[2])
                        || prosiriUsamljenog(poseta[0], poseta[1], poseta[2])
                        || razmakniUsamljenog(poseta[0], poseta[1], poseta[2])) {
                    bolje = true;
                    if (brojIzbezivih() == 0) {
                        return;
                    }
                }
            }
            if (!bolje) {
                return;
            }
        }
    }

    private boolean usamljenSeMoze(String nastavnik, String smena) {
        Integer nastavnikRb = indeksNastavnika.get(nastavnik);
        Integer smenaRb = indeksSmene.get(smena);
        int usamljen = 0;
        for (String dan : Dani.SVI) {
            if (nastavnikRb != null && smenaRb != null && "pauze".equals(profil)) {
                if (usamljenaSmena(nastavnikRb, danIndeks(dan), smenaRb)) {
                    usamljen += 1;
                }
            } else if (Integer.bitCount(maskaNastavnika(nastavnik, dan, smena)) == 1) {
                usamljen += 1;
            }
        }
        if ("pauze".equals(profil)) {
            return usamljen > 0;
        }
        return usamljen > neizbezniUsamljeni(nastavnik, smena);
    }

    private boolean usamljenaSmena(int nastavnikRb, int danRb, int smenaRb) {
        if (Integer.bitCount(zauzetoNastavnik[nastavnikRb][danRb][smenaRb]) != 1) {
            return false;
        }
        return !povezanaPoseta(nastavnikRb, danRb);
    }

    private boolean povezanaPoseta(int nastavnikRb, int danRb) {
        int prepodne = rbSmene("prepodne");
        int popodne = rbSmene("popodne");
        if (prepodne < 0 || popodne < 0) {
            return false;
        }
        return Pravila.jednaPoseta(
                zauzetoNastavnik[nastavnikRb][danRb][prepodne],
                zauzetoNastavnik[nastavnikRb][danRb][popodne],
                termini);
    }

    private int rbSmene(String ime) {
        for (int smenaRb = 0; smenaRb < imenaSmena.length; smenaRb++) {
            if (ime.equals(imenaSmena[smenaRb])) {
                return smenaRb;
            }
        }
        return -1;
    }

    private int casoviNastavnika(String nastavnik, String dan) {
        Integer nastavnikRb = indeksNastavnika.get(nastavnik);
        if (nastavnikRb == null) {
            return 0;
        }
        return casoviNastavnika(nastavnikRb, danIndeks(dan));
    }

    private int casoviNastavnika(int nastavnikRb, int danRb) {
        int ukupno = 0;
        for (int smenaRb = 0; smenaRb < imenaSmena.length; smenaRb++) {
            ukupno += Integer.bitCount(zauzetoNastavnik[nastavnikRb][danRb][smenaRb]);
        }
        return ukupno;
    }

    private int limitRupa() {
        return "pauze".equals(profil) ? 3 : 1;
    }

    private boolean spajanjeOk(int osnova, int preRasporeda, int preCasa) {
        if (razrednaNastava && greskeUcitelja() > preCasa) {
            return false;
        }
        if (greskeUcenika() > preRasporeda) {
            return false;
        }
        return brojIzbezivih() < osnova;
    }

    private void spojiUsamljene() {
        long doKada = System.nanoTime() + 2_000_000_000L;
        for (int round = 0; round < 12; round++) {
            if (System.nanoTime() > doKada) {
                return;
            }
            if (brojIzbezivih() == 0) {
                return;
            }
            boolean bolje = false;
            for (String[] poseta : new ArrayList<>(poseteJednogCasa())) {
                if (System.nanoTime() > doKada) {
                    return;
                }
                if (!usamljenSeMoze(poseta[0], poseta[2])) {
                    continue;
                }
                if (mostSmena(poseta[0], poseta[1], poseta[2])
                        || pokrijTanakDan(poseta[0], poseta[1], poseta[2])
                        || prosiriUsamljenog(poseta[0], poseta[1], poseta[2])
                        || razmakniUsamljenog(poseta[0], poseta[1], poseta[2])
                        || pomeriUsamljeni(poseta[0], poseta[1], poseta[2])
                        || pomeriUzBlok(poseta[0], poseta[1], poseta[2])) {
                    bolje = true;
                    if (brojIzbezivih() == 0) {
                        return;
                    }
                }
            }
            if (!bolje) {
                return;
            }
        }
    }

    private boolean mostSmena(String nastavnik, String dan, String smena) {
        if (!"pauze".equals(profil) || imenaSmena.length < 2) {
            return false;
        }
        Integer sam = dodeljenNa(nastavnik, dan, smena);
        Integer nastavnikRb = indeksNastavnika.get(nastavnik);
        Integer smenaRb = indeksSmene.get(smena);
        if (sam == null || nastavnikRb == null || smenaRb == null
                || !usamljenaSmena(nastavnikRb, danIndeks(dan), smenaRb)) {
            return false;
        }
        Komad usamljenKomad = komadi.get(sam);
        Mesto polaziste = dodela.get(sam);
        int osnova = brojIzbezivih();
        int preRasporeda = greskeUcenika();
        int preCasa = greskeUcitelja();
        Map<Integer, Mesto> sacuvano = snimiDodelu();
        boolean morningLone = !"popodne".equals(smena);
        int usamljenTermin = morningLone ? termini - 1 : 0;
        int terminDavaoca = morningLone ? 0 : termini - 1;
        skloni(sam);
        if (!sme(sam, dan, usamljenTermin)) {
            vratiDodelu(sacuvano);
            return false;
        }
        stavi(sam, dan, usamljenTermin);
        for (Map.Entry<Integer, Mesto> entry : snimakDodele()) {
            Komad komad = komadi.get(entry.getKey());
            if (komad.nastavnikRb != usamljenKomad.nastavnikRb || entry.getKey() == sam) {
                continue;
            }
            if (entry.getValue().dan.equals(dan) || komad.smenaRb == usamljenKomad.smenaRb) {
                continue;
            }
            if (Integer.bitCount(zauzetoNastavnik[komad.nastavnikRb][danIndeks(entry.getValue().dan)][komad.smenaRb]) < 2) {
                continue;
            }
            Mesto izvor = entry.getValue();
            skloni(entry.getKey());
            if (sme(entry.getKey(), dan, terminDavaoca)) {
                stavi(entry.getKey(), dan, terminDavaoca);
                if (spajanjeOk(osnova, preRasporeda, preCasa)) {
                    return true;
                }
            }
            vratiDodelu(sacuvano);
            sam = dodeljenNa(nastavnik, dan, smena);
            if (sam == null) {
                return false;
            }
            skloni(sam);
            if (!sme(sam, dan, usamljenTermin)) {
                vratiDodelu(sacuvano);
                return false;
            }
            stavi(sam, dan, usamljenTermin);
        }
        vratiDodelu(sacuvano);
        return false;
    }

    private boolean pokrijTanakDan(String nastavnik, String dan, String smena) {
        if (!"pauze".equals(profil) || imenaSmena.length < 2) {
            return false;
        }
        Integer sam = dodeljenNa(nastavnik, dan, smena);
        if (sam == null || casoviNastavnika(nastavnik, dan) != 1) {
            return false;
        }
        Komad usamljenKomad = komadi.get(sam);
        int osnova = brojIzbezivih();
        int preRasporeda = greskeUcenika();
        int preCasa = greskeUcitelja();
        List<Integer> davaoci = new ArrayList<>();
        for (Map.Entry<Integer, Mesto> entry : dodela.entrySet()) {
            Komad komad = komadi.get(entry.getKey());
            if (komad.nastavnikRb != usamljenKomad.nastavnikRb || entry.getValue().dan.equals(dan)) {
                continue;
            }
            if (Integer.bitCount(zauzetoNastavnik[komad.nastavnikRb][danIndeks(entry.getValue().dan)][komad.smenaRb]) < 2) {
                continue;
            }
            if (casoviNastavnika(komad.nastavnikRb, danIndeks(entry.getValue().dan)) < 3) {
                continue;
            }
            davaoci.add(entry.getKey());
        }
        for (int davalac : davaoci) {
            Mesto izvor = dodela.get(davalac);
            skloni(davalac);
            for (int termin = 0; termin < termini; termin++) {
                if (!sme(davalac, dan, termin)) {
                    continue;
                }
                stavi(davalac, dan, termin);
                if (spajanjeOk(osnova, preRasporeda, preCasa)) {
                    return true;
                }
                skloni(davalac);
            }
            stavi(davalac, izvor.dan, izvor.termin);
        }
        return false;
    }

    private boolean prosiriUsamljenog(String nastavnik, String dan, String smena) {
        Integer sam = dodeljenNa(nastavnik, dan, smena);
        if (sam == null) {
            return false;
        }
        Komad usamljenKomad = komadi.get(sam);
        int osnova = brojIzbezivih();
        int preRasporeda = greskeUcenika();
        int preCasa = greskeUcitelja();
        Map<Integer, Mesto> sacuvano = snimiDodelu();
        List<Integer> davaoci = new ArrayList<>();
        for (String donorDay : Dani.SVI) {
            davaoci.addAll(uciteljskiNa(usamljenKomad.odeljenjeRb, donorDay));
        }
        for (String meta : Dani.SVI) {
            if (meta.equals(dan)
                    || zauzetoNastavnik[usamljenKomad.nastavnikRb][danIndeks(meta)][usamljenKomad.smenaRb] == 0) {
                continue;
            }
            List<Integer> pokusaji = new ArrayList<>();
            pokusaji.add(-1);
            pokusaji.addAll(davaoci);
            for (int davalac : pokusaji) {
                vratiDodelu(sacuvano);
                sam = dodeljenNa(nastavnik, dan, smena);
                if (sam == null) {
                    vratiDodelu(sacuvano);
                    return false;
                }
                Mesto polaziste = dodela.get(sam);
                skloni(sam);
                if (davalac >= 0) {
                    if (!dodela.containsKey(davalac)) {
                        continue;
                    }
                    Mesto donorOrigin = dodela.get(davalac);
                    if (donorOrigin.dan.equals(dan) && donorOrigin.termin == polaziste.termin) {
                        continue;
                    }
                    skloni(davalac);
                    boolean parked = false;
                    for (int termin = 0; termin < termini; termin++) {
                        if (!sme(davalac, dan, termin)) {
                            continue;
                        }
                        stavi(davalac, dan, termin);
                        parked = true;
                        break;
                    }
                    if (!parked) {
                        continue;
                    }
                }
                int destMask = zauzetoNastavnik[usamljenKomad.nastavnikRb][danIndeks(meta)][usamljenKomad.smenaRb];
                boolean parkedLone = false;
                for (int termin = termini - 1; termin >= 0; termin--) {
                    if (brojRupa(destMask | bitovi(termin, usamljenKomad.trajanje)) > limitRupa()) {
                        continue;
                    }
                    if (!sme(sam, meta, termin)) {
                        continue;
                    }
                    stavi(sam, meta, termin);
                    parkedLone = true;
                    break;
                }
                if (!parkedLone) {
                    continue;
                }
                if (razrednaNastava) {
                    sabijDan(usamljenKomad.odeljenjeRb, danIndeks(dan));
                    sabijDan(usamljenKomad.odeljenjeRb, danIndeks(meta));
                    if (davalac >= 0 && dodela.containsKey(davalac)) {
                        sabijDan(komadi.get(davalac).odeljenjeRb, danIndeks(dodela.get(davalac).dan));
                    }
                }
                if (spajanjeOk(osnova, preRasporeda, preCasa)) {
                    return true;
                }
            }
        }
        vratiDodelu(sacuvano);
        return false;
    }

    private List<Integer> uciteljskiNa(int odeljenjeRb, String dan) {
        List<Integer> idjevi = new ArrayList<>();
        for (int id : komadiDana(odeljenjeRb, dan)) {
            if (oznakaUcitelja[komadi.get(id).nastavnikRb]) {
                idjevi.add(id);
            }
        }
        idjevi.sort((levo, desno) -> Integer.compare(dodela.get(desno).termin, dodela.get(levo).termin));
        return idjevi;
    }

    private boolean razmakniUsamljenog(String nastavnik, String dan, String smena) {
        Integer sam = dodeljenNa(nastavnik, dan, smena);
        if (sam == null) {
            return false;
        }
        Komad usamljenKomad = komadi.get(sam);
        Mesto polaziste = dodela.get(sam);
        int osnova = brojIzbezivih();
        int preRasporeda = greskeUcenika();
        int preCasa = greskeUcitelja();
        int dozvoljeneRupe = limitRupa();
        Map<Integer, Mesto> sacuvano = snimiDodelu();
        for (String meta : Dani.SVI) {
            if (meta.equals(dan)) {
                continue;
            }
            int destMask = zauzetoNastavnik[usamljenKomad.nastavnikRb][danIndeks(meta)][usamljenKomad.smenaRb];
            if (destMask == 0) {
                continue;
            }
            skloni(sam);
            for (int termin = 0; termin < termini; termin++) {
                if (brojRupa(destMask | bitovi(termin, usamljenKomad.trajanje)) > dozvoljeneRupe) {
                    continue;
                }
                if (!sme(sam, meta, termin)) {
                    continue;
                }
                stavi(sam, meta, termin);
                if (spajanjeOk(osnova, preRasporeda, preCasa)) {
                    return true;
                }
                vratiDodelu(sacuvano);
                sam = dodeljenNa(nastavnik, dan, smena);
                if (sam == null) {
                    return false;
                }
            }

            for (int nosilac : new ArrayList<>(komadiDana(usamljenKomad.odeljenjeRb, meta))) {
                if (nosilac == sam) {
                    continue;
                }
                skloni(sam);
                skloni(nosilac);
                for (int terminNosaca = 0; terminNosaca < termini; terminNosaca++) {
                    if (!sme(nosilac, polaziste.dan, terminNosaca)) {
                        continue;
                    }
                    stavi(nosilac, polaziste.dan, terminNosaca);
                    int maskaSad = zauzetoNastavnik[usamljenKomad.nastavnikRb][danIndeks(meta)][usamljenKomad.smenaRb];
                    for (int usamljenTermin = 0; usamljenTermin < termini; usamljenTermin++) {
                        if (brojRupa(maskaSad | bitovi(usamljenTermin, usamljenKomad.trajanje)) > dozvoljeneRupe) {
                            continue;
                        }
                        if (!sme(sam, meta, usamljenTermin)) {
                            continue;
                        }
                        stavi(sam, meta, usamljenTermin);
                        if (razrednaNastava) {
                            sabijDan(usamljenKomad.odeljenjeRb, danIndeks(polaziste.dan));
                            sabijDan(usamljenKomad.odeljenjeRb, danIndeks(meta));
                        }
                        if (spajanjeOk(osnova, preRasporeda, preCasa)) {
                            return true;
                        }
                        vratiDodelu(sacuvano);
                        sam = dodeljenNa(nastavnik, dan, smena);
                        if (sam == null) {
                            return false;
                        }
                        skloni(sam);
                        skloni(nosilac);
                        if (!sme(nosilac, polaziste.dan, terminNosaca)) {
                            break;
                        }
                        stavi(nosilac, polaziste.dan, terminNosaca);
                    }
                    vratiDodelu(sacuvano);
                    sam = dodeljenNa(nastavnik, dan, smena);
                    if (sam == null) {
                        return false;
                    }
                }
                vratiDodelu(sacuvano);
                sam = dodeljenNa(nastavnik, dan, smena);
                if (sam == null) {
                    return false;
                }
            }

            List<Integer> siblings = new ArrayList<>();
            for (Map.Entry<Integer, Mesto> entry : dodela.entrySet()) {
                Komad komad = komadi.get(entry.getKey());
                if (entry.getKey() == sam || komad.nastavnikRb != usamljenKomad.nastavnikRb
                        || komad.smenaRb != usamljenKomad.smenaRb || !entry.getValue().dan.equals(meta)) {
                    continue;
                }
                siblings.add(entry.getKey());
            }
            for (int brat : siblings) {
                Komad komadBrata = komadi.get(brat);
                for (int davalac : new ArrayList<>(komadiDana(komadBrata.odeljenjeRb, meta))) {
                    if (davalac == brat || davalac == sam) {
                        continue;
                    }
                    skloni(sam);
                    skloni(brat);
                    skloni(davalac);
                    for (int terminDavaoca = 0; terminDavaoca < termini; terminDavaoca++) {
                        if (!sme(davalac, polaziste.dan, terminDavaoca)) {
                            continue;
                        }
                        stavi(davalac, polaziste.dan, terminDavaoca);
                        for (int terminBrata = 0; terminBrata < termini; terminBrata++) {
                            if (!sme(brat, meta, terminBrata)) {
                                continue;
                            }
                            stavi(brat, meta, terminBrata);
                            int maskaSad = zauzetoNastavnik[usamljenKomad.nastavnikRb][danIndeks(meta)][usamljenKomad.smenaRb];
                            for (int usamljenTermin = 0; usamljenTermin < termini; usamljenTermin++) {
                                if (brojRupa(maskaSad | bitovi(usamljenTermin, usamljenKomad.trajanje)) > dozvoljeneRupe) {
                                    continue;
                                }
                                if (!sme(sam, meta, usamljenTermin)) {
                                    continue;
                                }
                                stavi(sam, meta, usamljenTermin);
                                if (razrednaNastava) {
                                    sabijDan(usamljenKomad.odeljenjeRb, danIndeks(polaziste.dan));
                                    sabijDan(usamljenKomad.odeljenjeRb, danIndeks(meta));
                                    sabijDan(komadBrata.odeljenjeRb, danIndeks(polaziste.dan));
                                    sabijDan(komadBrata.odeljenjeRb, danIndeks(meta));
                                }
                                if (spajanjeOk(osnova, preRasporeda, preCasa)) {
                                    return true;
                                }
                                vratiDodelu(sacuvano);
                                sam = dodeljenNa(nastavnik, dan, smena);
                                brat = rbDodele(komadBrata, meta, smena);
                                if (sam == null || brat < 0) {
                                    return false;
                                }
                                skloni(sam);
                                skloni(brat);
                                skloni(davalac);
                                if (!sme(davalac, polaziste.dan, terminDavaoca)) {
                                    break;
                                }
                                stavi(davalac, polaziste.dan, terminDavaoca);
                            }
                            vratiDodelu(sacuvano);
                            sam = dodeljenNa(nastavnik, dan, smena);
                            brat = rbDodele(komadBrata, meta, smena);
                            if (sam == null || brat < 0) {
                                return false;
                            }
                            skloni(sam);
                            skloni(brat);
                            skloni(davalac);
                            if (!sme(davalac, polaziste.dan, terminDavaoca)) {
                                break;
                            }
                            stavi(davalac, polaziste.dan, terminDavaoca);
                        }
                        vratiDodelu(sacuvano);
                        sam = dodeljenNa(nastavnik, dan, smena);
                        brat = rbDodele(komadBrata, meta, smena);
                        if (sam == null || brat < 0) {
                            return false;
                        }
                    }
                    vratiDodelu(sacuvano);
                    sam = dodeljenNa(nastavnik, dan, smena);
                    if (sam == null) {
                        return false;
                    }
                }
            }
        }
        vratiDodelu(sacuvano);
        return false;
    }

    private int rbDodele(Komad komad, String dan, String smena) {
        for (Map.Entry<Integer, Mesto> entry : dodela.entrySet()) {
            Komad onaj = komadi.get(entry.getKey());
            if (onaj.nastavnikRb == komad.nastavnikRb && onaj.odeljenjeRb == komad.odeljenjeRb
                    && onaj.predmetRb == komad.predmetRb && entry.getValue().dan.equals(dan)
                    && onaj.smenaRb == komad.smenaRb && stanje.smenaZa(onaj.odeljenje).equals(smena)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    private List<Integer> komadiDana(int odeljenjeRb, String dan) {
        List<Integer> idjevi = new ArrayList<>();
        for (Map.Entry<Integer, Mesto> entry : dodela.entrySet()) {
            if (komadi.get(entry.getKey()).odeljenjeRb == odeljenjeRb && entry.getValue().dan.equals(dan)) {
                idjevi.add(entry.getKey());
            }
        }
        return idjevi;
    }

    private boolean zameniUsamljeni(String nastavnik, String dan, String smena) {
        Integer id = dodeljenNa(nastavnik, dan, smena);
        if (id == null) {
            return false;
        }
        String odeljenje = komadi.get(id).odeljenje;
        Mesto moji = dodela.get(id);
        int najUsamljen = brojIzbezivih();
        int najCena = mekaCena();
        Integer najDrugi = null;
        Mesto najDrugoMesto = null;

        for (Map.Entry<Integer, Mesto> entry : snimakDodele()) {
            int onaj = entry.getKey();
            if (onaj == id || !komadi.get(onaj).odeljenje.equals(odeljenje)) {
                continue;
            }
            Mesto mestoDrugog = entry.getValue();
            if (mestoDrugog.dan.equals(moji.dan)) {
                continue;
            }
            skloni(id);
            skloni(onaj);
            if (probajPar(id, mestoDrugog.dan, mestoDrugog.termin, onaj, moji.dan, moji.termin)) {
                int sadUsamljen = brojIzbezivih();
                int costNow = mekaCena();
                if (sadUsamljen < najUsamljen || (sadUsamljen == najUsamljen && costNow < najCena)) {
                    najUsamljen = sadUsamljen;
                    najCena = costNow;
                    najDrugi = onaj;
                    najDrugoMesto = mestoDrugog;
                }
                skloni(id);
                skloni(onaj);
            }
            stavi(id, moji.dan, moji.termin);
            stavi(onaj, mestoDrugog.dan, mestoDrugog.termin);
        }

        if (najDrugi == null) {
            return false;
        }
        skloni(id);
        skloni(najDrugi);
        if (probajPar(id, najDrugoMesto.dan, najDrugoMesto.termin, najDrugi, moji.dan, moji.termin)) {
            return true;
        }
        stavi(id, moji.dan, moji.termin);
        stavi(najDrugi, najDrugoMesto.dan, najDrugoMesto.termin);
        return false;
    }

    private boolean rotirajUsamljeni(String nastavnik, String dan, String smena) {
        int osnova = brojIzbezivih();
        Integer sam = dodeljenNa(nastavnik, dan, smena);
        if (sam != null) {
            for (String meta : Dani.SVI) {
                if (meta.equals(dan) || maskaNastavnika(nastavnik, meta, smena) == 0) {
                    continue;
                }
                if (rotirajNaDan(sam, meta, osnova)) {
                    return true;
                }
            }
        }
        String loneSection = sam == null ? null : komadi.get(sam).odeljenje;
        for (Map.Entry<Integer, Mesto> entry : snimakDodele()) {
            Komad komad = komadi.get(entry.getKey());
            if (!komad.nastavnik.equals(nastavnik) || entry.getValue().dan.equals(dan)) {
                continue;
            }
            if (komad.odeljenje.equals(loneSection)) {
                continue;
            }
            if (!stanje.smenaZa(komad.odeljenje).equals(smena)) {
                continue;
            }
            if (rotirajNaDan(entry.getKey(), dan, osnova)) {
                return true;
            }
        }
        return false;
    }

    private boolean rotirajNaDan(int prvi, String targetDay, int osnova) {
        String odeljenje = komadi.get(prvi).odeljenje;
        Map<Integer, Mesto> mesta = new LinkedHashMap<>();
        for (Map.Entry<Integer, Mesto> entry : dodela.entrySet()) {
            if (komadi.get(entry.getKey()).odeljenje.equals(odeljenje)) {
                mesta.put(entry.getKey(), entry.getValue());
            }
        }
        if (!mesta.containsKey(prvi)) {
            return false;
        }
            Mesto prvoMesto = mesta.get(prvi);
            for (Map.Entry<Integer, Mesto> secondEntry : mesta.entrySet()) {
                int drugi = secondEntry.getKey();
                Mesto drugoMesto = secondEntry.getValue();
                if (drugi == prvi || !drugoMesto.dan.equals(targetDay)) {
                    continue;
                }
                if (probajRotaciju(List.of(
                    new Object[] {prvi, drugoMesto.dan, drugoMesto.termin},
                    new Object[] {drugi, prvoMesto.dan, prvoMesto.termin}), mesta)) {
                if (brojIzbezivih() < osnova) {
                    return true;
                }
                vratiRotaciju(List.of(prvi, drugi), mesta);
            }
            for (Map.Entry<Integer, Mesto> thirdEntry : mesta.entrySet()) {
                int treci = thirdEntry.getKey();
                Mesto thirdSlot = thirdEntry.getValue();
                if (treci == prvi || treci == drugi) {
                    continue;
                }
                if (!probajRotaciju(List.of(
                        new Object[] {prvi, drugoMesto.dan, drugoMesto.termin},
                        new Object[] {drugi, thirdSlot.dan, thirdSlot.termin},
                        new Object[] {treci, prvoMesto.dan, prvoMesto.termin}), mesta)) {
                    continue;
                }
                if (brojIzbezivih() < osnova) {
                    return true;
                }
                vratiRotaciju(List.of(prvi, drugi, treci), mesta);
            }
        }
        return false;
    }

    private boolean probajRotaciju(List<Object[]> redosled, Map<Integer, Mesto> mesta) {
        List<Integer> idjevi = new ArrayList<>();
        for (Object[] korak : redosled) {
            idjevi.add((Integer) korak[0]);
        }
        for (int id : idjevi) {
            skloni(id);
        }
        List<Integer> stavljen = new ArrayList<>();
        for (Object[] korak : redosled) {
            int id = (Integer) korak[0];
            String dan = (String) korak[1];
            int termin = (Integer) korak[2];
            if (!sme(id, dan, termin)) {
                for (int done : stavljen) {
                    skloni(done);
                }
                for (int restore : idjevi) {
                    Mesto mesto = mesta.get(restore);
                    stavi(restore, mesto.dan, mesto.termin);
                }
                return false;
            }
            stavi(id, dan, termin);
            stavljen.add(id);
        }
        return true;
    }

    private void vratiRotaciju(List<Integer> idjevi, Map<Integer, Mesto> mesta) {
        for (int id : idjevi) {
            skloni(id);
        }
        for (int id : idjevi) {
            Mesto mesto = mesta.get(id);
            stavi(id, mesto.dan, mesto.termin);
        }
    }

    private List<String[]> poseteJednogCasa() {
        List<String[]> usamljen = new ArrayList<>();
        for (String nastavnik : stanje.getNastavnici().keySet()) {
            Integer nastavnikRb = indeksNastavnika.get(nastavnik);
            for (String dan : Dani.SVI) {
                for (String smena : stanje.korisceneSmene()) {
                    Integer smenaRb = indeksSmene.get(smena);
                    boolean sam = nastavnikRb != null && smenaRb != null && "pauze".equals(profil)
                            ? usamljenaSmena(nastavnikRb, danIndeks(dan), smenaRb)
                            : Integer.bitCount(maskaNastavnika(nastavnik, dan, smena)) == 1;
                    if (sam) {
                        usamljen.add(new String[] {nastavnik, dan, smena});
                    }
                }
            }
        }
        return usamljen;
    }

    private boolean pomeriUsamljeni(String nastavnik, String dan, String smena) {
        Integer id = dodeljenNa(nastavnik, dan, smena);
        if (id == null) {
            return false;
        }
        Mesto izvorno = dodela.get(id);
        int osnova = brojIzbezivih();
        int preRasporeda = greskeUcenika();
        int preCasa = greskeUcitelja();
        skloni(id);
        String najDan = null;
        Integer najTermin = null;
        Integer najCena = null;
        for (String meta : Dani.SVI) {
            if (meta.equals(izvorno.dan)) {
                continue;
            }
            if (maskaNastavnika(nastavnik, meta, smena) == 0) {
                continue;
            }
            for (int termin = 0; termin < termini; termin++) {
                if (!sme(id, meta, termin)) {
                    continue;
                }
                stavi(id, meta, termin);
                int cena = mekaCena();
                boolean better = spajanjeOk(osnova, preRasporeda, preCasa);
                skloni(id);
                if (!better) {
                    continue;
                }
                if (najCena == null || cena < najCena) {
                    najCena = cena;
                    najDan = meta;
                    najTermin = termin;
                }
            }
        }
        if (najDan == null) {
            stavi(id, izvorno.dan, izvorno.termin);
            return false;
        }
        stavi(id, najDan, najTermin);
        return true;
    }

    private boolean popuniUsamljenDan(String nastavnik, String dan, String smena) {
        for (Map.Entry<Integer, Mesto> entry : snimakDodele()) {
            int id = entry.getKey();
            Komad komad = komadi.get(id);
            Mesto dodeljen = entry.getValue();
            if (!komad.nastavnik.equals(nastavnik) || dodeljen.dan.equals(dan)) {
                continue;
            }
            if (!stanje.smenaZa(komad.odeljenje).equals(smena)) {
                continue;
            }
            if (Integer.bitCount(maskaNastavnika(nastavnik, dodeljen.dan, smena)) < 3) {
                continue;
            }
            skloni(id);
            Integer najTermin = null;
            Integer najCena = null;
            for (int termin = 0; termin < termini; termin++) {
                if (!sme(id, dan, termin)) {
                    continue;
                }
                stavi(id, dan, termin);
                int cena = mekaCena();
                skloni(id);
                if (najCena == null || cena < najCena) {
                    najCena = cena;
                    najTermin = termin;
                }
            }
            if (najTermin != null) {
                stavi(id, dan, najTermin);
                return true;
            }
            stavi(id, dodeljen.dan, dodeljen.termin);
        }
        return false;
    }

    private Integer dodeljenNa(String nastavnik, String dan, String smena) {
        for (Map.Entry<Integer, Mesto> entry : dodela.entrySet()) {
            Komad komad = komadi.get(entry.getKey());
            if (!komad.nastavnik.equals(nastavnik) || !entry.getValue().dan.equals(dan)) {
                continue;
            }
            if (stanje.smenaZa(komad.odeljenje).equals(smena)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private Integer boljePomeranjem(int id, int najbolji) {
        if (!dodela.containsKey(id)) {
            return null;
        }
        Mesto izvorno = dodela.get(id);
        skloni(id);
        String chosenDay = izvorno.dan;
        int chosenPeriod = izvorno.termin;
        Integer izabranaCena = null;
        for (String dan : Dani.SVI) {
            for (int termin = 0; termin < termini; termin++) {
                if (!sme(id, dan, termin)) {
                    continue;
                }
                stavi(id, dan, termin);
                int cena = razrednaNastava && greskeUcitelja() > 0 ? Integer.MAX_VALUE : mekaCena();
                skloni(id);
                if (izabranaCena == null || cena < izabranaCena) {
                    izabranaCena = cena;
                    chosenDay = dan;
                    chosenPeriod = termin;
                }
            }
        }
        if (izabranaCena == null || izabranaCena == Integer.MAX_VALUE) {
            stavi(id, izvorno.dan, izvorno.termin);
            return null;
        }
        stavi(id, chosenDay, chosenPeriod);
        if (izabranaCena < najbolji) {
            return izabranaCena;
        }
        return null;
    }

    private Integer boljeZamenom(int levo, int desno, int najbolji) {
        if (!dodela.containsKey(levo) || !dodela.containsKey(desno)) {
            return null;
        }
        Mesto levoMesto = dodela.get(levo);
        Mesto desnoMesto = dodela.get(desno);
        if (levoMesto.dan.equals(desnoMesto.dan) && levoMesto.termin == desnoMesto.termin) {
            return null;
        }
        skloni(levo);
        skloni(desno);
        if (probajPar(levo, desnoMesto.dan, desnoMesto.termin, desno, levoMesto.dan, levoMesto.termin)) {
            int cena = razrednaNastava && greskeUcitelja() > 0 ? Integer.MAX_VALUE : mekaCena();
            if (cena < najbolji) {
                return cena;
            }
            skloni(levo);
            skloni(desno);
        }
        stavi(levo, levoMesto.dan, levoMesto.termin);
        stavi(desno, desnoMesto.dan, desnoMesto.termin);
        return null;
    }

    private boolean probajPar(
            int prvi, String firstDay, int firstPeriod, int drugi, String secondDay, int secondPeriod) {
        if (!sme(prvi, firstDay, firstPeriod)) {
            return false;
        }
        stavi(prvi, firstDay, firstPeriod);
        if (!sme(drugi, secondDay, secondPeriod)) {
            skloni(prvi);
            return false;
        }
        stavi(drugi, secondDay, secondPeriod);
        return true;
    }

    private int mekaCena() {
        int pauzeNastavnika = 0;
        int kasniNastavnik = 0;
        int rasponNastavnika = 0;
        int usamljeniNastavnici = 0;
        int kratkePosete = 0;
        int visakPauza = 0;
        int daniViskaPauza = 0;
        int greskeRazredne = 0;
        for (int t = 0; t < imenaNastavnika.length; t++) {
            for (int d = 0; d < nDays; d++) {
                for (int sh = 0; sh < imenaSmena.length; sh++) {
                    int maska = zauzetoNastavnik[t][d][sh];
                    int broj = Integer.bitCount(maska);
                    if ("pauze".equals(profil)) {
                        if (usamljenaSmena(t, d, sh)) {
                            usamljeniNastavnici += 1;
                        } else if (broj == 2) {
                            kratkePosete += 1;
                        }
                    } else if (broj == 1) {
                        usamljeniNastavnici += 1;
                    } else if (broj == 2) {
                        kratkePosete += 1;
                    }
                    int rupe = brojRupa(maska);
                    pauzeNastavnika += rupe;
                    if (rupe > 1) {
                        visakPauza += rupe - 1;
                        daniViskaPauza += 1;
                    }
                    if (oznakaUcitelja[t] && broj > 0) {
                        if ((maska & 1) == 0) {
                            greskeRazredne += 8;
                        }
                        greskeRazredne += rupe * 8;
                    }
                    if (broj > 0) {
                        int max = occMax(maska);
                        int min = occMin(maska);
                        kasniNastavnik += max;
                        if (broj >= 2) {
                            rasponNastavnika += max - min - (broj - 1);
                        }
                    }
                }
            }
        }

        int susedniTermini = 0;
        int rasipanje = 0;
        for (int s = 0; s < imenaOdeljenja.length; s++) {
            int daySum = 0;
            for (int d = 0; d < nDays; d++) {
                int broj = 0;
                Integer prosliTermin = null;
                String prevKey = null;
                String[] predmeti = predmetUDanu[s][d];
                for (int termin = 0; termin < termini; termin++) {
                    String kljuc = predmeti[termin];
                    if (kljuc == null) {
                        continue;
                    }
                    broj += 1;
                    if (prosliTermin != null && termin == prosliTermin + 1 && kljuc.equals(prevKey)
                            && !Pravila.jeBlok(kljuc, 2, 2, stanje.getTipSkole())) {
                        susedniTermini += 1;
                    }
                    prosliTermin = termin;
                    prevKey = kljuc;
                }
                teretDana[d] = broj;
                daySum += broj;
            }
            if (nDays > 0) {
                double mean = (double) daySum / nDays;
                for (int d = 0; d < nDays; d++) {
                    rasipanje += (int) Math.abs(teretDana[d] - mean);
                }
            }
        }

        int ogledaloSmene = 0;
        if (stanje.rotacijaUkljucena() && !"pauze".equals(profil)) {
            ogledaloSmene = cenaOgledala();
        }

        int slack = rezervaPauza();
        int overflowPauseDays = Math.max(0, daniViskaPauza - slack);
        return switch (profil) {
            case "student" -> 6 * pauzeNastavnika + 28 * susedniTermini + 16 * rasipanje
                    + 2 * kasniNastavnik + 400 * usamljeniNastavnici + 16 * ogledaloSmene
                    + 500 * greskeRazredne;
            case "pauze" -> 8 * pauzeNastavnika + 12 * Math.min(daniViskaPauza, slack)
                    + 50 * overflowPauseDays + 16 * visakPauza
                    + 1800 * usamljeniNastavnici + 8 * kratkePosete + 3 * rasponNastavnika
                    + 500 * greskeRazredne;
            default -> 18 * pauzeNastavnika + 120 * visakPauza + 6 * kasniNastavnik + 10 * rasponNastavnika
                    + 12 * susedniTermini + 8 * rasipanje + 400 * usamljeniNastavnici
                    + 16 * kratkePosete + 20 * ogledaloSmene + 500 * greskeRazredne;
        };
    }

    private int cenaOgledala() {
        int cena = 0;
        for (int odeljenjeRb = 0; odeljenjeRb < parRb.length; odeljenjeRb++) {
            int rbPara = parRb[odeljenjeRb];
            if (rbPara <= odeljenjeRb) {
                continue;
            }
            for (int danRb = 0; danRb < nDays; danRb++) {
                String[] leftDay = predmetUDanu[odeljenjeRb][danRb];
                String[] rightDay = predmetUDanu[rbPara][danRb];
                for (int termin = 0; termin < termini; termin++) {
                    String leviKljuc = leftDay[termin];
                    String desniKljuc = rightDay[termin];
                    if (leviKljuc == null) {
                        leviKljuc = "";
                    }
                    if (desniKljuc == null) {
                        desniKljuc = "";
                    }
                    if (!leviKljuc.equals(desniKljuc)) {
                        cena += 1;
                    }
                }
            }
        }
        return cena;
    }

    private int brojPraznih(Map<String, Map<String, List<Cas>>> raspored) {
        int empty = 0;
        for (String odeljenje : stanje.getOdeljenja()) {
            for (String dan : Dani.SVI) {
                for (Cas cas : raspored.get(odeljenje).get(dan)) {
                    if (cas == null) {
                        empty += 1;
                    }
                }
            }
        }
        return empty;
    }

    private void zauzmiZakljucano() {
        for (int t = 0; t < imenaNastavnika.length; t++) {
            Nastavnik nastavnik = stanje.getNastavnici().get(imenaNastavnika[t]);
            if (nastavnik == null) {
                continue;
            }
            for (String dan : nastavnik.getSlobodniDani()) {
                slobodanDan[t][danIndeks(dan)] = true;
            }
        }
        for (int s = 0; s < imenaOdeljenja.length; s++) {
            String odeljenje = imenaOdeljenja[s];
            int smenaRb = indeksSmene.getOrDefault(stanje.smenaZa(odeljenje), 0);
            for (int d = 0; d < nDays; d++) {
                List<Cas> celije = zakljucanRaspored.get(odeljenje).get(Dani.SVI.get(d));
                for (int termin = 0; termin < celije.size(); termin++) {
                    Cas cas = celije.get(termin);
                    if (cas == null || termin >= termini) {
                        continue;
                    }
                    int bit = 1 << termin;
                    zauzetoOdeljenje[s][d] |= bit;
                    Integer nastavnikRb = indeksNastavnika.get(cas.getNastavnik());
                    if (nastavnikRb != null) {
                        zauzetoNastavnik[nastavnikRb][d][smenaRb] |= bit;
                    }
                    String kljuc = cas.getPredmet().toLowerCase(Locale.ROOT);
                    Integer predmetRb = indeksPredmeta.get(kljuc);
                    if (predmetRb != null) {
                        brojPredmeta[s][d][predmetRb] += 1;
                    }
                    predmetUDanu[s][d][termin] = kljuc;
                }
            }
        }
    }

    private Map<String, Map<String, List<Cas>>> kopijaZakljucanog() {
        return stanje.kopijaRasporeda(stanje.getRaspored(), true);
    }

    private Map<String, Map<String, List<Cas>>> sloziRaspored() {
        Map<String, Map<String, List<Cas>>> raspored = kopijaZakljucanog();
        for (Map.Entry<Integer, Mesto> entry : dodela.entrySet()) {
            Komad komad = komadi.get(entry.getKey());
            Mesto mesto = entry.getValue();
            for (int offset = 0; offset < komad.trajanje; offset++) {
                raspored.get(komad.odeljenje).get(mesto.dan).set(
                        mesto.termin + offset,
                        new Cas(komad.predmet, komad.nastavnik, komad.maxDnevno, false));
            }
        }
        return raspored;
    }

    private int brojZakljucanih(String nastavnik, Zaduzenje dodela) {
        int broj = 0;
        for (String dan : Dani.SVI) {
            for (Cas cas : zakljucanRaspored.get(dodela.getOdeljenje()).get(dan)) {
                if (cas != null
                        && cas.jeZakljucan()
                        && cas.getNastavnik().equals(nastavnik)
                        && cas.getPredmet().equalsIgnoreCase(dodela.getPredmet())) {
                    broj += 1;
                }
            }
        }
        return broj;
    }

    private void dodajUgnjezdeno(Map<String, Map<String, Integer>> map, String nastavnik, String smena, int amount) {
        map.computeIfAbsent(nastavnik, nista -> new HashMap<>()).merge(smena, amount, Integer::sum);
    }

    private int ugnjezdeno(Map<String, Map<String, Integer>> map, String nastavnik, String smena) {
        return map.getOrDefault(nastavnik, Map.of()).getOrDefault(smena, 0);
    }

    private List<Map.Entry<Integer, Mesto>> snimakDodele() {
        List<Map.Entry<Integer, Mesto>> kopija = new ArrayList<>();
        for (Map.Entry<Integer, Mesto> entry : dodela.entrySet()) {
            kopija.add(Map.entry(entry.getKey(), entry.getValue()));
        }
        return kopija;
    }

    @SuppressWarnings("unchecked")
    private List<Mesto>[] snimakDomena() {
        List<Mesto>[] kopija = new List[domene.length];
        System.arraycopy(domene, 0, kopija, 0, domene.length);
        return kopija;
    }

    private void vratiDomene(List<Mesto>[] sacuvano) {
        System.arraycopy(sacuvano, 0, domene, 0, domene.length);
    }
}
