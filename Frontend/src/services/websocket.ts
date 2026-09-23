import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { WebSocketMessage } from '../types';

export class WebSocketService {
  private client: Client | null = null;
  private connected = false;
  private connecting: Promise<void> | null = null;
  private rejectConnection: ((error: Error) => void) | null = null;
  private stopping: Promise<void> = Promise.resolve();
  private disconnectListeners = new Set<() => void>();

  connect(): Promise<void> {
    if (this.connected) return Promise.resolve();
    if (this.connecting) return this.connecting;

    const connection = new Promise<void>((resolve, reject) => {
      let settled = false;
      const timer = setTimeout(() => {
        fail(new Error('실시간 연결 시간이 초과되었습니다. 다시 시도해주세요.'));
        void this.disconnect();
      }, 15000);
      const fail = (error: Error) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        reject(error);
      };
      this.rejectConnection = fail;

      void this.stopping.then(() => {
        if (settled) return;
        const url = import.meta.env.VITE_WS_URL || `${window.location.origin}/ws-BobGourmet`;
        const sockjsUrl = url.replace(/^ws:/, 'http:').replace(/^wss:/, 'https:');
        const client = new Client({
          webSocketFactory: () => new SockJS(sockjsUrl),
          connectHeaders: {}, // HttpOnly cookie authenticates the handshake/CONNECT.
          // The server treats disconnect as leave. Do not silently reconnect/rejoin.
          reconnectDelay: 0,
          connectionTimeout: 15000,
          heartbeatIncoming: 10000,
          heartbeatOutgoing: 10000,
          onConnect: () => {
            if (this.client !== client || settled) return;
            settled = true;
            clearTimeout(timer);
            this.connected = true;
            resolve();
          },
          onStompError: () => {
            fail(new Error('실시간 연결 인증 또는 메시지 처리에 실패했습니다.'));
            this.handleConnectionLoss(client);
          },
          onWebSocketError: () => {
            fail(new Error('실시간 서버에 연결할 수 없습니다.'));
            this.handleConnectionLoss(client);
          },
          onWebSocketClose: () => {
            fail(new Error('실시간 연결이 종료되었습니다.'));
            this.handleConnectionLoss(client);
          },
        });
        this.client = client;
        try {
          client.activate();
        } catch (error) {
          fail(error instanceof Error ? error : new Error('실시간 연결 실패'));
          this.handleConnectionLoss(client);
        }
      }).catch(error => fail(error instanceof Error ? error : new Error('실시간 연결 실패')));
    });
    this.connecting = connection;
    const clear = () => {
      if (this.connecting === connection) {
        this.connecting = null;
        this.rejectConnection = null;
      }
    };
    void connection.then(clear, clear);
    return connection;
  }

  private handleConnectionLoss(client: Client): void {
    if (this.client !== client) return;
    const wasConnected = this.connected;
    void this.disconnect();
    if (wasConnected) this.disconnectListeners.forEach(listener => listener());
  }

  disconnect(): Promise<void> {
    this.connected = false;
    this.rejectConnection?.(new Error('실시간 연결이 취소되었습니다.'));
    this.rejectConnection = null;
    this.connecting = null;
    const client = this.client;
    this.client = null;
    // Do not wait for the client's graceful DISCONNECT receipt on a simple broker.
    if (client) this.stopping = client.deactivate({ force: true });
    return this.stopping;
  }

  onConnectionLost(listener: () => void): () => void {
    this.disconnectListeners.add(listener);
    return () => { this.disconnectListeners.delete(listener); };
  }

  private subscribe(destination: string, callback: (body: string) => void): () => void {
    const client = this.client;
    if (!client || !this.connected) throw new Error('WebSocket not connected');
    let active = true;
    const subscription = client.subscribe(destination, message => {
      if (active && this.client === client && this.connected) callback(message.body);
    });
    // Sending SUBSCRIBE is not server acknowledgement. Spring simple broker has no receipts.
    return () => {
      if (!active) return;
      active = false;
      if (this.client === client && this.connected) subscription.unsubscribe();
    };
  }

  private subscribeMessages(destination: string, callback: (message: WebSocketMessage) => void): () => void {
    return this.subscribe(destination, body => {
      let message: WebSocketMessage;
      try {
        message = JSON.parse(body);
      } catch {
        console.warn('잘못된 실시간 메시지 형식을 무시했습니다.');
        return;
      }
      callback(message);
    });
  }

  subscribeToInitialState(callback: (message: WebSocketMessage) => void): () => void {
    return this.subscribeMessages('/user/queue/events', callback);
  }

  subscribeToRoom(roomId: string, callback: (message: WebSocketMessage) => void): () => void {
    return this.subscribeMessages(`/topic/room/${roomId}/events`, callback);
  }

  subscribeToRoomClosure(roomId: string, callback: () => void): () => void {
    return this.subscribe(`/topic/room/${roomId}/closed`, callback);
  }

  subscribeToMenuStatus(roomId: string, callback: (message: WebSocketMessage) => void): () => void {
    return this.subscribeMessages(`/topic/room/${roomId}/menuStatus`, callback);
  }

  isConnected(): boolean {
    return this.connected;
  }
}

export const webSocketService = new WebSocketService();
