import React from 'react';
import { useRoom } from '../../contexts/RoomContext';
import { useAuth } from '../../contexts/AuthContext';
import { MenuSubmissionForm } from '../menu/MenuSubmissionForm';
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
              {/* Debug info for draw button */}
              <div className="max-w-2xl mx-auto mt-4 p-4 bg-gray-100 rounded text-sm">
                <strong>Debug Info:</strong><br/>
                - Current user: {user?.username}<br/>
                - Room host: {currentRoom.hostUsername}<br/>
                - Is host: {user?.username === currentRoom.hostUsername ? 'Yes' : 'No'}<br/>
                - Users count: {currentRoom.users.length}<br/>
                - Participants count: {currentRoom.participants.length}<br/>
                {menuStatus && (
                  <>
                    - MenuStatus submitted: {Object.values(menuStatus.userSubmitStatus).filter(Boolean).length} / {currentRoom.users.length}<br/>
                    - All submitted (menuStatus): {Object.values(menuStatus.userSubmitStatus).filter(Boolean).length === currentRoom.users.length ? 'Yes' : 'No'}<br/>
                  </>
                )}
                - Participants submitted: {currentRoom.participants.filter(p => p.submittedMenu).length} / {currentRoom.participants.length}<br/>
                - All submitted (participants): {currentRoom.participants.length > 0 && currentRoom.participants.every(p => p.submittedMenu) ? 'Yes' : 'No'}<br/>
                - Participants: {JSON.stringify(currentRoom.participants.map(p => ({ username: p.username, submitted: p.submittedMenu })))}
              </div>
              {/* Show start draw button only if host hasn't submitted yet and all others have */}
              {!menuStatus?.userSubmitStatus?.[user?.username || ''] && 
               menuStatus && 
               Object.values(menuStatus.userSubmitStatus).filter(Boolean).length === currentRoom.users.length - 1 && 
               user?.username === currentRoom.hostUsername && (
                <div className="max-w-2xl mx-auto mt-8">
                  <div className="bg-yellow-50 border-2 border-yellow-200 rounded-lg p-6 text-center">
                    <h3 className="text-xl font-bold text-yellow-800 mb-4">🎯 Others Have Submitted!</h3>
                    <p className="text-yellow-700 mb-6">All other participants have submitted their menus. Submit yours to proceed with selection.</p>
                  </div>
                </div>
              )}
              
              {/* Start draw button for host - shown after host submits menu */}
              {user?.username === currentRoom.hostUsername && (
                <div className="max-w-2xl mx-auto mt-4">
                  {menuStatus?.userSubmitStatus?.[user?.username || ''] ? (
                    // Host has submitted - show prominent start button
                    <div className="bg-green-50 border-2 border-green-200 rounded-lg p-6 text-center">
                      <h3 className="text-xl font-bold text-green-800 mb-4">🎯 Ready to Start Draw!</h3>
                      <p className="text-green-700 mb-6">
                        You've submitted your menu. You can start the draw now or wait for more participants.
                      </p>
                      <button
                        onClick={startDraw}
                        disabled={isLoading}
                        className="bg-green-600 text-white py-3 px-8 rounded-md hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-green-500 disabled:opacity-50 font-semibold"
                      >
                        {isLoading ? 'Starting...' : 'Start Draw'}
                      </button>
                    </div>
                  ) : (
                    // Host hasn't submitted - show less prominent button for testing
                    <div className="bg-red-50 border-2 border-red-200 rounded-lg p-4 text-center">
                      <p className="text-red-700 text-sm mb-3">Force Start (Host Only - For Testing)</p>
                      <button
                        onClick={startDraw}
                        disabled={isLoading}
                        className="bg-red-600 text-white py-2 px-6 rounded-md hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500 disabled:opacity-50 text-sm"
                      >
                        {isLoading ? 'Starting...' : 'Force Start Draw'}
                      </button>
                    </div>
                  )}
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