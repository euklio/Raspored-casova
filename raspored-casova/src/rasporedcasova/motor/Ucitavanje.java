package rasporedcasova.motor;

import rasporedcasova.json.Json;
import rasporedcasova.model.Dani;
import rasporedcasova.model.Cas;
import rasporedcasova.model.Pravila;
import rasporedcasova.model.Nastavnik;
import rasporedcasova.model.Zaduzenje;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Ucitavanje {
    public static void ucitajFajl(Stanje stanje, Path putanja) throws Exception {
        String tekst = Files.readString(putanja, StandardCharsets.UTF_8);
        ucitajPodatke(stanje, Json.kaoMapa(Json.procitaj(tekst)));
    }

    private static Object uzmi(Map<String, Object> mapa, String... kljucevi) {
        for (String kljuc : kljucevi) {
            if (mapa != null && mapa.containsKey(kljuc) && mapa.get(kljuc) != null) {
                return mapa.get(kljuc);
            }
        }
        return null;
    }

    public static void ucitajPodatke(Stanje stanje, Map<String, Object> podaci) {
        int termini = Json.kaoBroj(uzmi(podaci, "casova_dnevno", "periods_per_day"), stanje.getTerminaDnevno());
        if (termini < 1) {
            throw new IllegalArgumentException("casova_dnevno mora biti najmanje 1.");
        }
        stanje.setTerminaDnevno(termini);

        String tipSkole = Json.kaoTekst(uzmi(podaci, "tip_skole", "school_type"), "").strip();
        if (tipSkole.isEmpty() || !Pravila.TIPOVI_SKOLE.contains(tipSkole)) {
            throw new IllegalArgumentException(
                    "Obavezno polje \"tip_skole\": \"1-4\", \"5-8\", \"srednja\" ili \"fakultet\".");
        }
        stanje.setTipSkole(tipSkole);
        stanje.setRotacija(Json.kaoLogicko(uzmi(podaci, "rotacija_smena", "shift_rotation"), false));
        if (uzmi(podaci, "jedna_smena", "single_shift") != null) {
            stanje.setJednaSmena(Json.kaoLogicko(uzmi(podaci, "jedna_smena", "single_shift"), false));
        }

        Object rawSections = uzmi(podaci, "odeljenja", "classes", "sections");
        if (rawSections == null) {
            throw new IllegalArgumentException("JSON mora da sadrži listu \"odeljenja\".");
        }
        List<String> odeljenja = new ArrayList<>();
        for (Object rawSection : Json.kaoLista(rawSections)) {
            String odeljenje = Json.kaoTekst(rawSection, "").strip();
            if (!odeljenje.isEmpty()) {
                odeljenja.add(odeljenje);
            }
        }
        if (odeljenja.isEmpty()) {
            throw new IllegalArgumentException("Nazivi odeljenja ne mogu biti prazni.");
        }
        stanje.getOdeljenja().clear();
        stanje.getOdeljenja().addAll(odeljenja);

        stanje.getSmeneOdeljenja().clear();
        Object rawShifts = uzmi(podaci, "smene_odeljenja", "class_shifts", "smene");
        Map<String, Object> smene = Map.of();
        if (rawShifts instanceof Map<?, ?>) {
            smene = Json.kaoMapa(rawShifts);
        }
        for (String odeljenje : odeljenja) {
            Object rawShift = smene.get(odeljenje);
            stanje.getSmeneOdeljenja().put(
                    odeljenje,
                    rawShift == null ? "prepodne" : Pravila.srediSmenu(Json.kaoTekst(rawShift, "prepodne")));
        }

        Map<String, Nastavnik> nastavnici = new LinkedHashMap<>();
        Object rawTeachers = uzmi(podaci, "nastavnici", "teachers");
        if (rawTeachers == null) {
            throw new IllegalArgumentException("JSON mora da sadrži bar jednog nastavnika u \"nastavnici\".");
        }
        for (Object rawTeacherObj : Json.kaoLista(rawTeachers)) {
            Map<String, Object> sirovNastavnik = Json.kaoMapa(rawTeacherObj);
            String ime = Json.kaoTekst(uzmi(sirovNastavnik, "ime", "name"), "").strip();
            if (ime.isEmpty()) {
                throw new IllegalArgumentException("Svaki nastavnik mora da ima ime.");
            }
            Set<String> slobodniDani = new LinkedHashSet<>(Dani.SVI);
            Object rawDays = uzmi(sirovNastavnik, "slobodni_dani", "available_days");
            if (rawDays != null) {
                slobodniDani.clear();
                for (Object rawDay : Json.kaoLista(rawDays)) {
                    slobodniDani.add(Dani.izNaziva(Json.kaoTekst(rawDay, "")));
                }
            }
            Set<String> losiDani = new LinkedHashSet<>(slobodniDani);
            losiDani.removeAll(Dani.SVI);
            if (!losiDani.isEmpty()) {
                throw new IllegalArgumentException(ime + " ima neispravne dane: " + String.join(", ", losiDani));
            }

            List<Zaduzenje> dodele = new ArrayList<>();
            Object rawAssignments = uzmi(sirovNastavnik, "zaduzenja", "assignments");
            if (rawAssignments != null) {
                for (Object rawAssignmentObj : Json.kaoLista(rawAssignments)) {
                    Map<String, Object> sirovaDodela = Json.kaoMapa(rawAssignmentObj);
                    String odeljenje = Json.kaoTekst(
                            uzmi(sirovaDodela, "odeljenje", "class", "section"), "").strip();
                    String predmet = Json.kaoTekst(uzmi(sirovaDodela, "predmet", "subject"), "").strip();
                    int nedeljno = Json.kaoBroj(uzmi(sirovaDodela, "nedeljno", "per_week", "weekly_count"), 0);
                    int maxDnevno = Json.kaoBroj(uzmi(sirovaDodela, "max_dnevno", "max_per_day"), 1);
                    if (!stanje.getOdeljenja().contains(odeljenje)) {
                        throw new IllegalArgumentException(ime + " ima zaduženje za nepoznato odeljenje " + odeljenje + ".");
                    }
                    if (Pravila.jeBlok(predmet, nedeljno, maxDnevno, tipSkole)) {
                        maxDnevno = 2;
                    }
                    if (Pravila.jeFizicko(predmet)) {
                        maxDnevno = 1;
                    }
                    if (predmet.isEmpty() || nedeljno < 1 || (maxDnevno != 1 && maxDnevno != 2)) {
                        throw new IllegalArgumentException(ime + " ima neispravno zaduženje.");
                    }
                    dodele.add(new Zaduzenje(odeljenje, predmet, nedeljno, maxDnevno));
                }
            }
            nastavnici.put(ime, new Nastavnik(ime, slobodniDani, dodele));
        }
        if (nastavnici.isEmpty()) {
            throw new IllegalArgumentException("JSON mora da sadrži bar jednog nastavnika u \"nastavnici\".");
        }

        stanje.getNastavnici().clear();
        stanje.getNastavnici().putAll(nastavnici);
        stanje.setRaspored(new LinkedHashMap<>());
        stanje.srediRaspored();

        Object rawLocked = uzmi(podaci, "zakljucani_casovi", "locked_lessons");
        if (rawLocked == null) {
            return;
        }
        for (Object rawLessonObj : Json.kaoLista(rawLocked)) {
            Map<String, Object> sirovCas = Json.kaoMapa(rawLessonObj);
            String nastavnik = Json.kaoTekst(uzmi(sirovCas, "nastavnik", "teacher"), "").strip();
            String odeljenje = Json.kaoTekst(uzmi(sirovCas, "odeljenje", "class", "section"), "").strip();
            String predmet = Json.kaoTekst(uzmi(sirovCas, "predmet", "subject"), "").strip();
            String dan = Dani.izNaziva(Json.kaoTekst(uzmi(sirovCas, "dan", "day"), "").strip());
            int termin = Json.kaoBroj(uzmi(sirovCas, "termin", "period"), 0) - 1;
            int maxDnevno = Json.kaoBroj(uzmi(sirovCas, "max_dnevno", "max_per_day"), 1);
            if (!stanje.getNastavnici().containsKey(nastavnik)
                    || !stanje.getOdeljenja().contains(odeljenje)
                    || !Dani.SVI.contains(dan)) {
                throw new IllegalArgumentException("Zaključani čas pokazuje na nepoznatog nastavnika, odeljenje ili dan.");
            }
            if (predmet.isEmpty() || termin < 0) {
                throw new IllegalArgumentException("Zaključani čas mora da ima predmet i čas od 1 naviše.");
            }
            if (termin >= stanje.getTerminaDnevno()) {
                stanje.setTerminaDnevno(termin + 1);
                stanje.srediRaspored();
            }
            stanje.getRaspored().get(odeljenje).get(dan).set(termin, new Cas(predmet, nastavnik, maxDnevno, true));
        }
    }
}
