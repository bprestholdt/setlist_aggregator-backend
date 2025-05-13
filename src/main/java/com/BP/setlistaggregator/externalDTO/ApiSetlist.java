package com.BP.setlistaggregator.externalDTO;

//DTO (Data Transfer Object) for a setlist from setlist.fm API
//DTOs= temporary Java object to hold API response data
//Data in dto form allows app to transform it into entity objects (Setlist, Artist, Song) and save to db

//ApiSetlist represents one concert's worth of data from the API response
public class ApiSetlist {
    //unique ID from Setlist.fm (separate from one used in DB)
    private String id;
    //date of the concert (as string from API)
    //parsed in service layer into LocalDate to map to Setlist entity
    private String eventDate;
    //nested object that wraps the main concert data- songs grouped by set section (eg set 1, set 2, encore)
    /*corresponds to nested JSON object in form:
     "sets": {
       "set": [
         { "name": "Set 1", "song": [...] },
         { "name": "Encore", "song": [...] }
        ]
      }
     */
    private Sets sets;

    //getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventDate() {
        return eventDate;
    }

    public void setEventDate(String eventDate) {
        this.eventDate = eventDate;
    }

    public Sets getSets() {
        return sets;
    }

    public void setSets(Sets sets) {
        this.sets = sets;
    }
}
