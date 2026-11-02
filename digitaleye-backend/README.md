# DigitalEye Backend

This is the `backend` branch's content: a Spring Boot REST API that sits
between the agent/dashboard and MySQL, handling login and (soon) ingesting
agent data.

## Important: this depends on the database module

This project does NOT duplicate the database code - it reuses the models
and DAOs from `digitaleye-database` as a real Maven dependency. That means
**the database module must be built and installed into your local Maven
repository before this project will compile.**

One-time setup, in this order:

1. Make sure `digitaleye-database` is already set up and working (schema
   created, `DbConnectionTest` runs successfully) - see its own README.
2. From inside the `digitaleye-database` folder, run:
   ```
   mvn clean install
   ```
   This compiles it and copies the resulting jar into your local `.m2`
   repository as `com.digitaleye:digitaleye-database:1.0-SNAPSHOT`, which
   is exactly what this project's `pom.xml` asks for.
3. Open `src/main/resources/db.properties` in **this** project and set
   your MySQL root password (same as the database module's).
4. Open this `digitaleye-backend` folder in VS Code / IntelliJ as its own
   Maven project. It will download Spring Boot, Spring Security, and the
   JWT library automatically.

## Running it

Run `DigitalEyeBackendApplication.java` (it has a `main` method), or:
```
mvn spring-boot:run
```
The server starts on **http://localhost:8080**.

## Testing the auth flow

Use Postman, curl, or VS Code's "REST Client" extension.

**1. Register an admin account:**
```
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123",
  "fullName": "Administrator",
  "email": "admin@digitaleye.local",
  "role": "ADMIN"
}
```
Response includes a `token` - copy it.

**2. Call the admin-only endpoint with that token:**
```
GET http://localhost:8080/api/admin/systems
Authorization: Bearer <paste token here>
```
Should return `[]` (empty list - no systems registered yet) with a 200
status. Without the `Authorization` header, or with a non-admin user's
token, this should instead return 401/403.

**3. Login again later (instead of registering):**
```
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

## What's included so far
- `AuthController` - `/api/auth/register` and `/api/auth/login`
- `SystemController` - `/api/admin/systems` (first real admin-only endpoint,
  backs the dashboard's "Client Systems" table)
- `SecurityConfig` / `JwtAuthFilter` / `JwtUtil` / `CustomUserDetailsService`
  - the full JWT authentication layer, role-based access
    (`/api/admin/**` requires `ROLE_ADMIN`)
- Passwords are hashed with BCrypt before they ever reach the database

## Still to add
- Endpoint(s) for the Java agent to push activity logs, screenshots, and
  heartbeats to (this is what connects Milestone 1's agent to this backend)
- Endpoints for alerts, alert rules, reports, and monitoring policy, once
  their DAOs exist in the database module
- Global exception handler so errors come back as clean JSON instead of
  default Spring stack traces

## Note on the IMPORTANT security fix needed later
`/api/auth/register` is currently open to anyone, including letting
someone register themselves as `"role": "ADMIN"`. That's fine for local
development right now, but before this is anything other than a local
PBL demo, registration needs to move behind an admin-only endpoint so
random users can't self-promote to admin.
