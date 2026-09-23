import { DrawResult, MenuStatus, Room, WebSocketMessage } from '../types';

export interface RoomState {
  room: Room | null;
  menuStatus: MenuStatus | null;
  drawResult: DrawResult | null;
}

export const emptyRoomState = (): RoomState => ({ room: null, menuStatus: null, drawResult: null });

// Lombok/Jackson boolean getters serialize isPrivate/isExcluded as private/excluded.
export function normalizeRoom(room: Room & { private?: boolean }): Room {
  return { ...room, isPrivate: room.private ?? room.isPrivate ?? false };
}

export function normalizeMenuStatus(status: MenuStatus): MenuStatus {
  return {
    ...status,
    menuVotes: Object.fromEntries(Object.entries(status.menuVotes).map(([key, vote]) => [
      key, { ...vote, isExcluded: (vote as typeof vote & { excluded?: boolean }).excluded ?? vote.isExcluded ?? false },
    ])),
  };
}

export function reduceRoomMessage(state: RoomState, message: WebSocketMessage, roomId: string, username: string): RoomState {
  if (!state.room || state.room.roomId !== roomId) return state;
  switch (message.type) {
    case 'ROOM_STATE_UPDATE': {
      if (message.payload.roomId !== roomId) return state;
      if (!message.payload.users.includes(username)) return emptyRoomState();
      const room = normalizeRoom(message.payload);
      const newRound = ['waiting', 'inputting'].includes(room.state) &&
        ['result_viewing', 'submitted'].includes(state.room.state);
      return { ...state, room, drawResult: newRound ? null : state.drawResult };
    }
    case 'PARTICIPANT_UPDATE': {
      const users = message.payload.map(participant => participant.username);
      if (!users.includes(username)) return emptyRoomState();
      return { ...state, room: { ...state.room, participants: message.payload, users } };
    }
    case 'MENU_STATUS_UPDATE':
      return { ...state, menuStatus: normalizeMenuStatus(message.payload) };
    case 'draw_result':
      return { ...state, drawResult: { selectedMenu: [message.payload.selectedMenu], selectedUser: 'Random Selection' } };
    default:
      return state;
  }
}
