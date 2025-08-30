import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { menuAPI } from '../../services/api';
import { useRoom } from '../../contexts/RoomContext';
import { useAuth } from '../../contexts/AuthContext';
import toast from 'react-hot-toast';

interface MenuSubmissionData {
  menuItems: { value: string }[];
}

export const MenuSubmissionForm: React.FC = () => {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { currentRoom, menuStatus } = useRoom();
  const { user } = useAuth();
  const { register, control, handleSubmit, formState: { errors }, reset } = useForm<MenuSubmissionData>({
    defaultValues: {
      menuItems: [{ value: '' }, { value: '' }, { value: '' }, { value: '' }]
    }
  });


  // Check if current user has already submitted
  const hasUserSubmitted = menuStatus?.userSubmitStatus?.[user?.username || ''] || false;
  const userSubmittedMenus = menuStatus?.submittedMenusByUsers?.[user?.username || ''] || [];

  const onSubmit = async (data: MenuSubmissionData) => {
    if (!currentRoom) {
      toast.error('No room selected');
      return;
    }

    if (hasUserSubmitted) {
      toast.error('You have already submitted your menu');
      return;
    }

    const menuItems = data.menuItems.map(item => item.value).filter(item => item.trim() !== '');
    
    if (menuItems.length === 0) {
      toast.error('Please add at least one menu item');
      return;
    }

    if (menuItems.length > 4) {
      toast.error('Maximum 4 menu items allowed');
      return;
    }

    try {
      setIsSubmitting(true);
      const response = await menuAPI.submitMenu(currentRoom.roomId, { menus: menuItems });
      console.log('Menu submission response:', response);
      toast.success('Menu submitted successfully!');
      reset();
    } catch (error: any) {
      console.error('Menu submission error:', error);
      toast.error(error.response?.data?.message || 'Failed to submit menu');
    } finally {
      setIsSubmitting(false);
    }
  };

  // If user has already submitted, show their menu
  if (hasUserSubmitted && userSubmittedMenus.length > 0) {
    return (
      <div className="max-w-2xl mx-auto bg-white p-8 rounded-lg shadow-md">
        <h2 className="text-2xl font-bold text-center mb-6 text-green-600">✅ Menu Submitted</h2>
        
        <div className="bg-green-50 border-2 border-green-200 rounded-lg p-6">
          <h3 className="font-semibold text-lg mb-4">Your Submitted Menu:</h3>
          <ul className="space-y-2">
            {userSubmittedMenus.map((item, index) => (
              <li key={index} className="text-green-800 font-medium">
                🍽️ {item}
              </li>
            ))}
          </ul>
        </div>

        <div className="mt-6 text-center text-sm text-gray-600">
          <p>Waiting for other participants to submit their menus...</p>
          {menuStatus && (
            <p className="mt-2">
              {Object.values(menuStatus.userSubmitStatus).filter(Boolean).length} / {currentRoom?.users.length} users have submitted
            </p>
          )}
        </div>
        
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto bg-white p-8 rounded-lg shadow-md">
      <h2 className="text-2xl font-bold text-center mb-6">Submit Your Menu</h2>
      
      {menuStatus && (
        <div className="mb-6 p-4 bg-blue-50 border-2 border-blue-200 rounded-lg">
          <p className="text-center text-blue-800">
            {Object.values(menuStatus.userSubmitStatus).filter(Boolean).length} / {currentRoom?.users.length} users have submitted their menus
          </p>
        </div>
      )}
      
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div className="space-y-3">
          {[0, 1, 2, 3].map((index) => (
            <div key={index} className="flex items-center space-x-2">
              <input
                {...register(`menuItems.${index}.value` as const, {
                  maxLength: { value: 100, message: 'Menu item too long' }
                })}
                type="text"
                className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder={`Menu item ${index + 1}`}
              />
            </div>
          ))}
          {errors.menuItems && (
            <p className="text-red-500 text-sm">Please check your menu items</p>
          )}
        </div>


        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full bg-green-600 text-white py-3 px-4 rounded-md hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-green-500 disabled:opacity-50"
        >
          {isSubmitting ? 'Submitting...' : 'Submit Menu'}
        </button>
      </form>
    </div>
  );
};