import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { useAuth } from '../../contexts/AuthContext';
import { SignupRequest } from '../../types';
import { authAPI } from '../../services/api';
import toast from 'react-hot-toast';

interface SignupFormProps {
  onSwitchToLogin: () => void;
}

interface SignupFormData extends Omit<SignupRequest, 'email'> {
  emailUsername: string;
  emailDomain: string;
  confirmPassword: string;
}

export const SignupForm: React.FC<SignupFormProps> = ({ onSwitchToLogin }) => {
  const { signup, isLoading } = useAuth();
  const { register, handleSubmit, formState: { errors }, watch, setValue } = useForm<SignupFormData>();
  const [allowedDomains, setAllowedDomains] = useState<string[]>([]);
  const [domainsLoading, setDomainsLoading] = useState(true);
  const [preVerificationStatus, setPreVerificationStatus] = useState<'none' | 'sending' | 'sent' | 'verified'>('none');
  const [sendingPreVerification, setSendingPreVerification] = useState(false);
  const [checkingPreVerification, setCheckingPreVerification] = useState(false);

  const password = watch('password');
  const emailUsername = watch('emailUsername');
  const emailDomain = watch('emailDomain');

  // Set allowed domains directly (no API call needed)
  useEffect(() => {
    const domains = [
      'gmail.com',
      'naver.com', 
      'daum.net',
      'kakao.com',
      'nate.com',
      'hanmail.net',
      'yahoo.com',
      'hotmail.com',
      'outlook.com'
    ];
    
    setAllowedDomains(domains);
    // Set default domain to first available domain
    setValue('emailDomain', domains[0]);
    setDomainsLoading(false);
  }, [setValue]);

  const getFullEmail = () => {
    if (emailUsername && emailDomain) {
      return `${emailUsername}@${emailDomain}`;
    }
    return '';
  };

  const sendPreVerification = async () => {
    const fullEmail = getFullEmail();
    if (!fullEmail) {
      toast.error('Please enter a valid email address first');
      return;
    }

    setSendingPreVerification(true);
    try {
      await authAPI.sendPreVerification(fullEmail);
      setPreVerificationStatus('sent');
      toast.success('Pre-verification email sent! Check your inbox and click the link.');
    } catch (error: any) {
      toast.error(error.response?.data || 'Failed to send pre-verification email');
    } finally {
      setSendingPreVerification(false);
    }
  };

  const checkPreVerificationStatus = async () => {
    const fullEmail = getFullEmail();
    if (!fullEmail) {
      toast.error('Please enter a valid email address first');
      return;
    }

    setCheckingPreVerification(true);
    try {
      await authAPI.checkPreVerification(fullEmail);
      setPreVerificationStatus('verified');
      toast.success('Email verified! You can now complete your signup.');
    } catch (error: any) {
      toast.error('Email not yet verified. Please check your inbox and click the verification link.');
      setPreVerificationStatus('sent'); // Reset to sent status
    } finally {
      setCheckingPreVerification(false);
    }
  };

  const onSubmit = async (data: SignupFormData) => {
    try {
      // Combine email username and domain
      const fullEmail = `${data.emailUsername}@${data.emailDomain}`;
      
      await signup({ 
        username: data.username, 
        email: fullEmail, 
        nickname: data.nickname,
        password: data.password 
      });
      
      // Show success message and switch to login
      toast.success('Account created successfully! You can now login.');
      onSwitchToLogin();
    } catch (error) {
      // Stay on signup form - error handling is done in AuthContext with toast
      // No redirect, user stays on signup page to fix issues
    }
  };

  return (
    <div className="max-w-md mx-auto bg-white p-8 rounded-lg shadow-md">
      <h2 className="text-2xl font-bold text-center mb-6">Join BobGourmet</h2>
      
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Username
          </label>
          <input
            {...register('username', { 
              required: 'Username is required',
              minLength: { value: 3, message: 'Username must be at least 3 characters' }
            })}
            type="text"
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="Choose a username"
          />
          {errors.username && (
            <p className="text-red-500 text-sm mt-1">{errors.username.message}</p>
          )}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Email Address
          </label>
          <div className="flex gap-1">
            <div className="flex-1">
              <input
                {...register('emailUsername', { 
                  required: 'Email username is required',
                  pattern: { 
                    value: /^[a-zA-Z0-9._-]+$/, 
                    message: 'Only letters, numbers, dots, hyphens and underscores allowed' 
                  },
                  minLength: { value: 1, message: 'Username cannot be empty' }
                })}
                type="text"
                className="w-full px-3 py-2 border border-gray-300 rounded-l-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                placeholder="username"
              />
            </div>
            <span className="flex items-center px-2 bg-gray-50 border-t border-b border-gray-300 text-gray-500">
              @
            </span>
            <div className="flex-1">
              <select
                {...register('emailDomain', { required: 'Please select an email domain' })}
                disabled={domainsLoading}
                className="w-full px-3 py-2 border border-gray-300 rounded-r-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 bg-white disabled:bg-gray-100"
              >
                {domainsLoading ? (
                  <option>Loading...</option>
                ) : (
                  allowedDomains.map((domain) => (
                    <option key={domain} value={domain}>
                      {domain}
                    </option>
                  ))
                )}
              </select>
            </div>
          </div>
          {(errors.emailUsername || errors.emailDomain) && (
            <p className="text-red-500 text-sm mt-1">
              {errors.emailUsername?.message || errors.emailDomain?.message}
            </p>
          )}
          {emailUsername && emailDomain && (
            <p className="text-gray-500 text-sm mt-1">
              Email: <span className="font-medium">{emailUsername}@{emailDomain}</span>
            </p>
          )}
          
          {/* Email verification section - Required for signup */}
          {emailUsername && emailDomain && (
            <div className="mt-2 space-y-2">
              {preVerificationStatus === 'none' && (
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={sendPreVerification}
                    disabled={sendingPreVerification}
                    className="px-3 py-1 text-xs bg-red-600 text-white rounded hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {sendingPreVerification ? 'Sending...' : 'Verify Email'}
                  </button>
                  <span className="text-xs text-red-600 flex items-center">
                    ⚠️ Required: Verify email before signup
                  </span>
                </div>
              )}
              
              {preVerificationStatus === 'sent' && (
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={checkPreVerificationStatus}
                    disabled={checkingPreVerification}
                    className="px-3 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {checkingPreVerification ? 'Checking...' : 'Check Status'}
                  </button>
                  <span className="text-xs text-orange-600 flex items-center">
                    📧 Check your inbox and click the verification link
                  </span>
                </div>
              )}
              
              {preVerificationStatus === 'verified' && (
                <div className="flex gap-2">
                  <span className="px-3 py-1 text-xs bg-green-100 text-green-800 rounded font-medium">
                    ✅ Verified
                  </span>
                  <span className="text-xs text-green-600 flex items-center">
                    Email verified! Ready to complete signup.
                  </span>
                </div>
              )}
            </div>
          )}
          
          {!domainsLoading && allowedDomains.length > 0 && (
            <p className="text-blue-600 text-xs mt-1">
              Only verified email domains are allowed for security
            </p>
          )}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Nickname
          </label>
          <input
            {...register('nickname', { 
              required: 'Nickname is required',
              minLength: { value: 3, message: 'Nickname must be at least 3 characters' },
              maxLength: { value: 20, message: 'Nickname must be less than 20 characters' }
            })}
            type="text"
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="Choose a nickname"
          />
          {errors.nickname && (
            <p className="text-red-500 text-sm mt-1">{errors.nickname.message}</p>
          )}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Password
          </label>
          <input
            {...register('password', { 
              required: 'Password is required',
              minLength: { value: 6, message: 'Password must be at least 6 characters' }
            })}
            type="password"
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="Create a password"
          />
          {errors.password && (
            <p className="text-red-500 text-sm mt-1">{errors.password.message}</p>
          )}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Confirm Password
          </label>
          <input
            {...register('confirmPassword', { 
              required: 'Please confirm your password',
              validate: value => value === password || 'Passwords do not match'
            })}
            type="password"
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="Confirm your password"
          />
          {errors.confirmPassword && (
            <p className="text-red-500 text-sm mt-1">{errors.confirmPassword.message}</p>
          )}
        </div>

        <button
          type="submit"
          disabled={isLoading || domainsLoading || preVerificationStatus !== 'verified'}
          className="w-full bg-blue-600 text-white py-2 px-4 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {isLoading ? (
            <span className="flex items-center justify-center">
              <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              Creating Account...
            </span>
          ) : domainsLoading ? (
            'Loading Email Options...'
          ) : preVerificationStatus !== 'verified' ? (
            'Verify Email First'
          ) : (
            'Sign Up'
          )}
        </button>
      </form>

      <div className="mt-4 p-3 bg-blue-50 rounded-md border border-blue-200">
        <p className="text-blue-800 text-xs text-center">
          {preVerificationStatus === 'verified' 
            ? '🎉 Email verified! You can signup and login immediately.'
            : '📧 Please verify your email address above before completing signup.'}
        </p>
      </div>

      <p className="text-center mt-4 text-sm text-gray-600">
        Already have an account?{' '}
        <button
          onClick={onSwitchToLogin}
          className="text-blue-600 hover:text-blue-800 font-medium"
        >
          Log in
        </button>
      </p>
    </div>
  );
};