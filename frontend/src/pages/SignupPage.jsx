import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { publicPlatformAPI } from '../services/api';

const TURNSTILE_SCRIPT_ID = 'cf-turnstile-script';
let turnstileScriptLoader = null;

function loadTurnstileScript() {
    if (typeof window === 'undefined') {
        return Promise.reject(new Error('Turnstile is unavailable on the server.'));
    }
    if (window.turnstile) {
        return Promise.resolve(window.turnstile);
    }
    if (turnstileScriptLoader) {
        return turnstileScriptLoader;
    }

    turnstileScriptLoader = new Promise((resolve, reject) => {
        const existingScript = document.getElementById(TURNSTILE_SCRIPT_ID);
        if (existingScript) {
            existingScript.addEventListener('load', () => resolve(window.turnstile), { once: true });
            existingScript.addEventListener('error', () => reject(new Error('Turnstile script failed to load.')), { once: true });
            return;
        }

        const script = document.createElement('script');
        script.id = TURNSTILE_SCRIPT_ID;
        script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
        script.async = true;
        script.defer = true;
        script.onload = () => resolve(window.turnstile);
        script.onerror = () => reject(new Error('Turnstile script failed to load.'));
        document.head.appendChild(script);
    });

    return turnstileScriptLoader;
}

export default function SignupPage() {
    const { login } = useAuth();
    const navigate = useNavigate();
    const widgetContainerRef = useRef(null);
    const widgetIdRef = useRef(null);
    const [companyName, setCompanyName] = useState('');
    const [ownerName, setOwnerName] = useState('');
    const [ownerEmail, setOwnerEmail] = useState('');
    const [password, setPassword] = useState('');
    const [workspaceSlug, setWorkspaceSlug] = useState('');
    const [turnstileToken, setTurnstileToken] = useState('');
    const [signupConfig, setSignupConfig] = useState({
        signupEnabled: true,
        turnstileEnabled: false,
        turnstileSiteKey: '',
    });
    const [configLoading, setConfigLoading] = useState(true);
    const [turnstileReady, setTurnstileReady] = useState(false);
    const [turnstileError, setTurnstileError] = useState('');
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
                    turnstileSiteKey: response.data?.turnstileSiteKey || '',
                });
            } catch (requestError) {
                if (active) {
                    setSignupConfig({
                        signupEnabled: false,
                        turnstileEnabled: false,
                        turnstileSiteKey: '',
                    });
                    setError(requestError.response?.data?.message || '가입 설정 정보를 불러오지 못했습니다.');
                }
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

    useEffect(() => {
        if (!signupConfig.turnstileEnabled || !signupConfig.turnstileSiteKey || !widgetContainerRef.current) {
            setTurnstileReady(!signupConfig.turnstileEnabled);
            return undefined;
        }

        let cancelled = false;
        setTurnstileReady(false);
        setTurnstileError('');
        setTurnstileToken('');

        loadTurnstileScript()
            .then((turnstile) => {
                if (cancelled || !widgetContainerRef.current || widgetIdRef.current !== null || !turnstile) {
                    return;
                }

                widgetIdRef.current = turnstile.render(widgetContainerRef.current, {
                    sitekey: signupConfig.turnstileSiteKey,
                    theme: 'light',
                    callback: (token) => {
                        setTurnstileToken(token || '');
                        setTurnstileReady(true);
                        setTurnstileError('');
                    },
                    'expired-callback': () => {
                        setTurnstileToken('');
                    },
                    'error-callback': () => {
                        setTurnstileToken('');
                        setTurnstileError('보안 확인을 완료하지 못했습니다. 다시 시도해 주세요.');
                    },
                });
                setTurnstileReady(true);
            })
            .catch((loadError) => {
                if (!cancelled) {
                    setTurnstileError(loadError.message || '보안 확인 스크립트를 불러오지 못했습니다.');
                }
            });

        return () => {
            cancelled = true;
            if (window.turnstile && widgetIdRef.current !== null) {
                window.turnstile.remove(widgetIdRef.current);
                widgetIdRef.current = null;
            }
        };
    }, [signupConfig.turnstileEnabled, signupConfig.turnstileSiteKey]);

    async function handleSubmit(event) {
        event.preventDefault();
        if (!companyName.trim() || !ownerName.trim() || !ownerEmail.trim() || !password.trim()) {
            setError('회사명, 관리자명, 이메일, 비밀번호를 모두 입력해 주세요.');
            return;
        }
        if (!signupConfig.signupEnabled) {
            setError('현재 가입 설정이 준비되지 않았습니다. 관리자에게 문의해 주세요.');
            return;
        }
        if (signupConfig.turnstileEnabled && !turnstileToken) {
            setError('보안 확인을 완료해 주세요.');
            return;
        }

        setError('');
        setLoading(true);
        try {
            const response = await publicPlatformAPI.signup({
                companyName,
                ownerName,
                ownerEmail,
                password,
                workspaceSlug,
                turnstileToken,
            });
            const tenantId = response.data?.tenant?.tenantId || '';
            await login(ownerEmail, password, tenantId);
            navigate('/', { replace: true });
        } catch (requestError) {
            setError(requestError.response?.data?.message || '워크스페이스 생성에 실패했습니다.');
            if (signupConfig.turnstileEnabled && window.turnstile && widgetIdRef.current !== null) {
                setTurnstileToken('');
                window.turnstile.reset(widgetIdRef.current);
            }
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="login-container">
            <div className="login-card">
                <div className="login-logo">
                    <div className="logo-mark">ML</div>
                    <h2>워크스페이스 시작</h2>
                    <p>회사 owner 계정을 만들고 바로 서비스를 사용할 수 있습니다.</p>
                </div>

                <form onSubmit={handleSubmit}>
                    {error && (
                        <div className="alert alert-error" style={{ marginBottom: 'var(--spacing-md)' }}>
                            {error}
                        </div>
                    )}

                    <div className="form-group">
                        <label className="form-label" htmlFor="companyName">회사명</label>
                        <input
                            id="companyName"
                            className="form-input"
                            type="text"
                            value={companyName}
                            onChange={(event) => setCompanyName(event.target.value)}
                            autoFocus
                        />
                    </div>

                    <div className="form-group">
                        <label className="form-label" htmlFor="workspaceSlug">워크스페이스 슬러그</label>
                        <input
                            id="workspaceSlug"
                            className="form-input"
                            type="text"
                            placeholder="비워두면 자동 생성됩니다"
                            value={workspaceSlug}
                            onChange={(event) => setWorkspaceSlug(event.target.value)}
                        />
                        <div className="form-helper">영문, 숫자, 하이픈만 사용할 수 있습니다.</div>
                    </div>

                    <div className="form-group">
                        <label className="form-label" htmlFor="ownerName">관리자 이름</label>
                        <input
                            id="ownerName"
                            className="form-input"
                            type="text"
                            value={ownerName}
                            onChange={(event) => setOwnerName(event.target.value)}
                        />
                    </div>

                    <div className="form-group">
                        <label className="form-label" htmlFor="ownerEmail">관리자 이메일</label>
                        <input
                            id="ownerEmail"
                            className="form-input"
                            type="email"
                            value={ownerEmail}
                            onChange={(event) => setOwnerEmail(event.target.value)}
                            autoComplete="email"
                        />
                    </div>

                    <div className="form-group">
                        <label className="form-label" htmlFor="ownerPassword">비밀번호</label>
                        <input
                            id="ownerPassword"
                            className="form-input"
                            type="password"
                            value={password}
                            onChange={(event) => setPassword(event.target.value)}
                            autoComplete="new-password"
                        />
                        <div className="form-helper">8자 이상 72자 이하로 입력해 주세요.</div>
                    </div>

                    {configLoading ? (
                        <div className="form-helper" style={{ marginBottom: 'var(--spacing-md)' }}>가입 보안 설정을 불러오는 중입니다.</div>
                    ) : !signupConfig.signupEnabled ? (
                        <div className="form-helper" style={{ marginBottom: 'var(--spacing-md)', color: 'var(--danger)' }}>
                            현재 가입 보안 설정이 완전하지 않아 owner 가입이 잠시 비활성화되어 있습니다.
                        </div>
                    ) : signupConfig.turnstileEnabled ? (
                        <div className="form-group">
                            <label className="form-label">보안 확인</label>
                            <div className="turnstile-shell" ref={widgetContainerRef}></div>
                            {turnstileError && <div className="form-helper" style={{ color: 'var(--danger)' }}>{turnstileError}</div>}
                        </div>
                    ) : (
                        <div className="form-helper" style={{ marginBottom: 'var(--spacing-md)' }}>
                            현재 환경에서는 추가 보안 확인 없이 owner 가입을 테스트할 수 있습니다.
                        </div>
                    )}

                    <button
                        type="submit"
                        className="btn btn-primary"
                        disabled={
                            loading
                            || configLoading
                            || !signupConfig.signupEnabled
                            || (signupConfig.turnstileEnabled && (!turnstileReady || !turnstileToken))
                        }
                    >
                        {loading ? '생성 중...' : '워크스페이스 생성'}
                    </button>
                </form>

                <div style={{ marginTop: 'var(--spacing-lg)', textAlign: 'center', fontSize: 'var(--font-size-sm)' }}>
                    이미 계정이 있다면 <Link to="/login">로그인</Link>
                </div>
            </div>
        </div>
    );
}
