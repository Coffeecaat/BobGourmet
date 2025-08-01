# BobGourmet Client

React TypeScript client for the BobGourmet restaurant selection application.

## Features

- 🔐 **JWT Authentication** - Login/Signup with secure token-based auth
- 🏠 **Room Management** - Create and join voting rooms with password protection
- 🍽️ **Menu Submission** - Submit restaurant menu options for voting
- 🗳️ **Real-time Voting** - Vote on menu options with live updates
- 🎲 **Random Selection** - Automated draw system for final restaurant choice
- 🔄 **WebSocket Integration** - Real-time updates using STOMP protocol
- 📱 **Responsive Design** - Works on desktop and mobile devices

## Tech Stack

- **React 18** with TypeScript
- **Vite** for fast development and building
- **Tailwind CSS** for styling
- **React Hook Form** for form management
- **React Query** for server state management
- **STOMP.js** for WebSocket communication
- **Axios** for HTTP requests
- **React Hot Toast** for notifications
- **Lucide React** for icons

## Prerequisites

- Node.js 16+ and npm
- BobGourmet Spring Boot backend running on localhost:8080

## Installation

1. Navigate to the client directory:
```bash
cd bobgourmet-client
```

2. Install dependencies:
```bash
npm install
```

3. **Security Setup (REQUIRED)**:
```bash
# Copy environment template
cp .env.example .env

# Edit .env file with your backend URLs
# See SECURITY.md for detailed instructions
```

⚠️ **CRITICAL**: Read `SECURITY.md` before running the application!

4. Start the development server:
```bash
npm run dev
```

5. Open http://localhost:5173 in your browser

## Project Structure

```
src/
├── components/          # React components
│   ├── auth/           # Login/Signup forms
│   ├── room/           # Room management components
│   ├── menu/           # Menu submission and voting
│   └── common/         # Shared components
├── contexts/           # React contexts for state management
├── services/           # API and WebSocket services
├── types/              # TypeScript type definitions
└── App.tsx            # Main application component
```

## API Integration

The client integrates with the BobGourmet Spring Boot backend:

- **Authentication**: `/api/auth/login`, `/api/auth/signup`
- **Room Management**: `/api/rooms/create`, `/api/rooms/join`, `/api/rooms/leave`
- **Menu System**: `/api/menus/submit`, `/api/menus/vote`, `/api/menus/draw`
- **WebSocket**: `/ws-BobGourmet` with STOMP protocol

## Development

- **Start dev server**: `npm run dev`
- **Build for production**: `npm run build`
- **Preview production build**: `npm run preview`
- **Type checking**: `npm run lint`

## Configuration

The Vite configuration includes proxy settings for the backend:
- API calls to `/api/*` are proxied to `http://localhost:8080`
- WebSocket connections to `/ws-BobGourmet` are proxied for development

## Usage Flow

1. **Sign up/Login** with username, email, and password
2. **Create Room** or **Join Room** from existing rooms on the list
3. **Wait for users** to join the room
4. **Submit menu** options when the room starts
5. **Vote** on submitted menus to exclude unwanted options -> not available yet
6. **View results** when the random draw selects the winning menu
7. **Room resets** automatically for another round ->not available yet

## Contributing

This client is designed to work seamlessly with the BobGourmet Spring Boot backend. Make sure both applications are running for full functionality.
