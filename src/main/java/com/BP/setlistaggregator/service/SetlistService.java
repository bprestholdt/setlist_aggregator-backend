package com.BP.setlistaggregator.service;

import com.BP.setlistaggregator.model.Setlist;
import com.BP.setlistaggregator.model.Artist;
//only importing model class, must fully qualify dto Song when used
import com.BP.setlistaggregator.model.Song;

import com.BP.setlistaggregator.externalDTO.SetSection;
import com.BP.setlistaggregator.externalDTO.ApiSetlist;
import com.BP.setlistaggregator.externalDTO.SetlistResponseWrapper;
import com.BP.setlistaggregator.internalDTO.SongsRanked;
import com.BP.setlistaggregator.internalDTO.SetlistConsistency;
import com.BP.setlistaggregator.internalDTO.ShowLengths;
import com.BP.setlistaggregator.internalDTO.TouringPace;
import java.time.temporal.ChronoUnit;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.BP.setlistaggregator.repositories.SetlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;

//class to perform business logic i.e. calculations on stats related to setlists
//source of most of the app's backend logic
//acts as bridge between frontend (controllers) and backend (repositories and API)
/*
Current functions/process:
Checks if artist searched by user already has setlists in db, fetches if not
Calls ArtistService and SetlistFMFetchService to handle API and DB coordination
Calculates statistics to present to user, currently includes methods to return:
-Top Encore Songs
-Rarest songs
Controls data flow between the app's controllers and repositories
*/
//Declare as Service so spring recognizes as service bean, allowing it to be automatically created and injected into other classes like controllers
@Service
public class SetlistService {
    private static final Logger log = LoggerFactory.getLogger(SetlistService.class);

    //declare tools needed for Service class to do its job
    //needed to save and load setlists from database (CRUD)
    private final SetlistRepository setlistRepository;
    //needed to handle artist operations
    private final ArtistService artistService;
    //needed to handle interactions with Setlist.fm API
    private final SetlistFMFetchService setlistFetcher;

    //max allowed size (in setlists per page) per setlist.fm API- should reduce time to fetch all by at least 50% along with total API calls compared to default 20
    private static final int PAGE_SIZE = 50;

    //configure how often to query API for new concerts since we last checked
    private static final int REFRESH_THRESHOLD_DAYS = 7;

    //a song has to miss at least this many shows in a row before its return counts as a bust-out
    private static final int MIN_BUSTOUT_GAP = 3;

    //share of shows a song must appear in to count as part of the artist's core set
    private static final double CORE_SONG_SHARE = 0.9;

    //songs played at between 20% and 60% of shows are rotating in and out of the set
    private static final double ROTATION_MIN_SHARE = 0.2;
    private static final double ROTATION_MAX_SHARE = 0.6;
    //rotation and new-song stats need enough shows to mean anything
    private static final int MIN_SHOWS_FOR_PATTERNS = 5;
    //"new in the set" = first played within the most recent quarter of shows in the range
    private static final double RECENT_SHARE = 0.25;

    //date format used by setlist.fm for eventDate
    private static final DateTimeFormatter SETLISTFM_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    //Constructor injection of dependencies, Spring will automatically inject these dependendies when app starts
    public SetlistService(SetlistRepository setlistRepository, ArtistService artistService, SetlistFMFetchService fetcher) {
        this.setlistRepository = setlistRepository;
        this.artistService = artistService;
        this.setlistFetcher = fetcher;
    }
    //main entry method for controller
    //first step upon user search- method to retrieve setlists from db, if not there from setlistFM fetcher
    public List<Setlist> getArtistSetlists(String artist, int maxSetlists) {

        //turn artist name into Artist object thats db entity (keeping track of if we have all their setlists)
        Artist artistObj = artistService.findOrCreateArtist(artist);

        log.debug("fullyFetched for " + artistObj.getName() + ": " + artistObj.isFullyFetched());

        //query database to see if artist already there, sort by most recent
        List<Setlist> existingSetlists = getOrderedSetlistsfromDB(artistObj);

        //true if user searching for all setlists
        boolean fetchAll = maxSetlists == -1;

        //fetch from API if we have less data for artist than is requested
        if (shouldFetchFromAPI(existingSetlists, maxSetlists, artistObj)) {
            if (artistObj.isFullyFetched()) {
                //already fetched historical artist setlists so just check for new ones after last fetched date
                //(any range works here: once the full history is saved, only new concerts can be missing)
                log.info("Already fetched full history for " + artist + ". Checking for new concerts.");
                fetchRecentSetlists(artistObj);
            }
            else {
                //either not fully fetched, or maxSetlists is bigger than what we have- fetch from SEtlist fm.
                log.info("Fetching more data from Setlist.fm API for: " + artist);
                fetchFromSetlistFm(artistObj, maxSetlists);
            }

            //re-query db to get latest results
            //query by the artist entity, not the typed name, so different capitalization still finds their setlists
            existingSetlists = getOrderedSetlistsfromDB(artistObj);
        }
        //trim list if specific range requested (only do this if not -1 so not fetching aLL)
        if (!fetchAll && existingSetlists.size() > maxSetlists) {
            return existingSetlists.subList(0, maxSetlists);
        }
        //return db stats if it already satisfies request
        return existingSetlists;
    }
    //helper method to determine if we should fetch from the API
    private boolean shouldFetchFromAPI(List<Setlist> existingSetlists, int maxSetlists, Artist artist) {

        LocalDate today = LocalDate.now();

        //determine if we need more data according to maxSetlists (if user requested all setlists or more than we have in db)
        //fetch more if we have no setlists, if the user chose fetch all, or if we have less than maxSetlists in db
        if (existingSetlists.isEmpty()) {
            return true;
        }

        //if user wants all setlists and we havent finished full history, must fetch
        //if we have the full history, still check for new concerts once it's been a week since we last checked
        if (maxSetlists == -1) {
            return !artist.isFullyFetched() || isDueForRefresh(artist, today);
        }

        //if fully fetched previously
        if (artist.isFullyFetched()) {
            //still may need to fetch new concerts that have been uploaded since we last checked
            //db already has latest data we assume if we've checked in last week
            return isDueForRefresh(artist, today);
        }
        //for ranges like 20 or 100-> fetch only if  we dont have enough yet
        return existingSetlists.size() < maxSetlists;
    }

    //true if we haven't asked Setlist.fm for new concerts within REFRESH_THRESHOLD_DAYS
    //lastCheckedDate = when we last asked the API; lastFetchedDate = date of the newest concert we have
    private boolean isDueForRefresh(Artist artist, LocalDate today) {
        LocalDate lastChecked = artist.getLastCheckedDate();
        return lastChecked == null || lastChecked.isBefore(today.minusDays(REFRESH_THRESHOLD_DAYS));
    }

    //helper method for duplicate logic in getArtistSetlists
    private List<Setlist> getOrderedSetlistsfromDB(Artist artist) {
        return setlistRepository.findByArtistOrderByDateDesc(artist);
    }
    //method to fetch artist setlists from setlistfm
    //paginated to allow it to fetch 20 setlists fast, 100 normal or ALL setlists at a throttled rate
    //if maxSetlists = -1, fetch all pages, otherwise fetch up to maxSetlists value
    private void fetchFromSetlistFm(Artist artist, int maxSetlists) {

        //fetch all setlists if -1
        boolean fetchAll = maxSetlists == -1;

        boolean savedNewSetlists = false;

        String artistName = artist.getName();

        //keep track of artist's last fetched date
        LocalDate newestFetchedDate = artist.getLastFetchedDate();

        log.debug("ArtistName input: " + artistName);

        //resolve MBID or get from DB so we get exact results
        String mbid = artist.getMbid();
        if (mbid == null || mbid.isEmpty()) {
            //only resolve if dont already have it saved
            mbid = artistService.resolveMBIDfromArtistNameString(artistName);
            artist.setMbid(mbid);
            artistService.saveArtist(artist);
            log.debug("Resolved MBID: " + mbid);
        }

        if (mbid == null) {
            log.warn("Could not resolve MBID for artist: " + artistName);
            return;
        }

        //list to hold all the setlists as we paginate
        List<Setlist> allFetched = new ArrayList<>();
        int page = 1;
        //count streak of pages of full duplicates so we can stop repeating calls
        int duplicatePageStreak = 0;
        //need to break loop somehow if artist's setlists already in db
        //prevent issue of not retrieving all time stats for artist already searched for smaller range on- change max duplicates to 15 pages if fetching all time
        int maxDuplicatePages = fetchAll ? 15 : 4;
        //true once we've gone through setlist.fm's last page, meaning the artist's whole history is saved
        boolean reachedLastPage = false;

        //loop to retrieve setlists from a certain # of pages on setlistfm. loop stops once we reach maxSetlists and boolean stop = true
        while (true) {
            if (shouldExitEarly(fetchAll, maxSetlists, allFetched.size(), page)) {
                break;
            }

            log.debug("Calling Setlist.fm with MBID: " + mbid);

            //extract data from page
            SetlistResponseWrapper response = fetchPageOrNull(mbid, page);
            if (response == null) {
                break;
            }
            List<ApiSetlist> apiPageResults = response.getSetlist();
            if (isLastPage(response)) {
                reachedLastPage = true;
            }

            //look up which of this page's setlists we already have (2 queries per page instead of 1 per setlist)
            SavedSetlists saved = loadSavedSetlists(apiPageResults, artist);

            //check if page result is all duplicates
            if (isAllDuplicates(apiPageResults, saved)) {
                duplicatePageStreak++;
                if (duplicatePageStreak >= maxDuplicatePages) {
                    break;
                }
                page++;
                //skip mapping if page is all dupes
                continue;
            }

            //log setlist structure from page
            logSetlistStructure(apiPageResults);

            log.debug("Processing API page " + page + " with " + apiPageResults.size() + " setlists");

            //call helper method to extract new setlists from fetched page
            List<Setlist> newSetlists = processPage(apiPageResults, artist, saved);

            if (newSetlists.isEmpty()) {
                //if we saved nothing, continue to next page unless it's too many in a row
                duplicatePageStreak++;
                if (duplicatePageStreak >= maxDuplicatePages) {
                    //we've fetched all setlists for artist already
                    artist.setFullyFetched(true);
                    artistService.saveArtist(artist);
                    log.debug("Saved artist fullyFetched = " + artist.isFullyFetched());
                    log.info("Too many duplicate pages in a row — stopping fetch.");
                    break;
                } else {
                    //log the empty/duplicate streak
                    log.debug("No new setlists on this page, continuing to next page. Duplicate page streak: " + duplicatePageStreak);
                    page++;
                    continue;
                }
            }

            //reset duplicate streak if newSetlists not empty cuz we saved valid setlists
            duplicatePageStreak = 0;
            //persist setlists from current page to db
            setlistRepository.saveAll(newSetlists);
            //add new fetched setlists to full collection so we can track progress
            allFetched.addAll(newSetlists);

            savedNewSetlists = true;

            //update newest fetched date if necessary
            for (Setlist setlist : newSetlists) {
                if (setlist.getDate() != null && (newestFetchedDate == null || setlist.getDate().isAfter(newestFetchedDate))) {
                    newestFetchedDate = setlist.getDate();
                }
            }
            //move to next page if save succeeds
            page++;

            //check if we need to pause calls to avoid 429 errors
            if (gottaThrottle(page)) {
                throttleTime();
            }
        }

        //persist updated fetch date to DB
        if (newestFetchedDate != null && (
            artist.getLastFetchedDate() == null || newestFetchedDate.isAfter(artist.getLastFetchedDate()))) {
            artist.setLastFetchedDate(newestFetchedDate);
            artistService.saveArtist(artist);
            log.info("Updated artist lastFetchedDate to: " + newestFetchedDate);
        }

        //logging after loop to ensure returning correct data
        log.info("Done fetching. Total pages visited: " + (page - 1));
        log.info("total setlists fetched and saved: " + allFetched.size());

        if (fetchAll && savedNewSetlists) {
            artist.setFullyFetched(true);
            log.info("Marked artist as fully fetched after exhausting API pages.");
        } else if (reachedLastPage && !artist.isFullyFetched()) {
            //went through every page setlist.fm has, so the whole history is saved even if a range was requested
            //stops re-fetching every search for artists with fewer shows than the selected range
            artist.setFullyFetched(true);
            log.info("Reached setlist.fm's last page. Marked artist as fully fetched.");
        } else if (fetchAll) {
            log.debug("Fetch ended, but no new setlists were saved — not marking fullyFetched");
        }

        //record that we just checked setlist.fm so we don't check again until REFRESH_THRESHOLD_DAYS pass
        artist.setLastCheckedDate(LocalDate.now());
        artistService.saveArtist(artist);
    }

    //modified fetch to return setlists newer than our last one by this artist using last fetched date
    private void fetchRecentSetlists(Artist artist) {
        String mbid = artist.getMbid();

        //if no mbid, stop fetch
        if (mbid == null || mbid.isEmpty()) {
            log.warn("No MBID set for artist: " + artist.getName() + ". Cannot fetch recent setlists.");
            return;
        }

        int page = 1;
        boolean done = false;
        //newest concert we already had before this check; anything older is already saved
        LocalDate cutoffDate = artist.getLastFetchedDate();
        //track newest date found
        LocalDate newestFetchedDate = cutoffDate;

        while (!done) {
            SetlistResponseWrapper response = fetchPageOrNull(mbid, page);
            if (response == null) {
                break;
            }
            List<ApiSetlist> pageResults = response.getSetlist();
            SavedSetlists saved = loadSavedSetlists(pageResults, artist);
            //list to hold only new setlists fetched that aren't already in db
            List<Setlist> newSetlists = new ArrayList<>();

            for (ApiSetlist apiSetlist : pageResults) {
                try {
                    LocalDate date = LocalDate.parse(apiSetlist.getEventDate(), SETLISTFM_DATE);

                    //stop if already have setlists newer than this
                    //(sometimes setlistFM returns them out of order)
                    //same-day shows are let through to the duplicate check so a second show that day isn't missed
                    //compare to the date we started with, not the newest one found so far, or a new show
                    //earlier on this page would make us skip the new shows listed after it
                    if (cutoffDate != null && date.isBefore(cutoffDate)) {
                        done = true;
                        break;
                    }
                    if (isDuplicateSetlist(apiSetlist, saved)) {
                        continue;
                    }

                    Setlist entity = setlistFetcher.mapApiSetlistToEntity(apiSetlist, artist);
                    if (entity != null) {
                        newSetlists.add(entity);
                        if (newestFetchedDate == null || date.isAfter(newestFetchedDate)) {
                            newestFetchedDate = date;
                        }
                    }
                } catch (Exception e) {
                        log.warn("Failed to parse or map recent setlist: " + e.getMessage());
                    }
                }
                //save new recent setlists to DB
                if (!newSetlists.isEmpty()) {
                    setlistRepository.saveAll(newSetlists);
                }
                page++;

                if (gottaThrottle(page)) {
                    throttleTime();
                }
            }
            //save updated lastFetchedDate in artist entity
            if (newestFetchedDate != null && (artist.getLastFetchedDate() == null || newestFetchedDate.isAfter(artist.getLastFetchedDate()))) {
                artist.setLastFetchedDate(newestFetchedDate);
                log.info("Updated lastFetchedDate to: " + newestFetchedDate);
            }
            //record that we just checked setlist.fm for new concerts
            artist.setLastCheckedDate(LocalDate.now());
            artistService.saveArtist(artist);
        }

    //fetchfromsetlistfm helper methods to modularize code
    //helper method to streamline exit logic
    private boolean shouldExitEarly(boolean fetchAll, int maxSetlists, int fetchedSoFar, int page) {
        //return true if we need to exit fetch loop
        //stop immediately if already fetched enough to satisfy maxSetlists range
        if (!fetchAll && fetchedSoFar >= maxSetlists) {
            log.debug("Fetched enough setlists to satisfy maxSetlists = " + maxSetlists);
            return true;
        }
        //explicit page cap to prevent runaway loops on bad API data
        //we should already break after 4 duplicate pages but this is safeguard
        if (page > 50) {
            log.warn("Aborting fetch after 50 pages to prevent infinite loop.");
            return true;
        }
        return false;
    }
    //helper method to fetch one page from setlist.fm api
    //returns the full response (setlists plus paging info) or null if the page has no setlists
    private SetlistResponseWrapper fetchPageOrNull(String mbid, int page) {
        //logging to confirm pagination works
        log.debug("Fetching page " + page + " of setlists for MBID " + mbid);
        //send GET request to Setlist.fm API at https://api.setlist.fm/rest/1.0/search/setlists?artistName= "user artist"
        //call fetch service method to fetch one page from response and wrap in dto object
        SetlistResponseWrapper response = setlistFetcher.fetchSetlistPage(mbid, page, PAGE_SIZE);
        //return empty list if no data in response or if setlist array is null
        if (response == null || response.getSetlist() == null || response.getSetlist().isEmpty())  {
            log.debug("issue- not backend, API returned no setlist data for page " + page + " — possibly no concerts found.");
            return null;
        }
        log.debug("API returned setlists: " + response.getSetlist().size());
        return response;
    }

    //setlist.fm reports the total number of setlists and the page size, so we can tell when we're on the last page
    //returns false if the API didn't include paging info
    private boolean isLastPage(SetlistResponseWrapper response) {
        if (response.getTotal() <= 0 || response.getItemsPerPage() <= 0) {
            return false;
        }
        return (long) response.getPage() * response.getItemsPerPage() >= response.getTotal();
    }

    //helper method to check if all setlists in API response already exist in local DB
    private boolean isAllDuplicates(List<ApiSetlist> apiSetlists, SavedSetlists saved) {
        int duplicatesOnPageCtr = 0;

        //loop each API setlist and check if it already exists locally
        for (ApiSetlist apiSetlist : apiSetlists) {
            if (isDuplicateSetlist(apiSetlist, saved)) {
                duplicatesOnPageCtr++;
            }
        }
        //return true only if every setlist on page is duplicate
        return duplicatesOnPageCtr == apiSetlists.size();
    }

    //helper method to convert API setlists (from current page) into DB entities and skip dupes/invalid entries
    //stops early if maxSetlists reached
    private List<Setlist> processPage(List<ApiSetlist> apiSetlists, Artist artist, SavedSetlists saved) {
        List<Setlist> results = new ArrayList<>();

        //loop thru all setlists returned by API
        for (ApiSetlist apiSetlist : apiSetlists) {
            try {
                if (isDuplicateSetlist(apiSetlist, saved)) {
                    log.debug("Skipping duplicate setlist on " + apiSetlist.getEventDate());
                    continue;
                }
                //convert API DTO to db entity if setlist not saved already
                Setlist dbSetlist = setlistFetcher.mapApiSetlistToEntity(apiSetlist, artist);

                //skip if mapping returned null (meaning empty/invalid setlist)
                if (dbSetlist == null) {
                    continue;
                }
                //add to list of results
                results.add(dbSetlist);

            } catch (Exception e) {
                log.warn("Skipping unparseable or invalid setlist on date " + apiSetlist.getEventDate());
            }
        }
        return results;
    }

    //helper method to determine if we need to pause calls to avoid hitting API rate limit
    private boolean gottaThrottle(int currentPage) {
        return currentPage > 1;
    }
    //helper method to pause execution (sleep) to avoid rate limits (only should occur if fetching all time)
    private void throttleTime() {
        try {
            //logging to help understand when we are requesting too much for testing
            log.debug("Throttling to avoid 2/sec API rate limit.... sleepy time for 70 ms. ZZZZZZZZZZZ");
            //sleep 1.5 second every page
            Thread.sleep(70L);
        }
        catch (InterruptedException e) {
            //safely restore interrupt status
            Thread.currentThread().interrupt();
        }
    }

    //print raw json from API for debugging empty setlist issue
    //debug logger method to print each setlist's structure
    private void logSetlistStructure(List<ApiSetlist> apiSetlists) {
        //skip the loop entirely unless debug logging is on
        if (!log.isDebugEnabled()) {
            return;
        }
        for (ApiSetlist apiSetlist : apiSetlists) {
            log.debug("Setlist on " + apiSetlist.getEventDate());
            if (apiSetlist.getSets() == null || apiSetlist.getSets().getSet() == null) {
                log.debug(" → No sets for this concert.");
            } else {
                for (SetSection section : apiSetlist.getSets().getSet()) {
                    if (section.getSong() == null || section.getSong().isEmpty()) {
                        log.debug(" → Set section has no songs.");
                    } else {
                        log.debug(" → Songs: " + section.getSong().size());
                    }
                }
            }
        }
    }

    //method to calculate an artists top 5 encore songs (final song)
    public List<SongsRanked> getTopEncoreSongs(List<Setlist> setlists) {

        //map to track amount of times each encore song appears
        Map<String, Integer> encoreCounts = new HashMap<>();

        //iterate each setlist retrieved
        for (Setlist setlist : setlists) {
            //get list of songs in that setlist
            List<Song> songs = extractSongs(setlist);

            //skip empty setlists
            if (songs.isEmpty()) {
                continue;
            }
            //call helper method to find song at encore position
            Song encore = findEncore(songs);
            //count encore song if found
            if (encore != null) {
                String title = encore.getTitle();
                //dont add encore song if blank title
                if (title != null && !title.isBlank()) {
                    int count = encoreCounts.getOrDefault(title, 0);
                    encoreCounts.put(title, count + 1);
                }
                else {
                    log.debug("Encore had no valid title: " + encore);
                }
            }
            else {
                log.debug("No encore song found in setlist with " + songs.size() + " songs");
            }
        }
        //log full stats before sorting songs
        log.debug("Total unique encore songs counted: " + encoreCounts.size());

        //call helper method to get 5 most common encore songs sorted
        List<SongsRanked> topEncores = getTopRankedSongs(encoreCounts, 5, false);

        //log final output
        log.debug("Top 5 encore songs:");
        for (SongsRanked song : topEncores) {
            log.debug(" - " + song.getTitle() + " (played " + song.getCount() + " times)");
        }
        return topEncores;
    }
    //helper method for encores containing logic to find highest position song
    //find song with highest position in setlist (will be last song/encore)
    private Song findEncore(List<Song> songs) {
        //keep track of highest position
        Song encore = null;
        int maxPosition = -1;

        //loop through all songs to find one with highest position
        for (Song song : songs) {
            if (song.getPosition() > maxPosition) {
                encore = song;
                maxPosition = song.getPosition();
            }
        }
        return encore;
    }

    //method to calculate top openers- essentially inverse of getTopEncoreSongs
    public List<SongsRanked> getTopOpenerSongs(List<Setlist> setlists) {
        //map to hold song title -> opener count
        Map<String, Integer> openerCounts = new HashMap<>();

        //loop through each setlist
        for (Setlist setlist : setlists) {
            List<Song> songs = extractSongs(setlist);

            //skip empty setlists
            if (songs.isEmpty()) {
                continue;
            }

            //loop through all songs in the setlist
            for (Song song : songs) {
                //check if this is the first song
                if (song.getPosition() == 1) {
                    String title = song.getTitle();
                    //skip blank/null titles
                    if (title != null && !title.isBlank()) {
                        int count = openerCounts.getOrDefault(title, 0);
                        openerCounts.put(title, count + 1);
                    }
                    break; //stop after first song found
                }
            }
        }

        //return top 5 most common openers, sorted descending
        return getTopRankedSongs(openerCounts, 5, false);
    }

    //method to return 5 rarest songs(least played) for selected artist
    public List<SongsRanked> getRarestSongs(List<Setlist> setlists) {
        //map to count appearances of each song
        Map<String, Integer> songCounts = new HashMap<>();

        //loop thru each setlist of artist
        for (Setlist setlist : setlists) {
            //get list of songs from setlist
            List<Song> songs = extractSongs(setlist);

            //skip empty setlists
            if (songs.isEmpty()) {
                continue;
            }
            //loop thru each song to count its appearances
            //log to verify is invalid songs were issue stopping calcs from returning
            int skipped = 0;
            for (Song song : songs) {
                String title = song.getTitle();
                //bug fix- skip song if null or blank
                if (title == null || title.isBlank()) {
                    log.debug("Skipping song with invalid title in rarest calculation");
                    skipped++;
                    continue;
                }
                int count = songCounts.getOrDefault(title, 0);
                songCounts.put(title, count + 1);
            }
            if (skipped > 1) {
                log.debug("Skipped " + skipped + " songs due to blank or null titles");
            }
        }

        //log songCounts before we sort them to make sure counting function correct
        log.debug("Total unique songs counted: " + songCounts.size());

        //call helper method to sort descending (boolean false) and return top 5 rarest
        List<SongsRanked> rarest = getTopRankedSongs(songCounts, 5, true);

        //log final output
        log.debug("Top 5 rarest songs:");
        for (SongsRanked song : rarest) {
            log.debug(" → " + song.getTitle() + " (played " + song.getCount() + " times)");
        }
        return rarest;
    }

    //method to calculate most played songs from given range in descending order
    public List<SongsRanked> getMostPlayedSongs(List<Setlist> setlists) {

        //map to count song appearances
        Map<String, Integer> songCounts = new HashMap<>();

        //loop thru each setlist
        for (Setlist setlist : setlists) {
            //get songs from each setlist
            List<Song> songs = extractSongs(setlist);

            //skip empty setlists
            if (songs.isEmpty()) {
                continue;
            }

            int skipped = 0;
            //loop thru each song to count its appearances in the setlists
            for (Song song : songs) {
                String title = song.getTitle();

                //skip song if title is null or blank
                if (title == null || title.isBlank()) {
                    log.debug("Skipping song with invalid title in most played calculation");
                    skipped++;
                    continue;
                }
                int playCount = songCounts.getOrDefault(title, 0);
                songCounts.put(title, playCount + 1);
            }
            if (skipped > 1) {
                log.debug("Skipped " + skipped + " songs due to blank or null titles");
            }
        }
        //log songCounts before we sort them
        log.debug("Total unique songs counted: " + songCounts.size());

        //call helper method to sort songs in descending order to return most played
        List<SongsRanked> mostPlayed = getTopRankedSongs(songCounts, 5, false);

        //log final output
        log.debug("Top 5 most played songs:");
        for (SongsRanked song : mostPlayed) {
            log.debug(" → " + song.getTitle() + " (played " + song.getCount() + " times)");
        }
        return mostPlayed;
    }

    //method to calculate thr average # of songs per setlist in given range
    public double getAvgSetlistLength(List<Setlist> setlists) {

        //track total song count
        int totalSongs = 0;
        //track total setlists with valid songs (not empty)
        int setsWithSongs = 0;

        //loop thru each setlist
        for (Setlist setlist : setlists) {
            //get song list
            List<Song> songs = extractSongs(setlist);

            //skip empty or null song lists
            if (songs.isEmpty()) {
                continue;
            }
            //increment total songs by # in setlist
            totalSongs += songs.size();
            //increment valid setlist ctr
            setsWithSongs++;
        }
        //avoid dividing by 0
        if (setsWithSongs == 0) {
            return 0.0;
        }
        //calculate average and return as dbl
        double avgLength = (double) totalSongs / setsWithSongs;
        //log results to console
        log.debug("Average setlist length: " + avgLength);
        return avgLength;
    }

    //method to measure how similar the artist's shows are to each other
    public SetlistConsistency getSetlistConsistency(List<Setlist> setlists) {
        //distinct song titles for each show, oldest show first
        List<Set<String>> shows = getShowSongTitlesOldestFirst(setlists);
        if (shows.isEmpty()) {
            return null;
        }

        //count how many shows each song appeared in
        Map<String, Integer> showsPerSong = new HashMap<>();
        for (Set<String> show : shows) {
            for (String title : show) {
                showsPerSong.merge(title, 1, Integer::sum);
            }
        }

        //average share of each show's songs that were also played at the show before it
        Double repeatRate = null;
        if (shows.size() > 1) {
            double totalShare = 0;
            for (int i = 1; i < shows.size(); i++) {
                Set<String> current = shows.get(i);
                Set<String> previous = shows.get(i - 1);
                int repeated = 0;
                for (String title : current) {
                    if (previous.contains(title)) {
                        repeated++;
                    }
                }
                totalShare += (double) repeated / current.size();
            }
            repeatRate = 100.0 * totalShare / (shows.size() - 1);
        }

        //core songs appear at 90%+ of shows, one-offs at exactly one
        int coreThreshold = (int) Math.ceil(shows.size() * CORE_SONG_SHARE);
        int coreSongs = 0;
        int oneOffs = 0;
        for (int count : showsPerSong.values()) {
            if (count >= coreThreshold) {
                coreSongs++;
            }
            if (count == 1) {
                oneOffs++;
            }
        }
        return new SetlistConsistency(repeatRate, coreSongs, showsPerSong.size(), oneOffs);
    }

    //method to find the song-to-song transitions the artist plays most often (song A immediately followed by song B)
    public List<SongsRanked> getTopTransitions(List<Setlist> setlists) {
        Map<String, Integer> pairCounts = new HashMap<>();

        for (Setlist setlist : setlists) {
            //put songs in the order they were played
            List<Song> songs = extractSongs(setlist);
            songs.sort(Comparator.comparingInt(Song::getPosition));

            for (int i = 1; i < songs.size(); i++) {
                String from = songs.get(i - 1).getTitle();
                String to = songs.get(i).getTitle();
                //skip blank titles and a song following itself
                if (!isValidTitle(from) || !isValidTitle(to) || from.equals(to)) {
                    continue;
                }
                pairCounts.merge(from + " → " + to, 1, Integer::sum);
            }
        }
        //a pair heard only once isn't a pattern
        pairCounts.values().removeIf(count -> count < 2);

        return getTopRankedSongs(pairCounts, 5, false);
    }

    //method to find songs that came back after the longest absence (most shows missed in a row) within the range
    //count in each result = number of shows the song sat out before returning
    public List<SongsRanked> getBiggestBustouts(List<Setlist> setlists) {
        List<Set<String>> shows = getShowSongTitlesOldestFirst(setlists);

        //index of the last show each song was played at, and the longest gap seen for each song
        Map<String, Integer> lastShowIndex = new HashMap<>();
        Map<String, Integer> longestGap = new HashMap<>();

        for (int i = 0; i < shows.size(); i++) {
            for (String title : shows.get(i)) {
                Integer lastIndex = lastShowIndex.get(title);
                if (lastIndex != null) {
                    int showsMissed = i - lastIndex - 1;
                    if (showsMissed >= MIN_BUSTOUT_GAP) {
                        longestGap.merge(title, showsMissed, Math::max);
                    }
                }
                lastShowIndex.put(title, i);
            }
        }
        return getTopRankedSongs(longestGap, 5, false);
    }

    //method to find the longest and shortest show (by number of songs) in the range
    //ties go to the most recent show
    public ShowLengths getShowLengths(List<Setlist> setlists) {
        Setlist longest = null;
        Setlist shortest = null;
        int longestCount = 0;
        int shortestCount = 0;

        //setlists are newest first, so strict comparisons keep the most recent show on ties
        for (Setlist setlist : setlists) {
            int count = extractSongs(setlist).size();
            if (count == 0 || setlist.getDate() == null) {
                continue;
            }
            if (longest == null || count > longestCount) {
                longest = setlist;
                longestCount = count;
            }
            if (shortest == null || count < shortestCount) {
                shortest = setlist;
                shortestCount = count;
            }
        }
        if (longest == null) {
            return null;
        }
        return new ShowLengths(longestCount, longest.getDate().toString(), shortestCount, shortest.getDate().toString());
    }

    //method to measure how often the artist plays shows: date span and average days between shows
    public TouringPace getTouringPace(List<Setlist> setlists) {
        LocalDate first = null;
        LocalDate last = null;
        int shows = 0;
        for (Setlist setlist : setlists) {
            LocalDate date = setlist.getDate();
            if (date == null || extractSongs(setlist).isEmpty()) {
                continue;
            }
            shows++;
            if (first == null || date.isBefore(first)) {
                first = date;
            }
            if (last == null || date.isAfter(last)) {
                last = date;
            }
        }
        if (shows == 0) {
            return null;
        }
        Double daysBetweenShows = null;
        if (shows > 1) {
            daysBetweenShows = (double) ChronoUnit.DAYS.between(first, last) / (shows - 1);
        }
        return new TouringPace(shows, first.toString(), last.toString(), daysBetweenShows);
    }

    //method to find songs that rotate in and out of the set (played at 20-60% of shows)
    //count in each result = % of shows the song was played at
    public List<SongsRanked> getRotationSongs(List<Setlist> setlists) {
        List<Set<String>> shows = getShowSongTitlesOldestFirst(setlists);
        if (shows.size() < MIN_SHOWS_FOR_PATTERNS) {
            return Collections.emptyList();
        }
        Map<String, Integer> showsPerSong = new HashMap<>();
        for (Set<String> show : shows) {
            for (String title : show) {
                showsPerSong.merge(title, 1, Integer::sum);
            }
        }
        Map<String, Integer> rotationPercents = new HashMap<>();
        for (Map.Entry<String, Integer> entry : showsPerSong.entrySet()) {
            double share = (double) entry.getValue() / shows.size();
            if (share >= ROTATION_MIN_SHARE && share <= ROTATION_MAX_SHARE) {
                rotationPercents.put(entry.getKey(), (int) Math.round(share * 100));
            }
        }
        return getTopRankedSongs(rotationPercents, 5, false);
    }

    //method to find songs that entered the set recently: first played within the latest quarter of shows in the range
    //count in each result = number of shows the song has been played at since it first appeared
    public List<SongsRanked> getNewSongs(List<Setlist> setlists) {
        List<Set<String>> shows = getShowSongTitlesOldestFirst(setlists);
        if (shows.size() < MIN_SHOWS_FOR_PATTERNS) {
            return Collections.emptyList();
        }
        //index of the first show in the "recent" window
        int recentStart = shows.size() - (int) Math.ceil(shows.size() * RECENT_SHARE);

        Set<String> playedBefore = new HashSet<>();
        for (int i = 0; i < recentStart; i++) {
            playedBefore.addAll(shows.get(i));
        }
        Map<String, Integer> newSongPlays = new HashMap<>();
        for (int i = recentStart; i < shows.size(); i++) {
            for (String title : shows.get(i)) {
                if (!playedBefore.contains(title)) {
                    newSongPlays.merge(title, 1, Integer::sum);
                }
            }
        }
        return getTopRankedSongs(newSongPlays, 5, false);
    }

    //helper to get each show's distinct song titles, oldest show first
    private List<Set<String>> getShowSongTitlesOldestFirst(List<Setlist> setlists) {
        List<Set<String>> shows = new ArrayList<>();
        for (Setlist setlist : setlists) {
            Set<String> titles = new HashSet<>();
            for (Song song : extractSongs(setlist)) {
                if (isValidTitle(song.getTitle())) {
                    titles.add(song.getTitle());
                }
            }
            if (!titles.isEmpty()) {
                shows.add(titles);
            }
        }
        //setlists come newest first, so reverse to walk through shows in the order they happened
        Collections.reverse(shows);
        return shows;
    }

    //helper to skip songs with missing titles
    private boolean isValidTitle(String title) {
        return title != null && !title.isBlank();
    }

    //helper method to return top N ranked songs based on play counts
    //ascending = true, lowest values first, used with getRarestSongs
    //descending = false, highest values first, used with getTopEncoreSongs
    private List<SongsRanked> getTopRankedSongs(Map<String, Integer> map, int maxResults, boolean ascending) {
        //convert map entries to list for sorting
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());

        //replaced manual selection sort with built sort for performance
        entries.sort((e1, e2) -> ascending
                ? Integer.compare(e1.getValue(), e2.getValue())
                : Integer.compare(e2.getValue(), e1.getValue()));

        entries = entries.subList(0, Math.min(maxResults, entries.size()));

        //log sorting direction
        log.debug("Sorting top " + maxResults + " keys by " + (ascending ? "lowest" : "highest") + " values");

        //convert to ranked DTOs of songs instead of just titles so we can return stats
        List <SongsRanked> topSongs = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            //add 1 to i so first song is ranked 1 and not 0
            //create new object of DTO and add it to list
            topSongs.add(new SongsRanked(i + 1, entry.getKey(), entry.getValue()));
        }
        return topSongs;
    }

    //helper method to extract all songs from Setlist object
    //Goes into sets, set, song structure from Setlist.fm api and flattens it into a list
    private List<Song> extractSongs(Setlist setlist) {
        //initialize empty list to hold all songs
        List<Song> songs = new ArrayList<>();

        //check that sets obj exists and has list of set sections
        if (setlist.getSongs() != null) {
            //loop thru each set section
            songs.addAll(setlist.getSongs());
        }
        else {
            log.debug("Setlist on " + setlist.getDate() + " has null song list");
        }
        //return list of all songs for this setlist
        return songs;
    }

    //what's already saved for the setlists on one API page
    //setlistfmIds: setlist.fm ids we've stored; legacyDates: dates of this artist's setlists saved before ids were stored
    private record SavedSetlists(Set<String> setlistfmIds, Set<LocalDate> legacyDates) {}

    //two queries per page instead of one query per setlist
    private SavedSetlists loadSavedSetlists(List<ApiSetlist> apiSetlists, Artist artist) {
        List<String> ids = new ArrayList<>();
        for (ApiSetlist apiSetlist : apiSetlists) {
            if (apiSetlist.getId() != null) {
                ids.add(apiSetlist.getId());
            }
        }
        Set<String> savedIds = ids.isEmpty() ? Set.of() : setlistRepository.findExistingSetlistfmIds(ids);
        Set<LocalDate> legacyDates = setlistRepository.findDatesWithoutSetlistfmId(artist);
        return new SavedSetlists(savedIds, legacyDates);
    }

    //helper method to determine if given API setlist already in db
    private boolean isDuplicateSetlist(ApiSetlist apiSetlist, SavedSetlists saved) {
        //match on setlist.fm's own id so two shows on the same day are both kept
        if (apiSetlist.getId() != null && saved.setlistfmIds().contains(apiSetlist.getId())) {
            return true;
        }
        try {
            //setlists saved before ids were stored can only be matched by artist + date
            LocalDate date = LocalDate.parse(apiSetlist.getEventDate(), SETLISTFM_DATE);
            return saved.legacyDates().contains(date);
        } catch (Exception e) {
            log.warn("Date parse error for duplicate check: " + apiSetlist.getEventDate());
            // treat unparseable entries as duplicates to be safe
            return true;
        }
    }
}
