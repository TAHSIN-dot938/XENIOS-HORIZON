// Pure input validation for socket events. No dependencies.
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
export const CALL_KINDS = ['audio', 'video'];
export const MAX_SDP_BYTES = 20_000;
export const MAX_CANDIDATE_BYTES = 2_000;

export const isUuid = (v) => typeof v === 'string' && UUID.test(v);

export function validateInvite(p) {
  if (!p || typeof p !== 'object') return 'invalid_payload';
  if (!isUuid(p.calleeId)) return 'invalid_callee';
  if (!CALL_KINDS.includes(p.kind)) return 'invalid_kind';
  return null;
}

export function validateSignal(p) {
  if (!p || typeof p !== 'object') return 'invalid_payload';
  if (!isUuid(p.callId)) return 'invalid_call';
  if (p.sdp !== undefined) {
    if (typeof p.sdp !== 'string' || Buffer.byteLength(p.sdp) > MAX_SDP_BYTES) return 'invalid_sdp';
    if (p.type !== 'offer' && p.type !== 'answer') return 'invalid_type';
  } else if (p.candidate !== undefined) {
    if (typeof p.candidate !== 'string' || Buffer.byteLength(p.candidate) > MAX_CANDIDATE_BYTES) return 'invalid_candidate';
  } else return 'empty_signal';
  return null;
}

export function validateCallId(p) {
  return p && isUuid(p.callId) ? null : 'invalid_call';
}
