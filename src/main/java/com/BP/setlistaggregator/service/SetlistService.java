package com.BP.setlistaggregator.service;

import com.BP.setlistaggregator.model.Setlist;
import com.BP.setlistaggregator.model.Artist;
//only importing model class, must fully qualify dto Song when used
import com.BP.setlistaggregator.model.Song;

import com.BP.setlistaggregator.externalDTO.SetSection;
import com.BP.setlistaggregator.externalDTO.ApiSetlist;
import com.BP.setlistaggregator.externalDTO.SetlistResponseWrapper;
import com.BP.setlistaggregator.externalDTO.Sets;
import com.BP.setlistaggregator.internalDTO.SongsRanked;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.BP.setlistaggregator.repositories.SetlistRepository;
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
    //declare tools needed for Service class to do its job
    //needed to save and load setlists from database (CRUD)
    private final SetlistRepository setlistRepository;
    //needed to handle artist operations
    private final ArtistService artistService;
    //needed to handle interactions with Setlist.fm API
    private final SetlistFMFetchService setlistFetcher;

    //max allowed size (in setlists per page) per setlist.fm API- should reduce time to fetch all by at least 50% along with total API calls compared to default 20
    private static final int PAGE_SIZE = 50;

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

        //query database to see if artist already there, sort by most recent
        List<Setlist> existingSetlists = getOrderedSetlistsfromDB(artistObj.getName());

        //true if user searching for all setlists
        boolean fetchAll = maxSetlists == -1;

        //fetch from API if we have less data for artist than is requested
        if (shouldFetchFromAPI(existingSetlists, maxSetlists, artistObj)) {
            //log that we're fetching more data from the API for an artist that already existed in db
            System.out.println("Fetching more data from Setlist.fm API for: " + artist);
            fetchFromSetlistFm(artistObj.getName(), maxSetlists);

            //re-query db to get latest results
            existingSetlists = getOrderedSetlistsfromDB(artist);
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
        //determine if we need more data according to maxSetlists (if user requested all setlists or more than we have in db)
        //fetch more if we have no setlists, if the user chose fetch all, or if we have less than maxSetlists in db
        if (existingSetlists.isEmpty()) {
            return true;
        }
        // fetch only if we haven’t fetched all yet
        if (maxSetlists == -1) {
            return !artist.isFullyFetched();
        }
        return existingSetlists.size() < maxSetlists;
    }
    //helper method for duplicate logic in getArtistSetlists
    private List<Setlist> getOrderedSetlistsfromDB(String artist) {
        return setlistRepository.findByArtistNameOrderByDateDesc(artist);
    }
    //method to fetch artist setlists from setlistfm
    //paginated to allow it to fetch 20 setlists fast, 100 normal or ALL setlists at a throttled rate
    //if maxSetlists = -1, fetch all pages, otherwise fetch up to maxSetlists value
    private List<Setlist> fetchFromSetlistFm(String artistName, int maxSetlists) {

        //fetch all setlists if -1
        boolean fetchAll = maxSetlists == -1;


        System.out.println("Calling findOrCreateArtist with input: " + artistName);

        //check if artist exists in local DB already using helper method
        Artist artist = artistService.findOrCreateArtist(artistName);
        System.out.println("ArtistName input: " + artistName);

        //resolve MBID from artist so we get exact results
        String mbid = artistService.resolveMBIDfromArtistNameString(artistName);
        System.out.println("Resolved MBID: " + mbid);

        if (mbid == null) {
            System.out.println("Could not resolve MBID for artist: " + artistName);
            return Collections.emptyList();
        }

        //list to hold all the setlists as we paginate
        List<Setlist> allFetched = new ArrayList<>();
        int page = 1;
        //count streak of pages of full duplicates so we can stop repeating calls
        int duplicatePageStreak = 0;
        //need to break loop somehow if artist's setlists already in db
        //prevent issue of not retrieving all time stats for artist already searched for smaller range on- change max duplicates to 15 pages if fetching all time
        int maxDuplicatePages = fetchAll ? 15 : 4;

        //loop to retrieve setlists from a certain # of pages on setlistfm. loop stops once we reach maxSetlists and boolean stop = true
        while (true) {
            if (shouldExitEarly(fetchAll, maxSetlists, allFetched.size(), page, duplicatePageStreak)) {
                break;
            }

            System.out.println("Calling Setlist.fm with MBID: " + mbid);

            //extract data from page
            List<ApiSetlist> apiPageResults = fetchPageOrNull(mbid, page);
            if (apiPageResults == null) {
                break;
            }

            //check if page result is all duplicates
            if (isAllDuplicates(apiPageResults, artist)) {
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

            //call helper method to extract new setlists from fetched page
            List<Setlist> newSetlists = processPage(apiPageResults, artist, maxSetlists, fetchAll, allFetched.size());

            if (newSetlists.isEmpty()) {
                //if we saved nothing, continue to next page unless it's too many in a row
                duplicatePageStreak++;
                if (duplicatePageStreak >= maxDuplicatePages) {
                    //we've fetched all setlists for artist already
                    artist.setFullyFetched(true);
                    artistService.saveArtist(artist);
                    System.out.println("Too many duplicate pages in a row — stopping fetch.");
                    break;
                } else {
                    //log the empty/duplicate streak
                    System.out.println("No new setlists on this page, continuing to next page. Duplicate page streak: " + duplicatePageStreak);
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
            //move to next page if save succeeds
            page++;

            //check if we need to pause calls to avoid 429 errors
            if (gottaThrottle(fetchAll, maxSetlists, page)) {
                throttleTime();
            }
        }

        //logging after loop to ensure returning correct data
        System.out.println("Done fetching. Total pages visited: " + (page - 1));
        System.out.println("total setlists fetched and saved: " + allFetched.size());

        //trim size in case request less than requested range
        //return saved DB setlists
        return limitResults(allFetched, fetchAll, maxSetlists);
    }

        //fetchfromsetlistfm helper methods to modularize code
    //helper method to streamline exit logic
    private boolean shouldExitEarly(boolean fetchAll, int maxSetlists, int fetchedSoFar, int page, int dupStreak) {
        //return true if we need to exit fetch loop
        //stop immediately if already fetched enough to satisfy maxSetlists range
        if (!fetchAll && fetchedSoFar >= maxSetlists) {
            System.out.println("Fetched enough setlists to satisfy maxSetlists = " + maxSetlists);
            return true;
        }
        //explicit page cap to prevent runaway loops on bad API data
        //we should already break after 4 duplicate pages but this is safeguard
        if (page > 50) {
            System.out.println("Aborting fetch after 50 pages to prevent infinite loop.");
            return true;
        }
        return false;
    }
    //helper method to fetch one page from setlist.fm api
    private List<ApiSetlist> fetchPageOrNull(String mbid, int page) {
        //logging to confirm pagination works
        System.out.println("Fetching page " + page + " of setlists for MBID " + mbid);
        //send GET request to Setlist.fm API at https://api.setlist.fm/rest/1.0/search/setlists?artistName= "user artist"
        //call fetch service method to fetch one page from response and wrap in dto object
        SetlistResponseWrapper response = setlistFetcher.fetchSetlistPage(mbid, page, PAGE_SIZE);
        //return empty list if no data in response or if setlist array is null
        if (response == null || response.getSetlist() == null || response.getSetlist().isEmpty())  {
            System.out.println("issue- not backend, API returned no setlist data for page " + page + " — possibly no concerts found.");
            return null;
        }
        System.out.println("API returned setlists: " + response.getSetlist().size());
        return response.getSetlist();
    }

    //helper method to check if all setlists in API response already exist in local DB
    private boolean isAllDuplicates(List<ApiSetlist> apiSetlists, Artist artist) {
        int duplicatesOnPageCtr = 0;

        //loop each API setlist and check if it already exists locally
        for (ApiSetlist apiSetlist : apiSetlists) {
            if (isDuplicateSetlist(apiSetlist, artist)) {
                duplicatesOnPageCtr++;
            }
        }
            //return true only if every setlist on page is duplicate
            return duplicatesOnPageCtr == apiSetlists.size();
    }

    //helper method to convert API setlists (from current page) into DB entities and skip dupes/invalid entries
    //stops early if maxSetlists reached
    private List<Setlist> processPage(List<ApiSetlist> apiSetlists, Artist artist, int maxSetlists, boolean fetchAll, int alreadyFetchedCtr) {
        List<Setlist> results = new ArrayList<>();

        //loop thru all setlists returned by API
        for (ApiSetlist apiSetlist : apiSetlists) {
            try {
                if (isDuplicateSetlist(apiSetlist, artist)) {
                    System.out.println("Skipping duplicate setlist on " + apiSetlist.getEventDate());
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

                //if we hit desired count of setlists from search, stop loop early
                if (!fetchAll && (alreadyFetchedCtr + results.size() >= maxSetlists)) {
                    break;
                }
            } catch (Exception e) {
                System.out.println("Skipping unparseable or invalid setlist on date " + apiSetlist.getEventDate());
            }
        }
        return results;
    }

    //helper method to determine if we need to pause calls to avoid hitting API rate limit
    private boolean gottaThrottle (boolean fetchAll, int maxSetlists, int currentPage) {
        //want to throttle only when fetching a lot of data (fetchAll option or maxSetlists > 100
        //boolean isLargeFetch = fetchAll || maxSetlists > 100;
        //always throttle every 2 pages after page 1 in heavy reqs
       // return isLargeFetch && currentPage > 1 && currentPage % 2 == 0;
        return currentPage > 1;
    }
    //helper method to pause execution (sleep) to avoid rate limits (only should occur if fetching all time)
    private void throttleTime() {
        try {
            //logging to help understand when we are requesting too much for testing
            System.out.println("Throttling to avoid 2/sec API rate limit.... sleepy time for 1.5 seconds. ZZZZZZZZZZZ");
            //sleep 1.5 second every page
            Thread.sleep(1500);
        }
        catch (InterruptedException e) {
            //safely restore interrupt status
            Thread.currentThread().interrupt();
        }
    }
    //helper to trim results
    private List<Setlist> limitResults(List<Setlist> allSetlists, boolean fetchAll, int maxSetlists) {
        if (!fetchAll && allSetlists.size() > maxSetlists) {
            return allSetlists.subList(0, maxSetlists);
        }
        return allSetlists;
    }

    //print raw json from API for debugging empty setlist issue
    //debug logger method to print each setlist's structure
    private void logSetlistStructure(List<ApiSetlist> apiSetlists) {
        for (ApiSetlist apiSetlist : apiSetlists) {
            System.out.println("Setlist on " + apiSetlist.getEventDate());
            if (apiSetlist.getSets() == null || apiSetlist.getSets().getSet() == null) {
                System.out.println(" → No sets for this concert.");
            } else {
                for (SetSection section : apiSetlist.getSets().getSet()) {
                    if (section.getSong() == null || section.getSong().isEmpty()) {
                        System.out.println(" → Set section has no songs.");
                    } else {
                        System.out.println(" → Songs: " + section.getSong().size());
                    }
                }
            }
        }
    }

    //method to calculate an artists top 5 encore songs (final song)
    public List<SongsRanked> getTopEncoreSongs(String artist, int maxSetlists) {

        //look up the setlists of user entered artist by calling method (from DB or API)
        List<Setlist> setlists = getArtistSetlists(artist, maxSetlists);

        //map to track amount of times each encore song appears
        Map<String, Integer> encoreCounts = new HashMap<>();

        //iterate each setlist retrieved
        for (Setlist setlist : setlists) {
            //get list of songs in that setlist
            List<Song> songs = extractSongs(setlist);

            //skip empty setlists
            if (songs == null || songs.isEmpty()) {
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
                    //logging to verify mapping works and songs are saved
                    //if doesn't print, extractSongs call is pulling from setlists with empty song lists- meaning mapping doesn't work or songs not being sdaved
                   // System.out.println("Encore song found, name:  " + title + " at position " + encore.getPosition());
                }
                else {
                    System.out.println("Encore had no valid title: " + encore);
                }
            }
            else {
                System.out.println("No encore song found in setlist with " + songs.size() + " songs");
            }
        }
        //log full stats before sorting songs
        System.out.println("Total unique encore songs counted: " + encoreCounts.size());

        //call helper method to get 5 most common encore songs sorted
        List<SongsRanked> topEncores = getTopRankedSongs(encoreCounts, 5, false);

        //log final output
        System.out.println("Top 5 encore songs:");
        for (SongsRanked song : topEncores) {
            System.out.println(" - " + song.getTitle() + " (played " + song.getCount() + " times)");
        }
        //log type and null check
        System.out.println("EncoreSongs output: " + (topEncores != null ? topEncores.size() : "null"));
        System.out.println("EncoreSongs class: " + (topEncores != null ? topEncores.getClass().getName() : "null"));

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
    public List<SongsRanked> getTopOpenerSongs(String artist, int maxSetlists) {
        //retrieve setlists from DB or API
        List<Setlist> setlists = getArtistSetlists(artist, maxSetlists);

        //map to hold song title -> opener count
        Map<String, Integer> openerCounts = new HashMap<>();

        //loop through each setlist
        for (Setlist setlist : setlists) {
            List<Song> songs = extractSongs(setlist);

            //skip empty setlists
            if (songs == null || songs.isEmpty()) {
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
    public List<SongsRanked> getRarestSongs(String artist, int maxSetlists) {
        //retrieve setlists for artist
        List<Setlist> setlists = getArtistSetlists(artist, maxSetlists);

        //map to count appearances of each song
        Map<String, Integer> songCounts = new HashMap<>();

        //loop thru each setlist of artist
        for (Setlist setlist : setlists) {
            //get list of songs from setlist
            List<Song> songs = extractSongs(setlist);

            //skip empty setlists
            if (songs == null || songs.isEmpty()) {
                continue;
            }
            //loop thru each song to count its appearances
            //log to verify is invalid songs were issue stopping calcs from returning
            int skipped = 0;
            for (Song song : songs) {
                String title = song.getTitle();
                //bug fix- skip song if null or blank
                if (title == null || title.isBlank()) {
                    System.out.println("Skipping song with invalid title in rarest calculation");
                    skipped++;
                    continue;
                }
                int count = songCounts.getOrDefault(title, 0);
                songCounts.put(title, count + 1);

                //log to confirm song is counted
                //System.out.println("Counting the song: " + title + ",  total so far is: " + (count + 1));

            }
            System.out.println("Skipped " + skipped + " songs due to blank or null titles");
        }

        //log songCounts before we sort them to make sure counting function correct
        System.out.println("Total unique songs counted: " + songCounts.size());

        //call helper method to sort descending (boolean false) and return top 5 rarest
        List<SongsRanked> rarest = getTopRankedSongs(songCounts, 5, true);

        //log final output
        System.out.println("Top 5 rarest songs:");
        for (SongsRanked song : rarest) {
            System.out.println(" → " + song.getTitle() + " (played " + song.getCount() + " times)");
        }
        return rarest;
    }

    //method to calculate thr average # of songs per setlist in given range
    public double getAvgSetlistLength (String artist, int maxSetlists) {

        //get all setlists from db or fetcher
        List<Setlist> setlists = getArtistSetlists(artist, maxSetlists);

        //track total song count
        int totalSongs = 0;
        //track total setlists with valid songs (not empty)
        int setsWithSongs = 0;

        //loop thru each setlist
        for (Setlist setlist : setlists) {
            //get song list
            List<Song> songs = extractSongs(setlist);

            //skip empty or null song lists
            if (songs == null || songs.isEmpty()) {
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
        //log results to console
        System.out.println("Average setlist length for " + artist + ": " + ((double) totalSongs / setsWithSongs));
        //calculate average and return as dbl
        return (double) totalSongs / setsWithSongs;

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
        System.out.println("Sorting top " + maxResults + " keys by " + (ascending ? "lowest" : "highest") + " values");

        //convert to ranked DTOs of songs instead of just titles so we can return stats
        List <SongsRanked> topSongs = new ArrayList<>();

        for (int i = 0; i < entries.size() && i < maxResults; i++) {
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

            //log amount of songs extracted
            System.out.println("Extracted " + songs.size() + " songs from setlist on " + setlist.getDate());
        }
        else {
            System.out.println("Setlist on " + setlist.getDate() + " has null song list");
        }
        //return list of all songs for this setlist
        return songs;
    }

    //helper method to determine if given API setlist already in db
    private boolean isDuplicateSetlist(ApiSetlist apiSetlist, Artist artist) {
        try {
            LocalDate date = LocalDate.parse(apiSetlist.getEventDate(), DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            return setlistRepository.existsByArtistAndDate(artist, date);
        } catch (Exception e) {
            System.out.println("Date parse error for duplicate check: " + apiSetlist.getEventDate());
            // treat unparseable entries as duplicates to be safe
            return true;
                }
         }
    }








