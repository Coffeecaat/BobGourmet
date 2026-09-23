import axios from 'axios';
import { AuthResponse, User, LoginRequest, SignupRequest, CreateRoomRequest, MenuSubmission, Room, MenuStatus } from '../types';
import { normalizeRoom, normalizeMenuStatus } from './roomState';

// Use relative URLs when served through proxy, absolute URLs for development
const API_BASE = import.meta.env.VITE_API_BASE_URL
  ? `${import.meta.env.VITE_API_BASE_URL.replace(/\/$/, '')}/api` : '/api';

const api = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // Include cookies in requests
});

// Cookie-based authentication - no need to add Authorization headers
// HttpOnly cookies are automatically sent by the browser
api.interceptors.request.use((config) => {
  // No Authorization header needed - cookies are sent automatically
  return config;
});

// Response interceptor to handle authentication errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    // Expected authentication failures are handled by their callers, not by a page redirect.
    const isAuthRequest = ['/auth/login', '/auth/register', '/auth/me', '/auth/logout']
      .includes(error.config?.url?.split('?')[0]);

    // A forbidden room/menu action does not mean the authentication cookie expired.
    if (error.response?.status === 401 && !isAuthRequest) {
      // Cookie is expired or invalid (but not a login failure)
      localStorage.removeItem('user'); // Only remove user data, not token (it's in cookie)
      localStorage.removeItem('bobgourmet_current_room'); // Clear room state too

      // Show toast message before redirect
      if (typeof window !== 'undefined') {
        // Use dynamic import to avoid dependency issues
        import('react-hot-toast').then(({ default: toast }) => {
          toast.error('Session expired. Please login again.', {
            duration: 6000, // Show session expiry for 6 seconds
          });
        }).catch(() => {
          // Fallback if toast is not available
          console.warn('Session expired. Please login again.');
        });
      }

      // Small delay to allow toast to show before redirect
      setTimeout(() => {
        window.location.href = '/';
      }, 100);
    }
    return Promise.reject(error);
  }
);

// Auth endpoints
export const authAPI = {
  login: (data: LoginRequest): Promise<AuthResponse> =>
    api.post('/auth/login', data).then(res => res.data),

  currentUser: (): Promise<User> =>
    api.get<User>('/auth/me', { timeout: 10000 }).then(res => res.data),

  signup: (data: SignupRequest): Promise<{ message: string }> =>
    api.post('/auth/register', data).then(res => ({ message: res.data })),

  verifyEmail: (token: string): Promise<{ message: string }> =>
    api.get(`/auth/verify-email?token=${encodeURIComponent(token)}`).then(res => ({ message: res.data })),

  resendVerificationEmail: (email: string): Promise<{ message: string }> =>
    api.post('/auth/resend-verification', { email }).then(res => ({ message: res.data })),

  resendVerificationByUsername: (username: string): Promise<{ message: string }> =>
    api.post('/auth/resend-verification', { username }).then(res => ({ message: res.data })),

  sendPreVerification: (email: string): Promise<{ message: string }> =>
    api.post(`/auth/send-pre-verification?email=${encodeURIComponent(email)}`).then(res => ({ message: res.data })),

  verifyPreVerification: (token: string): Promise<{ message: string }> =>
    api.get(`/auth/verify-pre-verification?token=${encodeURIComponent(token)}`).then(res => ({ message: res.data })),

  checkPreVerification: (email: string): Promise<{ message: string }> =>
    api.get(`/auth/check-pre-verification?email=${encodeURIComponent(email)}`).then(res => ({ message: res.data })),

  logout: (): Promise<{ message: string }> =>
    api.post('/auth/logout').then(res => ({ message: res.data })),
};

// Room endpoints
export const roomAPI = {
  getAllActiveRooms: (): Promise<Room[]> =>
    api.get<Room[]>('/MatchRooms').then(res => res.data.map(normalizeRoom)),

  createRoom: (data: CreateRoomRequest): Promise<Room> =>
    api.post<Room>('/MatchRooms', {
      roomName: data.roomName, maxUsers: data.maxUsers, private: data.isPrivate, password: data.password,
    }).then(res => normalizeRoom(res.data)),

  joinRoom: (roomId: string, password?: string): Promise<Room> =>
    api.post<Room>(`/MatchRooms/${roomId}/join`, password ? { password } : {}).then(res => normalizeRoom(res.data)),

  leaveRoom: (roomId: string) =>
    api.post(`/MatchRooms/${roomId}/leave`).then(res => res.data),

  getRoomInfo: (roomId: string): Promise<Room> =>
    api.get<Room>(`/MatchRooms/${roomId}`).then(res => normalizeRoom(res.data)),
};

// Menu endpoints
export const menuAPI = {
  submitMenu: (roomId: string, data: MenuSubmission): Promise<MenuStatus> =>
    api.post<MenuStatus>(`/MatchRooms/${roomId}/menus`, data).then(res => normalizeMenuStatus(res.data)),

  recommendMenu: (roomId: string, menuKey: string): Promise<MenuStatus> =>
    api.post<MenuStatus>(`/MatchRooms/${roomId}/menus/${encodeURIComponent(menuKey)}/recommend`).then(res => normalizeMenuStatus(res.data)),

  dislikeMenu: (roomId: string, menuKey: string): Promise<MenuStatus> =>
    api.post<MenuStatus>(`/MatchRooms/${roomId}/menus/${encodeURIComponent(menuKey)}/dislike`).then(res => normalizeMenuStatus(res.data)),

  startDraw: (roomId: string): Promise<Room> =>
    api.post<Room>(`/MatchRooms/${roomId}/start-draw`).then(res => normalizeRoom(res.data)),

  resetRoom: (roomId: string): Promise<Room> =>
    api.post<Room>(`/MatchRooms/${roomId}/reset`).then(res => normalizeRoom(res.data)),
};
