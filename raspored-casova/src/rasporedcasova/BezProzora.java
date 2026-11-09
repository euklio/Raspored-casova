package rasporedcasova;

import rasporedcasova.motor.NemaResenja;
import rasporedcasova.motor.Ucitavanje;
import rasporedcasova.motor.Motor;
import rasporedcasova.motor.Stanje;
import rasporedcasova.model.Dani;
import rasporedcasova.model.Cas;
import rasporedcasova.model.Pravila;
import rasporedcasova.model.Nastavnik;

import java.nio.file.Path;

public final class BezProzora {
    public static void main(String[] args) throws Exception {
        Path putanja = Path.of(args.length > 0 ? args[0] : "tests/os_1_4_18_odeljenja.json");
        String profil = args.length > 1 ? args[1] : "balanced";
        Stanje stanje = new Stanje();
        Ucitavanje.ucitajFajl(stanje, putanja);
        if (args.length > 2 && ("single".equalsIgnoreCase(args[2]) || "jedna".equalsIgnoreCase(args[2]))) {
            stanje.setJednaSmena(true);
        }
        System.out.println("singleShift=" + stanje.jednaSmenaUkljucena() + " shifts=" + stanje.korisceneSmene());
        long started = System.nanoTime();
        try {
            Motor.Rezultat rezultat = new Motor(stanje).napravi(profil);
            long ms = (System.nanoTime() - started) / 1_000_000L;
            System.out.println("nodes=" + rezultat.cvorovi() + " softCost=" + rezultat.mekaCena() + " ms=" + ms);
            System.out.println("violations=" + rezultat.napomene().size());
            for (String violation : rezultat.napomene()) {
                System.out.println(" - " + violation);
            }
            int classroomDays = 0;
            int badPause = 0;
            int kasno = 0;
            for (Nastavnik nastavnik : stanje.getNastavnici().values()) {
                if (!Pravila.ucitelj(nastavnik)) {
                    continue;
                }
                for (String dan : Dani.SVI) {
                    for (String smena : stanje.korisceneSmene()) {
                        int maska = 0;
                        int broj = 0;
                        for (String odeljenje : stanje.getOdeljenja()) {
                            if (!stanje.smenaZa(odeljenje).equals(smena)) {
                                continue;
                            }
                            java.util.List<Cas> celije = stanje.getRaspored().get(odeljenje).get(dan);
                            for (int termin = 0; termin < celije.size(); termin++) {
                                Cas cas = celije.get(termin);
                                if (cas != null && nastavnik.getIme().equals(cas.getNastavnik())) {
                                    maska |= 1 << termin;
                                    broj += 1;
                                }
                            }
                        }
                        if (broj == 0) {
                            continue;
                        }
                        classroomDays += 1;
                        if ((maska & 1) == 0) {
                            kasno += 1;
                        }
                        int rupe = Integer.bitCount(((1 << (32 - Integer.numberOfLeadingZeros(maska))) - 1)
                                >> Integer.numberOfTrailingZeros(maska)) - broj;
                        if (rupe > 0) {
                            badPause += 1;
                        }
                    }
                }
            }
            System.out.println("classroomDays=" + classroomDays + " late=" + kasno + " pauses=" + badPause);
            int teacherDays = 0;
            int singletonDays = 0;
            int daniViskaPauza = 0;
            int threePauseDays = 0;
            int[] histogramPauza = new int[6];
            for (Nastavnik nastavnik : stanje.getNastavnici().values()) {
                boolean uciteljski = Pravila.razredna(stanje.getTipSkole())
                        && Pravila.ucitelj(nastavnik);
                for (String dan : Dani.SVI) {
                    for (String smena : stanje.korisceneSmene()) {
                        int maska = 0;
                        for (String odeljenje : stanje.getOdeljenja()) {
                            if (!stanje.smenaZa(odeljenje).equals(smena)) {
                                continue;
                            }
                            java.util.List<Cas> celije = stanje.getRaspored().get(odeljenje).get(dan);
                            for (int termin = 0; termin < celije.size(); termin++) {
                                Cas cas = celije.get(termin);
                                if (cas != null && nastavnik.getIme().equals(cas.getNastavnik())) {
                                    maska |= 1 << termin;
                                }
                            }
                        }
                        int broj = Integer.bitCount(maska);
                        if (broj == 0) {
                            continue;
                        }
                        teacherDays += 1;
                        if (broj == 1) {
                            singletonDays += 1;
                        }
                        int rupe = 0;
                        if (broj >= 2) {
                            int raspon = Integer.SIZE - Integer.numberOfLeadingZeros(maska)
                                    - Integer.numberOfTrailingZeros(maska);
                            rupe = raspon - broj;
                        }
                        histogramPauza[Math.min(rupe, 5)] += 1;
                        if (rupe >= 2 && !uciteljski) {
                            daniViskaPauza += 1;
                        }
                        if (rupe >= 3 && !uciteljski) {
                            threePauseDays += 1;
                        }
                    }
                }
            }
            System.out.println("teacherDays=" + teacherDays + " singletons=" + singletonDays
                    + " extraPauseDays=" + daniViskaPauza + " threePlus=" + threePauseDays
                    + " pauses0/1/2/3+=" + histogramPauza[0] + "/" + histogramPauza[1] + "/"
                    + histogramPauza[2] + "/" + (histogramPauza[3] + histogramPauza[4] + histogramPauza[5]));
        } catch (NemaResenja izuzetak) {
            System.out.println("UNSAT " + izuzetak.getKind() + " nodes=" + izuzetak.getNodes());
            System.out.println(izuzetak.getMessage());
            System.exit(2);
        }
    }
}
