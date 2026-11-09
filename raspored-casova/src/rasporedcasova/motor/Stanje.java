package rasporedcasova.motor;

import rasporedcasova.model.Dani;
import rasporedcasova.model.Cas;
import rasporedcasova.model.Nastavnik;
import rasporedcasova.model.Zaduzenje;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Stanje {
    private final List<String> odeljenja = new ArrayList<>();
    private final Map<String, Nastavnik> nastavnici = new LinkedHashMap<>();
    private int terminaDnevno = 6;
    private String tipSkole = "";
    private final Map<String, String> smeneOdeljenja = new LinkedHashMap<>();
    private boolean rotacijaSmena;
    private boolean jednaSmena;
    private Map<String, Map<String, List<Cas>>> raspored = new LinkedHashMap<>();

    public List<String> getOdeljenja() {
        return odeljenja;
    }

    public Map<String, Nastavnik> getNastavnici() {
        return nastavnici;
    }

    public int getTerminaDnevno() {
        return terminaDnevno;
    }

    public void setTerminaDnevno(int terminaDnevno) {
        this.terminaDnevno = Math.max(1, terminaDnevno);
    }

    public String getTipSkole() {
        return tipSkole;
    }

    public void setTipSkole(String tipSkole) {
        this.tipSkole = tipSkole == null ? "" : tipSkole;
    }

    public Map<String, String> getSmeneOdeljenja() {
        return smeneOdeljenja;
    }

    public boolean rotacijaUkljucena() {
        return rotacijaSmena && !jednaSmena;
    }

    public void setRotacija(boolean rotacijaSmena) {
        this.rotacijaSmena = rotacijaSmena;
    }

    public boolean jednaSmenaUkljucena() {
        return jednaSmena;
    }

    public void setJednaSmena(boolean jednaSmena) {
        this.jednaSmena = jednaSmena;
    }

    public String sacuvanaSmena(String odeljenje) {
        return smeneOdeljenja.getOrDefault(odeljenje, "prepodne");
    }

    public String smenaZa(String odeljenje) {
        if (jednaSmena) {
            return "prepodne";
        }
        return sacuvanaSmena(odeljenje);
    }

    public List<String> korisceneSmene() {
        if (jednaSmena) {
            return List.of("prepodne");
        }
        Set<String> smene = new LinkedHashSet<>(smeneOdeljenja.values());
        if (smene.isEmpty()) {
            return List.of("prepodne");
        }
        return new ArrayList<>(smene);
    }

    public Map<String, Map<String, List<Cas>>> getRaspored() {
        return raspored;
    }

    public void setRaspored(Map<String, Map<String, List<Cas>>> raspored) {
        this.raspored = raspored;
    }

    public Map<String, List<Cas>> prazanRaspored() {
        Map<String, List<Cas>> sectionSchedule = new LinkedHashMap<>();
        for (String dan : Dani.SVI) {
            List<Cas> termini = new ArrayList<>();
            for (int i = 0; i < terminaDnevno; i++) {
                termini.add(null);
            }
            sectionSchedule.put(dan, termini);
        }
        return sectionSchedule;
    }

    public void srediRaspored() {
        terminaDnevno = Math.max(1, terminaDnevno);
        for (String odeljenje : odeljenja) {
            raspored.putIfAbsent(odeljenje, prazanRaspored());
            Map<String, List<Cas>> sectionSchedule = raspored.get(odeljenje);
            for (String dan : Dani.SVI) {
                List<Cas> sad = sectionSchedule.computeIfAbsent(dan, nista -> new ArrayList<>());
                while (sad.size() < terminaDnevno) {
                    sad.add(null);
                }
                if (sad.size() > terminaDnevno) {
                    sectionSchedule.put(dan, new ArrayList<>(sad.subList(0, terminaDnevno)));
                }
            }
        }
        raspored.keySet().removeIf(odeljenje -> !odeljenja.contains(odeljenje));
        smeneOdeljenja.keySet().removeIf(odeljenje -> !odeljenja.contains(odeljenje));
        for (String odeljenje : odeljenja) {
            smeneOdeljenja.putIfAbsent(odeljenje, "prepodne");
        }
    }

    public Map<String, Map<String, List<Cas>>> kopijaRasporeda(
            Map<String, Map<String, List<Cas>>> izvor, boolean lockedOnly) {
        Map<String, Map<String, List<Cas>>> copied = new LinkedHashMap<>();
        for (String odeljenje : odeljenja) {
            Map<String, List<Cas>> sectionCopy = new LinkedHashMap<>();
            for (String dan : Dani.SVI) {
                List<Cas> termini = new ArrayList<>();
                List<Cas> original = izvor.getOrDefault(odeljenje, Map.of()).getOrDefault(dan, List.of());
                for (int termin = 0; termin < terminaDnevno; termin++) {
                    Cas cas = termin < original.size() ? original.get(termin) : null;
                    if (cas == null) {
                        termini.add(null);
                    } else if (lockedOnly && !cas.jeZakljucan()) {
                        termini.add(null);
                    } else {
                        Cas kopija = cas.kopija();
                        if (lockedOnly) {
                            kopija.setZakljucan(true);
                        }
                        termini.add(kopija);
                    }
                }
                sectionCopy.put(dan, termini);
            }
            copied.put(odeljenje, sectionCopy);
        }
        return copied;
    }

    public void preimenujNastavnikaURasporedu(String staroIme, String newName) {
        for (Map<String, List<Cas>> sectionSchedule : raspored.values()) {
            for (String dan : Dani.SVI) {
                for (Cas cas : sectionSchedule.get(dan)) {
                    if (cas != null && cas.getNastavnik().equals(staroIme)) {
                        cas.setNastavnik(newName);
                    }
                }
            }
        }
    }

    public void skloniNastavnikaIzRasporeda(String ime) {
        for (Map<String, List<Cas>> sectionSchedule : raspored.values()) {
            for (String dan : Dani.SVI) {
                List<Cas> termini = sectionSchedule.get(dan);
                for (int rb = 0; rb < termini.size(); rb++) {
                    Cas cas = termini.get(rb);
                    if (cas != null && cas.getNastavnik().equals(ime)) {
                        termini.set(rb, null);
                    }
                }
            }
        }
    }

    public void preimenujOdeljenje(String staroIme, String newName) {
        int rb = odeljenja.indexOf(staroIme);
        if (rb >= 0) {
            odeljenja.set(rb, newName);
        }
        Map<String, List<Cas>> pomeren = raspored.remove(staroIme);
        raspored.put(newName, pomeren != null ? pomeren : prazanRaspored());
        String smena = smeneOdeljenja.remove(staroIme);
        smeneOdeljenja.put(newName, smena != null ? smena : "prepodne");
        for (Nastavnik nastavnik : nastavnici.values()) {
            for (Zaduzenje dodela : nastavnik.getDodele()) {
                if (dodela.getOdeljenje().equals(staroIme)) {
                    dodela.setOdeljenje(newName);
                }
            }
        }
    }

    public void obrisiOdeljenje(String odeljenje) {
        odeljenja.remove(odeljenje);
        raspored.remove(odeljenje);
        smeneOdeljenja.remove(odeljenje);
        for (Nastavnik nastavnik : nastavnici.values()) {
            nastavnik.getDodele().removeIf(dodela -> dodela.getOdeljenje().equals(odeljenje));
        }
    }
}
