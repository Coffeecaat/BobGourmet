import React, { useEffect, useState } from 'react';
import { useRoom } from '../../contexts/RoomContext';
import { roomAPI } from '../../services/api';
import { Room } from '../../types';
import { Users, Lock, Unlock, RefreshCw } from 'lucide-react';
import toast from 'react-hot-toast';

interface RoomListProps {
  onCancel: () => void;
}

export const RoomList: React.FC<RoomListProps> = ({ onCancel }) => {
  const { joinRoom, isLoading } = useRoom();
  const [rooms, setRooms] = useState<Room[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedRoom, setSelectedRoom] = useState<Room | null>(null);
  const [password, setPassword] = useState('');
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const fetchRooms = async () => {
    try {
      setLoading(true);
      const activeRooms = await roomAPI.getAllActiveRooms();
      setRooms(activeRooms);
      setLastUpdated(new Date());
    } catch (error: any) {
      console.error('Failed to fetch rooms:', error);
      toast.error('Failed to load rooms');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRooms();
    
    // Auto-refresh every 10 seconds
    const intervalId = setInterval(fetchRooms, 10000);
    
    return () => {
      clearInterval(intervalId);
    };
  }, []);

  const handleJoinRoom = async (room: Room) => {
    if (room.isPrivate) {
      setSelectedRoom(room);
      setShowPasswordModal(true);
      return;
    }

    try {
      await joinRoom(room.roomId);
    } catch (error: any) {
      console.error('Join room error:', error);
    }
  };

  const handleJoinPrivateRoom = async () => {
    if (!selectedRoom) return;

    try {
      await joinRoom(selectedRoom.roomId, password);
      setShowPasswordModal(false);
      setPassword('');
      setSelectedRoom(null);
    } catch (error: any) {
      console.error('Join private room error:', error);
    }
  };

  const closePasswordModal = () => {
    setShowPasswordModal(false);
    setPassword('');
    setSelectedRoom(null);
  };

  const getStateDisplay = (state: string) => {
    switch (state) {
      case 'waiting':
        return <span className="px-2 py-1 bg-gray-100 text-gray-800 rounded-full text-xs">Waiting</span>;
      case 'inputting':
        return <span className="px-2 py-1 bg-blue-100 text-blue-800 rounded-full text-xs">Menu Input</span>;
      case 'submitted':
        return <span className="px-2 py-1 bg-yellow-100 text-yellow-800 rounded-full text-xs">Ready to Draw</span>;
      case 'started':
        return <span className="px-2 py-1 bg-green-100 text-green-800 rounded-full text-xs">Voting</span>;
      case 'result_viewing':
        return <span className="px-2 py-1 bg-purple-100 text-purple-800 rounded-full text-xs">Results</span>;
      default:
        return <span className="px-2 py-1 bg-gray-100 text-gray-800 rounded-full text-xs">{state}</span>;
    }
  };

  if (loading) {
    return (
      <div className="max-w-4xl mx-auto bg-white p-8 rounded-lg shadow-md">
        <div className="flex items-center justify-center h-64">
          <RefreshCw className="animate-spin h-8 w-8 text-blue-500" />
          <span className="ml-2 text-gray-600">Loading rooms...</span>
        </div>
      </div>
    );
  }

  return (
    <>
      <div className="max-w-4xl mx-auto bg-white p-4 sm:p-6 lg:p-8 rounded-lg shadow-md">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6 gap-4">
          <div>
            <h2 className="text-xl sm:text-2xl font-bold text-gray-900">Available Rooms</h2>
            {lastUpdated && (
              <p className="text-sm text-gray-500">
                Last updated: {lastUpdated.toLocaleTimeString()}
              </p>
            )}
          </div>
          <div className="flex space-x-2">
            <button
              onClick={fetchRooms}
              disabled={loading}
              className="bg-blue-100 text-blue-700 px-3 py-2 rounded-md hover:bg-blue-200 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 flex-shrink-0"
            >
              <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            </button>
            <button
              onClick={onCancel}
              className="bg-gray-300 text-gray-700 px-4 py-2 rounded-md hover:bg-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-500 flex-shrink-0"
            >
              Cancel
            </button>
          </div>
        </div>

        {rooms.length === 0 ? (
          <div className="text-center py-12">
            <Users className="mx-auto h-12 w-12 text-gray-400 mb-4" />
            <h3 className="text-lg font-medium text-gray-900 mb-2">No active rooms</h3>
            <p className="text-gray-500">Create a new room to get started!</p>
          </div>
        ) : (
          <div>
            {/* Desktop Table View */}
            <div className="hidden lg:block overflow-hidden rounded-lg border border-gray-200">
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Room
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Host
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Players
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Status
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Action
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {rooms.map((room) => (
                    <tr key={room.roomId} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center">
                          {room.isPrivate ? (
                            <Lock className="h-4 w-4 text-red-500 mr-2" />
                          ) : (
                            <Unlock className="h-4 w-4 text-green-500 mr-2" />
                          )}
                          <div>
                            <div className="text-sm font-medium text-gray-900">
                              {room.roomName}
                            </div>
                            <div className="text-sm text-gray-500">
                              ID: {room.roomId}
                            </div>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm text-gray-900">{room.hostUsername}</div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm text-gray-900">
                          <Users className="inline h-4 w-4 mr-1" />
                          {room.users.length}/{room.maxUsers}
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        {getStateDisplay(room.state)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                        <button
                          onClick={() => handleJoinRoom(room)}
                          disabled={isLoading || room.users.length >= room.maxUsers}
                          className="bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          {isLoading ? 'Joining...' : room.users.length >= room.maxUsers ? 'Full' : 'Join'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Mobile Card View */}
            <div className="lg:hidden space-y-4">
              {rooms.map((room) => (
                <div key={room.roomId} className="bg-white border border-gray-200 rounded-lg p-4 shadow-sm">
                  <div className="flex items-start justify-between mb-3">
                    <div className="flex items-start">
                      {room.isPrivate ? (
                        <Lock className="h-4 w-4 text-red-500 mr-2 mt-0.5" />
                      ) : (
                        <Unlock className="h-4 w-4 text-green-500 mr-2 mt-0.5" />
                      )}
                      <div>
                        <div className="text-sm font-medium text-gray-900">
                          {room.roomName}
                        </div>
                        <div className="text-xs text-gray-500">
                          ID: {room.roomId}
                        </div>
                      </div>
                    </div>
                    {getStateDisplay(room.state)}
                  </div>
                  
                  <div className="flex items-center justify-between mb-3">
                    <div className="text-sm text-gray-600">
                      <span className="font-medium">Host:</span> {room.hostUsername}
                    </div>
                    <div className="text-sm text-gray-600 flex items-center">
                      <Users className="inline h-4 w-4 mr-1" />
                      {room.users.length}/{room.maxUsers}
                    </div>
                  </div>

                  <button
                    onClick={() => handleJoinRoom(room)}
                    disabled={isLoading || room.users.length >= room.maxUsers}
                    className="w-full bg-blue-600 text-white py-2 px-4 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed text-sm font-medium"
                  >
                    {isLoading ? 'Joining...' : room.users.length >= room.maxUsers ? 'Room Full' : 'Join Room'}
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Password Modal */}
      {showPasswordModal && selectedRoom && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white p-6 rounded-lg shadow-xl max-w-sm w-full mx-4">
            <h3 className="text-lg font-semibold mb-4">Private Room Password</h3>
            <p className="text-gray-600 mb-4">
              This room requires a password to join: <strong>{selectedRoom.roomName}</strong>
            </p>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter password"
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 mb-4"
              onKeyPress={(e) => e.key === 'Enter' && handleJoinPrivateRoom()}
            />
            <div className="flex space-x-3">
              <button
                onClick={closePasswordModal}
                className="flex-1 bg-gray-300 text-gray-700 py-2 px-4 rounded-md hover:bg-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-500"
              >
                Cancel
              </button>
              <button
                onClick={handleJoinPrivateRoom}
                disabled={isLoading || !password.trim()}
                className="flex-1 bg-blue-600 text-white py-2 px-4 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
              >
                {isLoading ? 'Joining...' : 'Join'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};