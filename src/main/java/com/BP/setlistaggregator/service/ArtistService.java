package com.BP.setlistaggregator.service;

import com.BP.setlistaggregator.repositories.ArtistRepository;
import com.BP.setlistaggregator.model.Artist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;


//service class solely for handling artist objects
@Service
public class ArtistService {
    private static final Logger log = LoggerFactory.getLogger(ArtistService.class);

    private final ArtistRepository artistRepository;

    public ArtistService(ArtistRepository artistRepository) {
        this.artistRepository = artistRepository;
    }

    //helper method used in fetchFromSetlistFM method to retrieve existing artist or create new one if not found
    public Artist findOrCreateArtist(String artistName) {

        log.debug("Looking up or creating artist with name: " + artistName);

        //try to find in db
        Optional<Artist> optionalArtist = artistRepository.findByNameIgnoreCase(artistName);

        //return existing artist if found
        if (optionalArtist.isPresent()) {
            Artist existingArtist = optionalArtist.get();
            //debug refetching issue
            log.debug("Found existing artist: " + existingArtist.getName() + " | fullyFetched: " + existingArtist.isFullyFetched() + " | lastFetchedDate: " + existingArtist.getLastFetchedDate());
            return existingArtist;
        }

        //create new artist if not found in db
        Artist newArtist = new Artist();
        newArtist.setName(artistName);
        //log creation
        log.info("Created new artist entry for: " + artistName);
        return artistRepository.save(newArtist);
    }

    //helper method to search MusicBrainz for artist MBID using the String we have
    public String resolveMBIDfromArtistNameString(String artistName) {
        try {
            log.debug("Resolving MBID for artist: " + artistName);

            //url encode the artist name and form MusicBrainz search query
            String searchURL = "https://musicbrainz.org/ws/2/artist?query=artist:" +
                    URLEncoder.encode(artistName, StandardCharsets.UTF_8) +
                    "&fmt=json";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchURL))
                    .header("User-Agent", "SetlistAggregator/1.0 (bradenprestholdt@gmail.com)")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            //parse JSON response to get top result's MBID
            JSONObject json = new JSONObject(response.body());
            JSONArray artists = json.getJSONArray("artists");

            if (!artists.isEmpty()) {
                JSONObject firstArtist = artists.getJSONObject(0);

                //get the top artist's MBID to use in Setlist fm query
                return firstArtist.getString("id");
            } else {
                log.debug("No results found in MBID for: " + artistName);
                return null;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch MBID from MusicBrainz " + e.getMessage());
            return null;
        }
    }

    // new method to persist artist updates like setting fullyFetched=true
    public Artist saveArtist(Artist artist) {

        return artistRepository.save(artist);
    }


    //implement in future: get artist stats, artist suggestions, etc.
}
