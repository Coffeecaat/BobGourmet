import axios from 'axios';
import { AuthResponse, LoginRequest, SignupRequest, CreateRoomRequest, MenuSubmission } from '../types';

const API_BASE = (import.meta as any).env?.VITE_API_BASE_URL ? `${(import.meta as any).env.VITE_API_BASE_URL}/api` : '/api';

const api = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add token to requests (except auth endpoints)
api.interceptors.request.use((config) => {
  // Don't add token to authentication endpoints
  const authEndpoints = ['/auth/login', '/auth/register', '/auth/oauth'];
  const isAuthEndpoint = authEndpoints.some(endpoint => config.url?.includes(endpoint));
  
  if (!isAuthEndpoint) {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

// Response interceptor to handle token expiration
api.interceptors.response.use(
  (response) => response,
  (error) => {
    // Check if this is a login error - don't redirect on login failures
    const isLoginError = error.config?.url?.includes('/auth/login') || 
                         error.config?.url?.includes('/auth/register') ||
                         error.config?.url?.includes('/auth/oauth');
    
    if ((error.response?.status === 401 || error.response?.status === 403) && !isLoginError) {
      // Token is expired or invalid (but not a login failure)
      localStorage.removeItem('token');
      localStorage.removeItem('user');
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
  
  loginWithGoogle: (code: string, state?: string | null): Promise<AuthResponse> =>
    api.post('/auth/oauth/google', { code, state }).then(res => res.data),
  
  signup: (data: SignupRequest): Promise<{ message: string }> =>
    api.post('/auth/register', data).then(res => ({ message: res.data })),
};

// Room endpoints
export const roomAPI = {
  getAllActiveRooms: () =>
    api.get('/MatchRooms').then(res => res.data),

  createRoom: (data: CreateRoomRequest) =>
    api.post('/MatchRooms', data).then(res => res.data),
  
  joinRoom: (roomId: string, password?: string) =>
    api.post(`/MatchRooms/${roomId}/join`, password ? { password } : {}).then(res => res.data),
  
  leaveRoom: (roomId: string) =>
    api.post(`/MatchRooms/${roomId}/leave`).then(res => res.data),
  
  getRoomInfo: (roomId: string) =>
    api.get(`/MatchRooms/${roomId}`).then(res => res.data),
};

// Menu endpoints
export const menuAPI = {
  submitMenu: (roomId: string, data: MenuSubmission) =>
    api.post(`/MatchRooms/${roomId}/menus`, data).then(res => res.data),
  
  recommendMenu: (roomId: string, menuKey: string) =>
    api.post(`/MatchRooms/${roomId}/menus/${menuKey}/recommend`).then(res => res.data),
  
  dislikeMenu: (roomId: string, menuKey: string) =>
    api.post(`/MatchRooms/${roomId}/menus/${menuKey}/dislike`).then(res => res.data),
  
  startDraw: (roomId: string) =>
    api.post(`/MatchRooms/${roomId}/start-draw`).then(res => res.data),
  
  resetRoom: (roomId: string) =>
    api.post(`/MatchRooms/${roomId}/reset`).then(res => res.data),
};