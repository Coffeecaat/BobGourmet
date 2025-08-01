import React from 'react';
import { useForm } from 'react-hook-form';
import { useRoom } from '../../contexts/RoomContext';

interface CreateRoomFormData {
  roomName: string;
  maxUsers: number;
  isPrivate: boolean;
  password?: string;
}

interface CreateRoomFormProps {
  onCancel: () => void;
}

export const CreateRoomForm: React.FC<CreateRoomFormProps> = ({ onCancel }) => {
  const { createRoom, isLoading } = useRoom();
  const { register, handleSubmit, watch, formState: { errors } } = useForm<CreateRoomFormData>({
    defaultValues: {
      roomName: '',
      maxUsers: 4,
      isPrivate: false,
    }
  });

  const isPrivate = watch('isPrivate');

  const onSubmit = async (data: CreateRoomFormData) => {
    try {
      await createRoom(data.roomName, data.maxUsers, data.isPrivate, data.password);
    } catch (error) {
      // Error handling is done in RoomContext
    }
  };

  return (
    <div className="max-w-md mx-auto bg-white p-8 rounded-lg shadow-md">
      <h2 className="text-2xl font-bold text-center mb-6">Create Room</h2>
      
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Room Name
          </label>
          <input
            {...register('roomName', { 
              required: 'Room name is required',
              minLength: { value: 2, message: 'Room name must be at least 2 characters' },
              maxLength: { value: 20, message: 'Room name must be less than 20 characters' }
            })}
            type="text"
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="Enter room name"
          />
          {errors.roomName && (
            <p className="text-red-500 text-sm mt-1">{errors.roomName.message}</p>
          )}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Maximum Users
          </label>
          <select
            {...register('maxUsers', { 
              required: 'Maximum users is required',
              min: { value: 2, message: 'Minimum 2 users required' },
              max: { value: 10, message: 'Maximum 10 users allowed' }
            })}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {[2, 3, 4, 5, 6, 7, 8, 9, 10].map(num => (
              <option key={num} value={num}>{num} users</option>
            ))}
          </select>
          {errors.maxUsers && (
            <p className="text-red-500 text-sm mt-1">{errors.maxUsers.message}</p>
          )}
        </div>

        <div className="flex items-center">
          <input
            {...register('isPrivate')}
            type="checkbox"
            id="isPrivate"
            className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
          />
          <label htmlFor="isPrivate" className="ml-2 block text-sm text-gray-700">
            Private Room (requires password)
          </label>
        </div>

        {isPrivate && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Room Password
            </label>
            <input
              {...register('password', { 
                required: isPrivate ? 'Password is required for private rooms' : false
              })}
              type="password"
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Enter room password"
            />
            {errors.password && (
              <p className="text-red-500 text-sm mt-1">{errors.password.message}</p>
            )}
          </div>
        )}

        <div className="flex space-x-4">
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 bg-gray-300 text-gray-700 py-2 px-4 rounded-md hover:bg-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-500"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isLoading}
            className="flex-1 bg-blue-600 text-white py-2 px-4 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          >
            {isLoading ? 'Creating...' : 'Create Room'}
          </button>
        </div>
      </form>
    </div>
  );
};