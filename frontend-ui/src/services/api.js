const API_BASE = 'http://localhost:8080/api/land';

// Parse response — backend returns raw JSON strings from the blockchain
const parseResponse = async (res) => {
  const text = await res.text();
  if (!res.ok) throw new Error(text || `HTTP ${res.status}`);
  try { return JSON.parse(text); } catch { return text; }
};

/** POST /api/land — Register a new land asset */
export const registerLand = (data) =>
  fetch(API_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  }).then(parseResponse);

/** GET /api/land/{ulpin} — Query a land asset by ULPIN */
export const getLandByUlpin = (ulpin) =>
  fetch(`${API_BASE}/${encodeURIComponent(ulpin)}`).then(parseResponse);

/** GET /api/land/owner/{ownerId} — Query all assets for an owner */
export const getLandByOwner = (ownerId) =>
  fetch(`${API_BASE}/owner/${encodeURIComponent(ownerId)}`).then(parseResponse);

/**
 * PUT /api/land/{ulpin}/transfer — Transfer ownership
 *
 * NOTE: The backend service layer currently passes empty strings for sellerId
 * and newDocumentHash to the chaincode, which will reject the transaction.
 * The backend DTO and service must be fixed to forward all required fields.
 * This client sends the full payload in anticipation of that fix.
 */
export const transferOwnership = (ulpin, data) =>
  fetch(`${API_BASE}/${encodeURIComponent(ulpin)}/transfer`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data), // { newOwnerId, sellerId, newDocumentHash }
  }).then(parseResponse);

/**
 * POST /api/land/{ulpin}/mutate — Split a parcel into two children
 *
 * NOTE: The backend currently passes the wrong number of arguments to the
 * chaincode mutateLand function (5 instead of the required 7).
 * Fix the backend service before relying on this endpoint.
 */
export const mutateLand = (ulpin, data) =>
  fetch(`${API_BASE}/${encodeURIComponent(ulpin)}/mutate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  }).then(parseResponse);
