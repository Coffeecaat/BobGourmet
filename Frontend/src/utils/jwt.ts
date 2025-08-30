export interface JWTPayload {
  sub: string; // username
  nickname?: string;
  iat: number; // issued at
  exp: number; // expiration
}

export function decodeJWT(token: string): JWTPayload | null {
  try {
    // JWT has 3 parts separated by dots: header.payload.signature
    const parts = token.split('.');
    if (parts.length !== 3) {
      return null;
    }

    // Decode the payload (second part)
    const payload = parts[1];
    
    // Add padding if needed for base64 decoding
    const paddedPayload = payload + '='.repeat((4 - payload.length % 4) % 4);
    
    // Decode base64 with proper UTF-8 handling
    const decodedBytes = Uint8Array.from(atob(paddedPayload), c => c.charCodeAt(0));
    const decoder = new TextDecoder('utf-8');
    const decodedPayload = decoder.decode(decodedBytes);
    
    // Parse JSON
    return JSON.parse(decodedPayload) as JWTPayload;
  } catch (error) {
    console.error('Failed to decode JWT:', error);
    return null;
  }
}

export function getUserFromJWT(token: string): { username: string; nickname?: string } | null {
  const payload = decodeJWT(token);
  if (!payload) {
    return null;
  }

  return {
    username: payload.sub,
    nickname: payload.nickname
  };
}