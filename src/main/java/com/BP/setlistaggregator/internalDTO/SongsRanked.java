package com.BP.setlistaggregator.internalDTO;

//internal response DTO that structures data from my own backend to the frontend
//class to structure backend response to frontend in order to be able to display song ranks and play counts
public class SongsRanked {
    private int rank;
    private String title;
    private int count;

    public SongsRanked(int rank, String title, int count) {
        this.rank = rank;
        this.title = title;
        this.count = count;
    }

    //getters/setters


    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public java.lang.String getTitle() {
        return title;
    }

    public void setTitle(java.lang.String title) {
        this.title = title;
    }
}
