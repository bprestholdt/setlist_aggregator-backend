package com.BP.setlistaggregator.controllers;

import com.BP.setlistaggregator.model.Artist;
import com.BP.setlistaggregator.service.ArtistService;
import com.BP.setlistaggregator.repositories.ArtistRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.HashMap;
/*controllers act as liasion between front and back ends by:
Accepting HTTP requests from front end (or postman)
Triggering service layer methods like SetlistService
Returning responses back to the front end in JSON form
 */
//ArtistController manages artists in the database
//No ArtistService yet because simple logic for now allows us to just talk to repository directly
@RestController
//base url path for artist-related endpoints
@RequestMapping("/api/artists")
public class ArtistController {

    //dependency injection of ArtistService
    //changed from repository so we don't talk directly to respository
    private final ArtistService artistService;

    //constructor injection of repository automatically by Spring
    public ArtistController(ArtistService artistService) {

        this.artistService = artistService;

    }

    //GET request in order to fetch all artists in the database
    @GetMapping
    public List<Artist> getAllArtists() {
        //call repo to retuen all artists
        return artistService.getAllArtists();
    }

    //POST request to save new artist to our database
    //accepts Artist object as JSON in request body then saves it to db
    @PostMapping
    public Artist addArtist(@RequestBody Artist artist) {
        //save received artist object into our database using service method
        return artistService.findOrCreateArtist(artist.getName());
    }

    //GET endpoint for single artist queries
    @GetMapping("/{name}")
    public Artist getArtistByName(@PathVariable String name) {

        return artistService.findByName(name).orElse(null);
    }

    //get by MBID (MusicBrainz ID) to implement in future to resolve artist ambiguities (i.e. names like Future)
    //Maps to GET /api/artists/mbid/MBIDhere
    @GetMapping("/mbid/{mbid}")
    public Artist getArtistByMbid(@PathVariable String mbid) {
        //use service layer to look up artist by mbid
        return artistService.findByMbid(mbid).orElse(null);
    }

    //resolve a user inputted artist name String to their official MBID using service method
    //ex: GET /api/artists/resolve?name=radiohead
    @GetMapping("/resolve")
    public HashMap<String, String> resolveArtistNametoMBID(@RequestParam String artistName) {
        HashMap<String, String> response = new HashMap<>();

        //call musicBrainz query helper from ArtistService
        String mbid = artistService.resolveMBIDfromArtistNameString(artistName);

        if (mbid == null) {
            response.put("error", "no MBID found for artist: " + artistName);
            return response;
        }
        //save or retrieve artist from DB
        Artist artist = artistService.findOrCreateArtist(artistName);
        artist.setMbid(mbid);
        artistService.saveArtist(artist);

        //send back clean name + mbid for use by frontend
        response.put("mbid", mbid);
        response.put("name", artist.getName());

        return response;
    }


}