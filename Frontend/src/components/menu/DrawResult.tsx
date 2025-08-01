import React from 'react';
import { DrawResult as DrawResultType } from '../../types';
import { Trophy, Utensils, User } from 'lucide-react';

interface DrawResultProps {
  result: DrawResultType;
}

export const DrawResult: React.FC<DrawResultProps> = ({ result }) => {
  return (
    <div className="max-w-2xl mx-auto bg-white rounded-lg shadow-md overflow-hidden">
      <div className="bg-gradient-to-r from-yellow-400 to-orange-500 p-6 text-white text-center">
        <Trophy className="mx-auto mb-4" size={48} />
        <h2 className="text-3xl font-bold mb-2">🎉 Winner Selected! 🎉</h2>
        <p className="text-yellow-100">The draw has decided your meal!</p>
      </div>

      <div className="p-8">
        <div className="text-center mb-8">
          <div className="flex items-center justify-center mb-4">
            <User className="text-blue-500 mr-2" size={24} />
            <span className="text-xl font-semibold text-gray-700">Selected by:</span>
          </div>
          <div className="bg-blue-50 border-2 border-blue-200 rounded-lg p-4 inline-block">
            <span className="text-2xl font-bold text-blue-700">{result.selectedUser}</span>
          </div>
        </div>

        <div className="text-center">
          <div className="flex items-center justify-center mb-4">
            <Utensils className="text-green-500 mr-2" size={24} />
            <span className="text-xl font-semibold text-gray-700">Selected Menu:</span>
          </div>
          <div className="bg-green-50 border-2 border-green-200 rounded-lg p-6">
            <ul className="space-y-2">
              {result.selectedMenu.map((item, index) => (
                <li key={index} className="text-lg text-green-800 font-medium">
                  🍽️ {item}
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="mt-8 text-center text-sm text-gray-500">
          <p>Enjoy your meal! The room will reset shortly for another round.</p>
        </div>
      </div>
    </div>
  );
};