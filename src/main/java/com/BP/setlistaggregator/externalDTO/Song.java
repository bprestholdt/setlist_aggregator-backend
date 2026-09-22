package com.BP.setlistaggregator.externalDTO;

//represents single song from setlist in raw API form
public class Song {
    //song title
    private String name;
    //true if this is a recorded intro/outro played over the PA, not a live song
    private Boolean tape;
    //set if the song is a cover: { "name": "Ben E. King" }
    private Cover cover;

    //original artist of a covered song
    public static class Cover {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getTape() {
        return tape;
    }

    public void setTape(Boolean tape) {
        this.tape = tape;
    }

    public Cover getCover() {
        return cover;
    }

    public void setCover(Cover cover) {
        this.cover = cover;
    }
}
