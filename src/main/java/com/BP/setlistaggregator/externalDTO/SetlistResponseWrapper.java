package com.BP.setlistaggregator.externalDTO;
import java.util.List;

//class to match the API response structure from Setlist.fm
//this wrapper wraps the root API response to allow extraction of ApiSetlist entries
public class SetlistResponseWrapper {
    //Top level JSON object from Setlist.fm has field called "setlist" which holds list of concerts
    private List<ApiSetlist> setlist;
    //paging info setlist.fm sends with every page, used to tell when we've reached the last page
    private int total;
    private int page;
    private int itemsPerPage;

    //getter for list of setlists to be used in other classes
    public List<ApiSetlist> getSetlist() {
        return setlist;
    }

    public void setSetlist(List<ApiSetlist> setlist) {
        this.setlist = setlist;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    public void setItemsPerPage(int itemsPerPage) {
        this.itemsPerPage = itemsPerPage;
    }
}
