package com.BP.setlistaggregator.service;

import com.BP.setlistaggregator.externalDTO.*;
import com.BP.setlistaggregator.model.Artist;
import com.BP.setlistaggregator.model.Setlist;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
//increase max buffer size
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import java.time.Duration;


//service class that handles interaction with the Setlist.fm API
@Service
public class SetlistFMFetchService {
    //talks to Setlist.FM's REST API
    private final WebClient webClient;

    public SetlistFMFetchService(WebClient.Builder builder) {
        //Allows us to build an HTTP client with a base URL of Setlist.FM's API
        //increased buffer prevents spring crashing when API sends too much data on one page
        this.webClient = builder
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .responseTimeout(Duration.ofSeconds(20))))
                .codecs(configurer -> configurer.defaultCodecs()
                        //10mb buffer
                        .maxInMemorySize(10 * 1024 * 1024))
                .baseUrl("https://api.setlist.fm/rest/1.0")
                .build();
    }
    //NEW- fetch by mbid instead of string to ensure accuracy- test edge cases further
    //helper method to send GET request to Setlist.fm API for a specific artist page
    public SetlistResponseWrapper fetchSetlistPage(String mbid, int page, int size) {
        long start = System.currentTimeMillis();
        try {
            //send request to Setlist.fm API for one page of setlists
            SetlistResponseWrapper response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/artist/" + mbid + "/setlists")
                            .queryParam("artistMBID", mbid)
                            .queryParam("p", page)
                            .queryParam("size", size)
                            .build())
                    //tell API to send JSON instead of XML default
                    .header("Accept", "application/json")
                    //API key defined in system environment variables
                    .header("x-api-key", System.getenv("SFM_API_KEY"))
                    .retrieve()
                    //Map outer API wrapper to its Java class
                    //This tells Spring to turn the JSON response into DTOs
                    //JSON is deserialized in SetlistResponseWrapper into list of ApiSetlist objects
                    .bodyToMono(SetlistResponseWrapper.class)
                    //block request until full data received
                    .block();

            System.out.println("⏱ Page " + page + " fetched in " + (System.currentTimeMillis() - start) + " ms");

            return response;
        }
        catch (WebClientResponseException.NotFound e) {
            //404 error — reached past last page
            System.out.println("No setlists found for artist MBID " + mbid);
            return null;
        } catch (WebClientResponseException.TooManyRequests e) {
            //429 rate limit hit- try sleeping, retry once
            System.out.println("Rate limit hit! Backing off 30 sec!!!!!");
            try {
                //sleep 30 sec
                Thread.sleep(30_000);
                //retry once after sleep
                return fetchSetlistPage(mbid, page, size);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            catch (Exception retryException) {
                //even the retry failed- log and return null instead of breaking
                System.out.println("Retry failed after rate limit: " + retryException.getMessage());
            }
            return null;
        }
        catch (Exception e) {
            System.out.println("Error fetching setlists by MBID: " + e.getMessage());
            return null;
        }
    }

    //Transforms the API data/DTOs into database entities
    //method to convert API setlist from Setlist.fm into local Setlist entity saveable to local DB
    public Setlist mapApiSetlistToEntity(ApiSetlist apiSetlist, Artist artist) {
        //create new setlist entity
        Setlist setlist = new Setlist();

        //set foreign key reference to artist
        setlist.setArtist(artist);

        //parse event date string from API to localdate
        LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(apiSetlist.getEventDate(), DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } catch (Exception e) {
            System.out.println("Date parsing failed for: " + apiSetlist.getEventDate());
            //fallback or skip
            parsedDate = LocalDate.now();
        }
        setlist.setDate(parsedDate);

        //implement later
        setlist.setVenue("Unknown Venue");
        setlist.setCity("Unknown City");
        setlist.setCountry("Unknown Country");

        //global position counter across all songs in all sections
        int positionCtr = 1;

        //create empty list to hold songs (explicitly of internal song model because of 2 Song classes)
        List<com.BP.setlistaggregator.model.Song> dbSongs = new ArrayList<>();

        //look thru each section of API setlist once
        if (apiSetlist.getSets() != null && apiSetlist.getSets().getSet() != null) {
            for (SetSection section : apiSetlist.getSets().getSet()) {
                if (section.getSong() != null) {
                    //convert each API song to DB song object and add it to list
                    //fix for top encore songs- loop over all sections instead of just 1
                    for (com.BP.setlistaggregator.externalDTO.Song apiSong : section.getSong()) {
                        //create new Song entity
                        com.BP.setlistaggregator.model.Song dbSong = new com.BP.setlistaggregator.model.Song();
                        dbSong.setTitle(apiSong.getName());
                        //link song to this setlist
                        dbSong.setSetlist(setlist);

                        //set song position!!!
                        dbSong.setPosition(positionCtr);

                        dbSongs.add(dbSong);

                        //option to verify positions
                        //System.out.println("Saved song '" + apiSong.getName() + "' at position " + positionCtr);


                        //debug log to confirm songs are being parsed and position being assigned correctly
                        //System.out.println("Mapped song: " + apiSong.getName() + " at position " + positionCtr);
                        positionCtr++;
                    }
                }
            }
        }

        //new method to check if setlist is empty and avoid saving it to db
        if (dbSongs.isEmpty()) {
            System.out.println("Skipping empty setlist for artist " + artist.getName() + " on " + apiSetlist.getEventDate());
            return null;
        }
        //add all songs to the setlist
        setlist.setSongs(dbSongs);
        //log to confirm what data is being saved
        System.out.println("Mapped " + dbSongs.size() + " songs for setlist on " + apiSetlist.getEventDate());

        return setlist;
    }

    //future: rate limit tracker,pagination helpers, etc
}
