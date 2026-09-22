package com.BP.setlistaggregator.service;

import com.BP.setlistaggregator.internalDTO.SetlistSummary;
import com.BP.setlistaggregator.internalDTO.SongsRanked;
import com.BP.setlistaggregator.internalDTO.TourSummary;
import com.BP.setlistaggregator.model.Setlist;
import com.BP.setlistaggregator.model.Song;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

//calculates every stat shown on the results page from a list of an artist's setlists
//SetlistService decides which setlists to use (DB or setlist.fm); this class only does the math
//all stats leave out tapes (recorded intros/outros) so they only count songs actually played live
@Service
public class SetlistStatsService {

    //how many songs each ranked list shows
    private static final int TOP_N = 5;
    //share of shows a song must appear in to count as part of the artist's core set
    private static final double CORE_SONG_SHARE = 0.9;
    //songs played at between 20% and 60% of shows are rotating in and out of the set
    private static final double ROTATION_MIN_SHARE = 0.2;
    private static final double ROTATION_MAX_SHARE = 0.6;
    //"new in the set" = first played within the most recent quarter of shows in the range
    private static final double RECENT_SHARE = 0.25;
    //a song has to miss at least this many shows in a row before its return counts as a bust-out
    private static final int MIN_BUSTOUT_GAP = 3;
    //set position stats only use songs played at 25%+ of shows so one-offs don't skew them
    private static final double SET_POSITION_MIN_SHARE = 0.25;
    //pattern stats (rotation, new songs, set position) need enough shows to mean anything
    private static final int MIN_SHOWS_FOR_PATTERNS = 5;
    //a song only counts as a regional special if it was played at 2+ shows in that one country
    private static final int MIN_REGIONAL_SHOWS = 2;
    //...and that country wasn't most of the tour (otherwise every rarity looks "regional")
    private static final double MAX_REGIONAL_COUNTRY_SHARE = 0.6;

    //one show as the stats see it: live songs in the order they were played, plus where and when
    private record Show(LocalDate date, List<Song> songs, Set<String> titles,
                        String venue, String city, String country, String tour) {}

    //calculate every stat for the results page in one pass over the setlists
    //keys are grouped the way the results page shows them
    public Map<String, Object> calculateAllStats(List<Setlist> setlists) {
        List<Show> shows = toShows(setlists);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("summary", getSummary(shows));
        //the songs
        stats.put("mostPlayed", getMostPlayedSongs(shows));
        stats.put("rarest", getRarestSongs(shows));
        stats.put("rotation", getRotationSongs(shows));
        stats.put("newSongs", getNewSongs(shows));
        //how the show runs
        stats.put("openers", getTopOpeners(shows));
        stats.put("encores", getTopEncoreSongs(shows));
        stats.put("closers", getTopClosers(shows));
        stats.put("transitions", getTopTransitions(shows));
        //deep cuts & patterns
        stats.put("bustouts", getBiggestBustouts(shows));
        stats.put("covers", getTopCovers(shows));
        stats.put("early", getUsuallyEarly(shows));
        stats.put("late", getSavedForLate(shows));
        //on the road
        stats.put("cities", getTopCities(shows));
        stats.put("countries", getTopCountries(shows));
        stats.put("venues", getTopVenues(shows));
        stats.put("tours", getTours(shows));
        stats.put("regional", getRegionalSpecials(shows));
        return stats;
    }

    //convert setlists (newest first, as SetlistService returns them) into shows, oldest first
    //shows with no live songs (e.g. only tapes) are dropped
    private List<Show> toShows(List<Setlist> setlists) {
        List<Show> shows = new ArrayList<>();
        for (Setlist setlist : setlists) {
            List<Song> liveSongs = new ArrayList<>();
            if (setlist.getSongs() != null) {
                for (Song song : setlist.getSongs()) {
                    if (!song.isTape() && isValidTitle(song.getTitle())) {
                        liveSongs.add(song);
                    }
                }
            }
            if (liveSongs.isEmpty()) {
                continue;
            }
            //put songs in the order they were played
            liveSongs.sort(Comparator.comparingInt(Song::getPosition));
            Set<String> titles = new LinkedHashSet<>();
            for (Song song : liveSongs) {
                titles.add(song.getTitle());
            }
            shows.add(new Show(setlist.getDate(), liveSongs, titles,
                    setlist.getVenue(), setlist.getCity(), setlist.getCountry(), setlist.getTour()));
        }
        //setlists come newest first, so reverse to walk through shows in the order they happened
        Collections.reverse(shows);
        return shows;
    }

    // ---------------- summary tiles ----------------

    private SetlistSummary getSummary(List<Show> shows) {
        if (shows.isEmpty()) {
            return null;
        }

        //dates, show lengths, song counts
        LocalDate first = null;
        LocalDate last = null;
        Show longest = null;
        Show shortest = null;
        int totalSongs = 0;
        int coverSongs = 0;
        int showsWithEncore = 0;
        Set<String> countries = new HashSet<>();
        Set<String> cities = new HashSet<>();

        for (Show show : shows) {
            LocalDate date = show.date();
            if (date != null) {
                if (first == null || date.isBefore(first)) first = date;
                if (last == null || date.isAfter(last)) last = date;
            }
            int count = show.songs().size();
            //>= / <= so ties go to the most recent show (shows are oldest first)
            if (longest == null || count >= longest.songs().size()) longest = show;
            if (shortest == null || count <= shortest.songs().size()) shortest = show;
            totalSongs += count;

            boolean hadEncore = false;
            for (Song song : show.songs()) {
                if (song.getCoverArtist() != null) coverSongs++;
                if (song.isEncore()) hadEncore = true;
            }
            if (hadEncore) showsWithEncore++;
            if (show.country() != null) countries.add(show.country());
            if (show.city() != null) cities.add(show.city());
        }

        Double daysBetweenShows = null;
        if (shows.size() > 1 && first != null && last != null) {
            daysBetweenShows = (double) ChronoUnit.DAYS.between(first, last) / (shows.size() - 1);
        }

        //how many shows each song appeared in
        Map<String, Integer> showsPerSong = countShowsPerSong(shows);
        int coreThreshold = (int) Math.ceil(shows.size() * CORE_SONG_SHARE);
        int coreSongs = 0;
        int oneOffs = 0;
        for (int count : showsPerSong.values()) {
            if (count >= coreThreshold) coreSongs++;
            if (count == 1) oneOffs++;
        }

        return new SetlistSummary(
                shows.size(),
                first != null ? first.toString() : null,
                last != null ? last.toString() : null,
                (double) totalSongs / shows.size(),
                longest.songs().size(),
                longest.date() != null ? longest.date().toString() : null,
                shortest.songs().size(),
                shortest.date() != null ? shortest.date().toString() : null,
                daysBetweenShows,
                showsPerSong.size(),
                oneOffs,
                getRepeatRate(shows),
                coreSongs,
                totalSongs > 0 ? 100.0 * coverSongs / totalSongs : null,
                100.0 * showsWithEncore / shows.size(),
                countries.size(),
                cities.size()
        );
    }

    //average % of each show's songs that were also played at the show before it
    private Double getRepeatRate(List<Show> shows) {
        if (shows.size() < 2) {
            return null;
        }
        double totalShare = 0;
        for (int i = 1; i < shows.size(); i++) {
            Set<String> current = shows.get(i).titles();
            Set<String> previous = shows.get(i - 1).titles();
            int repeated = 0;
            for (String title : current) {
                if (previous.contains(title)) repeated++;
            }
            totalShare += (double) repeated / current.size();
        }
        return 100.0 * totalShare / (shows.size() - 1);
    }

    // ---------------- the songs ----------------

    //songs played at the most shows; count = number of shows (frontend shows it as % of shows)
    private List<SongsRanked> getMostPlayedSongs(List<Show> shows) {
        return rank(countShowsPerSong(shows), false);
    }

    //songs played the fewest times; count = times played
    private List<SongsRanked> getRarestSongs(List<Show> shows) {
        Map<String, Integer> plays = new HashMap<>();
        for (Show show : shows) {
            for (Song song : show.songs()) {
                plays.merge(song.getTitle(), 1, Integer::sum);
            }
        }
        return rank(plays, true);
    }

    //songs that rotate in and out of the set (played at 20-60% of shows); count = % of shows
    private List<SongsRanked> getRotationSongs(List<Show> shows) {
        if (shows.size() < MIN_SHOWS_FOR_PATTERNS) {
            return Collections.emptyList();
        }
        Map<String, Integer> percents = new HashMap<>();
        for (Map.Entry<String, Integer> entry : countShowsPerSong(shows).entrySet()) {
            double share = (double) entry.getValue() / shows.size();
            if (share >= ROTATION_MIN_SHARE && share <= ROTATION_MAX_SHARE) {
                percents.put(entry.getKey(), (int) Math.round(share * 100));
            }
        }
        return rank(percents, false);
    }

    //songs first played within the latest quarter of shows; count = shows played at since then
    private List<SongsRanked> getNewSongs(List<Show> shows) {
        if (shows.size() < MIN_SHOWS_FOR_PATTERNS) {
            return Collections.emptyList();
        }
        int recentStart = shows.size() - (int) Math.ceil(shows.size() * RECENT_SHARE);
        Set<String> playedBefore = new HashSet<>();
        for (int i = 0; i < recentStart; i++) {
            playedBefore.addAll(shows.get(i).titles());
        }
        Map<String, Integer> plays = new HashMap<>();
        for (int i = recentStart; i < shows.size(); i++) {
            for (String title : shows.get(i).titles()) {
                if (!playedBefore.contains(title)) {
                    plays.merge(title, 1, Integer::sum);
                }
            }
        }
        return rank(plays, false);
    }

    // ---------------- how the show runs ----------------

    //first live song of each show; count = shows it opened
    private List<SongsRanked> getTopOpeners(List<Show> shows) {
        return rankPerShow(shows, show -> show.songs().get(0).getTitle());
    }

    //last song of the main set (the song right before the encore, or the last song if there was no encore)
    //count = shows it closed the main set
    private List<SongsRanked> getTopClosers(List<Show> shows) {
        return rankPerShow(shows, show -> {
            String lastMainSetSong = null;
            for (Song song : show.songs()) {
                if (!song.isEncore()) lastMainSetSong = song.getTitle();
            }
            //every song was marked encore (rare data quirk): fall back to the last song
            return lastMainSetSong != null ? lastMainSetSong : show.songs().get(show.songs().size() - 1).getTitle();
        });
    }

    //songs played in sections setlist.fm marks as encores; count = shows it was in the encore
    private List<SongsRanked> getTopEncoreSongs(List<Show> shows) {
        Map<String, Integer> counts = new HashMap<>();
        for (Show show : shows) {
            Set<String> encoreTitles = new HashSet<>();
            for (Song song : show.songs()) {
                if (song.isEncore()) encoreTitles.add(song.getTitle());
            }
            for (String title : encoreTitles) {
                counts.merge(title, 1, Integer::sum);
            }
        }
        return rank(counts, false);
    }

    //song pairs most often played back to back; count = times; pairs heard once are left out
    private List<SongsRanked> getTopTransitions(List<Show> shows) {
        Map<String, Integer> pairs = new HashMap<>();
        for (Show show : shows) {
            List<Song> songs = show.songs();
            for (int i = 1; i < songs.size(); i++) {
                String from = songs.get(i - 1).getTitle();
                String to = songs.get(i).getTitle();
                if (!from.equals(to)) {
                    pairs.merge(from + " → " + to, 1, Integer::sum);
                }
            }
        }
        pairs.values().removeIf(count -> count < 2);
        return rank(pairs, false);
    }

    // ---------------- deep cuts & patterns ----------------

    //songs that came back after sitting out the most shows in a row; count = shows missed
    private List<SongsRanked> getBiggestBustouts(List<Show> shows) {
        Map<String, Integer> lastShowIndex = new HashMap<>();
        Map<String, Integer> longestGap = new HashMap<>();
        for (int i = 0; i < shows.size(); i++) {
            for (String title : shows.get(i).titles()) {
                Integer lastIndex = lastShowIndex.get(title);
                if (lastIndex != null) {
                    int missed = i - lastIndex - 1;
                    if (missed >= MIN_BUSTOUT_GAP) {
                        longestGap.merge(title, missed, Math::max);
                    }
                }
                lastShowIndex.put(title, i);
            }
        }
        return rank(longestGap, false);
    }

    //most played covers, labeled "Song (Original Artist)"; count = times played
    private List<SongsRanked> getTopCovers(List<Show> shows) {
        Map<String, Integer> plays = new HashMap<>();
        for (Show show : shows) {
            for (Song song : show.songs()) {
                if (song.getCoverArtist() != null) {
                    plays.merge(song.getTitle() + " (" + song.getCoverArtist() + ")", 1, Integer::sum);
                }
            }
        }
        return rank(plays, false);
    }

    //regular songs that usually come earliest in the set; count = average song number
    private List<SongsRanked> getUsuallyEarly(List<Show> shows) {
        return rankBySetPosition(shows, true);
    }

    //regular songs usually saved for the end; count = average song number
    private List<SongsRanked> getSavedForLate(List<Show> shows) {
        return rankBySetPosition(shows, false);
    }

    //ranks songs by average relative position (0 = first song, 1 = last), so long and short shows compare fairly
    private List<SongsRanked> rankBySetPosition(List<Show> shows, boolean earliestFirst) {
        if (shows.size() < MIN_SHOWS_FOR_PATTERNS) {
            return Collections.emptyList();
        }
        Map<String, double[]> totals = new HashMap<>(); //title -> {sum of relative position, sum of song number, shows}
        for (Show show : shows) {
            List<Song> songs = show.songs();
            if (songs.size() < 2) continue;
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < songs.size(); i++) {
                String title = songs.get(i).getTitle();
                //only count a song's first appearance in a show
                if (!seen.add(title)) continue;
                double[] t = totals.computeIfAbsent(title, k -> new double[3]);
                t[0] += (double) i / (songs.size() - 1);
                t[1] += i + 1;
                t[2]++;
            }
        }
        int minShows = Math.max(2, (int) Math.ceil(shows.size() * SET_POSITION_MIN_SHARE));
        List<Map.Entry<String, double[]>> eligible = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : totals.entrySet()) {
            if (entry.getValue()[2] >= minShows) eligible.add(entry);
        }
        Comparator<Map.Entry<String, double[]>> byPosition =
                Comparator.comparingDouble(e -> e.getValue()[0] / e.getValue()[2]);
        if (!earliestFirst) byPosition = byPosition.reversed();
        eligible.sort(byPosition.thenComparing(Map.Entry::getKey));

        List<SongsRanked> results = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_N, eligible.size()); i++) {
            double[] t = eligible.get(i).getValue();
            results.add(new SongsRanked(i + 1, eligible.get(i).getKey(), (int) Math.round(t[1] / t[2])));
        }
        return results;
    }

    // ---------------- on the road ----------------

    //count = shows in each city / country / venue / tour
    private List<SongsRanked> getTopCities(List<Show> shows) {
        return rankPerShow(shows, Show::city);
    }

    private List<SongsRanked> getTopCountries(List<Show> shows) {
        return rankPerShow(shows, Show::country);
    }

    //venues labeled with their city so same-named venues don't merge ("Paradise Rock Club, Boston, MA")
    private List<SongsRanked> getTopVenues(List<Show> shows) {
        return rankPerShow(shows, show -> show.venue() == null ? null
                : show.city() == null ? show.venue() : show.venue() + ", " + show.city());
    }

    //each tour in the range with its show count, average set length, and songs played, most recent tour first
    private List<TourSummary> getTours(List<Show> shows) {
        //group shows by tour, keeping them in date order
        Map<String, List<Show>> byTour = new LinkedHashMap<>();
        for (Show show : shows) {
            if (show.tour() != null) {
                byTour.computeIfAbsent(show.tour(), k -> new ArrayList<>()).add(show);
            }
        }
        List<TourSummary> tours = new ArrayList<>();
        for (Map.Entry<String, List<Show>> entry : byTour.entrySet()) {
            List<Show> tourShows = entry.getValue();
            int totalSongs = 0;
            Set<String> songs = new HashSet<>();
            for (Show show : tourShows) {
                totalSongs += show.songs().size();
                songs.addAll(show.titles());
            }
            LocalDate first = tourShows.get(0).date();
            LocalDate last = tourShows.get(tourShows.size() - 1).date();
            tours.add(new TourSummary(entry.getKey(), tourShows.size(), (double) totalSongs / tourShows.size(), songs.size(),
                    first != null ? first.toString() : null, last != null ? last.toString() : null));
        }
        //most recent tour first
        tours.sort(Comparator.comparing(TourSummary::lastDate, Comparator.nullsLast(Comparator.reverseOrder())));
        return tours.subList(0, Math.min(TOP_N, tours.size()));
    }

    //songs played in only one country (at 2+ shows there), labeled "Song (Country)"; count = shows
    //shows whether the set changes by country, e.g. a local cover or a song saved for one market
    private List<SongsRanked> getRegionalSpecials(List<Show> shows) {
        //how many shows per country, and which countries each song was played in
        Map<String, Integer> showsPerCountry = new HashMap<>();
        Map<String, Map<String, Integer>> songCountries = new HashMap<>(); //title -> country -> shows
        for (Show show : shows) {
            if (show.country() == null) continue;
            showsPerCountry.merge(show.country(), 1, Integer::sum);
            for (String title : show.titles()) {
                songCountries.computeIfAbsent(title, k -> new HashMap<>()).merge(show.country(), 1, Integer::sum);
            }
        }
        //need at least two countries to compare
        if (showsPerCountry.size() < 2) {
            return Collections.emptyList();
        }
        int showsWithCountry = 0;
        for (int count : showsPerCountry.values()) showsWithCountry += count;

        Map<String, Integer> specials = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : songCountries.entrySet()) {
            Map<String, Integer> countries = entry.getValue();
            if (countries.size() != 1) continue;
            Map.Entry<String, Integer> only = countries.entrySet().iterator().next();
            double countryShare = (double) showsPerCountry.get(only.getKey()) / showsWithCountry;
            if (only.getValue() >= MIN_REGIONAL_SHOWS && countryShare <= MAX_REGIONAL_COUNTRY_SHARE) {
                specials.put(entry.getKey() + " (" + only.getKey() + ")", only.getValue());
            }
        }
        return rank(specials, false);
    }

    // ---------------- helpers ----------------

    //how many shows each song appeared in
    private Map<String, Integer> countShowsPerSong(List<Show> shows) {
        Map<String, Integer> counts = new HashMap<>();
        for (Show show : shows) {
            for (String title : show.titles()) {
                counts.merge(title, 1, Integer::sum);
            }
        }
        return counts;
    }

    //count one value per show (skipping nulls) and rank the most common
    private List<SongsRanked> rankPerShow(List<Show> shows, Function<Show, String> valueOf) {
        Map<String, Integer> counts = new HashMap<>();
        for (Show show : shows) {
            String value = valueOf.apply(show);
            if (value != null) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return rank(counts, false);
    }

    //sort by count (highest first, or lowest first if ascending), ties alphabetical so results are stable,
    //then keep the top 5 as ranked entries
    private List<SongsRanked> rank(Map<String, Integer> counts, boolean ascending) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        Comparator<Map.Entry<String, Integer>> byCount = Map.Entry.comparingByValue();
        if (!ascending) byCount = byCount.reversed();
        entries.sort(byCount.thenComparing(Map.Entry.comparingByKey()));

        List<SongsRanked> ranked = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_N, entries.size()); i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            //add 1 to i so first song is ranked 1 and not 0
            ranked.add(new SongsRanked(i + 1, entry.getKey(), entry.getValue()));
        }
        return ranked;
    }

    //skip songs with missing titles
    private boolean isValidTitle(String title) {
        return title != null && !title.isBlank();
    }
}
