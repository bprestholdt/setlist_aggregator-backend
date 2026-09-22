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
    //where the show was played: { "name": "Soldier Field", "city": { "name": "Chicago", "stateCode": "IL", "country": { "code": "US", "name": "United States" } } }
    private Venue venue;
    //tour the show was part of, if any: { "name": "Mathematics Tour" }
    private Tour tour;

    //nested API objects only used for mapping, kept inside this class since nothing else needs them
    public static class Venue {
        private String name;
        private City city;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public City getCity() { return city; }
        public void setCity(City city) { this.city = city; }
    }

    public static class City {
        private String name;
        private String stateCode;
        private Country country;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStateCode() { return stateCode; }
        public void setStateCode(String stateCode) { this.stateCode = stateCode; }
        public Country getCountry() { return country; }
        public void setCountry(Country country) { this.country = country; }
    }

    public static class Country {
        private String code;
        private String name;
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class Tour {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public Venue getVenue() {
        return venue;
    }

    public void setVenue(Venue venue) {
        this.venue = venue;
    }

    public Tour getTour() {
        return tour;
    }

    public void setTour(Tour tour) {
        this.tour = tour;
    }

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
