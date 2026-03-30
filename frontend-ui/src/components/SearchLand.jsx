import { useState } from 'react';
import { getLandByUlpin } from '../services/api';
import LandRecord from './LandRecord';

export default function SearchLand() {
  const [ulpin, setUlpin] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const onSearch = async (e) => {
    e.preventDefault();
    setLoading(true);
    setResult(null);
    setError(null);

    try {
      const data = await getLandByUlpin(ulpin.trim());
      setResult(data);
    } catch (err) {
      setError(
        err.message?.includes('404') || err.message?.toLowerCase().includes('not found')
          ? `No asset found for ULPIN: ${ulpin}`
          : `Ledger query failed: ${err.message}`
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Search by ULPIN</h1>
        <p className="page-subtitle">
          Query the world state for an exact Unique Land Parcel Identification Number
        </p>
      </div>

      <div className="card">
        <div className="card-title">
          <span>◎</span> ULPIN Lookup
        </div>

        <form onSubmit={onSearch}>
          <div className="search-bar">
            <input
              className="field-input mono"
              value={ulpin}
              onChange={(e) => setUlpin(e.target.value)}
              placeholder="e.g. MP-JBP-2026-003"
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
      </div>

      {result && <LandRecord data={result} />}
    </>
  );
}
