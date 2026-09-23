export interface User {
  username: string;
  email: string;
  nickname?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface SignupRequest {
  username: string;
  email: string;
  nickname: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string | null;
}

export interface CreateRoomRequest {
  roomName: string;
  maxUsers: number;
  isPrivate: boolean;
  password?: string;
}

export interface JoinRoomRequest {
  password?: string;
}

export interface Participant {
  username: string;
  nickname: string;
  endpoint: string;
  submittedMenu: boolean;
}

export interface Room {
  roomId: string;
  roomName: string;
  hostUsername: string;
  hostIpAddress: string;
  hostPort: number;
  maxUsers: number;
  users: string[];
  participants: Participant[];
  state: string;
  isPrivate: boolean;
  hostNickname: string;
}

export interface MenuStatus {
  submittedMenusByUsers: { [username: string]: string[] };
  menuVotes: { [menuKey: string]: MenuVoteInfo };
  dislikedAndExcludedMenuKeys: string[];
  userSubmitStatus: { [username: string]: boolean };
}

export interface MenuVoteInfo {
  recommenders: string[];
  submitters: string[];
  dislikedBy: string[];
  isExcluded: boolean;
}

export interface MenuSubmission {
  menus: string[];
}

export interface MenuOption {
  username: string;
  menuItems: string[];
}

export interface DrawResult {
  selectedMenu: string[];
  selectedUser: string;
}

export type WebSocketMessage = { roomId?: string } & (
  | { type: 'ROOM_STATE_UPDATE'; payload: Room }
  | { type: 'PARTICIPANT_UPDATE'; payload: Participant[] }
  | { type: 'MENU_STATUS_UPDATE'; payload: MenuStatus }
  | { type: 'draw_result'; payload: { selectedMenu: string } });
