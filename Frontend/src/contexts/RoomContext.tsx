import React, { createContext, useCallback, useContext, useEffect, useRef, useState, ReactNode } from 'react';
import { Room, DrawResult, MenuOption, MenuStatus } from '../types';
import { webSocketService } from '../services/websocket';
import { subscribeRoomSession } from '../services/roomSubscription';
import { emptyRoomState, normalizeRoom, reduceRoomMessage, RoomState } from '../services/roomState';
import { roomAPI, menuAPI } from '../services/api';
import { useAuth } from './AuthContext';
import toast from 'react-hot-toast';

interface RoomContextType {
  currentRoom: Room | null;
  drawResult: DrawResult | null;
  menus: MenuOption[];
  menuStatus: MenuStatus | null;
  isLoading: boolean;
  joinRoom: (roomId: string, password?: string) => Promise<void>;
  leaveRoom: () => Promise<void>;
  createRoom: (roomName: string, maxUsers: number, isPrivate: boolean, password?: string) => Promise<void>;
  startDraw: () => Promise<void>;
  updateMenuStatus: (roomId: string, status: MenuStatus) => void;
}

const RoomContext = createContext<RoomContextType | undefined>(undefined);
export const useRoom = () => {
  const context = useContext(RoomContext);
  if (!context) throw new Error('useRoom must be used within RoomProvider');
  return context;
};

export const RoomProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const { user } = useAuth();
  const username = user?.username;
  const [state, setState] = useState<RoomState>(() => {
    try {
      const saved = JSON.parse(localStorage.getItem('bobgourmet_current_room') || 'null') as Room | null;
      return { ...emptyRoomState(), room: saved?.roomId && saved.users?.includes(username || '') ? normalizeRoom(saved) : null };
    } catch {
      return emptyRoomState();
    }
  });
  const stateRef = useRef(state);
  const mounted = useRef(false);
  const operationPending = useRef(false);
  const [isLoading, setIsLoading] = useState(false);
  const update = useCallback((change: (previous: RoomState) => RoomState) => {
    if (!mounted.current) return;
    const next = change(stateRef.current);
    stateRef.current = next;
    setState(next);
  }, []);
  const clearRoom = useCallback(() => update(emptyRoomState), [update]);
  const currentRoom = state.room;

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      // React StrictMode immediately mounts again; do not create a false server-side leave.
      queueMicrotask(() => { if (!mounted.current) void webSocketService.disconnect(); });
    };
  }, []);

  useEffect(() => {
    if (currentRoom) localStorage.setItem('bobgourmet_current_room', JSON.stringify(currentRoom));
    else localStorage.removeItem('bobgourmet_current_room');
  }, [currentRoom]);

  useEffect(() => {
    if (!username) {
      clearRoom();
      void webSocketService.disconnect();
      return;
    }
    return webSocketService.onConnectionLost(() => {
      clearRoom();
      toast.error('실시간 연결이 끊겼습니다. 방 목록에서 다시 입장해주세요.');
    });
  }, [username, clearRoom]);

  useEffect(() => {
    const roomId = currentRoom?.roomId;
    if (!roomId || !username) return;
    let cancelled = false;
    let revision = 0;
    const dispose = subscribeRoomSession(roomId, {
      message: (message) => {
        if (cancelled) return;
        revision++;
        update(previous => reduceRoomMessage(previous, message, roomId, username));
      },
      closed: () => {
        if (cancelled || stateRef.current.room?.roomId !== roomId) return;
        clearRoom();
        toast.error('방장이 방을 종료했습니다.');
      },
      ready: () => {
        const beforeRead = revision;
        // REST reconciles room details, not menu status or a subscription acknowledgement.
        void roomAPI.getRoomInfo(roomId).then(room => {
          if (cancelled || revision !== beforeRead) return;
          update(previous => reduceRoomMessage(previous, { type: 'ROOM_STATE_UPDATE', payload: room }, roomId, username));
        }).catch((error: any) => {
          if (cancelled || stateRef.current.room?.roomId !== roomId) return;
          if ([401, 403, 404].includes(error.response?.status)) clearRoom();
          toast.error('방 상태를 확인할 수 없습니다. 방 목록에서 상태를 확인해주세요.');
        });
      },
      error: () => {
        if (!cancelled) {
          clearRoom();
          toast.error('실시간 연결에 실패했습니다. 다시 입장해주세요.');
        }
      },
    });
    return () => { cancelled = true; dispose(); };
  }, [currentRoom?.roomId, username, update, clearRoom]);

  const updateMenuStatus = (roomId: string, status: MenuStatus) => {
    if (!username) return;
    update(previous => reduceRoomMessage(previous, { type: 'MENU_STATUS_UPDATE', payload: status }, roomId, username));
  };

  const enterRoom = async (request: () => Promise<Room>, successMessage: string) => {
    if (!username || operationPending.current || stateRef.current.room) return;
    operationPending.current = true;
    setIsLoading(true);
    try {
      await webSocketService.connect();
      if (!mounted.current) return;
      const room = await request();
      if (!mounted.current) return;
      if (!webSocketService.isConnected()) throw new Error('연결이 종료되었습니다. 방 목록에서 상태를 확인해주세요.');
      update(() => ({ ...emptyRoomState(), room }));
      toast.success(successMessage);
    } catch (error: any) {
      if (mounted.current) toast.error(error.response?.data?.message || error.message || '방 입장에 실패했습니다.');
      throw error;
    } finally {
      operationPending.current = false;
      if (mounted.current) setIsLoading(false);
    }
  };

  const createRoom = (roomName: string, maxUsers: number, isPrivate: boolean, password?: string) =>
    enterRoom(() => roomAPI.createRoom({ roomName, maxUsers, isPrivate, password }), '방을 생성했습니다.');
  const joinRoom = (roomId: string, password?: string) =>
    enterRoom(() => roomAPI.joinRoom(roomId, password), '방에 입장했습니다.');

  const leaveRoom = async () => {
    const roomId = stateRef.current.room?.roomId;
    if (!roomId || operationPending.current) return;
    operationPending.current = true;
    setIsLoading(true);
    try {
      await roomAPI.leaveRoom(roomId);
      if (stateRef.current.room?.roomId === roomId) clearRoom();
      // Keep the authenticated socket alive. A delayed DISCONNECT must not leave a new room.
      toast.success('방에서 나왔습니다.');
    } catch (error: any) {
      toast.error(error.response?.data?.message || '퇴장에 실패했습니다.');
    } finally {
      operationPending.current = false;
      if (mounted.current) setIsLoading(false);
    }
  };

  const startDraw = async () => {
    const roomId = stateRef.current.room?.roomId;
    if (!roomId || !username || operationPending.current) return;
    operationPending.current = true;
    setIsLoading(true);
    try {
      const room = await menuAPI.startDraw(roomId);
      update(previous => reduceRoomMessage(previous, { type: 'ROOM_STATE_UPDATE', payload: room }, roomId, username));
      if (room.state !== 'result_viewing') toast('추첨 가능한 메뉴가 없어 메뉴 입력 단계로 돌아갔습니다.');
    } catch (error: any) {
      toast.error(error.response?.data?.message || '추첨에 실패했습니다.');
    } finally {
      operationPending.current = false;
      if (mounted.current) setIsLoading(false);
    }
  };

  const menus = Object.entries(state.menuStatus?.submittedMenusByUsers || {})
    .map(([menuUsername, menuItems]) => ({ username: menuUsername, menuItems }));
  return (
    <RoomContext.Provider value={{
      currentRoom, menuStatus: state.menuStatus, drawResult: state.drawResult, menus,
      isLoading, createRoom, joinRoom, leaveRoom, startDraw, updateMenuStatus,
    }}>
      {children}
    </RoomContext.Provider>
  );
};
