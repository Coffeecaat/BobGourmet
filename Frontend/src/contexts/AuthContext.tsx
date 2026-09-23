import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { User, LoginRequest, SignupRequest } from '../types';
import { authAPI } from '../services/api';
import toast from 'react-hot-toast';

interface AuthContextType {
  user: User | null;
  login: (data: LoginRequest) => Promise<void>;
  signup: (data: SignupRequest) => Promise<void>;
  logout: () => void;
  isLoading: boolean;
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
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // Restore display identity only; protected API/CONNECT requests verify the cookie.
    // The current backend has no current-user endpoint.
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      // Try to get user data from localStorage first (for persistence)
      const savedUser = localStorage.getItem('user');
      if (savedUser) {
        setUser(JSON.parse(savedUser));
      }

      // The cookie will be automatically sent with requests
      // If the cookie is invalid/expired, the API will return 401
      // and our interceptor will handle the redirect
      setIsLoading(false);
    } catch (error) {
      // If there's any error, clear user data
      localStorage.removeItem('user');
      setUser(null);
      setIsLoading(false);
    }
  };

  const login = async (data: LoginRequest) => {
    try {
      setIsLoading(true);
      await authAPI.login(data);

      // Backend sets HttpOnly cookie automatically, no token in response
      // Create user object from login data
      const user: User = {
        username: data.username,
        email: '' // We don't have email from login, could fetch from API later
      };

      setUser(user);

      // Only store user data, not token (it's in HttpOnly cookie)
      localStorage.setItem('user', JSON.stringify(user));

      toast.success('Login successful!', {
        duration: 4000, // Show success for 4 seconds
      });

      setIsLoading(false); // Clear loading on success
    } catch (error: any) {
      console.error('Login error:', error); // Debug log
      console.error('Error response:', error.response); // Debug error response
      console.error('Error response data:', JSON.stringify(error.response?.data, null, 2)); // Debug response data
      console.error('Error status:', error.response?.status); // Debug status
      const errorMessage = error.response?.data?.message || 'Login failed';

      setIsLoading(false);

      // Always show toast for all login errors including email verification
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

      // Always re-throw error so LoginForm can handle it
      throw error;
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

  const logout = async () => {
    try {
      // Call logout API to clear HttpOnly cookie on server
      await authAPI.logout();
    } catch (error) {
      console.error('Logout API call failed:', error);
      // Continue with frontend cleanup even if API call fails
    }

    setUser(null);
    localStorage.removeItem('user'); // Only remove user data, cookie is handled by server

    toast.success('Logged out successfully', {
      duration: 3000, // Show logout success for 3 seconds
    });
  };

  const value: AuthContextType = {
    user,
    login,
    signup,
    logout,
    isLoading,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
