import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useAuth } from '../../contexts/AuthContext';
import { LoginRequest } from '../../types';
import { GoogleOAuthButton } from './GoogleOAuthButton';
import ResendVerification from './ResendVerification';

interface LoginFormProps {
  onSwitchToSignup: () => void;
}

export const LoginForm: React.FC<LoginFormProps> = ({ onSwitchToSignup }) => {
  const { login, isLoading } = useAuth();
  const { register, handleSubmit, formState: { errors } } = useForm<LoginRequest>();
  const [showResendOption, setShowResendOption] = useState(false);

  const onSubmit = async (data: LoginRequest) => {
    try {
      setShowResendOption(false); // Hide resend option on new login attempt
      await login(data);
    } catch (error: any) {
      // Check if the error is specifically about email verification
      const errorMessage = error.response?.data?.message || '';
      if (errorMessage.toLowerCase().includes('email verification') || 
          errorMessage.includes('이메일 인증이 필요합니다')) {
        setShowResendOption(true);
      }
    }
  };

  const handleResendSuccess = () => {
    setShowResendOption(false);
  };

  const handleResendCancel = () => {
    setShowResendOption(false);
  };

  return (
    <div className="max-w-md mx-auto bg-white p-8 rounded-lg shadow-md">
      <h2 className="text-2xl font-bold text-center mb-6">Login to BobGourmet</h2>
      
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Username
          </label>
          <input
            {...register('username', { required: 'Username is required' })}
            type="text"
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="Enter your username"
          />
          {errors.username && (
            <p className="text-red-500 text-sm mt-1">{errors.username.message}</p>
          )}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Password
          </label>
          <input
            {...register('password', { required: 'Password is required' })}
            type="password"
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="Enter your password"
          />
          {errors.password && (
            <p className="text-red-500 text-sm mt-1">{errors.password.message}</p>
          )}
        </div>

        <button
          type="submit"
          disabled={isLoading}
          className="w-full bg-blue-600 text-white py-2 px-4 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
        >
          {isLoading ? 'Logging in...' : 'Login'}
        </button>
      </form>

      {/* Email Verification Resend Section */}
      {showResendOption && (
        <ResendVerification 
          onSuccess={handleResendSuccess}
          onCancel={handleResendCancel}
        />
      )}

      <div className="mt-6">
        <div className="relative">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-gray-300" />
          </div>
          <div className="relative flex justify-center text-sm">
            <span className="px-2 bg-white text-gray-500">Or continue with</span>
          </div>
        </div>

        <div className="mt-6">
          <GoogleOAuthButton
            onSuccess={(credential) => {
              // Handle success if needed
              console.log('Google OAuth success:', credential);
            }}
            onError={() => {
              // Handle error if needed
              console.error('Google OAuth error');
            }}
            disabled={isLoading}
          />
        </div>
      </div>

      <p className="text-center mt-4 text-sm text-gray-600">
        Don't have an account?{' '}
        <button
          onClick={onSwitchToSignup}
          className="text-blue-600 hover:text-blue-800 font-medium"
        >
          Sign up
        </button>
      </p>
    </div>
  );
};