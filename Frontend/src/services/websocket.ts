import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { WebSocketMessage } from '../types';

class WebSocketService {
  private client: Client | null = null;
  private connected = false;
  private token: string | null = null;

  connect(token: string): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.connected && this.token === token) {
        resolve();
        return;
      }

      // Store token for reconnection
      this.token = token;

      // Disconnect existing connection if different token
      if (this.client && this.token !== token) {
        this.client.deactivate();
      }

      const wsUrl = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws-BobGourmet';
      const sockjsUrl = wsUrl.replace('ws://', 'http://').replace('wss://', 'https://');

      this.client = new Client({
        brokerURL: wsUrl,
        webSocketFactory: () => new SockJS(sockjsUrl, null, {
          timeout: 30000,
        }),
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
        debug: (str) => {
          console.log('STOMP Debug:', str);
        },
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onWebSocketError: (event) => {
          console.error('WebSocket error:', event);
        },
        onWebSocketClose: (event) => {
          console.log('WebSocket closed:', event);
          this.connected = false;
        },
      });

      this.client.onConnect = () => {
        console.log('WebSocket connected');
        this.connected = true;
        resolve();
      };

      this.client.onStompError = (frame) => {
        console.error('WebSocket error:', frame);
        reject(new Error('WebSocket connection failed'));
      };

      this.client.onDisconnect = () => {
        console.log('WebSocket disconnected');
        this.connected = false;
      };

      this.client.onWebSocketError = (error) => {
        console.error('WebSocket connection error:', error);
        this.connected = false;
      };

      // Add automatic reconnection on disconnect
      this.client.onDisconnect = () => {
        console.log('WebSocket disconnected');
        this.connected = false;
        
        // Auto-reconnect if we have a token (means user is still authenticated)
        if (this.token) {
          console.log('Attempting to reconnect...');
          setTimeout(() => {
            if (this.token && !this.connected) {
              this.connect(this.token).catch(console.error);
            }
          }, 5000);
        }
      };

      this.client.activate();
    });
  }

  disconnect(): void {
    if (this.client) {
      this.client.deactivate();
      this.connected = false;
    }
    // Clear token to prevent auto-reconnection
    this.token = null;
  }

  subscribeToRoom(roomId: string, onMessage: (message: WebSocketMessage) => void): () => void {
    if (!this.client || !this.connected) {
      throw new Error('WebSocket not connected');
    }

    const subscription = this.client.subscribe(
      `/topic/room/${roomId}/events`,
      (message) => {
        try {
          const parsedMessage: WebSocketMessage = JSON.parse(message.body);
          onMessage(parsedMessage);
        } catch (error) {
          console.error('Failed to parse WebSocket message:', error);
        }
      }
    );

    return () => {
      subscription.unsubscribe();
    };
  }

  subscribeToRoomClosure(roomId: string, onRoomClosed: () => void): () => void {
    if (!this.client || !this.connected) {
      throw new Error('WebSocket not connected');
    }

    const subscription = this.client.subscribe(
      `/topic/room/${roomId}/closed`,
      () => {
        onRoomClosed();
      }
    );

    return () => {
      subscription.unsubscribe();
    };
  }

  isConnected(): boolean {
    return this.connected;
  }
}

export const webSocketService = new WebSocketService();