// Builds WebRTC ICE servers. TURN uses coturn's "use-auth-secret" ephemeral credentials.
import { createHmac } from 'node:crypto';

export function buildIceServers({ turnUrl, turnSecret, userId, nowSec, ttlSec = 3600 }) {
  const servers = [{ urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'] }];
  if (turnUrl && turnSecret) {
    const username = `${nowSec + ttlSec}:${userId}`;
    const credential = createHmac('sha1', turnSecret).update(username).digest('base64');
    servers.push({ urls: turnUrl.split(',').map((u) => u.trim()).filter(Boolean), username, credential });
  }
  return { iceServers: servers, ttlSec, turnConfigured: Boolean(turnUrl && turnSecret) };
}
