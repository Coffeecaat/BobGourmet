# 🛡️ Security Setup Guide

## ⚠️ **IMPORTANT: Before Running This Application**

This application requires proper security configuration to run safely. Follow all steps below before deployment.

## 🔐 **Required Environment Variables**

### 1. Copy Environment Template
```bash
cp .env.example .env
```

### 2. Set Required Variables

Edit your `.env` file with the following **REQUIRED** values:

```bash
# JWT Configuration (CRITICAL - MUST BE CHANGED)
JWT_SECRET=your-super-secure-256-bit-jwt-secret-key-here
JWT_EXPIRATION=900000

# Database Configuration (Production)
DATABASE_URL=jdbc:postgresql://localhost:5432/bobgourmet
DATABASE_USERNAME=your-db-username
DATABASE_PASSWORD=your-secure-db-password

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password

# CORS Configuration
CORS_ALLOWED_ORIGINS=https://your-frontend-domain.com

# Logging Level (INFO for production, DEBUG for development)
LOGGING_LEVEL=INFO
```

## 🔑 **Generating Secure JWT Secret**

**CRITICAL**: Never use the default JWT secret in production!

### Option 1: Using OpenSSL
```bash
openssl rand -base64 32
```

### Option 2: Using Node.js
```bash
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
```

### Option 3: Using Python
```bash
python -c "import secrets; print(secrets.token_urlsafe(32))"
```

## 🌍 **Environment-Specific Configuration**

### Development Environment
- Uses H2 in-memory database
- Debug logging enabled
- CORS allows localhost origins
- Redis password optional

### Production Environment
- **MUST** use external PostgreSQL database
- **MUST** set secure JWT secret
- **MUST** use INFO logging level
- **MUST** configure proper CORS origins
- **MUST** secure Redis with password

## 🚀 **Deployment Checklist**

Before deploying to production:

- [ ] ✅ Generated new JWT secret (32+ characters)
- [ ] ✅ Set secure database credentials
- [ ] ✅ Configured Redis password
- [ ] ✅ Set production CORS origins
- [ ] ✅ Set logging level to INFO
- [ ] ✅ Verified `.env` is in `.gitignore`
- [ ] ✅ Never commit `.env` to version control

## 🔒 **Security Best Practices**

1. **Rotate JWT Secrets**: Change JWT secret periodically
2. **Use HTTPS**: Always use HTTPS in production
3. **Database Security**: Use strong passwords and encrypted connections
4. **Redis Security**: Enable authentication and use strong passwords
5. **CORS Configuration**: Only allow trusted domains
6. **Logging**: Avoid logging sensitive information
7. **Regular Updates**: Keep dependencies updated

## 🚨 **Security Incidents**

If you suspect a security breach:

1. **Immediately** rotate JWT secret
2. **Immediately** change database passwords
3. **Immediately** change Redis passwords
4. Review access logs
5. Notify affected users if necessary

## 📞 **Support**

For security-related questions or issues, please open an issue on GitHub with the `security` label.

---

⚠️ **Remember**: Security is not optional. Follow this guide completely before deploying to production.