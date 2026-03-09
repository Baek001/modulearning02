import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { publicPlatformAPI } from '../services/api';

export default function SignupPage() {
    const { login } = useAuth();
    const navigate = useNavigate();
    const [ownerEmail, setOwnerEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [signupConfig, setSignupConfig] = useState({
        signupEnabled: true,
        turnstileEnabled: false,
    });
    const [configLoading, setConfigLoading] = useState(true);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        let active = true;

        async function loadSignupConfig() {
            setConfigLoading(true);
            try {
                const response = await publicPlatformAPI.signupConfig();
                if (!active) {
                    return;
                }

                setSignupConfig({
                    signupEnabled: response.data?.signupEnabled !== false,
                    turnstileEnabled: Boolean(response.data?.turnstileEnabled),
                });
            } catch (requestError) {
                if (!active) {
                    return;
                }

                setSignupConfig({
                    signupEnabled: false,
                    turnstileEnabled: false,
                });
                setError(requestError.response?.data?.message || '회원가입 설정 정보를 불러오지 못했습니다.');
            } finally {
                if (active) {
                    setConfigLoading(false);
                }
            }
        }

        loadSignupConfig();
        return () => {
            active = false;
        };
    }, []);

    async function handleSubmit(event) {
        event.preventDefault();
        if (!ownerEmail.trim() || !password.trim() || !confirmPassword.trim()) {
            setError('이메일, 비밀번호, 비밀번호 확인을 입력해 주세요.');
            return;
        }
        if (password !== confirmPassword) {
            setError('비밀번호와 비밀번호 확인이 일치하지 않습니다.');
            return;
        }
        if (!signupConfig.signupEnabled) {
            setError('현재 회원가입 설정이 준비되지 않았습니다. 관리자에게 문의해 주세요.');
            return;
        }
        if (signupConfig.turnstileEnabled) {
            setError('이 환경에서는 테스트용 간편 회원가입이 비활성화되어 있습니다.');
            return;
        }

        setError('');
        setLoading(true);
        try {
            const response = await publicPlatformAPI.signup({
                ownerEmail,
                password,
            });
            const tenantId = response.data?.tenant?.tenantId || '';
            await login(ownerEmail, password, tenantId);
            navigate('/', { replace: true });
        } catch (requestError) {
            setError(requestError.response?.data?.message || '회원가입에 실패했습니다.');
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="login-container">
            <div className="login-card">
                <div className="login-logo">
                    <div className="logo-mark">ML</div>
                    <h2>회원가입</h2>
                    <p>테스트용 계정을 빠르게 만들고 바로 로그인합니다.</p>
                </div>

                <form onSubmit={handleSubmit}>
                    {error && (
                        <div className="alert alert-error" style={{ marginBottom: 'var(--spacing-md)' }}>
                            {error}
                        </div>
                    )}

                    <div className="form-group">
                        <label className="form-label" htmlFor="signup-email">이메일</label>
                        <input
                            id="signup-email"
                            className="form-input"
                            type="email"
                            value={ownerEmail}
                            onChange={(event) => setOwnerEmail(event.target.value)}
                            autoComplete="email"
                            autoFocus
                        />
                    </div>

                    <div className="form-group">
                        <label className="form-label" htmlFor="signup-password">비밀번호</label>
                        <input
                            id="signup-password"
                            className="form-input"
                            type="password"
                            value={password}
                            onChange={(event) => setPassword(event.target.value)}
                            autoComplete="new-password"
                        />
                    </div>

                    <div className="form-group">
                        <label className="form-label" htmlFor="signup-confirm-password">비밀번호 확인</label>
                        <input
                            id="signup-confirm-password"
                            className="form-input"
                            type="password"
                            value={confirmPassword}
                            onChange={(event) => setConfirmPassword(event.target.value)}
                            autoComplete="new-password"
                        />
                    </div>

                    {configLoading && (
                        <div className="form-helper" style={{ marginBottom: 'var(--spacing-md)' }}>
                            회원가입 설정을 확인하는 중입니다.
                        </div>
                    )}
                    {!configLoading && signupConfig.turnstileEnabled && (
                        <div className="form-helper" style={{ marginBottom: 'var(--spacing-md)', color: 'var(--danger)' }}>
                            현재 환경에서는 추가 보안 설정이 켜져 있어 간편 회원가입을 사용할 수 없습니다.
                        </div>
                    )}

                    <button
                        type="submit"
                        className="btn btn-primary"
                        disabled={loading || configLoading || !signupConfig.signupEnabled || signupConfig.turnstileEnabled}
                    >
                        {loading ? '가입 중...' : '회원가입'}
                    </button>
                </form>

                <div style={{ marginTop: 'var(--spacing-lg)', textAlign: 'center', fontSize: 'var(--font-size-sm)' }}>
                    이미 계정이 있다면 <Link to="/login">로그인</Link>
                </div>
            </div>
        </div>
    );
}
