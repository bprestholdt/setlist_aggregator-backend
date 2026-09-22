package com.BP.setlistaggregator.externalDTO;

import java.util.List;

//represents each section of a concert set like set 1, 2 , encore. not all concerts have sections
public class SetSection {
    //optional section label in API, sometimes labels encores or special show parts
    private String name;
    //list of songs played in section
    /* JSON form :
        {
            "name": "Encore",
            "song": [
              { "name": "Fake Plastic Trees" },
              { "name": "Karma Police" }
            ]
          }
        Every song entry becomes a Song dto object
     */
    private List<Song> song;
    //encore number (1 = first encore, 2 = second...) if this section is an encore, otherwise missing
    private Integer encore;

    //true if setlist.fm marks this section as an encore
    public boolean isEncore() {
        return encore != null && encore > 0;
    }

    public Integer getEncore() {
        return encore;
    }

    public void setEncore(Integer encore) {
        this.encore = encore;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Song> getSong() {
        return song;
    }

    public void setSong(List<Song> song) {
        this.song = song;
    }
}
