import React, { createContext, useContext, useState, useEffect, useSyncExternalStore, ReactNode } from 'react';
import { User, LoginRequest, SignupRequest } from '../types';
import { authAPI } from '../services/api';
import { createAuthSession, authErrorMessage } from '../services/authSession';
import toast from 'react-hot-toast';

interface AuthContextType {
  user: User | null;
  login: (data: LoginRequest) => Promise<void>;
  signup: (data: SignupRequest) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<User | null>;
  isLoading: boolean;
  authError: string | null;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [session] = useState(() => createAuthSession(authAPI, {
    removeItem: (key: string) => window.localStorage.removeItem(key),
  }));
  const state = useSyncExternalStore(session.subscribe, session.getSnapshot, session.getSnapshot);

  useEffect(() => { void session.restore(); }, [session]);

  const login = async (data: LoginRequest): Promise<void> => {
    try {
      const user: User | null = await session.login(data);
      if (user) toast.success('Login successful!', { duration: 4000 });
    } catch (error: unknown) {
      toast.error(authErrorMessage(error, '로그인을 완료하지 못했습니다. 인증 쿠키와 연결 상태를 확인해주세요.'));
      throw error;
    }
  };

  const signup = async (data: SignupRequest): Promise<void> => {
    try {
      const response = await authAPI.signup(data);
      toast.success(response.message || 'Account created successfully! Please login.', { duration: 4000 });
    } catch (error: unknown) {
      toast.error(authErrorMessage(error, 'Signup failed'), { duration: 8000 });
      throw error;
    }
  };

  const logout = async (): Promise<void> => {
    try {
      if (await session.logout()) toast.success('Logged out successfully', { duration: 3000 });
    } catch {
      // The HttpOnly cookie may still be valid. Do not claim logout succeeded on a network failure.
      toast.error('서버에서 로그아웃하지 못했습니다. 다시 시도해주세요.');
    }
  };

  return (
    <AuthContext.Provider value={{ user: state.user, isLoading: state.isLoading,
      authError: state.error, refreshUser: session.restore, login, signup, logout }}>
      {children}
    </AuthContext.Provider>
  );
};
