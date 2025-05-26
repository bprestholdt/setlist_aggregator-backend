package com.BP.setlistaggregator.controllers;

import com.BP.setlistaggregator.model.*;
import com.BP.setlistaggregator.repositories.SetlistRepository;
import com.BP.setlistaggregator.service.SetlistService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import com.BP.setlistaggregator.internalDTO.SongsRanked;

//class to handle requests involving setlist data, stats
//allow React frontend on port 3000 to make requests to Spring Boot backend on 8080
@CrossOrigin(origins = "https://setlist-aggregator-frontend.vercel.app")
//annotation to tell Spring this is a REST API controller that returns JSON
@RestController
//base URL for endpoints in class
@RequestMapping("/api/setlists")
public class SetlistController {
    private final SetlistService setlistService;

    //constructor injection of setlist service as dependency
    //Spring automatically passes instance of SetlistService when controller is created
    public SetlistController(SetlistService setlistService) {

        this.setlistService = setlistService;
    }

    //GET endpoint to fetch setlists from user defined artist
    //maps to GET /api/setlists?artist=Radiohead
    //returns list of setlists, each containing songs, artist etc in JSON format
    @GetMapping
    public List<Setlist> getArtistSetlists(@RequestParam String artist, @RequestParam(defaultValue = "50") String setlistRange)
    {
        //have to use String in parameter to allow for user to select all, so conversion is in case number entered
        int maxSetlists = parseSetlistRange(setlistRange);
        //get all setlists from database using service layer method
        return setlistService.getArtistSetlists(artist, maxSetlists);
    }
    //new consolidated GET endpoint to return all processed setlist stats in one object
    //maps to: GET /api/setlists/stats?artist=Radiohead&setlistRange=50
    //have to return a map in order to return the stats together in one response
    //Map groups values under separate keys, with keys being labels like "encores", "rarest"
    //and values being List<SongsRanked> or double
    //Spring automatically serializes the map into JSON to return to frontend
    @GetMapping("/stats")
    public Map<String, Object> getCombinedStats(@RequestParam String artist, @RequestParam(defaultValue = "50") String setlistRange) {
        int maxSetlists = parseSetlistRange(setlistRange);

        //get each stat into local variables using service methods
        List<SongsRanked> encores = setlistService.getTopEncoreSongs(artist, maxSetlists);
        List<SongsRanked> rarest = setlistService.getRarestSongs(artist, maxSetlists);
        List<SongsRanked> openers = setlistService.getTopOpenerSongs(artist, maxSetlists);
        List<SongsRanked> mostPlayed = setlistService.getMostPlayedSongs(artist, maxSetlists);
        double avgLength = setlistService.getAvgSetlistLength(artist, maxSetlists);

        //log each result for debugging
        System.out.println("encores: " +encores);
        System.out.println("rarest: " +rarest);
        System.out.println("openers: " +openers);
        System.out.println("mostPlayed: " + mostPlayed);
        System.out.println("averageLength: " + avgLength);

        //build response map of stats
        Map<String, Object> stats = new HashMap<>();
        stats.put("encores", encores);
        stats.put("rarest", rarest);
        stats.put("averageLength", avgLength);
        stats.put("openers", openers);
        stats.put("mostPlayed", mostPlayed);

        return stats;
    }

    //helper method to convert user input to maxSetlists int value
    //avoiding repeat logic in endpoints
    private int parseSetlistRange(String setlistRange) {
        if (setlistRange.equalsIgnoreCase("all")) {
            return -1;
        }
        try {
            return Integer.parseInt(setlistRange);
        }
        //should never happen as range selected from dropdown
        catch (NumberFormatException e) {
            return 50;
        }
        }

    /* implement in future - endpoint for summary of stats
    //Get stats for artists sets
    @GetMapping("/stats")
    public String getArtistStats(@RequestParam String artist) {
        //save setlist to database
        return setlistService.calculateArtistStats(artist);
    } */
}
