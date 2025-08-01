    # 🍽️ BobGourmet - Restaurant Selection App

A Spring Boot application for restaurant selection through real-time multiplayer voting. Features room-based gameplay where users can create/join rooms, submit menu options, and participate in random selection draws.

## 🚀 **Quick Start**

### Prerequisites
- Java 21+
- Redis Server
- Node.js 18+ (for frontend)

### 1. Clone the Repository
```bash
git clone <your-repo-url>
cd BobGourmet
```

### 2. Security Setup (REQUIRED)
```bash
# Copy environment template
cp .env.example .env

# Edit .env file with your secure values
# See SECURITY.md for detailed instructions
```

⚠️ **CRITICAL**: Read `SECURITY.md` before running the application!

### 3. Start Redis Server
```bash
# Using Docker
docker run -d -p 6379:6379 redis:alpine

# Or install Redis locally
redis-server
```

### 4. Run the Application
```bash
# Development mode (uses H2 database)
./gradlew bootRun

# Production mode (requires PostgreSQL)
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

### 5. Access the Application
- Backend API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

## 🏗️ **Architecture Overview**

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
- Security configuration in `SecurityConfig` with CORS for frontend

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

## 🛠️ **Development**

### Build Commands
```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run specific test class
./gradlew test --tests "MenuServiceTest"

# Clean build
./gradlew clean build
```

### Configuration Profiles
- `dev`: H2 database, debug logging, Redis localhost:6379
- `prod`: PostgreSQL, production Redis settings

### API Documentation
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## 🔌 **API Endpoints**

### Authentication
- `POST /api/auth/login` - User login
- `POST /api/auth/register` - User registration

### Room Management
- `GET /api/MatchRooms` - List active rooms
- `POST /api/MatchRooms` - Create room
- `POST /api/MatchRooms/{roomId}/join` - Join room
- `POST /api/MatchRooms/{roomId}/leave` - Leave room
- `GET /api/MatchRooms/{roomId}` - Get room details

### Menu System
- `POST /api/MatchRooms/{roomId}/menus` - Submit menus
- `POST /api/MatchRooms/{roomId}/menus/{menuKey}/recommend` - Recommend menu
- `POST /api/MatchRooms/{roomId}/menus/{menuKey}/dislike` - Dislike menu
- `POST /api/MatchRooms/{roomId}/start-draw` - Start random selection
- `POST /api/MatchRooms/{roomId}/reset` - Reset room state

## 🧪 **Testing**

### Running Tests
```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests "MenuServiceTest"

# Integration tests
./gradlew test --tests "*IntegrationTest"
```

### Test Configuration
- Integration tests use Testcontainers for Redis
- Unit tests mock Redis repository operations
- Test configuration includes H2 database and embedded Redis

## 🚀 **Deployment**

### Production Requirements
- PostgreSQL database
- Redis server with authentication
- Secure JWT secret (see SECURITY.md)
- HTTPS/SSL certificates
- Environment variables configured

### Environment Variables
See `.env.example` and `SECURITY.md` for complete list.

## 🔒 **Security**

⚠️ **READ SECURITY.md BEFORE DEPLOYMENT**

- JWT-based authentication
- Password encryption with BCrypt
- CORS protection
- Environment-based configuration
- No hardcoded secrets

## 🤝 **Contributing**

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests: `./gradlew test`
5. Submit a pull request

## 📄 **License**

This project is licensed under the MIT License.

## 🐛 **Issues**

Found a bug? Please open an issue on GitHub with:
- Steps to reproduce
- Expected behavior
- Actual behavior
- Environment details
