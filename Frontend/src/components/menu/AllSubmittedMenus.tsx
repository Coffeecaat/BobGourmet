import React from 'react';
import { useRoom } from '../../contexts/RoomContext';

export const AllSubmittedMenus: React.FC = () => {
  const { menuStatus, currentRoom } = useRoom();

  if (!currentRoom) {
    return null;
  }

  // Get only users who have actually submitted menus (userSubmitStatus === true AND has non-empty menus)
  const submittedUsers = menuStatus ? 
    Object.entries(menuStatus.userSubmitStatus)
      .filter(([username, hasSubmitted]) => {
        const userMenus = menuStatus.submittedMenusByUsers[username] || [];
        return hasSubmitted && userMenus.length > 0;
      })
      .map(([username]) => username) : [];
  
  if (submittedUsers.length === 0) {
    return (
      <div className="max-w-4xl mx-auto mt-6 p-4 bg-gray-50 rounded-lg">
        <h3 className="text-lg font-semibold text-gray-800 mb-4">📝 Submitted Menus</h3>
        <p className="text-gray-600">No menus submitted yet. Be the first to submit your menu ideas!</p>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto mt-6 p-4 bg-gray-50 rounded-lg">
      <h3 className="text-lg font-semibold text-gray-800 mb-4">📝 Submitted Menus ({submittedUsers.length}/{currentRoom.users.length})</h3>
      
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {submittedUsers.map((username) => {
          const userMenus = menuStatus?.submittedMenusByUsers[username] || [];
          const participant = currentRoom.participants.find(p => p.username === username);
          const displayName = participant?.nickname || username;
          
          return (
            <div key={username} className="bg-white p-4 rounded-lg shadow-sm border border-gray-200">
              <div className="flex items-center mb-3">
                <span className="font-medium text-gray-800">{displayName}</span>
                <span className="ml-2 px-2 py-1 bg-green-100 text-green-700 text-xs rounded-full">
                  ✓ Submitted
                </span>
              </div>
              
              <ul className="space-y-1">
                {userMenus.map((menu, index) => (
                  <li key={index} className="flex items-center text-gray-700">
                    <span className="text-orange-500 mr-2">🍽️</span>
                    <span className="text-sm">{menu}</span>
                  </li>
                ))}
              </ul>
              
              {userMenus.length === 0 && (
                <p className="text-gray-500 text-sm italic">No menus listed</p>
              )}
            </div>
          );
        })}
      </div>
      
      <div className="mt-4 text-center">
        <p className="text-sm text-gray-600">
          {currentRoom.users.length - submittedUsers.length > 0 && (
            <>Waiting for {currentRoom.users.length - submittedUsers.length} more participant(s) to submit their menus...</>
          )}
          {submittedUsers.length === currentRoom.users.length && (
            <>🎉 All participants have submitted their menus! Ready for the next step.</>
          )}
        </p>
      </div>
    </div>
  );
};