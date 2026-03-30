const CHAIN_ICON = '⬡';

const StatusBadge = ({ status }) => {
  const isActive = status === 'ACTIVE';
  return (
    <span className={`badge ${isActive ? 'badge-active' : 'badge-retired'}`}>
      <span style={{ fontSize: 8 }}>●</span>
      {status}
    </span>
  );
};

/**
 * LandRecord — displays a single LandAsset returned from the blockchain.
 * @param {{ data: object, compact?: boolean }} props
 */
export default function LandRecord({ data, compact = false }) {
  if (!data) return null;

  return (
    <div className="land-record">
      <div className="record-header">
        <span className="record-ulpin">{data.ulpin}</span>
        <StatusBadge status={data.status} />
      </div>

      <div className="record-body">
        <div className="record-field">
          <span className="record-label">Current Owner</span>
          <span className="record-value">{data.currentOwnerId}</span>
        </div>

        <div className="record-field">
          <span className="record-label">GPS Coordinates</span>
          <span className="record-value mono">{data.gpsCoordinates}</span>
        </div>

        <div className="record-field">
          <span className="record-label">Parent ULPIN</span>
          <span className="record-value mono">{data.parentUlpin ?? 'None (Root Asset)'}</span>
        </div>

        {!compact && (
          <div className="record-field full">
            <span className="record-label">Document Hash (IPFS / SHA-256)</span>
            <span className="record-value mono">{data.documentHash}</span>
          </div>
        )}
      </div>

      <div className="record-footer">
        <span>{CHAIN_ICON}</span>
        <span>Immutable Record · Hyperledger Fabric · landchannel</span>
      </div>
    </div>
  );
}
