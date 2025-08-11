import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { User, LoginRequest, SignupRequest } from '../types';
import { authAPI } from '../services/api';
import toast from 'react-hot-toast';

interface AuthContextType {
  user: User | null;
  token: string | null;
  login: (data: LoginRequest) => Promise<void>;
  loginWithOAuth: (code: string, state?: string | null) => Promise<void>;
  signup: (data: SignupRequest) => Promise<void>;
  logout: () => void;
  isLoading: boolean;
  isTokenValid: () => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
};

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Function to check if token is valid (not expired)
  const isTokenValid = () => {
    const token = localStorage.getItem('token');
    if (!token) return false;
    
    try {
      // Decode JWT token to check expiration
      const payload = JSON.parse(atob(token.split('.')[1]));
      const currentTime = Date.now() / 1000;
      return payload.exp > currentTime;
    } catch (error) {
      return false;
    }
  };

  useEffect(() => {
    const savedToken = localStorage.getItem('token');
    const savedUser = localStorage.getItem('user');
    
    if (savedToken && savedUser && isTokenValid()) {
      setToken(savedToken);
      setUser(JSON.parse(savedUser));
    } else if (savedToken || savedUser) {
      // Token exists but is invalid, clear storage
      localStorage.removeItem('token');
      localStorage.removeItem('user');
    }
    setIsLoading(false);
  }, []);

  const login = async (data: LoginRequest) => {
    try {
      setIsLoading(true);
      const response = await authAPI.login(data);
      
      console.log('Login response:', response); // Debug log
      
      setToken(response.accessToken);
      
      // Create user object from login data since backend might not return user info
      const user: User = {
        username: data.username,
        email: '' // We don't have email from login, could fetch from API later
      };
      
      setUser(user);
      
      localStorage.setItem('token', response.accessToken);
      localStorage.setItem('user', JSON.stringify(user));
      
      toast.success('Login successful!', {
        duration: 4000, // Show success for 4 seconds
      });
      
      setIsLoading(false); // Clear loading on success
    } catch (error: any) {
      console.error('Login error:', error); // Debug log
      const errorMessage = error.response?.data?.message || 'Login failed';
      
      setIsLoading(false);
      
      // Show toast immediately for better user experience
      toast.error(errorMessage, {
        duration: 4000,
        position: 'top-center',
        style: {
          background: '#DC2626',
          color: '#fff',
          fontWeight: 'bold',
          fontSize: '20px',
          padding: '30px 40px',
          borderRadius: '16px',
          border: '5px solid #EF4444',
          boxShadow: '0 25px 30px -5px rgba(0, 0, 0, 0.2), 0 15px 15px -5px rgba(0, 0, 0, 0.1)',
          zIndex: 999999,
          minWidth: '500px',
          maxWidth: '700px',
          lineHeight: '1.6',
          textAlign: 'center',
        },
      });
      
      // DON'T throw the error - this might be causing re-renders that dismiss toasts
      // throw error;
    }
  };

  const signup = async (data: SignupRequest) => {
    try {
      setIsLoading(true);
      const response = await authAPI.signup(data);
      
      toast.success(response.message || 'Account created successfully! Please login.', {
        duration: 4000, // Show success for 4 seconds
      });
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Signup failed', {
        duration: 8000, // Show error for 8 seconds
        style: {
          background: '#DC2626',
          color: '#fff',
          fontWeight: 'bold',
          fontSize: '15px',
          padding: '16px 20px',
          borderRadius: '8px',
          border: '2px solid #EF4444',
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
          zIndex: 10000,
        },
      });
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const loginWithOAuth = async (code: string, state?: string | null) => {
    try {
      setIsLoading(true);
      
      // Send OAuth code to backend for token exchange
      const response = await authAPI.loginWithGoogle(code, state);
      
      console.log('OAuth login response:', response);
      
      setToken(response.accessToken);
      
      // Create user object from OAuth response
      const user: User = {
        username: response.username || response.email,
        email: response.email || ''
      };
      
      setUser(user);
      
      localStorage.setItem('token', response.accessToken);
      localStorage.setItem('user', JSON.stringify(user));
      
      toast.success('Google login successful!', {
        duration: 4000,
      });
      
      setIsLoading(false);
    } catch (error: any) {
      console.error('OAuth login error:', error);
      const errorMessage = error.response?.data?.message || 'Google login failed';
      
      setIsLoading(false);
      
      toast.error(errorMessage, {
        duration: 4000,
        position: 'top-center',
        style: {
          background: '#DC2626',
          color: '#fff',
          fontWeight: 'bold',
          fontSize: '20px',
          padding: '30px 40px',
          borderRadius: '16px',
          border: '5px solid #EF4444',
          boxShadow: '0 25px 30px -5px rgba(0, 0, 0, 0.2), 0 15px 15px -5px rgba(0, 0, 0, 0.1)',
          zIndex: 999999,
          minWidth: '500px',
          maxWidth: '700px',
          lineHeight: '1.6',
          textAlign: 'center',
        },
      });
      
      throw error;
    }
  };

  const logout = () => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    toast.success('Logged out successfully', {
      duration: 3000, // Show logout success for 3 seconds
    });
  };

  const value: AuthContextType = {
    user,
    token,
    login,
    loginWithOAuth,
    signup,
    logout,
    isLoading,
    isTokenValid,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};