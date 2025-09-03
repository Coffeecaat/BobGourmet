import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { authAPI } from '../../services/api';
import toast from 'react-hot-toast';

interface ResendVerificationProps {
  onSuccess?: () => void;
  onCancel?: () => void;
}

interface ResendFormData {
  email: string;
}

export const ResendVerification: React.FC<ResendVerificationProps> = ({ onSuccess, onCancel }) => {
  const [isLoading, setIsLoading] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm<ResendFormData>();

  const onSubmit = async (data: ResendFormData) => {
    try {
      setIsLoading(true);
      await authAPI.resendVerificationEmail(data.email);
      
      toast.success('Verification email sent successfully! Please check your inbox and spam folder.', {
        duration: 6000,
        position: 'top-center',
        style: {
          background: '#10B981',
          color: '#fff',
          fontWeight: 'bold',
          fontSize: '14px',
          padding: '16px 20px',
          borderRadius: '8px',
        }
      });
      
      onSuccess?.();
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to resend verification email';
      toast.error(errorMessage, {
        duration: 4000,
        position: 'top-center',
        style: {
          background: '#DC2626',
          color: '#fff',
          fontWeight: 'bold',
          fontSize: '14px',
          padding: '16px 20px',
          borderRadius: '8px',
        }
      });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="mt-4 p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
      <div className="flex items-center mb-3">
        <svg className="h-5 w-5 text-yellow-400 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L5.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
        </svg>
        <h4 className="text-sm font-medium text-yellow-800">Email Verification Required</h4>
      </div>
      
      <p className="text-sm text-yellow-700 mb-4">
        Your account needs email verification before you can log in. Enter your email address to receive a new verification link.
      </p>
      
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-3">
        <div>
          <label className="block text-xs font-medium text-yellow-800 mb-1">
            Email Address
          </label>
          <input
            {...register('email', { 
              required: 'Email is required',
              pattern: {
                value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                message: 'Please enter a valid email address'
              }
            })}
            type="email"
            className="w-full px-3 py-2 border border-yellow-300 rounded-md focus:outline-none focus:ring-2 focus:ring-yellow-500 text-sm bg-white"
            placeholder="Enter your email address"
          />
          {errors.email && (
            <p className="text-red-600 text-xs mt-1">{errors.email.message}</p>
          )}
        </div>
        
        <div className="flex space-x-2">
          <button
            type="submit"
            disabled={isLoading}
            className="flex-1 bg-yellow-600 text-white py-2 px-3 rounded-md hover:bg-yellow-700 focus:outline-none focus:ring-2 focus:ring-yellow-500 disabled:opacity-50 text-sm font-medium"
          >
            {isLoading ? 'Sending...' : 'Resend Email'}
          </button>
          
          {onCancel && (
            <button
              type="button"
              onClick={onCancel}
              className="px-3 py-2 text-yellow-700 hover:text-yellow-900 text-sm font-medium"
            >
              Cancel
            </button>
          )}
        </div>
      </form>
      
      <div className="mt-3 text-xs text-yellow-600">
        💡 <strong>Tip:</strong> Check your spam/junk folder if you don't see the email in your inbox.
      </div>
    </div>
  );
};

export default ResendVerification;