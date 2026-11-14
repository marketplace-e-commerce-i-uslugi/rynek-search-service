package pl.rynek.search.index;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stała lista większych miast Polski ze współrzędnymi: podpowiedzi miejscowości, filtr promienia ({@code radiusKm}) i
 * współrzędne ofert (oferta ma tylko tekst lokalizacji, np. „Kraków”). Pełna baza miejscowości (geokodowanie) to osobna decyzja.
 */
public final class Cities {

    /** Miasto: nazwa, województwo i współrzędne środka. */
    public record City(String name, String region, double lat, double lon) {

        public String key() {
            return fold(name);
        }
    }

    private static final List<City> ALL = List.of(
            new City("Warszawa", "mazowieckie", 52.2297, 21.0122), new City("Kraków", "małopolskie", 50.0647, 19.9450),
            new City("Łódź", "łódzkie", 51.7592, 19.4560), new City("Wrocław", "dolnośląskie", 51.1079, 17.0385),
            new City("Poznań", "wielkopolskie", 52.4064, 16.9252), new City("Gdańsk", "pomorskie", 54.3520, 18.6466),
            new City("Szczecin", "zachodniopomorskie", 53.4285, 14.5528), new City("Bydgoszcz", "kujawsko-pomorskie", 53.1235, 18.0084),
            new City("Lublin", "lubelskie", 51.2465, 22.5684), new City("Białystok", "podlaskie", 53.1325, 23.1688),
            new City("Katowice", "śląskie", 50.2649, 19.0238), new City("Gdynia", "pomorskie", 54.5189, 18.5305),
            new City("Częstochowa", "śląskie", 50.8118, 19.1203), new City("Radom", "mazowieckie", 51.4027, 21.1471),
            new City("Toruń", "kujawsko-pomorskie", 53.0138, 18.5984), new City("Sosnowiec", "śląskie", 50.2863, 19.1041),
            new City("Rzeszów", "podkarpackie", 50.0412, 21.9991), new City("Kielce", "świętokrzyskie", 50.8661, 20.6286),
            new City("Gliwice", "śląskie", 50.2945, 18.6714), new City("Olsztyn", "warmińsko-mazurskie", 53.7784, 20.4801),
            new City("Zabrze", "śląskie", 50.3249, 18.7857), new City("Bielsko-Biała", "śląskie", 49.8224, 19.0584),
            new City("Bytom", "śląskie", 50.3484, 18.9157), new City("Zielona Góra", "lubuskie", 51.9356, 15.5062),
            new City("Rybnik", "śląskie", 50.0971, 18.5463), new City("Ruda Śląska", "śląskie", 50.2584, 18.8563),
            new City("Opole", "opolskie", 50.6751, 17.9213), new City("Tychy", "śląskie", 50.1218, 18.9666),
            new City("Gorzów Wielkopolski", "lubuskie", 52.7368, 15.2288), new City("Elbląg", "warmińsko-mazurskie", 54.1522, 19.4088),
            new City("Płock", "mazowieckie", 52.5463, 19.7065), new City("Wałbrzych", "dolnośląskie", 50.7840, 16.2843),
            new City("Włocławek", "kujawsko-pomorskie", 52.6483, 19.0678), new City("Tarnów", "małopolskie", 50.0121, 20.9858),
            new City("Chorzów", "śląskie", 50.2970, 18.9540), new City("Koszalin", "zachodniopomorskie", 54.1943, 16.1714),
            new City("Kalisz", "wielkopolskie", 51.7611, 18.0910), new City("Legnica", "dolnośląskie", 51.2070, 16.1619),
            new City("Grudziądz", "kujawsko-pomorskie", 53.4837, 18.7536), new City("Słupsk", "pomorskie", 54.4641, 17.0285),
            new City("Jaworzno", "śląskie", 50.2060, 19.2750), new City("Nowy Sącz", "małopolskie", 49.6249, 20.6970),
            new City("Jelenia Góra", "dolnośląskie", 50.9044, 15.7194), new City("Konin", "wielkopolskie", 52.2230, 18.2511),
            new City("Piotrków Trybunalski", "łódzkie", 51.4053, 19.7033), new City("Lubin", "dolnośląskie", 51.4012, 16.2015),
            new City("Siedlce", "mazowieckie", 52.1676, 22.2902), new City("Inowrocław", "kujawsko-pomorskie", 52.7973, 18.2616),
            new City("Mielec", "podkarpackie", 50.2872, 21.4249), new City("Gniezno", "wielkopolskie", 52.5348, 17.5826),
            new City("Zakopane", "małopolskie", 49.2992, 19.9496), new City("Przemyśl", "podkarpackie", 49.7838, 22.7678),
            new City("Ostrołęka", "mazowieckie", 53.0840, 21.5742), new City("Leszno", "wielkopolskie", 51.8403, 16.5749),
            new City("Pruszków", "mazowieckie", 52.1704, 20.8120), new City("Piaseczno", "mazowieckie", 52.0726, 21.0233));

    private static final Map<String, City> BY_KEY = ALL.stream().collect(Collectors.toMap(City::key, Function.identity()));

    /** Oferta bez konkretnej miejscowości (usługa lub wysyłka na terenie całego kraju). */
    public static final String NATIONWIDE = "cala polska";

    private Cities() {
    }

    /** Małe litery bez znaków diakrytycznych: „Łódź” i „lodz” to ten sam klucz. */
    public static String fold(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace('ł', 'l').trim();
    }

    /** Miasto o dokładnie takiej nazwie (bez względu na wielkość liter i polskie znaki). */
    public static Optional<City> exact(String name) {
        return Optional.ofNullable(BY_KEY.get(fold(name)));
    }

    /** Pierwsze znane miasto wymienione w tekście lokalizacji, np. „Kraków, Podgórze”. */
    public static Optional<City> in(String locationText) {
        String folded = fold(locationText);
        return ALL.stream().filter(city -> folded.contains(city.key())).findFirst();
    }

    /** Miasta, których nazwa zaczyna się od wpisanego tekstu (bez względu na wielkość liter i polskie znaki). */
    public static List<City> startingWith(String prefix, int limit) {
        String folded = fold(prefix);
        List<City> result = new ArrayList<>();
        for (City city : ALL) {
            if (city.key().startsWith(folded)) {
                result.add(city);
                if (result.size() == limit) {
                    break;
                }
            }
        }
        return result;
    }
}
