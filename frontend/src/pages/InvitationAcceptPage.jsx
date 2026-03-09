import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { publicPlatformAPI } from '../services/api';

function buildInitialForm(invitation) {
    return {
        userNm: invitation?.inviteName || '',
        userEmail: invitation?.inviteEmail || '',
        password: '',
        confirmPassword: '',
    };
}

export default function InvitationAcceptPage() {
    const { token = '' } = useParams();
    const { login, user, loading: authLoading } = useAuth();
    const navigate = useNavigate();
    const [invitation, setInvitation] = useState(null);
    const [form, setForm] = useState(buildInitialForm());
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        if (!authLoading && user) {
            navigate(user.onboardingComplete ? '/' : '/onboarding', { replace: true });
        }
    }, [authLoading, navigate, user]);

    useEffect(() => {
        let active = true;

        async function loadInvitation() {
            if (!token) {
                setError('초대 토큰이 없습니다.');
                setLoading(false);
                return;
            }

            setLoading(true);
            setError('');
            try {
                const response = await publicPlatformAPI.invitation(token);
                if (!active) {
                    return;
                }

                const nextInvitation = response.data?.invitation || null;
                setInvitation(nextInvitation);
                setForm(buildInitialForm(nextInvitation));
            } catch (requestError) {
                if (active) {
                    setError(requestError.response?.data?.message || '초대 정보를 불러오지 못했습니다.');
                }
            } finally {
                if (active) {
                    setLoading(false);
                }
            }
        }

        loadInvitation();
        return () => {
            active = false;
        };
    }, [token]);

    async function handleSubmit(event) {
        event.preventDefault();
        if (!form.userNm.trim() || !form.userEmail.trim() || !form.password.trim()) {
            setError('이름, 이메일, 비밀번호를 입력해 주세요.');
            return;
        }
        if (form.password !== form.confirmPassword) {
            setError('비밀번호 확인 값이 일치하지 않습니다.');
            return;
        }

        setSubmitting(true);
        setError('');
        try {
            const response = await publicPlatformAPI.acceptInvitation({
                token,
                userNm: form.userNm,
                userEmail: form.userEmail,
                password: form.password,
            });
            const tenantId = response.data?.tenant?.tenantId || '';
            const session = await login(form.userEmail, form.password, tenantId);
            navigate(session.user?.onboardingComplete ? '/' : '/onboarding', { replace: true });
        } catch (requestError) {
            setError(requestError.response?.data?.message || '초대 수락에 실패했습니다.');
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className="login-container">
            <div className="login-card">
                <div className="login-logo">
                    <div className="logo-mark">ML</div>
                    <h2>워크스페이스 초대 수락</h2>
                    <p>초대받은 회사 계정을 연결하고 바로 첫 로그인 설정을 진행합니다.</p>
                </div>

                {loading ? (
                    <div className="alert" style={{ marginBottom: 'var(--spacing-md)' }}>초대 정보를 확인하는 중입니다.</div>
                ) : !invitation ? (
                    <div className="alert alert-error" style={{ marginBottom: 'var(--spacing-md)' }}>
                        {error || '유효한 초대 정보를 찾지 못했습니다.'}
                    </div>
                ) : (
                    <>
                        <div className="card" style={{ marginBottom: 'var(--spacing-md)', boxShadow: 'none', border: '1px solid var(--gray-200)' }}>
                            <div className="card-body" style={{ padding: 'var(--spacing-md)' }}>
                                <strong>{invitation.tenantNm || '워크스페이스'}</strong>
                                <div style={{ marginTop: 'var(--spacing-xs)', color: 'var(--gray-500)' }}>
                                    역할: {invitation.tenantRoleCd || 'MEMBER'}
                                </div>
                            </div>
                        </div>

                        <form onSubmit={handleSubmit}>
                            {error && (
                                <div className="alert alert-error" style={{ marginBottom: 'var(--spacing-md)' }}>
                                    {error}
                                </div>
                            )}

                            <div className="form-group">
                                <label className="form-label" htmlFor="invite-name">이름</label>
                                <input
                                    id="invite-name"
                                    className="form-input"
                                    type="text"
                                    value={form.userNm}
                                    onChange={(event) => setForm((current) => ({ ...current, userNm: event.target.value }))}
                                />
                            </div>

                            <div className="form-group">
                                <label className="form-label" htmlFor="invite-email">이메일</label>
                                <input
                                    id="invite-email"
                                    className="form-input"
                                    type="email"
                                    value={form.userEmail}
                                    onChange={(event) => setForm((current) => ({ ...current, userEmail: event.target.value }))}
                                    autoComplete="email"
                                />
                            </div>

                            <div className="form-group">
                                <label className="form-label" htmlFor="invite-password">비밀번호</label>
                                <input
                                    id="invite-password"
                                    className="form-input"
                                    type="password"
                                    value={form.password}
                                    onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
                                    autoComplete="new-password"
                                />
                            </div>

                            <div className="form-group">
                                <label className="form-label" htmlFor="invite-confirm-password">비밀번호 확인</label>
                                <input
                                    id="invite-confirm-password"
                                    className="form-input"
                                    type="password"
                                    value={form.confirmPassword}
                                    onChange={(event) => setForm((current) => ({ ...current, confirmPassword: event.target.value }))}
                                    autoComplete="new-password"
                                />
                            </div>

                            <button type="submit" className="btn btn-primary" disabled={submitting || loading}>
                                {submitting ? '초대 수락 중...' : '초대 수락 후 시작'}
                            </button>
                        </form>
                    </>
                )}

                <div style={{ marginTop: 'var(--spacing-lg)', textAlign: 'center', fontSize: 'var(--font-size-sm)' }}>
                    다른 계정으로 로그인하려면 <Link to="/login">로그인 화면으로 이동</Link>
                </div>
            </div>
        </div>
    );
}
