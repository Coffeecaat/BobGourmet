import React, { useEffect } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';

export const OAuthCallback: React.FC = () => {
  const { loginWithOAuth } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    const handleOAuthCallback = async () => {
      try {
        // Get URL parameters
        const urlParams = new URLSearchParams(window.location.search);
        const token = urlParams.get('token');
        const error = urlParams.get('error');

        if (error) {
          console.error('OAuth error:', error);
          toast.error('Google login failed');
          navigate('/');
          return;
        }

        if (token) {
          // Backend has already processed OAuth and returned JWT token
          console.log('Received OAuth token:', token.substring(0, 20) + '...');
          
          // Decode token to get user info
          try {
            if (token.split('.').length !== 3) {
              throw new Error('Invalid JWT format');
            }
            const payload = JSON.parse(atob(token.split('.')[1]));
            console.log('Decoded JWT payload:', payload);
            const user = {
              username: payload.sub,
              email: payload.email || ''
            };
            
            // Store in localStorage
            localStorage.setItem('token', token);
            localStorage.setItem('user', JSON.stringify(user));
            
            // Trigger a page reload to update AuthContext
            toast.success('Google login successful!', {
              duration: 2000,
            });
            
            // Wait a bit for the toast then reload to update AuthContext
            setTimeout(() => {
              window.location.href = localStorage.getItem('oauth_redirect_url') || '/';
              localStorage.removeItem('oauth_redirect_url');
            }, 1000);
            
          } catch (err) {
            console.error('Could not decode token for user info:', err);
            toast.error('Invalid authentication token');
            navigate('/');
            return;
          }
        } else {
          // No token received, redirect to home
          navigate('/');
        }
      } catch (error) {
        console.error('OAuth callback error:', error);
        toast.error('Authentication failed');
        navigate('/');
      }
    };

    handleOAuthCallback();
  }, [navigate]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md w-full space-y-8 p-8">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <h2 className="mt-6 text-lg font-medium text-gray-900">
            Completing Google Sign In...
          </h2>
          <p className="mt-2 text-sm text-gray-600">
            Please wait while we verify your account.
          </p>
        </div>
      </div>
    </div>
  );
};