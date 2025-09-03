import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { LoadingSpinner } from '../common/LoadingSpinner';
import { authAPI } from '../../services/api';

const EmailVerification: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('');

  useEffect(() => {
    const verifyEmail = async () => {
      const token = searchParams.get('token');
      
      if (!token) {
        setStatus('error');
        setMessage('Verification token is missing from the URL.');
        return;
      }

      try {
        const response = await authAPI.verifyEmail(token);
        setStatus('success');
        setMessage(response.message || 'Email verified successfully! You can now login to BobGourmet.');
      } catch (error: any) {
        console.error('Verification error:', error);
        setStatus('error');
        const errorMessage = error.response?.data || error.message || 'Email verification failed. The token may be invalid or expired.';
        setMessage(errorMessage);
      }
    };

    verifyEmail();
  }, [searchParams]);

  const handleLoginRedirect = () => {
    window.location.href = '/';
  };

  if (status === 'loading') {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <LoadingSpinner />
          <h2 className="mt-4 text-xl font-semibold text-gray-800">Verifying your email...</h2>
          <p className="mt-2 text-gray-600">Please wait while we verify your account.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="container mx-auto px-4">
        <div className="max-w-md mx-auto bg-white rounded-lg shadow-md p-8 text-center">
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-gray-800 mb-2">🍽️ BobGourmet</h1>
            <h2 className="text-xl font-semibold text-gray-800">Email Verification</h2>
          </div>

          <div className="mb-6">
            {status === 'success' ? (
              <div className="text-green-600">
                <svg className="mx-auto h-16 w-16 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <h3 className="text-lg font-semibold mb-2">Verification Successful!</h3>
              </div>
            ) : (
              <div className="text-red-600">
                <svg className="mx-auto h-16 w-16 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L5.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
                </svg>
                <h3 className="text-lg font-semibold mb-2">Verification Failed</h3>
              </div>
            )}
          </div>

          <div className="mb-6">
            <p className="text-gray-600">{message}</p>
          </div>

          <div className="space-y-3">
            <button
              onClick={handleLoginRedirect}
              className="w-full bg-blue-600 text-white py-3 px-4 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 transition duration-200"
            >
              {status === 'success' ? 'Continue to Login' : 'Back to Login'}
            </button>

            {status === 'error' && (
              <button
                onClick={() => window.location.reload()}
                className="w-full bg-gray-600 text-white py-2 px-4 rounded-md hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-gray-500 transition duration-200"
              >
                Try Again
              </button>
            )}
          </div>

          {status === 'error' && (
            <div className="mt-6 text-sm text-gray-500">
              <p>If you continue to have issues, please contact support or try requesting a new verification email.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default EmailVerification;