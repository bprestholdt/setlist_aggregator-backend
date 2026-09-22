package com.BP.setlistaggregator.internalDTO;

//one tour within the selected range, for comparing tours side by side
//dates are yyyy-MM-dd strings
public record TourSummary(String name, int shows, double avgSongs, int uniqueSongs, String firstDate, String lastDate) {}
