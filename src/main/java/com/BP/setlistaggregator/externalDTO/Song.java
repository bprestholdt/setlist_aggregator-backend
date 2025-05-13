package com.BP.setlistaggregator.externalDTO;

//represents single song from setlist in raw API form
public class Song {
    //song title
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}