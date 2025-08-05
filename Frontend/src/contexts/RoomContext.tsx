import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { Room, WebSocketMessage, DrawResult, MenuOption, MenuStatus } from '../types';
import { webSocketService } from '../services/websocket';
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
}

const RoomContext = createContext<RoomContextType | undefined>(undefined);

export const useRoom = () => {
  const context = useContext(RoomContext);
  if (!context) {
    throw new Error('useRoom must be used within RoomProvider');
  }
  return context;
};

interface RoomProviderProps {
  children: ReactNode;
}

export const RoomProvider: React.FC<RoomProviderProps> = ({ children }) => {
  const { token, user, isTokenValid } = useAuth();
  const [currentRoom, setCurrentRoom] = useState<Room | null>(() => {
    // Try to restore room state from localStorage on initial load
    try {
      const savedRoom = localStorage.getItem('bobgourmet_current_room');
      return savedRoom ? JSON.parse(savedRoom) : null;
    } catch {
      return null;
    }
  });
  const [drawResult, setDrawResult] = useState<DrawResult | null>(null);
  const [menus, setMenus] = useState<MenuOption[]>([]);
  const [menuStatus, setMenuStatus] = useState<MenuStatus | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // Save room state to localStorage whenever currentRoom changes
  useEffect(() => {
    if (currentRoom) {
      localStorage.setItem('bobgourmet_current_room', JSON.stringify(currentRoom));
    } else {
      localStorage.removeItem('bobgourmet_current_room');
    }
  }, [currentRoom]);

  // Handle initial connection and room recovery
  useEffect(() => {
    if (!token || !user) {
      // Clear room state if no valid authentication
      setCurrentRoom(null);
      return;
    }

    // Check if token is still valid
    if (!isTokenValid()) {
      // Token is expired, clear room state
      setCurrentRoom(null);
      return;
    }

    const initializeConnection = async () => {
      try {
        // Always ensure WebSocket is connected when we have a token
        if (!webSocketService.isConnected()) {
          console.log('Connecting to WebSocket...');
          await webSocketService.connect(token);
          console.log('WebSocket connected successfully');
        }

        // If we have a saved room, try to recover the room state
        if (currentRoom && currentRoom.roomId) {
          console.log('Attempting to recover room state for:', currentRoom.roomId);
          try {
            // Fetch current room details from server to verify room still exists
            const roomDetails = await roomAPI.getRoomInfo(currentRoom.roomId);
            setCurrentRoom(roomDetails);
            toast.success('Room state recovered successfully', { duration: 3000 });
          } catch (error: any) {
            console.error('Failed to recover room state:', error);
            if (error.response?.status === 404) {
              // Room no longer exists
              setCurrentRoom(null);
              toast.error('Previous room no longer exists', { duration: 4000 });
            } else if (error.response?.status === 500) {
              // Server error - room might still exist, don't clear state yet
              console.warn('Server error during room recovery, keeping room state');
              toast.error('Unable to verify room status. Please try refreshing again.', { duration: 5000 });
            } else {
              // Other error - clear room state
              setCurrentRoom(null);
              toast.error('Failed to recover room state. Please rejoin the room.', { duration: 4000 });
            }
          }
        }
      } catch (error) {
        console.error('Failed to initialize WebSocket connection:', error);
        toast.error('Connection failed. Please check your internet connection.', { duration: 4000 });
      }
    };

    initializeConnection();

    return () => {
      // Only disconnect if no room is active
      if (!currentRoom) {
        webSocketService.disconnect();
      }
    };
  }, [token, user]); // Don't include currentRoom here to avoid infinite loops

  useEffect(() => {
    if (!currentRoom || !token) return;

    let unsubscribeRoom: (() => void) | undefined;
    let unsubscribeClosure: (() => void) | undefined;

    const setupSubscriptions = async () => {
      try {
        // Ensure WebSocket is connected before setting up subscriptions
        let attempts = 0;
        const maxAttempts = 10;
        
        while (!webSocketService.isConnected() && attempts < maxAttempts) {
          console.log(`Waiting for WebSocket connection... (attempt ${attempts + 1}/${maxAttempts})`);
          if (attempts === 0) {
            // Try to connect if not already connected
            try {
              await webSocketService.connect(token);
            } catch (connectError) {
              console.error('Failed to connect WebSocket:', connectError);
            }
          }
          
          // Wait 500ms before next check
          await new Promise(resolve => setTimeout(resolve, 500));
          attempts++;
        }

        if (!webSocketService.isConnected()) {
          console.error('Failed to establish WebSocket connection after multiple attempts');
          toast.error('Real-time updates unavailable. Please refresh the page.', { duration: 5000 });
          return;
        }

        console.log('Setting up WebSocket subscriptions for room:', currentRoom.roomId);
        
        unsubscribeRoom = webSocketService.subscribeToRoom(
          currentRoom.roomId,
          (message: WebSocketMessage) => {
            handleWebSocketMessage(message);
          }
        );

        unsubscribeClosure = webSocketService.subscribeToRoomClosure(
          currentRoom.roomId,
          () => {
            console.log('Room closure message received for room:', currentRoom.roomId);
            toast.error('Room has been closed by the host', { duration: 4000 });
            // Clear all room state
            setCurrentRoom(null);
            setDrawResult(null);
            setMenus([]);
            setMenuStatus(null);
            // Disconnect WebSocket when room is closed
            webSocketService.disconnect();
          }
        );

        console.log('WebSocket subscriptions established successfully');
      } catch (error) {
        console.error('Failed to setup WebSocket subscriptions:', error);
        toast.error('Failed to establish real-time connection. Some features may not work properly.', { duration: 5000 });
      }
    };

    setupSubscriptions();

    return () => {
      unsubscribeRoom?.();
      unsubscribeClosure?.();
    };
  }, [currentRoom?.roomId, token]);

  const handleWebSocketMessage = (message: WebSocketMessage) => {
    console.log('WebSocket message received:', message); // Debug log
    
    switch (message.type) {
      case 'ROOM_STATE_UPDATE':
        console.log('Updating room state:', message.payload);
        setCurrentRoom(message.payload);
        break;
      case 'PARTICIPANT_UPDATE':
        console.log('Updating participants:', message.payload);
        // Update participants list and users array
        if (currentRoom) {
          const oldUsers = currentRoom.users || [];
          const newUsers = message.payload.map((p: any) => p.username);
          
          // Check for new users
          const joinedUsers = newUsers.filter((username: string) => !oldUsers.includes(username));
          const leftUsers = oldUsers.filter((username: string) => !newUsers.includes(username));
          
          // Show notifications for joins/leaves (but not for the current user)
          joinedUsers.forEach((username: string) => {
            if (username !== user?.username) {
              toast.success(`${username} joined the room`);
            }
          });
          
          leftUsers.forEach((username: string) => {
            if (username !== user?.username) {
              toast.info(`${username} left the room`);
            }
          });
          
          setCurrentRoom(prev => prev ? { 
            ...prev, 
            participants: message.payload,
            users: newUsers
          } : null);
        }
        break;
      case 'MENU_STATUS_UPDATE':
        console.log('Menu status update:', message.payload);
        console.log('User submit status:', message.payload.userSubmitStatus);
        console.log('Submitted count:', Object.values(message.payload.userSubmitStatus || {}).filter(Boolean).length);
        setMenuStatus(message.payload);
        
        // Convert submitted menus to MenuOption format for existing components
        const menuOptions: MenuOption[] = Object.entries(message.payload.submittedMenusByUsers || {})
          .map(([username, menuItems]) => ({ username, menuItems }));
        setMenus(menuOptions);
        break;
      case 'draw_result':
        console.log('Draw result received:', message.payload);
        // Backend sends {"selectedMenu": "pizza"}, convert to expected format
        const drawResultData = {
          selectedMenu: [message.payload.selectedMenu], // Convert string to array
          selectedUser: 'Random Selection' // Backend doesn't send user info yet
        };
        setDrawResult(drawResultData);
        break;
      default:
        console.log('Unknown WebSocket message type:', message.type, message);
    }
  };

  const createRoom = async (roomName: string, maxUsers: number, isPrivate: boolean, password?: string) => {
    try {
      setIsLoading(true);
      const response = await roomAPI.createRoom({ roomName, maxUsers, isPrivate, password });
      setCurrentRoom(response);
      toast.success('Room created successfully!');
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Failed to create room');
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const joinRoom = async (roomId: string, password?: string) => {
    try {
      setIsLoading(true);
      
      // Ensure WebSocket is connected before joining
      if (token && !webSocketService.isConnected()) {
        console.log('Connecting to WebSocket before joining room...');
        await webSocketService.connect(token);
      }
      
      const response = await roomAPI.joinRoom(roomId, password);
      console.log('Join room response:', response); // Debug log
      setCurrentRoom(response); // Backend returns RoomDetails directly
      
      // The WebSocket should handle real-time updates, so we rely on that
      // instead of polling for room state
      
      toast.success('Joined room successfully!');
    } catch (error: any) {
      console.error('Join room error:', error); // Debug log
      toast.error(error.response?.data?.message || 'Failed to join room');
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const leaveRoom = async () => {
    if (!currentRoom) return;
    
    try {
      setIsLoading(true);
      await roomAPI.leaveRoom(currentRoom.roomId);
      
      // Clear room state and localStorage
      setCurrentRoom(null);
      setDrawResult(null);
      setMenus([]);
      setMenuStatus(null);
      
      // Disconnect WebSocket when leaving room
      webSocketService.disconnect();
      
      toast.success('Left room successfully');
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Failed to leave room');
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const startDraw = async () => {
    if (!currentRoom) {
      toast.error('No room selected');
      return;
    }

    try {
      setIsLoading(true);
      await menuAPI.startDraw(currentRoom.roomId);
      toast.success('Draw started!');
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Failed to start draw');
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const value: RoomContextType = {
    currentRoom,
    drawResult,
    menus,
    menuStatus,
    isLoading,
    joinRoom,
    leaveRoom,
    createRoom,
    startDraw,
  };

  return <RoomContext.Provider value={value}>{children}</RoomContext.Provider>;
};