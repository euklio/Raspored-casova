package rasporedcasova.model;

import java.util.List;

public final class Dani {
    public static final List<String> SVI = List.of(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday");

    private Dani() {
    }

    public static String izNaziva(String dan) {
        String s = dan == null ? "" : dan.strip();
        return switch (s) {
            case "Ponedeljak", "ponedeljak", "Pon", "Monday" -> "Monday";
            case "Utorak", "utorak", "Uto", "Tuesday" -> "Tuesday";
            case "Sreda", "sreda", "Sre", "Wednesday" -> "Wednesday";
            case "Četvrtak", "Cetvrtak", "četvrtak", "cetvrtak", "Čet", "Thursday" -> "Thursday";
            case "Petak", "petak", "Pet", "Friday" -> "Friday";
            default -> s;
        };
    }

    public static String label(String dan) {
        return switch (dan) {
            case "Monday" -> "Ponedeljak";
            case "Tuesday" -> "Utorak";
            case "Wednesday" -> "Sreda";
            case "Thursday" -> "Četvrtak";
            case "Friday" -> "Petak";
            default -> dan;
        };
    }

    public static String kratko(String dan) {
        return switch (dan) {
            case "Monday" -> "Pon";
            case "Tuesday" -> "Uto";
            case "Wednesday" -> "Sre";
            case "Thursday" -> "Čet";
            case "Friday" -> "Pet";
            default -> dan;
        };
    }
}
