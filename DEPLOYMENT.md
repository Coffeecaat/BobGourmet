# BobGourmet Google Cloud Deployment Guide

## Prerequisites

1. **Google Cloud Account**: Sign up at https://cloud.google.com/
2. **Google Cloud SDK**: Install from https://cloud.google.com/sdk/docs/install
3. **Docker**: Install from https://docs.docker.com/get-docker/

## Step 1: Google Cloud Project Setup

1. **Create a new project**:
   ```bash
   gcloud projects create bobgourmet-app --name="BobGourmet App"
   gcloud config set project bobgourmet-app
   ```

2. **Enable required APIs**:
   ```bash
   gcloud services enable cloudbuild.googleapis.com
   gcloud services enable run.googleapis.com
   gcloud services enable containerregistry.googleapis.com
   gcloud services enable sql-component.googleapis.com
   gcloud services enable redis.googleapis.com
   ```

3. **Set up billing** (required for Cloud Run):
   - Go to Google Cloud Console → Billing
   - Link a billing account to your project

## Step 2: Database Setup (Cloud SQL PostgreSQL)

1. **Create PostgreSQL instance**:
   ```bash
   gcloud sql instances create bobgourmet-db \
     --database-version=POSTGRES_15 \
     --cpu=1 \
     --memory=3840MB \
     --region=asia-northeast3 \
     --storage-type=SSD \
     --storage-size=10GB
   ```

2. **Create database and user**:
   ```bash
   gcloud sql databases create bobgourmet --instance=bobgourmet-db
   gcloud sql users create bobgourmet-user --instance=bobgourmet-db --password=YOUR_SECURE_PASSWORD
   ```

3. **Get connection details**:
   ```bash
   gcloud sql instances describe bobgourmet-db
   ```

## Step 3: Redis Setup (Cloud Memorystore)

1. **Create Redis instance**:
   ```bash
   gcloud redis instances create bobgourmet-redis \
     --size=1 \
     --region=asia-northeast3 \
     --redis-version=redis_6_x
   ```

2. **Get Redis connection details**:
   ```bash
   gcloud redis instances describe bobgourmet-redis --region=asia-northeast3
   ```

## Step 4: Backend Deployment

1. **Navigate to backend directory**:
   ```bash
   cd Backend
   ```

2. **Build and deploy using Cloud Build**:
   ```bash
   gcloud builds submit . --config=cloudbuild.yaml
   ```

3. **Set environment variables for Cloud Run**:
   ```bash
   gcloud run services update bobgourmet-backend \
     --region=asia-northeast3 \
     --set-env-vars="DATABASE_URL=jdbc:postgresql://[CLOUD_SQL_IP]:5432/bobgourmet" \
     --set-env-vars="DATABASE_USERNAME=bobgourmet-user" \
     --set-env-vars="DATABASE_PASSWORD=YOUR_SECURE_PASSWORD" \
     --set-env-vars="REDIS_HOST=[REDIS_IP]" \
     --set-env-vars="REDIS_PORT=6379" \
     --set-env-vars="JWT_SECRET=YOUR_JWT_SECRET_HERE" \
     --set-env-vars="FRONTEND_URLS=https://[FRONTEND_URL]" \
     --set-env-vars="ALLOWED_ORIGINS=https://[FRONTEND_URL]"
   ```

## Step 5: Frontend Deployment

1. **Update API endpoints**: Update `src/services/api.ts` with your backend URL:
   ```typescript
   const API_BASE_URL = 'https://bobgourmet-backend-[hash].a.run.app';
   ```

2. **Navigate to frontend directory**:
   ```bash
   cd Frontend
   ```

3. **Build and deploy**:
   ```bash
   gcloud builds submit . --config=cloudbuild.yaml
   ```

## Step 6: Database Migration

1. **Connect to Cloud SQL**:
   ```bash
   gcloud sql connect bobgourmet-db --user=bobgourmet-user
   ```

2. **Run initial schema creation** (your Spring Boot app will handle this with JPA)

## Step 7: Domain Setup (Optional)

1. **Map custom domain**:
   ```bash
   gcloud run domain-mappings create --service=bobgourmet-frontend --domain=yourdomain.com --region=asia-northeast3
   gcloud run domain-mappings create --service=bobgourmet-backend --domain=api.yourdomain.com --region=asia-northeast3
   ```

## Environment Variables Reference

### Backend Required Variables:
- `DATABASE_URL`: PostgreSQL connection string
- `DATABASE_USERNAME`: Database username
- `DATABASE_PASSWORD`: Database password
- `REDIS_HOST`: Redis instance IP
- `JWT_SECRET`: JWT signing secret (generate with `openssl rand -hex 32`)
- `FRONTEND_URLS`: Frontend URL for CORS
- `ALLOWED_ORIGINS`: WebSocket allowed origins

### Costs Estimation (Seoul Region):
- **Cloud Run**: ~$0-5/month for small usage
- **Cloud SQL (db-f1-micro)**: ~$7-15/month
- **Cloud Memorystore (1GB)**: ~$25/month
- **Container Registry**: ~$0-1/month

## Monitoring and Logs

1. **View logs**:
   ```bash
   gcloud run logs tail bobgourmet-backend --region=asia-northeast3
   gcloud run logs tail bobgourmet-frontend --region=asia-northeast3
   ```

2. **Monitor performance**: Use Google Cloud Console → Cloud Run → Service details

## Troubleshooting

1. **Build fails**: Check `gcloud builds log [BUILD_ID]`
2. **Service won't start**: Check environment variables and logs
3. **Database connection issues**: Verify Cloud SQL IP whitelist and credentials
4. **CORS errors**: Update `FRONTEND_URLS` environment variable

## Security Considerations

1. Use Google Secret Manager for sensitive data
2. Enable VPC for database security
3. Set up IAM roles with least privilege
4. Enable audit logs
5. Use HTTPS only (Cloud Run enforces this by default)