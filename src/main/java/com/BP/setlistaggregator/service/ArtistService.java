package com.BP.setlistaggregator.service;

import com.BP.setlistaggregator.repositories.ArtistRepository;
import com.BP.setlistaggregator.model.Artist;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.List;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
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
    private final ArtistRepository artistRepository;

    public ArtistService(ArtistRepository artistRepository) {
        this.artistRepository = artistRepository;
    }

    //helper method used in fetchFromSetlistFM method to retrieve existing artist or create new one if not found
    public Artist findOrCreateArtist(String artistName) {

        System.out.println("Looking up or creating artist with name: " + artistName);

        //try to find in db
        Artist artist = artistRepository.findByNameIgnoreCase(artistName).orElse(null);

        //if not found, create and save a new artist
        if (artist == null) {
            artist = new Artist();
            artist.setName(artistName);
            artist = artistRepository.save(artist);

            //log creation
            System.out.println("Created new artist entry for: " + artistName);
        }

        //return the artist (new or existing)
        return artist;
    }

    public Optional<Artist> findByName(String name) {

        return artistRepository.findByNameIgnoreCase(name);

    }
    //helper method to search MusicBrainz for artist MBID using the String we have
    public String resolveMBIDfromArtistNameString(String artistName) {
        try {
            System.out.println("Resolving MBID for artist: " + artistName);

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
                System.out.println("No results found in MBID for: " + artistName);
                return null;
            }
        }
                    catch (Exception e) {
                        System.err.println ("Failed to fetch MBID from MusicBrainz " + e.getMessage());
                        return null;
            }
        }

    //helper method to look up artist using MusicBrainz ID (mbid)
    public Optional<Artist> findByMbid(String mbid) {
        return artistRepository.findByMbid(mbid);
    }
    //method to get all artists from db
    public List<Artist> getAllArtists() {
        //delegate to repository to fetch all rows from artist table
        return artistRepository.findAll();
    }
    // new method to persist artist updates like setting fullyFetched=true
    public Artist saveArtist(Artist artist) {

        return artistRepository.save(artist);
    }


    //implement in future: get artist stats, artist suggestions, etc.
}
