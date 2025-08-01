# 🛡️ Frontend Security Setup Guide

## ⚠️ **IMPORTANT: Before Running This Application**

This frontend application requires proper configuration to connect to the backend securely.

## 🔐 **Required Environment Variables**

### 1. Copy Environment Template
```bash
cp .env.example .env
```

### 2. Set Required Variables

Edit your `.env` file with the following values:

```bash
# API Configuration
REACT_APP_API_BASE_URL=http://localhost:8080

# WebSocket Configuration
REACT_APP_WS_URL=ws://localhost:8080/ws-BobGourmet
```

## 🌍 **Environment-Specific Configuration**

### Development Environment
```bash
REACT_APP_API_BASE_URL=http://localhost:8080
REACT_APP_WS_URL=ws://localhost:8080/ws-BobGourmet
```

### Production Environment
```bash
REACT_APP_API_BASE_URL=https://your-api-domain.com
REACT_APP_WS_URL=wss://your-api-domain.com/ws-BobGourmet
```

## 🚀 **Deployment Checklist**

Before deploying to production:

- [ ] ✅ Set correct production API URL (HTTPS)
- [ ] ✅ Set correct production WebSocket URL (WSS)
- [ ] ✅ Verified `.env` is in `.gitignore`
- [ ] ✅ Never commit `.env` to version control
- [ ] ✅ Backend CORS allows your frontend domain

## 🔒 **Security Notes**

1. **HTTPS Only**: Always use HTTPS in production
2. **Secure WebSockets**: Use WSS (not WS) in production
3. **Environment Variables**: All `REACT_APP_*` variables are public
4. **No Secrets**: Never put API keys or secrets in frontend env vars
5. **CORS**: Ensure backend allows your frontend domain

## 🚨 **Common Issues**

### CORS Errors
- Ensure backend CORS allows your frontend domain
- Check that URLs don't have trailing slashes

### WebSocket Connection Failed
- Verify WebSocket URL is correct
- Check that backend WebSocket endpoint is accessible
- Ensure WSS is used with HTTPS

### API Connection Failed
- Verify API base URL is correct
- Check network connectivity
- Ensure backend is running and accessible

---

⚠️ **Remember**: Always use HTTPS and WSS in production environments.