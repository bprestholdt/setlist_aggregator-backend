# Setlist Aggregator – Backend (Spring Boot + PostgreSQL)

This is the backend service for the [Setlist Aggregator](https://setlist-aggregator-frontend.vercel.app) web app ([frontend repo](https://github.com/bprestholdt/setlist_aggregator-frontend)). It provides a REST API that:
- Fetches live setlist data from the Setlist.fm API
- Stores and updates concert data in a PostgreSQL database
- Calculates statistics from an artist's setlists

## Features
- Resolves the searched artist to their MusicBrainz ID so ambiguous names match the right artist
- Fetches setlists from Setlist.fm page by page, with throttling and a single retry on rate limits
- Caches setlists in PostgreSQL; checks for new concerts at most once a week per artist
- Skips duplicates using Setlist.fm's own setlist IDs, so two shows on the same day are both kept
- Saves covers, encore sections, venue, city, country, and tour for every show; recorded intros/outros (tapes) are left out of all stats
- Calculates, for the last 20, last 100, or all shows:
  - Summary: shows and date span, songs per show, longest/shortest show, days between shows, night-to-night repeat rate, different songs, encore and cover rates, countries and cities
  - The songs: most played (as % of shows), rarest, most played covers, songs in rotation, new songs, biggest bust-outs
  - How the show runs: openers, main set closers, encore songs, signature transitions, songs usually played early or saved for late
  - On the road: top cities, countries, and venues, tour-by-tour comparison, and regional specials (songs played in only one country)

## API
- `GET /api/setlists/stats?artist=Radiohead&setlistRange=20` – all stats in one response (`setlistRange` is a number or `all`)
- `GET /health` – returns `ok` without touching the database, for uptime checks

## Technologies
- Java 17, Spring Boot, Spring Data JPA
- PostgreSQL
- Maven, Docker
- WebClient for Setlist.fm requests

## Setup (Local)

### 1. Prerequisites
- Java 17+
- PostgreSQL
- Maven (or the included `mvnw` wrapper)

### 2. Environment variables
```bash
export SFM_API_KEY=your-setlistfm-api-key
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/setlists
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=your-password
```
Tables are created or updated automatically on startup.

### 3. Run
```bash
./mvnw spring-boot:run
```
The API runs on port 8080 (or `$PORT` if set). Set `logging.level.com.BP.setlistaggregator=debug` to see per-page fetch details.

### Docker
```bash
docker build -t setlist-backend .
docker run -p 8080:8080 -e SFM_API_KEY=... -e SPRING_DATASOURCE_URL=... -e SPRING_DATASOURCE_USERNAME=... -e SPRING_DATASOURCE_PASSWORD=... setlist-backend
```
