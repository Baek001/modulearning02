import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

export default function LoginPage() {
    const { login } = useAuth();
    const navigate = useNavigate();
    const [identifier, setIdentifier] = useState('');
    const [userPw, setUserPw] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    async function handleSubmit(event) {
        event.preventDefault();
        if (!identifier.trim() || !userPw.trim()) {
            setError('이메일 또는 로그인 ID와 비밀번호를 입력해 주세요.');
            return;
        }

        setError('');
        setLoading(true);
        try {
            await login(identifier, userPw);
            navigate('/', { replace: true });
        } catch (requestError) {
            setError(requestError.response?.data?.message || '로그인에 실패했습니다. 이메일 또는 로그인 ID와 비밀번호를 확인해 주세요.');
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="login-container">
            <div className="login-card">
                <div className="login-logo">
                    <div className="logo-mark">ML</div>
                    <h2>모두의 러닝</h2>
                    <p>멀티테넌트 그룹웨어 플랫폼에 로그인합니다.</p>
                </div>

                <form onSubmit={handleSubmit}>
                    {error && (
                        <div className="alert alert-error" style={{ marginBottom: 'var(--spacing-md)' }}>
                            {error}
                        </div>
                    )}

                    <div className="form-group">
                        <label className="form-label" htmlFor="identifier">이메일 또는 로그인 ID</label>
                        <input
                            id="identifier"
                            className="form-input"
                            type="text"
                            placeholder="name@company.com"
                            value={identifier}
                            onChange={(event) => setIdentifier(event.target.value)}
                            autoComplete="username"
                            autoFocus
                        />
                    </div>

                    <div className="form-group">
                        <label className="form-label" htmlFor="userPw">비밀번호</label>
                        <input
                            id="userPw"
                            className="form-input"
                            type="password"
                            placeholder="비밀번호를 입력해 주세요"
                            value={userPw}
                            onChange={(event) => setUserPw(event.target.value)}
                            autoComplete="current-password"
                        />
                    </div>

                    <button type="submit" className="btn btn-primary" disabled={loading}>
                        {loading ? '로그인 중...' : '로그인'}
                    </button>
                </form>

                <div style={{ marginTop: 'var(--spacing-lg)', textAlign: 'center', fontSize: 'var(--font-size-sm)' }}>
                    처음 사용하는 회사라면 <Link to="/signup">워크스페이스 만들기</Link>
                </div>
            </div>
        </div>
    );
}
