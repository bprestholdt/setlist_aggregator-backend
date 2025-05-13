package com.BP.setlistaggregator.externalDTO;

import java.util.List;

//wrapper for multiple "sets" object from setlist.fm API, an object containing multiple "set" sections
public class Sets {
    //list of set sections (eg set 1, Set 2, Encore)
    //in form of SetSection objects
    private List<SetSection> set;

    public List<SetSection> getSet() {
        return set;
    }

    public void setSet(List<SetSection> set) {
        this.set = set;
    }
}
