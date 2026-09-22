package com.BP.setlistaggregator.service;

import com.BP.setlistaggregator.model.Setlist;
import com.BP.setlistaggregator.model.Artist;

import com.BP.setlistaggregator.externalDTO.SetSection;
import com.BP.setlistaggregator.externalDTO.ApiSetlist;
import com.BP.setlistaggregator.externalDTO.SetlistResponseWrapper;


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
Stats are calculated separately in SetlistStatsService from the setlists this class returns
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

        //setlists cached by an older version of the app are missing details (tapes, covers, encores, places, tours)
        //clear them once so they're re-fetched with everything
        if (artistObj.hasOutdatedData()) {
            artistObj = clearOutdatedSetlists(artistObj);
        }

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
    //delete an artist's outdated cached setlists and reset their fetch tracking so the normal flow re-fetches them
    //returns the saved artist (the bulk delete clears Hibernate's cache, so we keep using the freshly saved copy)
    private Artist clearOutdatedSetlists(Artist artist) {
        int songsDeleted = setlistRepository.deleteSongsByArtist(artist);
        int setlistsDeleted = setlistRepository.deleteSetlistsByArtist(artist);
        log.info("Cleared " + setlistsDeleted + " outdated setlists (" + songsDeleted + " songs) for " + artist.getName() + " to re-fetch with new details");

        artist.setFullyFetched(false);
        artist.setLastFetchedDate(null);
        artist.setLastCheckedDate(null);
        artist.setDataVersion(Artist.CURRENT_DATA_VERSION);
        return artistService.saveArtist(artist);
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
