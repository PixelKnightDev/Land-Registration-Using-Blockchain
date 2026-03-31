import { useState } from 'react';
import axios from 'axios';

// Ensure your API URL matches the others in your project
const API_URL = 'http://localhost:8080/api/land'; 

export default function AssetHistory() {
  const [ulpin, setUlpin] = useState('');
  const [history, setHistory] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchHistory = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get(`${API_URL}/${ulpin}/history`);
      // Sort the array so the newest transactions are at the top
      const sortedHistory = response.data.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
      setHistory(sortedHistory);
    } catch (err) {
      setError(err.response?.data || 'Failed to fetch audit trail from the blockchain.');
      setHistory([]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card">
      <div className="card-header">
        <h2 className="card-title">Immutable Audit Trail</h2>
        <p className="card-subtitle">Query the cryptographic ledger history for an asset.</p>
      </div>

      <div className="card-body">
        <form className="form-grid" onSubmit={fetchHistory}>
          <div className="form-group">
            <label>Asset ULPIN</label>
            <input
              required
              placeholder="e.g. MP-JBP-2026-003"
              value={ulpin}
              onChange={(e) => setUlpin(e.target.value)}
            />
          </div>
          <button className="btn btn-primary" disabled={loading} style={{ gridColumn: '1 / -1' }}>
            {loading ? 'Querying Ledger...' : 'Trace Provenance'}
          </button>
        </form>

        {error && <div className="alert alert-error">{error}</div>}

        {history.length > 0 && (
          <div style={{ marginTop: '2rem', borderLeft: '3px solid #0056b3', paddingLeft: '20px' }}>
            <h3 style={{ marginBottom: '1rem', color: '#333' }}>Ledger Records</h3>
            {history.map((record, index) => (
              <div key={index} style={{ marginBottom: '1.5rem', position: 'relative' }}>
                <div style={{ position: 'absolute', left: '-29px', top: '5px', width: '15px', height: '15px', borderRadius: '50%', backgroundColor: '#0056b3', border: '3px solid white' }} />
                
                <span style={{ fontSize: '0.85rem', color: '#666', fontWeight: 'bold' }}>
                  {new Date(record.timestamp).toLocaleString()}
                </span>
                
                <div style={{ backgroundColor: '#f4faff', padding: '10px', borderRadius: '6px', marginTop: '5px', border: '1px solid #cce5ff' }}>
                  <div style={{ marginBottom: '5px' }}>
                    <strong>TxID: </strong>
                    <span style={{ fontFamily: 'monospace', color: '#d63384' }}>{record.txId}</span>
                  </div>
                  {record.isDeleted ? (
                    <span style={{ color: 'red', fontWeight: 'bold' }}>ASSET DELETED (MUTATED)</span>
                  ) : (
                    <>
                      <div><strong>Owner ID:</strong> {record.value.currentOwnerId}</div>
                      <div><strong>Status:</strong> {record.value.status}</div>
                    </>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}