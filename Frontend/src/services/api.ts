import axios from 'axios';
import { AuthResponse, LoginRequest, SignupRequest, CreateRoomRequest, JoinRoomRequest, MenuSubmission } from '../types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ? `${import.meta.env.VITE_API_BASE_URL}/api` : '/api';

const api = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add token to requests
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Auth endpoints
export const authAPI = {
  login: (data: LoginRequest): Promise<AuthResponse> =>
    api.post('/auth/login', data).then(res => res.data),
  
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