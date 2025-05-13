package com.BP.setlistaggregator.externalDTO;
import java.util.List;

//class to match the API response structure from Setlist.fm
//this wrapper wraps the root API response to allow extraction of ApiSetlist entries
public class SetlistResponseWrapper {
    //Top level JSON object from Setlist.fm has field called "setlist" which holds list of concerts
    private List<ApiSetlist> setlist;

    //getter for list of setlists to be used in other classes
    public List<ApiSetlist> getSetlist() {
        return setlist;
    }

    public void setSetlist(List<ApiSetlist> setlist) {
        this.setlist = setlist;
    }
}
