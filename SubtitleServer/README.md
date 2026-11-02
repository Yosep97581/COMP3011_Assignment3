# COMP3011 Assignment 1 - Speech to Text

## Overview
A Spring Boot web application that records audio from the browser,
uploads it to the Java backend, and transcribes it using the OpenAI
speech-to-text API.

## Main Features
- Browser microphone recording
- Speech-to-text transcription
- Global token usage statistics
- Server uptime API
- Graceful shutdown API
- Concurrent request handling using virtual threads

## Project Structure
- controller - REST API endpoints
- service - application and external API logic
- model - API response objects
- static - HTML, CSS and JavaScript frontend

## API Endpoints
- POST /api/v1/transcribe
- GET /api/v1/admin/uptime
- GET /api/v1/global/stats
- POST /api/v1/admin/shutdown

## Configuration
The application reads the OpenAI API key from the environment variable:

OPENAI_API_KEY

The key is never stored in the source code or returned to clients.

## Concurrency
Virtual threads are enabled for handling blocking HTTP requests.

Shared global token counters use AtomicLong so updates remain safe
when multiple requests occur at the same time.

## Testing
Automated tests cover:
- REST API regression behaviour
- transcription controller success and failure cases
- graceful shutdown responses
- thread-safe statistics updates
- 250 concurrent blocking HTTP requests

The concurrency test completed 250 simultaneous requests successfully
without crashes or lost responses.

## Running the Application

Build:

./mvnw clean package

Run:

java -jar target/Assignment1-0.0.1-SNAPSHOT.jar

Then open:

http://localhost:8080/