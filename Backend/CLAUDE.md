    # CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BobGourmet is a Spring Boot application for restaurant selection through real-time multiplayer voting. It features room-based gameplay where users can create/join rooms, submit menu options, and participate in random selection draws. The application uses WebSocket for real-time communication and Redis for session management.

## Build and Development Commands

```bash
# Build the project
./gradlew build

# Run the application (dev profile active by default)
./gradlew bootRun

# Run tests
./gradlew test

# Run a specific test class
./gradlew test --tests "MenuServiceTest"

# Clean build
./gradlew clean build
```

## Architecture Overview

### Technology Stack
- **Framework**: Spring Boot 3.5.0 with Java 21
- **Security**: JWT-based authentication with Spring Security
- **Database**: H2 (dev), PostgreSQL (prod)
- **Cache/Session**: Redis for room state management
- **Real-time**: WebSocket with STOMP protocol
- **Documentation**: OpenAPI 3 with Swagger UI

### Core Components

**Authentication Flow**
- JWT-based stateless authentication using `JwtProvider` and `JwtAuthFilter`
- User registration/login handled by `SignupService` and `LoginService`
- Security configuration in `SecurityConfig` with CORS for frontend at localhost:5173

**Room Management System**
- `MatchroomService`: Core business logic for room lifecycle (create/join/leave)
- `RedisRoomRepository`: Redis-based atomic operations using Lua scripts for consistency
- `RoomStateService`: State transitions (waiting → inputting → started → result_viewing)
- Optimistic locking with retry mechanism for concurrent room operations

**Menu Voting System**
- `MenuService`: Handles menu submission, voting, and random selection
- State-based workflow with WebSocket broadcasts for real-time updates
- Automatic state transitions with scheduled cleanup (10-second result viewing)

**WebSocket Communication**
- STOMP endpoint at `/ws-BobGourmet/**`
- Topic-based messaging: `/topic/room/{roomId}/events` and `/topic/room/{roomId}/closed`
- Connection lifecycle managed by `StompEventListener`

### Data Flow Patterns

**Room Operations**: Atomic Redis operations → Service layer validation → WebSocket broadcast → Client state update

**Menu Workflow**: Submit → Vote → Random Draw → Result Display (10s) → Reset to Input

**Error Handling**: Global exception handler with custom exceptions (`RoomException`, `InvalidInputException`)

## Development Notes

### Configuration Profiles
- `dev`: H2 database, debug logging, Redis localhost:6379
- `prod`: PostgreSQL, production Redis settings

### Key Design Patterns
- Repository pattern with Redis Lua scripts for atomicity
- Service layer with transaction management
- DTO pattern for API contracts
- WebSocket message broadcasting with structured payloads

### Testing
- Integration tests use Testcontainers for Redis
- Unit tests mock Redis repository operations
- Test configuration includes H2 database and embedded Redis

### Security Considerations
- JWT secret configured per environment
- CORS restricted to localhost:5173 for development
- Password encoding with BCrypt
- Room access control (private rooms require passwords)