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
        const code = urlParams.get('code');
        const state = urlParams.get('state');
        const error = urlParams.get('error');

        if (error) {
          console.error('OAuth error:', error);
          toast.error('Google login failed');
          navigate('/');
          return;
        }

        if (code) {
          // Exchange code for JWT token via backend
          await loginWithOAuth(code, state);
          
          // Redirect to original page or dashboard
          const redirectUrl = localStorage.getItem('oauth_redirect_url') || '/';
          localStorage.removeItem('oauth_redirect_url');
          navigate(redirectUrl);
        } else {
          // No code received, redirect to home
          navigate('/');
        }
      } catch (error) {
        console.error('OAuth callback error:', error);
        toast.error('Authentication failed');
        navigate('/');
      }
    };

    handleOAuthCallback();
  }, [loginWithOAuth, navigate]);

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