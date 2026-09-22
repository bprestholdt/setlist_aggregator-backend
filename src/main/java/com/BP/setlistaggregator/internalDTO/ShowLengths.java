package com.BP.setlistaggregator.internalDTO;

//longest and shortest show in the selected range (song counts and dates as yyyy-MM-dd)
public record ShowLengths(int longestSongs, String longestDate, int shortestSongs, String shortestDate) {}
