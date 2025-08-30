import React from 'react';
import { useRoom } from '../../contexts/RoomContext';
import { useAuth } from '../../contexts/AuthContext';
import { MenuSubmissionForm } from '../menu/MenuSubmissionForm';
import { AllSubmittedMenus } from '../menu/AllSubmittedMenus';
import { VotingInterface } from '../menu/VotingInterface';
import { DrawResult } from '../menu/DrawResult';
import { Users, Clock, Hash } from 'lucide-react';

export const RoomView: React.FC = () => {
  const { currentRoom, drawResult, menus, menuStatus, leaveRoom, startDraw, isLoading } = useRoom();
  const { user } = useAuth();

  if (!currentRoom) {
    return null;
  }

  const getStateDisplay = () => {
    switch (currentRoom.state) {
      case 'waiting':
        return 'Waiting for users';
      case 'inputting':
        return 'Menu submission phase';
      case 'started':
        return 'Voting phase';
      case 'result_viewing':
        return 'Results';
      default:
        return currentRoom.state;
    }
  };

  const getStateColor = () => {
    switch (currentRoom.state) {
      case 'waiting':
        return 'bg-yellow-100 text-yellow-800';
      case 'inputting':
        return 'bg-blue-100 text-blue-800';
      case 'started':
        return 'bg-green-100 text-green-800';
      case 'result_viewing':
        return 'bg-purple-100 text-purple-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 py-8">
      <div className="max-w-6xl mx-auto px-4">
        {/* Room Header */}
        <div className="bg-white rounded-lg shadow-md p-6 mb-8">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center space-x-4">
              <div className="flex items-center space-x-2">
                <Hash className="text-gray-500" size={20} />
                <span className="font-mono text-lg font-semibold">{currentRoom.roomId}</span>
              </div>
              <span className={`px-3 py-1 rounded-full text-sm font-medium ${getStateColor()}`}>
                {getStateDisplay()}
              </span>
            </div>
            <button
              onClick={leaveRoom}
              disabled={isLoading}
              className="bg-red-600 text-white px-4 py-2 rounded-md hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500 disabled:opacity-50"
            >
              Leave Room
            </button>
          </div>

          <div className="flex items-center space-x-6 text-sm text-gray-600">
            <div className="flex items-center space-x-2">
              <Users size={16} />
              <span>{currentRoom.users.length} / {currentRoom.maxUsers} users</span>
            </div>
            <div className="flex items-center space-x-2">
              <Clock size={16} />
              <span>Room: {currentRoom.isPrivate ? 'Private' : 'Public'}</span>
            </div>
          </div>

          <div className="mt-4">
            <h3 className="text-sm font-medium text-gray-700 mb-2">Users in room:</h3>
            <div className="flex flex-wrap gap-2">
              {currentRoom.users.map((username) => (
                <span
                  key={username}
                  className={`px-3 py-1 rounded-full text-sm ${
                    username === user?.username
                      ? 'bg-blue-100 text-blue-800 font-medium'
                      : 'bg-gray-100 text-gray-700'
                  }`}
                >
                  {username} {username === user?.username && '(You)'}
                </span>
              ))}
            </div>
          </div>
        </div>

        {/* State-based Content */}
        <div className="space-y-8">
          {currentRoom.state === 'waiting' && (
            <div className="text-center py-12">
              <h2 className="text-2xl font-bold text-gray-700 mb-4">
                Waiting for more users to join...
              </h2>
              <p className="text-gray-500">
                Share the room ID <strong>{currentRoom.roomId}</strong> with your friends!
              </p>
            </div>
          )}

          {(currentRoom.state === 'inputting' || currentRoom.state === 'submitted') && (
            <>
              <MenuSubmissionForm />
              <AllSubmittedMenus />
              {/* Single consolidated draw button for host */}
              {user?.username === currentRoom.hostUsername && menuStatus && (
                <div className="max-w-2xl mx-auto mt-8">
                  {(() => {
                    const hostHasSubmitted = menuStatus.userSubmitStatus[user?.username || ''] || false;
                    const totalSubmitted = Object.values(menuStatus.userSubmitStatus).filter(Boolean).length;
                    const allHaveSubmitted = totalSubmitted === currentRoom.users.length;
                    
                    if (hostHasSubmitted && allHaveSubmitted) {
                      // All users including host have submitted - green prominent button
                      return (
                        <div className="bg-green-50 border-2 border-green-200 rounded-lg p-6 text-center">
                          <h3 className="text-xl font-bold text-green-800 mb-4">🎯 All Menus Submitted!</h3>
                          <p className="text-green-700 mb-6">Everyone has submitted their menus. Ready to start the draw!</p>
                          <button
                            onClick={startDraw}
                            disabled={isLoading}
                            className="bg-green-600 text-white py-3 px-8 rounded-md hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-green-500 disabled:opacity-50 font-semibold"
                          >
                            {isLoading ? 'Starting...' : 'Start Draw'}
                          </button>
                        </div>
                      );
                    } else if (hostHasSubmitted && !allHaveSubmitted) {
                      // Host submitted but others haven't - blue waiting button
                      return (
                        <div className="bg-blue-50 border-2 border-blue-200 rounded-lg p-6 text-center">
                          <h3 className="text-xl font-bold text-blue-800 mb-4">⏳ Waiting for Others</h3>
                          <p className="text-blue-700 mb-4">
                            {totalSubmitted}/{currentRoom.users.length} users have submitted. Waiting for remaining participants.
                          </p>
                          <button
                            onClick={startDraw}
                            disabled={isLoading}
                            className="bg-blue-600 text-white py-2 px-6 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
                          >
                            {isLoading ? 'Starting...' : 'Start Draw Early'}
                          </button>
                        </div>
                      );
                    } else if (!hostHasSubmitted && totalSubmitted === currentRoom.users.length - 1) {
                      // Host hasn't submitted but all others have - yellow prompt
                      return (
                        <div className="bg-yellow-50 border-2 border-yellow-200 rounded-lg p-6 text-center">
                          <h3 className="text-xl font-bold text-yellow-800 mb-4">🎯 Others Have Submitted!</h3>
                          <p className="text-yellow-700 mb-6">All other participants have submitted their menus. Submit yours to proceed with selection.</p>
                        </div>
                      );
                    }
                    return null; // Don't show button for other cases
                  })()}
                </div>
              )}
            </>
          )}

          {currentRoom.state === 'started' && (
            <VotingInterface menus={menus} />
          )}

          {currentRoom.state === 'result_viewing' && drawResult && (
            <DrawResult result={drawResult} />
          )}
        </div>
      </div>
    </div>
  );
};