import React from 'react';
import { useForm } from 'react-hook-form';
import { useRoom } from '../../contexts/RoomContext';

interface JoinRoomFormData {
  roomId: string;
  password?: string;
}

interface JoinRoomFormProps {
  onCancel: () => void;
}

export const JoinRoomForm: React.FC<JoinRoomFormProps> = ({ onCancel }) => {
  const { joinRoom, isLoading } = useRoom();
  const { register, handleSubmit, formState: { errors } } = useForm<JoinRoomFormData>();

  const onSubmit = async (data: JoinRoomFormData) => {
    try {
      await joinRoom(data.roomId, data.password);
      // No need to call onCancel here - the room state change will automatically redirect
    } catch (error: any) {
      // Error handling is done in RoomContext, but we can add specific handling here
      console.error('Join room form error:', error);
    }
  };

  return (
    <div className="max-w-md mx-auto bg-white p-8 rounded-lg shadow-md">
      <h2 className="text-2xl font-bold text-center mb-6">Join Room</h2>
      
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Room ID
          </label>
          <input
            {...register('roomId', { required: 'Room ID is required' })}
            type="text"
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="Enter room ID"
          />
          {errors.roomId && (
            <p className="text-red-500 text-sm mt-1">{errors.roomId.message}</p>
          )}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Password (if private room)
          </label>
          <input
            {...register('password')}
            type="password"
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="Enter password (optional)"
          />
        </div>

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
            {isLoading ? 'Joining...' : 'Join Room'}
          </button>
        </div>
      </form>
    </div>
  );
};