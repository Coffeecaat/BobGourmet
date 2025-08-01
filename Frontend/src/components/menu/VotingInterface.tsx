import React, { useState } from 'react';
import { menuAPI } from '../../services/api';
import { X } from 'lucide-react';
import { MenuOption } from '../../types';
import toast from 'react-hot-toast';

interface VotingInterfaceProps {
  menus: MenuOption[];
}

export const VotingInterface: React.FC<VotingInterfaceProps> = ({ menus }) => {
  const [excludedMenus, setExcludedMenus] = useState<string[]>([]);
  const [isVoting, setIsVoting] = useState(false);

  const toggleMenuExclusion = (username: string) => {
    setExcludedMenus(prev => 
      prev.includes(username) 
        ? prev.filter(u => u !== username)
        : [...prev, username]
    );
  };

  const submitVote = async () => {
    try {
      setIsVoting(true);
      await menuAPI.vote(excludedMenus);
      toast.success('Vote submitted successfully!');
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Failed to submit vote');
    } finally {
      setIsVoting(false);
    }
  };

  if (menus.length === 0) {
    return (
      <div className="max-w-4xl mx-auto bg-white p-8 rounded-lg shadow-md text-center">
        <h2 className="text-2xl font-bold mb-4">Waiting for votes...</h2>
        <p className="text-gray-600">All users need to submit their votes before proceeding.</p>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto bg-white p-8 rounded-lg shadow-md">
      <h2 className="text-2xl font-bold text-center mb-6">Vote on Menus</h2>
      <p className="text-center text-gray-600 mb-6">
        Click on menus you DON'T want to exclude them from the draw
      </p>
      
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
        {menus.map((menu) => (
          <div
            key={menu.username}
            className={`
              border-2 rounded-lg p-4 cursor-pointer transition-all
              ${excludedMenus.includes(menu.username)
                ? 'border-red-300 bg-red-50 opacity-50'
                : 'border-green-300 bg-green-50 hover:bg-green-100'
              }
            `}
            onClick={() => toggleMenuExclusion(menu.username)}
          >
            <div className="flex items-center justify-between mb-3">
              <h3 className="font-semibold text-lg">{menu.username}'s Menu</h3>
              {excludedMenus.includes(menu.username) && (
                <X className="text-red-500" size={20} />
              )}
            </div>
            <ul className="space-y-1">
              {menu.menuItems.map((item, itemIndex) => (
                <li key={itemIndex} className="text-gray-700">
                  • {item}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>

      <div className="text-center">
        <p className="text-sm text-gray-600 mb-4">
          Excluded menus: {excludedMenus.length} / {menus.length}
        </p>
        <button
          onClick={submitVote}
          disabled={isVoting}
          className="bg-blue-600 text-white py-3 px-8 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
        >
          {isVoting ? 'Submitting Vote...' : 'Submit Vote'}
        </button>
      </div>
    </div>
  );
};