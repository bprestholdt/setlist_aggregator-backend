package com.BP.setlistaggregator.internalDTO;

//how often the artist played shows within the selected range
//daysBetweenShows is null when there's only one show
public record TouringPace(int shows, String firstDate, String lastDate, Double daysBetweenShows) {}
