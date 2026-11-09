package rasporedcasova.model;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Pravila {
    public static final List<String> TIPOVI_SKOLE = List.of("1-4", "5-8", "srednja", "fakultet");

    private Pravila() {
    }

    public static String sklopi(String vrednost) {
        String lower = vrednost.toLowerCase(Locale.ROOT);
        return lower
                .replace("č", "c")
                .replace("ć", "c")
                .replace("š", "s")
                .replace("ž", "z")
                .replace("đ", "dj");
    }

    public static String srediSmenu(String sirovo) {
        String kljuc = sklopi(sirovo == null ? "" : sirovo).replace(" ", "");
        if (Set.of("popodne", "poslepodne", "poslepodnevna", "druga", "2").contains(kljuc)) {
            return "popodne";
        }
        return "prepodne";
    }

    public static boolean jeBlok(String predmet) {
        String kljuc = sklopi(predmet);
        return kljuc.contains("tehnika") || kljuc.contains("tehnic");
    }

    public static boolean jeLikovno(String predmet) {
        return sklopi(predmet).contains("likovn");
    }

    public static boolean jeBlok(String predmet, int nedeljno) {
        return jeBlok(predmet, nedeljno, 0, null);
    }

    public static boolean jeBlok(String predmet, int nedeljno, int maxDnevno) {
        return jeBlok(predmet, nedeljno, maxDnevno, null);
    }

    public static boolean jeBlok(Zaduzenje dodela) {
        return jeBlok(dodela, null);
    }

    public static boolean jeBlok(Zaduzenje dodela, String tipSkole) {
        if (dodela == null) {
            return false;
        }
        return jeBlok(
                dodela.getPredmet(),
                dodela.getNedeljno(),
                dodela.getMaxDnevno(),
                tipSkole);
    }

    public static boolean jeBlok(String predmet, int nedeljno, int maxDnevno, String tipSkole) {
        if (jeBlok(predmet)) {
            return true;
        }
        if (jeLikovno(predmet)) {
            if (tipSkole != null && razredna(tipSkole)) {
                return false;
            }
            return nedeljno >= 2 || maxDnevno == 2;
        }
        if (!"srednja".equals(tipSkole)) {
            return false;
        }
        String kljuc = sklopi(predmet);
        boolean labBlock = kljuc.contains("hemij") || kljuc.contains("informat");
        return labBlock && (nedeljno >= 2 || maxDnevno == 2);
    }

    public static boolean smePoredBloka(String predmet) {
        return sklopi(predmet).contains("hemij");
    }

    public static boolean jeFizicko(String predmet) {
        return sklopi(predmet).contains("fizicko");
    }

    public static boolean upareneSmene(String tipSkole) {
        return "5-8".equals(tipSkole) || "srednja".equals(tipSkole);
    }

    public static String parOdeljenje(String odeljenje) {
        if (odeljenje == null || odeljenje.isBlank()) {
            return null;
        }
        String[] delovi = odeljenje.split("-", 2);
        if (delovi.length != 2) {
            return null;
        }
        String sufiks = delovi[1].strip();
        String parRazred = parRazred(delovi[0].strip());
        if (parRazred == null) {
            return null;
        }
        return parRazred + "-" + sufiks;
    }

    private static String parRazred(String gradeKey) {
        String roman = switch (gradeKey.toUpperCase(Locale.ROOT)) {
            case "I" -> "II";
            case "II" -> "I";
            case "III" -> "IV";
            case "IV" -> "III";
            default -> null;
        };
        if (roman != null) {
            return roman;
        }
        try {
            int grade = Integer.parseInt(gradeKey);
            int par = switch (grade) {
                case 5 -> 7;
                case 7 -> 5;
                case 6 -> 8;
                case 8 -> 6;
                default -> -1;
            };
            if (par < 0) {
                return null;
            }
            return Integer.toString(par);
        } catch (NumberFormatException nista) {
            return null;
        }
    }

    public static int terminiZaTip(String tipSkole) {
        return switch (tipSkole) {
            case "1-4" -> 5;
            case "5-8", "srednja" -> 7;
            default -> 8;
        };
    }

    public static boolean razredna(String tipSkole) {
        return "1-4".equals(tipSkole);
    }

    public static boolean predmetni(String predmet) {
        String kljuc = sklopi(predmet);
        return kljuc.contains("englesk") || kljuc.contains("veronauk") || kljuc.contains("nemack")
                || kljuc.contains("njemack");
    }

    public static boolean ucitelj(Nastavnik nastavnik) {
        if (nastavnik == null || nastavnik.getDodele().isEmpty()) {
            return false;
        }
        for (Zaduzenje dodela : nastavnik.getDodele()) {
            if (!predmetni(dodela.getPredmet())) {
                return true;
            }
        }
        return false;
    }

    public static String nazivTipa(String tipSkole) {
        return switch (tipSkole) {
            case "1-4" -> "Osnovna 1–4";
            case "5-8" -> "Osnovna 5–8";
            case "srednja" -> "Srednja škola";
            case "fakultet" -> "Fakultet";
            default -> "— izaberite tip škole —";
        };
    }

    public static String nazivSmene(String smena) {
        return "popodne".equals(smena) ? "poslepodne" : "prepodne";
    }

    public static boolean odPrvogCasa(String tipSkole) {
        return razredna(tipSkole) || Set.of("5-8", "srednja").contains(tipSkole);
    }

    public static int[] graniceDana(String tipSkole) {
        return switch (tipSkole) {
            case "1-4" -> new int[] {4, 5};
            case "5-8", "srednja" -> new int[] {5, 7};
            default -> new int[] {1, 16};
        };
    }

    public static int dozvolaPauza(int teacherCount) {
        return Math.min(8, Math.max(3, (Math.max(1, teacherCount) + 4) / 6));
    }

    public static int limitPosete(String tipSkole, boolean oznakaUcitelja) {
        if (!razredna(tipSkole) || oznakaUcitelja) {
            return Integer.MAX_VALUE;
        }
        int[] granice = graniceDana(tipSkole);
        return Math.max(1, granice[1] - granice[0] + 1);
    }

    public static boolean jednaPoseta(int morningMask, int afternoonMask, int terminaDnevno) {
        if (morningMask == 0 || afternoonMask == 0 || terminaDnevno <= 0) {
            return false;
        }
        int lastMorning = terminaDnevno - 1;
        return (morningMask & (1 << lastMorning)) != 0 && (afternoonMask & 1) != 0;
    }
}
