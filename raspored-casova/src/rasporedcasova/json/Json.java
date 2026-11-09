package rasporedcasova.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private final String tekst;
    private int rb;

    private Json(String tekst) {
        this.tekst = tekst;
    }

    public static Object procitaj(String tekst) {
        Json citac = new Json(tekst);
        Object vrednost = citac.procitajVrednost();
        citac.preskoci();
        if (citac.rb < citac.tekst.length()) {
            throw new IllegalArgumentException("Unexpected trailing JSON content.");
        }
        return vrednost;
    }

    public static Map<String, Object> kaoMapa(Object vrednost) {
        if (!(vrednost instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected a JSON object.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> rezultat = (Map<String, Object>) map;
        return rezultat;
    }

    public static List<Object> kaoLista(Object vrednost) {
        if (!(vrednost instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected a JSON array.");
        }
        @SuppressWarnings("unchecked")
        List<Object> rezultat = (List<Object>) list;
        return rezultat;
    }

    public static String kaoTekst(Object vrednost, String rezerva) {
        return vrednost == null ? rezerva : String.valueOf(vrednost);
    }

    public static int kaoBroj(Object vrednost, int rezerva) {
        if (vrednost == null) {
            return rezerva;
        }
        if (vrednost instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(vrednost));
    }

    public static boolean kaoLogicko(Object vrednost, boolean rezerva) {
        if (vrednost == null) {
            return rezerva;
        }
        if (vrednost instanceof Boolean flag) {
            return flag;
        }
        return Boolean.parseBoolean(String.valueOf(vrednost));
    }

    private Object procitajVrednost() {
        preskoci();
        if (rb >= tekst.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON.");
        }
        char ch = tekst.charAt(rb);
        if (ch == '{') {
            return procitajMapu();
        }
        if (ch == '[') {
            return procitajListu();
        }
        if (ch == '"') {
            return procitajTekst();
        }
        if (ch == 't' || ch == 'f') {
            return procitajLogicko();
        }
        if (ch == 'n') {
            procitajLiteral("null");
            return null;
        }
        return procitajBroj();
    }

    private Map<String, Object> procitajMapu() {
        ocekuj('{');
        Map<String, Object> object = new LinkedHashMap<>();
        preskoci();
        if (virni() == '}') {
            rb++;
            return object;
        }
        while (true) {
            preskoci();
            String kljuc = procitajTekst();
            preskoci();
            ocekuj(':');
            object.put(kljuc, procitajVrednost());
            preskoci();
            char ch = virni();
            if (ch == ',') {
                rb++;
                continue;
            }
            if (ch == '}') {
                rb++;
                return object;
            }
            throw new IllegalArgumentException("Expected comma or } in JSON object.");
        }
    }

    private List<Object> procitajListu() {
        ocekuj('[');
        List<Object> array = new ArrayList<>();
        preskoci();
        if (virni() == ']') {
            rb++;
            return array;
        }
        while (true) {
            array.add(procitajVrednost());
            preskoci();
            char ch = virni();
            if (ch == ',') {
                rb++;
                continue;
            }
            if (ch == ']') {
                rb++;
                return array;
            }
            throw new IllegalArgumentException("Expected comma or ] in JSON array.");
        }
    }

    private String procitajTekst() {
        ocekuj('"');
        StringBuilder builder = new StringBuilder();
        while (rb < tekst.length()) {
            char ch = tekst.charAt(rb++);
            if (ch == '"') {
                return builder.toString();
            }
            if (ch == '\\') {
                if (rb >= tekst.length()) {
                    throw new IllegalArgumentException("Unfinished JSON escape.");
                }
                char escaped = tekst.charAt(rb++);
                switch (escaped) {
                    case '"', '\\', '/' -> builder.append(escaped);
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> {
                        if (rb + 4 > tekst.length()) {
                            throw new IllegalArgumentException("Invalid unicode escape.");
                        }
                        int kod = Integer.parseInt(tekst.substring(rb, rb + 4), 16);
                        builder.append((char) kod);
                        rb += 4;
                    }
                    default -> throw new IllegalArgumentException("Unknown JSON escape.");
                }
            } else {
                builder.append(ch);
            }
        }
        throw new IllegalArgumentException("Unfinished JSON string.");
    }

    private Object procitajBroj() {
        int start = rb;
        if (virni() == '-') {
            rb++;
        }
        while (rb < tekst.length() && Character.isDigit(tekst.charAt(rb))) {
            rb++;
        }
        if (rb < tekst.length() && tekst.charAt(rb) == '.') {
            rb++;
            while (rb < tekst.length() && Character.isDigit(tekst.charAt(rb))) {
                rb++;
            }
        }
        String sirovo = tekst.substring(start, rb);
        if (sirovo.contains(".")) {
            return Double.parseDouble(sirovo);
        }
        return Long.parseLong(sirovo);
    }

    private boolean procitajLogicko() {
        if (tekst.startsWith("true", rb)) {
            rb += 4;
            return true;
        }
        procitajLiteral("false");
        return false;
    }

    private void procitajLiteral(String oznakaLiterala) {
        if (!tekst.startsWith(oznakaLiterala, rb)) {
            throw new IllegalArgumentException("Expected " + oznakaLiterala);
        }
        rb += oznakaLiterala.length();
    }

    private void ocekuj(char ocekivano) {
        preskoci();
        if (virni() != ocekivano) {
            throw new IllegalArgumentException("Expected " + ocekivano);
        }
        rb++;
    }

    private char virni() {
        if (rb >= tekst.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON.");
        }
        return tekst.charAt(rb);
    }

    private void preskoci() {
        while (rb < tekst.length()) {
            char ch = tekst.charAt(rb);
            if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
                rb++;
                continue;
            }
            if (ch == '/' && rb + 1 < tekst.length() && tekst.charAt(rb + 1) == '/') {
                rb += 2;
                while (rb < tekst.length() && tekst.charAt(rb) != '\n') {
                    rb++;
                }
                continue;
            }
            break;
        }
    }
}
