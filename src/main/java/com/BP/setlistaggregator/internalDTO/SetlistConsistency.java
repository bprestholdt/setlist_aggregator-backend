package com.BP.setlistaggregator.internalDTO;

//how similar an artist's shows are to each other within the selected range
//repeatRate: average % of a show's songs that were also played at the show before it (null if only one show)
//coreSongs: songs played at 90% or more of the shows
//uniqueSongs: different songs played across all the shows
//oneOffs: songs played at only one show
public record SetlistConsistency(Double repeatRate, int coreSongs, int uniqueSongs, int oneOffs) {}
