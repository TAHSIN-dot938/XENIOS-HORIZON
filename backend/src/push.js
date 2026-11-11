// Decides whether/what to push for a notification row. Pure; sending is injected.

const PREF_FOR_KIND = { message: 'messages', friend_request: 'friend_requests', friend_accepted: 'friend_requests', call_missed: 'calls' };

export function shouldPush(kind, prefs) {
  const key = PREF_FOR_KIND[kind];
  return Boolean(key) && prefs?.[key] !== false;
}

const trunc = (s, n) => (typeof s === 'string' && s.length > n ? s.slice(0, n - 1) + '…' : s ?? '');

/** FCM data-only message (the app builds the notification so channel/sound/prefs apply). */
export function buildNotificationMessage(notification, actor, prefs) {
  const name = actor?.display_name || actor?.username || 'Someone';
  const titles = {
    message: name,
    friend_request: 'New friend request',
    friend_accepted: 'Friend request accepted',
    call_missed: 'Missed call',
  };
  const bodies = {
    message: trunc(notification.payload?.preview, 120),
    friend_request: `${name} wants to be your friend`,
    friend_accepted: `${name} accepted your request`,
    call_missed: `Missed call from ${name}`,
  };
  return {
    data: {
      type: notification.kind,
      notification_id: String(notification.id),
      title: titles[notification.kind] ?? 'THE HORIZON',
      body: bodies[notification.kind] ?? '',
      conversation_id: String(notification.payload?.conversation_id ?? ''),
      actor_id: String(notification.actor_id ?? ''),
      sound: String(prefs?.sound ?? 'horizon_chime'),
      vibrate: String(prefs?.vibrate !== false),
    },
    android: { priority: 'high' },
  };
}

export function buildIncomingCallMessage(call, caller) {
  return {
    data: {
      type: 'call_incoming',
      call_id: call.id,
      kind: call.kind,
      caller_id: call.callerId,
      caller_name: caller?.display_name || caller?.username || 'Unknown',
    },
    android: { priority: 'high', ttl: 45_000 },
  };
}

/** FCM error codes meaning the token is permanently dead and should be deleted. */
export function isDeadTokenError(code) {
  return code === 'messaging/registration-token-not-registered' || code === 'messaging/invalid-registration-token';
}
