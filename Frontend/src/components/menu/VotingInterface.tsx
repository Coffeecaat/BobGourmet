import React, { useRef, useState } from 'react';
import { menuAPI } from '../../services/api';
import { useRoom } from '../../contexts/RoomContext';
import { useAuth } from '../../contexts/AuthContext';
import toast from 'react-hot-toast';

export const VotingInterface: React.FC = () => {
  const { currentRoom, menuStatus, updateMenuStatus } = useRoom();
  const { user } = useAuth();
  const [pending, setPending] = useState(false);
  const pendingRef = useRef(false);
  if (!currentRoom || !user) return null;
  if (!menuStatus) return <p className="text-center text-gray-500">메뉴 상태를 수신하는 중입니다.</p>;
  const entries = Object.entries(menuStatus.menuVotes);
  if (!entries.length) return null;

  const vote = async (menuKey: string, action: 'recommend' | 'dislike') => {
    if (pendingRef.current) return;
    pendingRef.current = true;
    setPending(true);
    try {
      const status = action === 'recommend'
        ? await menuAPI.recommendMenu(currentRoom.roomId, menuKey)
        : await menuAPI.dislikeMenu(currentRoom.roomId, menuKey);
      updateMenuStatus(currentRoom.roomId, status);
      toast.success(action === 'recommend' ? '메뉴를 추천했습니다.' : '메뉴를 추첨에서 제외했습니다.');
    } catch (error: any) {
      toast.error(error.response?.data?.message || '메뉴 의견을 저장하지 못했습니다.');
    } finally {
      pendingRef.current = false;
      setPending(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto bg-white p-8 rounded-lg shadow-md">
      <h2 className="text-2xl font-bold text-center mb-4">메뉴 추천 · 제외</h2>
      <p className="text-sm text-gray-600 mb-6">추천은 개인 메뉴 할당량을 사용합니다. 제외한 메뉴는 이번 추첨에서 빠지며, 현재 서버는 취소 기능을 제공하지 않습니다.</p>
      <div className="grid gap-4 md:grid-cols-2">
        {entries.map(([menuKey, details]) => {
          const excluded = details.isExcluded || menuStatus.dislikedAndExcludedMenuKeys.includes(menuKey);
          const recommended = details.recommenders.includes(user.username);
          return (
            <div key={menuKey} className={`border rounded-lg p-4 ${excluded ? 'bg-red-50' : 'bg-white'}`}>
              <h3 className="font-semibold mb-2">{menuKey}</h3>
              <p className="text-sm text-gray-600 mb-3">추천 {details.recommenders.length} · {excluded ? '추첨 제외' : '추첨 대상'}</p>
              <div className="flex gap-2">
                <button type="button" onClick={() => void vote(menuKey, 'recommend')}
                  disabled={pending || recommended || excluded}
                  className="px-3 py-2 rounded bg-blue-600 text-white disabled:opacity-50">
                  {recommended ? '추천 완료' : '추천'}
                </button>
                <button type="button" onClick={() => void vote(menuKey, 'dislike')}
                  disabled={pending || excluded}
                  className="px-3 py-2 rounded bg-red-600 text-white disabled:opacity-50">
                  {excluded ? '제외됨' : '추첨에서 제외'}
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
