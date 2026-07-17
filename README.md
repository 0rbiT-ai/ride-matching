# Ride Matching System

Backend system for a real-time ride-matching application. The system is built using a microservices architecture, leveraging Spring Boot, Kafka, Redis, and MySQL to handle real-time location updates, ride requests, and driver matching.

---

## Project Description

The system consists of three main microservices and a Docker Compose setup for external infrastructure dependencies:

1. **Location Service (Port 8082)**: Manages real-time driver coordinates. It stores locations in Redis using geospatial indexes (`GEOADD` and `GEORADIUS`/`GEOSEARCH` operations) to enable fast geographic queries.
2. **Ride Service (Port 8083)**: Manages the lifecycle of a ride (Requesting, Starting, Completing, and Cancelling). It persists ride states in MySQL and initiates the matching process by publishing messages to Kafka.
3. **Matching Service (Port 8084)**: Orchestrates the driver assignment. It consumes ride request events from Kafka, queries the Location Service to retrieve active drivers within a 5km radius, scores them using a custom distance/rating algorithm, and publishes the selected driver back via Kafka.

---

## Architecture Flow

The interaction between the components follows the event-driven and REST flow shown below:

```text
Driver Phone -> Location Service -> Redis (GEOADD)

Rider App -> Ride Service -> Kafka (ride.requested)
                                      |
                           Matching Service (consumer)
                                      |
                           Location Service (find nearby drivers)
                                      |
                           Matching Algorithm (score drivers)
                                      |
                           Kafka (ride.matched)
                                      |
                           Ride Service (update ride with driver)
```

### Component Details
* **Driver Phone -> Location Service**: Drivers send HTTP POST requests with their current coordinates to the Location Service every 3 seconds.
* **Location Service -> Redis**: Driver coordinates are cached in Redis under the key `drivers:locations` using geospatial data types.
* **Rider App -> Ride Service**: A rider requests a ride via HTTP POST. The Ride Service creates a database entry in MySQL with `REQUESTED` status, calculates the estimated fare, and changes the status to `MATCHING`.
* **Ride Service -> Kafka (`ride.requested`)**: The Ride Service broadcasts a `ride.requested` event containing ride details and pickup coordinates.
* **Matching Service -> Location Service**: The Matching Service consumes the request, extracts pickup coordinates, and queries the Location Service synchronously over HTTP (using OpenFeign) to find drivers within a default 5km radius.
* **Matching Algorithm**: The Matching Service scores retrieved drivers based on distance (70% weight) and rating (30% weight). It then selects the driver with the highest score.
* **Matching Service -> Kafka (`ride.matched`)**: The Matching Service broadcasts a `ride.matched` event containing the assigned driver's ID.
* **Kafka -> Ride Service**: The Ride Service consumes the event, assigns the driver to the ride record in MySQL, and updates the ride status to `ACCEPTED`.

---

## Infrastructure and Run Guide

### 1. Run Docker Compose
The external services are containerized using Docker Compose. Run the following command in the root directory to spin up the infrastructure:

```bash
docker compose up -d
```

This starts:
* **Redis** (Port 6379)
* **MySQL** (Port 3306)
* **Zookeeper** (Port 2181)
* **Kafka** (Port 9092)

**Note on Database Settings**:
The `ride-service` configuration (`application.yaml`) expects a MySQL database named `ridematching` with the username `root` and password `1234`. The default `docker-compose.yml` creates a database named `ride_db` with the password `root`. 

To ensure the services connect correctly:
1. Update `ride-service/src/main/resources/application.yaml` credentials to match Docker Compose, or
2. Manually connect to the MySQL container and execute `CREATE DATABASE ridematching;` before starting the Ride Service.

### 2. Build and Start the Services
Run each service using Maven. Run the following commands in their respective directories:

#### Build all services
```bash
# In location-service, matching-service, and ride-service
./mvnw clean install
```

#### Run Location Service
```bash
cd location-service
./mvnw spring-boot:run
```

#### Run Ride Service
```bash
cd ride-service
./mvnw spring-boot:run
```

#### Run Matching Service
```bash
cd matching-service
./mvnw spring-boot:run
```

---

## API Documentation

### 1. Location Service API

#### Update Driver Location
Updates or adds the coordinates of an active driver.
* **Endpoint**: `POST /api/v1/locations/drivers/update`
* **Content-Type**: `application/json`
* **Request Body**:
```json
{
  "driverId": "driver_uuid_101",
  "latitude": 37.7749,
  "longitude": -122.4194
}
```
* **Response**: `200 OK` (Plain text: `"Driver Location Updated"`)

#### Get Nearby Drivers
Queries drivers within a specific radius of coordinates.
* **Endpoint**: `GET /api/v1/locations/drivers/nearby`
* **Query Parameters**:
  * `latitude` (double, required)
  * `longitude` (double, required)
  * `radius` (double, optional, default: `5.0`)
* **Response**: `200 OK`
```json
[
  {
    "driverId": "driver_uuid_101",
    "latitude": 37.7749,
    "longitude": -122.4194,
    "distanceInKm": 0.15
  }
]
```

#### Remove Offline Driver
Removes a driver from the geospatial index.
* **Endpoint**: `DELETE /api/v1/locations/drivers/{driverId}`
* **Response**: `200 OK` (Plain text: `"Driver Removed Successfully"`)

---

### 2. Ride Service API

#### Request a Ride
Creates a new ride request and initiates matching.
* **Endpoint**: `POST /api/v1/rides/request`
* **Content-Type**: `application/json`
* **Request Body**:
```json
{
  "riderId": "rider_uuid_202",
  "pickupLatitude": 37.7750,
  "pickupLongitude": -122.4195,
  "pickupAddress": "123 Market St, San Francisco",
  "dropLatitude": 37.7891,
  "dropLongitude": -122.4014,
  "dropAddress": "Union Square, San Francisco"
}
```
* **Response**: `201 Created`
```json
{
  "id": "ride_uuid_999",
  "riderId": "rider_uuid_202",
  "driverId": null,
  "pickupLatitude": 37.775,
  "pickupLongitude": -122.4195,
  "pickupAddress": "123 Market St, San Francisco",
  "dropLatitude": 37.7891,
  "dropLongitude": -122.4014,
  "dropAddress": "Union Square, San Francisco",
  "status": "MATCHING",
  "estimatedFare": 78.50,
  "actualFare": 0.0,
  "createdAt": "2026-07-17T18:46:37",
  "startedAt": null,
  "completedAt": null
}
```

#### Get Ride by ID
Fetches details of a specific ride.
* **Endpoint**: `GET /api/v1/rides/{rideId}`
* **Response**: `200 OK`
```json
{
  "id": "ride_uuid_999",
  "riderId": "rider_uuid_202",
  "driverId": "driver_uuid_101",
  "pickupLatitude": 37.775,
  "pickupLongitude": -122.4195,
  "pickupAddress": "123 Market St, San Francisco",
  "dropLatitude": 37.7891,
  "dropLongitude": -122.4014,
  "dropAddress": "Union Square, San Francisco",
  "status": "ACCEPTED",
  "estimatedFare": 78.50,
  "actualFare": 0.0,
  "createdAt": "2026-07-17T18:46:37",
  "startedAt": null,
  "completedAt": null
}
```

#### Get Rides by Rider ID
Lists all rides requested by a particular rider, ordered by creation date descending.
* **Endpoint**: `GET /api/v1/rides/rider/{riderId}`
* **Response**: `200 OK`

#### Start a Ride
Updates the ride state to active. Must be in `ACCEPTED` state to start.
* **Endpoint**: `PUT /api/v1/rides/{rideId}/start`
* **Response**: `200 OK`
```json
{
  "status": "RIDE_STARTED",
  "startedAt": "2026-07-17T18:50:00",
  ...
}
```

#### Complete a Ride
Completes the active ride and sets the final actual fare. Must be in `RIDE_STARTED` state.
* **Endpoint**: `PUT /api/v1/rides/{rideId}/complete`
* **Response**: `200 OK`
```json
{
  "status": "COMPLETED",
  "actualFare": 78.50,
  "completedAt": "2026-07-17T19:05:00",
  ...
}
```

#### Cancel a Ride
Cancels the ride request.
* **Endpoint**: `PUT /api/v1/rides/{rideId}/cancel`
* **Response**: `200 OK`
```json
{
  "status": "CANCELLED",
  ...
}
```

---

## Testing Flow

Follow these steps to test the end-to-end integration:

### Step 1: Simulate a Driver Coming Online
Register a driver's location close to the target pickup area in San Francisco.
```bash
curl -X POST http://localhost:8082/api/v1/locations/drivers/update \
  -H "Content-Type: application/json" \
  -d '{
    "driverId": "driver-test-01",
    "latitude": 37.7749,
    "longitude": -122.4194
  }'
```

### Step 2: Request a Ride
Create a ride request near the driver's coordinates.
```bash
curl -X POST http://localhost:8083/api/v1/rides/request \
  -H "Content-Type: application/json" \
  -d '{
    "riderId": "rider-test-02",
    "pickupLatitude": 37.7750,
    "pickupLongitude": -122.4195,
    "pickupAddress": "123 Market St, San Francisco",
    "dropLatitude": 37.7891,
    "dropLongitude": -122.4014,
    "dropAddress": "Union Square, San Francisco"
  }'
```
Take note of the `"id"` field returned in the JSON response (e.g. `c0a80102-8611-1a3b-8186-11f8e8de0000`).

### Step 3: Verify Automated Matching
Wait 1-2 seconds for Kafka event roundtrips to finish. Query the ride status using the ride ID from the previous step.
```bash
curl -X GET http://localhost:8083/api/v1/rides/YOUR_RIDE_ID_HERE
```
The response should confirm that the state transitioned from `MATCHING` to `ACCEPTED`, and `driverId` is set to `"driver-test-01"`.

### Step 4: Progress the Ride State
Simulate the driver starting the ride:
```bash
curl -X PUT http://localhost:8083/api/v1/rides/YOUR_RIDE_ID_HERE/start
```

Simulate the driver completing the ride at the destination:
```bash
curl -X PUT http://localhost:8083/api/v1/rides/YOUR_RIDE_ID_HERE/complete
```
In the completion response, the `status` will show `COMPLETED` and the `actualFare` will match the calculated estimate.
