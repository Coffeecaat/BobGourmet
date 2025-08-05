# Universal Deployment Guide for Full-Stack Applications

## Core Deployment Concepts (Platform-Agnostic)

### 1. Environment Variables (MOST IMPORTANT)
```bash
# Database Connection
DATABASE_URL=your-database-connection-string
DATABASE_USERNAME=your-db-user
DATABASE_PASSWORD=your-db-password

# Redis Connection
REDIS_HOST=your-redis-host
REDIS_PORT=6379

# Security
JWT_SECRET=your-secret-key

# Frontend API URLs
VITE_API_BASE_URL=https://your-backend-url
VITE_WS_URL=wss://your-backend-url/ws-endpoint
```

**Key Rule**: Your code needs different settings for development vs production. NEVER hardcode secrets.

### 2. Build vs Runtime Understanding
- **Build Time**: When code gets compiled/bundled (React build, Java jar creation)
- **Runtime**: When application runs and serves users

**Frontend**: Environment variables must be set during BUILD TIME
**Backend**: Environment variables are read during RUNTIME

### 3. Container/Packaging Strategy
**Backend Options**:
- JAR file: `java -jar app.jar`
- Docker container
- Must expose correct port

**Frontend Options**:
- Static files (HTML, CSS, JS) served by web server
- Docker container with web server (Nginx)

### 4. External Services Your App Needs
- **Database**: PostgreSQL, MySQL, etc.
- **Cache**: Redis (if using)
- **File Storage**: If needed

Can be: Managed services, Self-hosted, or Docker containers

## Essential Files You Always Need

### Application Configuration
```
Backend:
├── application.properties (or application.yml)
├── application-prod.properties  # Production settings
└── application-dev.properties   # Development settings

Frontend:
├── .env.production    # Production environment variables
├── .env.development   # Development environment variables
└── .env.local         # Local overrides (don't commit)
```

### Build Configuration
```
Backend:
├── build.gradle (or pom.xml)
└── Dockerfile (optional but recommended)

Frontend:
├── package.json
├── vite.config.ts (or webpack config)
└── Dockerfile (optional)
```

### Health Check Endpoint (REQUIRED)
```java
@RestController
public class HealthController {
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
```

## Universal Deployment Process

### Step 1: Local Development Works ✓
- App runs on your machine
- Database connects
- Frontend talks to backend

### Step 2: Externalize Configuration ✓
```java
// ❌ BAD - hardcoded
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb

// ✅ GOOD - configurable  
spring.datasource.url=${DATABASE_URL}
```

### Step 3: Build for Production ✓
```bash
# Backend
./gradlew build

# Frontend  
npm run build
```

### Step 4: Test Production Build Locally ✓
```bash
# Set production environment variables
export DATABASE_URL=your-prod-db-url
export REDIS_HOST=your-redis-host

# Run production build
java -jar build/libs/app.jar
```

### Step 5: Deploy to Any Platform ✓

## Platform Examples

### Heroku (Easiest)
```bash
# Just push code, set environment variables
git push heroku main
heroku config:set DATABASE_URL=your-db-url
heroku config:set REDIS_URL=your-redis-url
```

### AWS Options
- **Elastic Beanstalk**: Upload JAR + set environment variables
- **ECS**: Docker container + environment variables  
- **Lambda**: Serverless functions

### Google Cloud Options
- **App Engine**: Upload code + app.yaml config
- **Cloud Run**: Docker container + environment variables

### Azure Options
- **App Service**: Upload code + configure app settings
- **Container Instances**: Docker container

### Docker (Works Anywhere)
```dockerfile
# Backend Dockerfile
FROM openjdk:21
COPY build/libs/app.jar /app.jar
EXPOSE 8080
CMD ["java", "-jar", "/app.jar"]

# Frontend Dockerfile
FROM nginx:alpine
COPY dist/ /usr/share/nginx/html/
EXPOSE 80
```

## Key Principles to Remember

### 1. Twelve-Factor App Principles
- **Config in environment**: Never hardcode settings
- **Dependencies explicit**: List all dependencies clearly
- **Build, release, run**: Separate these stages
- **Stateless processes**: Don't store user data in memory

### 2. Security Essentials
```properties
# ❌ NEVER commit these to GitHub
DATABASE_PASSWORD=secret123
JWT_SECRET=your-secret-key

# ✅ Use environment variables instead
DATABASE_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}
```

### 3. CORS Configuration
```java
// Allow your frontend domain
@CrossOrigin(origins = "${FRONTEND_URL}")
```

### 4. Health & Monitoring Must-Haves
- Health check endpoint (`/health`)
- Proper logging configuration
- Error handling that doesn't expose secrets

## Simplified Deployment Checklist

For ANY platform, you need these 4 things:

### ✅ 1. Environment Variables
```
DATABASE_URL=...
REDIS_URL=...
FRONTEND_URL=...
JWT_SECRET=...
```

### ✅ 2. Build Commands
```bash
# Backend
./gradlew build

# Frontend
npm run build
```

### ✅ 3. Run Commands
```bash
# Backend
java -jar app.jar

# Frontend
serve static files from /dist folder
```

### ✅ 4. Port Configuration
```java
// Backend listens on PORT environment variable
server.port=${PORT:8080}
```

## Common Configuration Patterns

### Backend Application Properties Template
```properties
# Application name
spring.application.name=YourApp

# Database
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}
spring.jpa.hibernate.ddl-auto=${DDL_MODE:update}

# Redis
spring.data.redis.host=${REDIS_HOST}
spring.data.redis.port=${REDIS_PORT:6379}

# JWT
jwt.secret=${JWT_SECRET}
jwt.expiration=${JWT_EXPIRATION:3600000}

# CORS
cors.allowed-origins=${FRONTEND_URL}

# Server
server.port=${PORT:8080}

# Health endpoint
management.endpoints.web.exposure.include=health,info
management.endpoint.health.enabled=true
```

### Frontend Environment Variables Template
```bash
# Production (.env.production)
VITE_API_BASE_URL=https://your-backend-url
VITE_WS_URL=wss://your-backend-url/ws

# Development (.env.development)  
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080/ws
```

## Troubleshooting Common Issues

### "Cannot connect to database"
- Check DATABASE_URL format
- Verify database is running and accessible
- Check firewall/network settings

### "CORS errors"
- Verify FRONTEND_URL matches exactly
- Check protocol (http vs https)
- Ensure CORS configuration is loaded

### "Build failures"
- Check all dependencies are listed
- Verify environment variables during build
- Check Node.js/Java versions

### "App won't start"
- Check PORT environment variable
- Verify health endpoint responds
- Check application logs for errors

---

## Remember: The Key to Success

**Focus on WHAT needs to be configured, not HOW each platform wants it configured.**

The complexity comes from platform-specific requirements, but these core principles work everywhere. Master these fundamentals, and you can deploy anywhere!