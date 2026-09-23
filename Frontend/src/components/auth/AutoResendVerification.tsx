import React, { useState } from 'react';
import { authAPI } from '../../services/api';
import toast from 'react-hot-toast';

interface AutoResendVerificationProps {
  username: string;
  onSuccess: () => void;
  onCancel: () => void;
}

const AutoResendVerification: React.FC<AutoResendVerificationProps> = ({ 
  username, 
  onSuccess, 
  onCancel 
}) => {
  const [isResending, setIsResending] = useState(false);

  const handleResend = async () => {
    if (!username) {
      toast.error('Username is required for resending verification');
      return;
    }

    try {
      setIsResending(true);
      
      // Call resend API with the username - backend will handle email lookup
      await authAPI.resendVerificationByUsername(username);
      
      toast.success('Verification email sent successfully!');
      onSuccess();
      
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Failed to resend verification email');
    } finally {
      setIsResending(false);
    }
  };

  return (
    <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mt-4">
      <div className="flex items-start">
        <div className="flex-shrink-0">
          <svg className="h-5 w-5 text-blue-400" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
          </svg>
        </div>
        <div className="ml-3 flex-1">
          <h3 className="text-sm font-medium text-blue-800">
            Email Verification Required
          </h3>
          <div className="mt-2 text-sm text-blue-700">
            <p>Your account needs email verification to log in.</p>
          </div>
          <div className="mt-4 flex space-x-2">
            <button
              onClick={handleResend}
              disabled={isResending}
              className="bg-blue-600 text-white px-4 py-2 text-sm rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
            >
              {isResending ? 'Sending...' : 'Resend Verification Email'}
            </button>
            <button
              onClick={onCancel}
              className="bg-gray-300 text-gray-700 px-4 py-2 text-sm rounded-md hover:bg-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-500"
            >
              Cancel
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AutoResendVerification;
