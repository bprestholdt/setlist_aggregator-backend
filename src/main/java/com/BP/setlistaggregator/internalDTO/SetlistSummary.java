package com.BP.setlistaggregator.internalDTO;

//headline numbers for the selected range, shown in the summary tiles at the top of the results page
//dates are yyyy-MM-dd strings; Double fields are null when there isn't enough data (e.g. only one show)
public record SetlistSummary(
        //shows analyzed and the dates they span
        int shows,
        String firstDate,
        String lastDate,
        //songs per show (tapes excluded)
        Double avgSongs,
        int longestSongs,
        String longestDate,
        int shortestSongs,
        String shortestDate,
        //average days between shows
        Double daysBetweenShows,
        //different songs played, and how many were played at only one show
        int uniqueSongs,
        int oneOffs,
        //average % of a show's songs that were also played at the show before it
        Double repeatRate,
        //songs played at 90%+ of shows
        int coreSongs,
        //% of songs played that were covers
        Double coverShare,
        //% of shows with an encore section
        Double encoreRate,
        //different countries and cities played
        int countries,
        int cities
) {}
