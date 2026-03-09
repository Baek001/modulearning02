export default function UserAvatar({
    userName = '',
    filePath = '',
    className = '',
    style = {},
    alt,
}) {
    const fallbackLabel = (userName || '?').trim().charAt(0).toUpperCase() || '?';

    return (
        <div
            className={className}
            style={{
                overflow: 'hidden',
                display: 'grid',
                placeItems: 'center',
                ...style,
            }}
        >
            {filePath ? (
                <img
                    src={filePath}
                    alt={alt || `${userName || 'user'} profile`}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                />
            ) : (
                fallbackLabel
            )}
        </div>
    );
}
