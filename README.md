# Setlist Aggregator – Backend (Spring Boot + PostgreSQL)

This is the backend service for the Setlist Aggregator web app. It provides a RESTful API that:
- Fetches live setlist data from the Setlist.fm API
- Stores and updates concert data in a PostgreSQL database
- Calculates statistics like most common encores, rarest songs, and average setlist length

## Features
- Fetch setlists for a given artist (via Setlist.fm)
- Calculate:
  - Top 5 encore songs
  - Top 5 rarest songs
  - Average number of songs per concert
- Caches results in a local PostgreSQL database
- Supports pagination, throttling, and duplicate filtering

## Technologies
- Java 17
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Maven
- WebClient for HTTP requests

## Setup (Local)

### 1. Prerequisites
- Java 17+
- PostgreSQL
- Maven

### 2. Environment Variable
Set your Setlist.fm API key as an environment variable:
```bash
export SFM_API_KEY=your-api-key-here
# Force rebuild
