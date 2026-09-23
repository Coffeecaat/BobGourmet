import { WebSocketMessage } from '../types';
import { webSocketService } from './websocket';

// A disposer is available immediately, including while CONNECT is still pending.
export function subscribeRoomSession(roomId: string, handlers: {
  message: (message: WebSocketMessage, initial: boolean) => void;
  closed: () => void;
  ready: () => void;
  error: (error: unknown) => void;
}): () => void {
  let cancelled = false;
  const subscriptions: (() => void)[] = [];
  const dispose = () => {
    cancelled = true;
    subscriptions.splice(0).forEach(unsubscribe => unsubscribe());
  };
  void webSocketService.connect().then(() => {
    if (cancelled) return;
    const receive = (message: WebSocketMessage, initial = false) => {
      // Initial snapshots must identify the requested room, even on the same socket.
      if (initial && message.roomId !== roomId) return;
      if (!cancelled) handlers.message(message, initial);
    };
    try {
      subscriptions.push(webSocketService.subscribeToInitialState(message => receive(message, true)));
      subscriptions.push(webSocketService.subscribeToRoomClosure(roomId, () => {
        if (!cancelled) handlers.closed();
      }));
      subscriptions.push(webSocketService.subscribeToMenuStatus(roomId, receive));
      // This subscription triggers the backend's initial room/menu snapshots.
      subscriptions.push(webSocketService.subscribeToRoom(roomId, receive));
      if (!cancelled) handlers.ready();
    } catch (error) {
      dispose();
      handlers.error(error);
    }
  }, error => {
    if (!cancelled) handlers.error(error);
  });
  return dispose;
}
