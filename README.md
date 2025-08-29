# 🍽️ BobGourmet - 실시간 메뉴 선택 서비

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-brightgreen)
![React](https://img.shields.io/badge/React-18-blue)
![TypeScript](https://img.shields.io/badge/TypeScript-5.0+-blue)
![Java](https://img.shields.io/badge/Java-21-orange)

실시간 멀티플레이어 투표를 통한 맛집 선정 웹 애플리케이션입니다. 방 기반 게임플레이를 통해 사용자들이 방을 만들거나 참여하여 메뉴 옵션을 제출하고 랜덤 추첨에 참여할 수 있습니다.

## ✨ **주요 기능**

- 🔐 **Google OAuth2 로그인** - 간편한 Google 계정 소셜 로그인
- 🏠 **실시간 방 시스템** - WebSocket을 통한 즉시 상태 동기화
- 🎲 **메뉴 추첨 시스템** - 공정한 랜덤 선택 알고리즘
- 👥 **멀티플레이어 지원** - 최대 10명까지 동시 참여
- 📱 **반응형 디자인** - 데스크톱 및 모바일 기기에서 동작
- 🛡️ **보안 설정** - 환경 변수 기반 비밀 정보 관리
- 🚀 **늦은 참가자 지원** - 진행 중인 방에도 자연스럽게 참여 가능

## 🏗️ **아키텍처**

```
BobGourmet-FullStack/
├── Backend/                 # Spring Boot API 서버
│   ├── src/main/java/      # Java 소스 코드
│   ├── src/main/resources/ # 설정 파일
│   ├── build.gradle        # 빌드 설정
│   └── .env.example        # 환경 변수 템플릿
└── Frontend/               # React TypeScript 클라이언트
    ├── src/                # React 소스 코드
    ├── package.json        # 의존성
    └── .env.example        # 환경 변수 템플릿
```

## 🚀 **빠른 시작**

### 필수 요구사항
- **Java 21+** 및 Gradle
- **Node.js 18+** 및 npm
- **Redis 서버** (Docker 또는 로컬 설치)
- **Google OAuth2 앱** (선택사항, 로그인 기능용)

### 1. 저장소 클론
```bash
git clone https://github.com/yourusername/BobGourmet-FullStack.git
cd BobGourmet-FullStack
```

### 2. 보안 설정 (중요!)
```bash
# 백엔드 환경 변수 설정
cp Backend/.env.example Backend/.env

# 프론트엔드 환경 변수 설정
cp Frontend/.env.example Frontend/.env

# 환경 변수 파일 수정 (아래 섹션 참조)
```

### 3. Redis 서버 시작
```bash
# Docker 사용
docker run -d -p 6379:6379 redis:alpine

# 또는 로컬 Redis 설치 후
redis-server
```

### 4. 백엔드 실행
```bash
cd Backend

# 개발 모드 (H2 데이터베이스)
./gradlew bootRun

# 운영 모드 (PostgreSQL 필요)
SPRING_PROFILES_ACTIVE=cloud ./gradlew bootRun
```

### 5. 프론트엔드 실행
```bash
cd Frontend

# 의존성 설치
npm install

# 개발 서버 시작
npm run dev
```

프론트엔드는 다음 주소에서 이용 가능합니다: **http://localhost:5173**

## 🔧 **백엔드 설정**

### 기술 스택
- **프레임워크**: Spring Boot 3.5.0 with Java 21
- **보안**: Spring Security + JWT + OAuth2 Google 로그인
- **데이터베이스**: H2 (개발용), PostgreSQL (운영용)
- **캐시**: 방 상태 관리용 Redis
- **실시간**: STOMP 프로토콜을 사용한 WebSocket

### 환경 변수 설정
`Backend/.env.example`에서 `Backend/.env` 생성:

```properties
# JWT 설정 (필수)
JWT_SECRET=여기에-안전한-256비트-시크릿-입력
JWT_EXPIRATION=3600000

# Google OAuth2 설정 (선택)
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
GOOGLE_REDIRECT_URI=http://localhost:8080/login/oauth2/code/google

# 데이터베이스 (운영용 PostgreSQL)
DATABASE_URL=jdbc:postgresql://localhost:5432/bobgourmet
DATABASE_USERNAME=데이터베이스-사용자명
DATABASE_PASSWORD=데이터베이스-비밀번호

# Redis 설정
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redis-비밀번호

# CORS 설정 (프론트엔드 URL)
CORS_ALLOWED_ORIGINS=http://localhost:5173

# 로깅 레벨 (개발: DEBUG, 운영: INFO)
LOGGING_LEVEL=DEBUG
```

### 빌드 명령어
```bash
cd Backend

# 프로젝트 빌드
./gradlew build

# 테스트 실행
./gradlew test

# 특정 프로필로 실행
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

### API 문서
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

## 🎨 **프론트엔드 설정**

### 기술 스택
- **프레임워크**: React 18 with TypeScript
- **빌드 도구**: Vite
- **스타일링**: Tailwind CSS
- **HTTP 클라이언트**: Axios
- **WebSocket**: SockJS를 통한 STOMP
- **상태 관리**: React Context API

### 환경 변수 설정
`Frontend/.env.example`에서 `Frontend/.env` 생성:

```bash
# 백엔드 API URL
VITE_API_BASE_URL=http://localhost:8080

# WebSocket URL
VITE_WS_URL=ws://localhost:8080/ws-BobGourmet
```

### 빌드 명령어
```bash
cd Frontend

# 의존성 설치
npm install

# 개발 서버 시작
npm run dev

# 운영용 빌드
npm run build

# 운영 빌드 미리보기
npm run preview
```

## 🔌 **API 엔드포인트**

### 인증
- `POST /api/auth/login` - 사용자 로그인
- `POST /api/auth/register` - 사용자 등록
- `GET /oauth2/authorization/google` - Google OAuth2 로그인 시작
- `GET /api/auth/oauth/callback` - OAuth2 콜백 처리

### 방 관리
- `GET /api/MatchRooms` - 활성 방 목록 조회
- `POST /api/MatchRooms` - 방 생성
- `POST /api/MatchRooms/{roomId}/join` - 방 참여
- `POST /api/MatchRooms/{roomId}/leave` - 방 나가기

### 메뉴 시스템
- `POST /api/MatchRooms/{roomId}/menus` - 메뉴 제출
- `POST /api/MatchRooms/{roomId}/menus/{menuKey}/recommend` - 메뉴 추천(미구현)
- `POST /api/MatchRooms/{roomId}/menus/{menuKey}/dislike` - 메뉴 비추천(미구현)
- `POST /api/MatchRooms/{roomId}/start-draw` - 랜덤 선택 시작

## 🎮 **사용 방법**

1. **회원가입/로그인** - 일반 계정 또는 Google 계정으로 간편 로그인
2. **방 둘러보기** - 테이블 형식으로 모든 활성 방 확인
3. **방 만들기** - 사용자 정의 설정으로 공개/비공개 방 생성
4. **방 참여** - 공개 방은 클릭으로 참여, 비공개 방은 비밀번호 입력
5. **메뉴 제출** - 방이 시작되면 메뉴 제안 추가
6. **투표** - 다른 참가자들의 제안에 추천 또는 비추천
7. **랜덤 추첨** - 호스트가 추첨을 시작하여 당첨 식당 선택
8. **결과 확인** - 선택된 메뉴로 식사를 드시면 됩니다! 🍽️

## 🧪 **테스트**

### 백엔드 테스트
```bash
cd Backend
./gradlew test
./gradlew test --tests "*IntegrationTest"
./gradlew test --tests "*OAuth2*"
```

### 프론트엔드 테스트
```bash
cd Frontend
npm run test
npm run test:coverage
```

## 🚀 **운영 배포**

### 백엔드 (Spring Boot)
```bash
cd Backend
./gradlew build
java -jar build/libs/BobGourmet-*.jar
```

### 프론트엔드 (정적 파일)
```bash
cd Frontend
npm run build
# dist/ 폴더를 정적 호스팅 서비스에 배포
```

### Docker 배포
```bash
# 프론트엔드 Docker 빌드
cd Frontend
docker build -t bobgourmet-frontend .

# 백엔드 Docker 빌드 (Dockerfile 별도 작성 필요)
cd Backend
docker build -t bobgourmet-backend .
```

### 운영용 환경 변수
- **백엔드**: 호스팅 플랫폼에서 모든 환경 변수 설정
- **프론트엔드**: 운영 URL로 `.env` 업데이트 (HTTPS/WSS)
- **Google OAuth2**: Google Cloud Console에서 운영 도메인 등록
- **보안**: 강력한 JWT 시크릿과 데이터베이스 비밀번호 사용

## 🔒 **보안**

- ✅ **OAuth2 소셜 로그인** - Google 계정 기반 안전한 인증
- ✅ **하드코딩된 비밀 정보 없음** - 모든 민감한 데이터는 환경 변수에 저장
- ✅ **JWT 인증** - 보안 토큰 기반 무상태 인증
- ✅ **비밀번호 암호화** - BCrypt 해싱
- ✅ **CORS 보호** - 설정 가능한 허용 오리진
- ✅ **환경 분리** - 개발/운영 설정 완전 분리

⚠️ **중요**: `Backend/SECURITY.md`와 `Frontend/SECURITY.md`의 보안 문서를 읽어주세요


## 📁 **프로젝트 구조**

```
Backend/
├── src/main/java/com/example/BobGourmet/
│   ├── Controller/         # REST API 엔드포인트
│   ├── Service/           # 비즈니스 로직
│   ├── Repository/        # 데이터 접근 계층
│   ├── Entity/            # JPA 엔티티
│   ├── DTO/               # 데이터 전송 객체
│   ├── Security/          # JWT + OAuth2 인증
│   └── Config/            # Spring 설정

Frontend/
├── src/
│   ├── components/        # React 컴포넌트
│   │   ├── auth/         # 로그인/회원가입 폼
│   │   ├── room/         # 방 관리
│   │   ├── menu/         # 메뉴 투표 시스템
│   │   └── common/       # 공유 컴포넌트
│   ├── contexts/         # React 컨텍스트 프로바이더
│   ├── services/         # API 및 WebSocket 클라이언트
│   └── types/            # TypeScript 정의
```

## 🆕 **최근 업데이트 (v2.0.0)**

- 🔐 **Google OAuth2 로그인 완전 구현**
- 🚀 **늦은 참가자 지원 시스템 개선**
- 🔄 **실시간 WebSocket 메시지 동기화 강화**
- 🛡️ **보안 설정 전면 개선**
- 🧪 **포괄적인 OAuth2 테스트 추가**
- 📦 **Docker 멀티스테이지 빌드 최적화**

## 📄 **라이선스**

이 프로젝트는 MIT 라이선스 하에 있습니다 - 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

## 🐛 **문제 및 지원**

버그를 발견했거나 도움이 필요하신가요?
- 📋 **이슈**: GitHub에서 이슈를 생성해주세요
- 💬 **토론**: 질문은 GitHub Discussions를 이용해주세요
- 📧 **보안**: 보안 문제는 비공개로 신고해주세요

## 🏆 **크레딧**

사용 기술 스택:
- **백엔드**: Spring Boot, Spring Security, OAuth2, Redis, WebSocket
- **프론트엔드**: React, TypeScript, Vite, Tailwind CSS
- **데이터베이스**: H2 (개발용), PostgreSQL (운영용)
- **실시간**: WebSocket을 통한 STOMP
- **인증**: JWT + Google OAuth2
