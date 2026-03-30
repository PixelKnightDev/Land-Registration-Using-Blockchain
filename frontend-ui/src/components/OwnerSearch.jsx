import { useState } from 'react';
import { getLandByOwner } from '../services/api';
import LandRecord from './LandRecord';

export default function OwnerSearch() {
  const [ownerId, setOwnerId] = useState('');
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState(null);
  const [error, setError] = useState(null);

  const onSearch = async (e) => {
    e.preventDefault();
    setLoading(true);
    setResults(null);
    setError(null);

    try {
      const data = await getLandByOwner(ownerId.trim());
      // data may be an array or a JSON-encoded array string
      const list = Array.isArray(data) ? data : JSON.parse(data);
      setResults(list);
    } catch (err) {
      setError(`Query failed: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Search by Owner</h1>
        <p className="page-subtitle">
          CouchDB rich query — returns all land assets registered to an Aadhaar ID
        </p>
      </div>

      <div className="card">
        <div className="card-title">
          <span>◈</span> Owner Lookup
        </div>

        <form onSubmit={onSearch}>
          <div className="search-bar">
            <input
              className="field-input"
              value={ownerId}
              onChange={(e) => setOwnerId(e.target.value)}
              placeholder="e.g. AADHAR-1122-3344"
              required
            />
            <button className="btn btn-primary" type="submit" disabled={loading}>
              {loading ? <span className="spinner" /> : 'Query Ledger'}
            </button>
          </div>
        </form>

        {error && (
          <div className="alert alert-error fade-in" style={{ marginTop: 16 }}>
            {error}
          </div>
        )}

        {results !== null && results.length === 0 && (
          <div className="alert alert-info fade-in" style={{ marginTop: 16 }}>
            No land assets found for this owner ID.
          </div>
        )}

        {results !== null && results.length > 0 && (
          <div className="alert alert-success fade-in" style={{ marginTop: 16 }}>
            Found {results.length} asset{results.length !== 1 ? 's' : ''} registered to {ownerId}
          </div>
        )}
      </div>

      {results && results.length > 0 && (
        <div className="records-list fade-in">
          {results.map((asset) => (
            <LandRecord key={asset.ulpin} data={asset} compact />
          ))}
        </div>
      )}
    </>
  );
}
