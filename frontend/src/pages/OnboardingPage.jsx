import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import UserAvatar from '../components/UserAvatar';
import { useAuth } from '../contexts/AuthContext';
import { myPageAPI } from '../services/api';

function createInitialForm(nextUser = {}) {
    return {
        userNm: nextUser.userNm || '',
        userEmail: nextUser.userEmail || '',
        userTelno: nextUser.userTelno || '',
        extTel: nextUser.extTel || '',
        deptId: nextUser.deptId || '',
        jbgdCd: nextUser.jbgdCd || '',
        jobGradeName: nextUser.jbgdNm || '',
        profileImage: null,
    };
}

export default function OnboardingPage() {
    const navigate = useNavigate();
    const { user, loading: authLoading, applyAuthSessionData, logout } = useAuth();
    const [profile, setProfile] = useState(user || null);
    const [departments, setDepartments] = useState([]);
    const [jobGrades, setJobGrades] = useState([]);
    const [jobGradeFallbackRequired, setJobGradeFallbackRequired] = useState(false);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const [form, setForm] = useState(createInitialForm(user || {}));
    const [previewUrl, setPreviewUrl] = useState('');

    useEffect(() => {
        if (!form.profileImage) {
            setPreviewUrl('');
            return undefined;
        }

        const nextPreviewUrl = URL.createObjectURL(form.profileImage);
        setPreviewUrl(nextPreviewUrl);
        return () => URL.revokeObjectURL(nextPreviewUrl);
    }, [form.profileImage]);

    useEffect(() => {
        if (authLoading) {
            return;
        }
        if (!user) {
            navigate('/login', { replace: true });
            return;
        }
        if (user.onboardingComplete) {
            navigate('/', { replace: true });
            return;
        }

        let active = true;

        async function loadOnboarding() {
            setLoading(true);
            setError('');
            try {
                const response = await myPageAPI.onboarding();
                if (!active) {
                    return;
                }

                const data = response.data || {};
                const nextUser = data.user || user;

                setProfile(nextUser);
                setDepartments(Array.isArray(data.departments) ? data.departments : []);
                setJobGrades(Array.isArray(data.jobGrades) ? data.jobGrades : []);
                setJobGradeFallbackRequired(Boolean(data.jobGradeFallbackRequired));
                setForm(createInitialForm(nextUser));

                if (data.onboardingComplete) {
                    navigate('/', { replace: true });
                }
            } catch (requestError) {
                if (active) {
                    setError(requestError.response?.data?.message || '첫 로그인 설정 정보를 불러오지 못했습니다.');
                }
            } finally {
                if (active) {
                    setLoading(false);
                }
            }
        }

        loadOnboarding();
        return () => {
            active = false;
        };
    }, [authLoading, navigate, user]);

    const selectedFileName = form.profileImage?.name || '선택된 파일이 없습니다.';
    const departmentOptions = useMemo(() => departments.filter((item) => item?.deptId), [departments]);
    const jobGradeOptions = useMemo(() => jobGrades.filter((item) => item?.jbgdCd), [jobGrades]);
    const currentAvatarPath = previewUrl || profile?.filePath || user?.filePath || '';

    async function handleSubmit(event) {
        event.preventDefault();

        if (!form.userNm.trim()) {
            setError('이름을 입력해 주세요.');
            return;
        }
        if (!form.deptId) {
            setError('부서를 선택해 주세요.');
            return;
        }
        if (!form.userTelno.trim()) {
            setError('휴대폰 번호를 입력해 주세요.');
            return;
        }
        if (jobGradeFallbackRequired && !form.jobGradeName.trim()) {
            setError('직급을 입력해 주세요.');
            return;
        }
        if (!jobGradeFallbackRequired && !form.jbgdCd) {
            setError('직급을 선택해 주세요.');
            return;
        }

        setSubmitting(true);
        setError('');
        try {
            const response = await myPageAPI.completeOnboarding(form);
            const nextSession = applyAuthSessionData(response.data);
            navigate(nextSession.user?.onboardingComplete ? '/' : '/onboarding', { replace: true });
        } catch (requestError) {
            setError(requestError.response?.data?.message || '첫 로그인 설정 저장에 실패했습니다.');
        } finally {
            setSubmitting(false);
        }
    }

    async function handleLogout() {
        await logout();
        navigate('/login', { replace: true });
    }

    return (
        <div className="login-container">
            <div className="login-card" style={{ width: 'min(720px, calc(100vw - 2rem))' }}>
                <div className="login-logo" style={{ marginBottom: 'var(--spacing-lg)' }}>
                    <div className="logo-mark">ML</div>
                    <h2>첫 로그인 설정</h2>
                    <p>이름, 부서, 직급, 연락처를 입력하면 서비스를 바로 사용할 수 있습니다.</p>
                </div>

                {error && (
                    <div className="alert alert-error" style={{ marginBottom: 'var(--spacing-md)' }}>
                        {error}
                    </div>
                )}

                {loading ? (
                    <div className="alert">온보딩 정보를 불러오는 중입니다.</div>
                ) : (
                    <form className="form-shell" onSubmit={handleSubmit}>
                        <div style={{ display: 'grid', gap: 'var(--spacing-lg)', marginBottom: 'var(--spacing-lg)' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-md)', flexWrap: 'wrap' }}>
                                <UserAvatar
                                    className="avatar avatar-lg"
                                    userName={form.userNm || profile?.userNm || user?.userEmail || 'User'}
                                    filePath={currentAvatarPath}
                                    style={{
                                        width: '88px',
                                        height: '88px',
                                        borderRadius: '26px',
                                        background: 'linear-gradient(135deg, #0f766e, #0284c7)',
                                        color: '#fff',
                                        fontSize: '36px',
                                        fontWeight: 700,
                                    }}
                                />
                                <div style={{ flex: 1 }}>
                                    <div style={{ fontWeight: 700, fontSize: 'var(--font-size-lg)', color: 'var(--gray-900)' }}>
                                        {profile?.tenantNm || user?.tenantNm || '워크스페이스'}
                                    </div>
                                    <div style={{ color: 'var(--gray-500)', marginTop: 'var(--spacing-xs)' }}>
                                        {form.userEmail || profile?.userEmail || user?.userEmail || '-'}
                                    </div>
                                    <div style={{ color: 'var(--gray-500)', marginTop: 'var(--spacing-xs)', fontSize: 'var(--font-size-sm)' }}>
                                        프로필 사진은 선택사항입니다.
                                    </div>
                                </div>
                            </div>

                            <div className="form-section">
                                <div className="form-section-title">기본 정보</div>
                                <div className="form-grid">
                                    <div className="form-group">
                                        <label className="form-label" htmlFor="onboarding-name">이름</label>
                                        <input
                                            id="onboarding-name"
                                            type="text"
                                            className="form-input"
                                            value={form.userNm}
                                            onChange={(event) => setForm((current) => ({ ...current, userNm: event.target.value }))}
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label className="form-label" htmlFor="onboarding-email">이메일</label>
                                        <input
                                            id="onboarding-email"
                                            type="email"
                                            className="form-input"
                                            value={form.userEmail}
                                            disabled
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label className="form-label" htmlFor="onboarding-department">부서</label>
                                        <select
                                            id="onboarding-department"
                                            className="form-input"
                                            value={form.deptId}
                                            onChange={(event) => setForm((current) => ({ ...current, deptId: event.target.value }))}
                                        >
                                            <option value="">부서를 선택해 주세요.</option>
                                            {departmentOptions.map((department) => (
                                                <option key={department.deptId} value={department.deptId}>
                                                    {department.deptNm}
                                                </option>
                                            ))}
                                        </select>
                                    </div>
                                    <div className="form-group">
                                        <label className="form-label" htmlFor="onboarding-phone">휴대폰 번호</label>
                                        <input
                                            id="onboarding-phone"
                                            type="text"
                                            className="form-input"
                                            placeholder="010-0000-0000"
                                            value={form.userTelno}
                                            onChange={(event) => setForm((current) => ({ ...current, userTelno: event.target.value }))}
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label className="form-label" htmlFor="onboarding-job-grade">
                                            {jobGradeFallbackRequired ? '직급 입력' : '직급'}
                                        </label>
                                        {jobGradeFallbackRequired ? (
                                            <input
                                                id="onboarding-job-grade"
                                                type="text"
                                                className="form-input"
                                                placeholder="예: 매니저"
                                                value={form.jobGradeName}
                                                onChange={(event) => setForm((current) => ({ ...current, jobGradeName: event.target.value }))}
                                            />
                                        ) : (
                                            <select
                                                id="onboarding-job-grade"
                                                className="form-input"
                                                value={form.jbgdCd}
                                                onChange={(event) => setForm((current) => ({ ...current, jbgdCd: event.target.value }))}
                                            >
                                                <option value="">직급을 선택해 주세요.</option>
                                                {jobGradeOptions.map((jobGrade) => (
                                                    <option key={jobGrade.jbgdCd} value={jobGrade.jbgdCd}>
                                                        {jobGrade.jbgdNm}
                                                    </option>
                                                ))}
                                            </select>
                                        )}
                                    </div>
                                    <div className="form-group">
                                        <label className="form-label" htmlFor="onboarding-ext">내선 번호</label>
                                        <input
                                            id="onboarding-ext"
                                            type="text"
                                            className="form-input"
                                            placeholder="선택사항"
                                            value={form.extTel}
                                            onChange={(event) => setForm((current) => ({ ...current, extTel: event.target.value }))}
                                        />
                                    </div>
                                    <div className="form-group form-span-2">
                                        <label className="form-label">프로필 사진</label>
                                        <div className="form-file">
                                            <label className="form-file-control" htmlFor="onboarding-profile-image">
                                                <span className="form-file-button">파일 선택</span>
                                                <span className="form-file-name">{selectedFileName}</span>
                                            </label>
                                            <input
                                                id="onboarding-profile-image"
                                                className="form-file-input"
                                                type="file"
                                                accept="image/*"
                                                onChange={(event) => setForm((current) => ({ ...current, profileImage: event.target.files?.[0] || null }))}
                                            />
                                            <div className="form-helper">이미지 파일만 업로드할 수 있습니다.</div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="form-actions" style={{ justifyContent: 'space-between' }}>
                            <button type="button" className="btn btn-outline" onClick={handleLogout} disabled={submitting}>
                                로그아웃
                            </button>
                            <button type="submit" className="btn btn-primary" disabled={submitting}>
                                {submitting ? '저장 중...' : '설정 완료 후 시작'}
                            </button>
                        </div>
                    </form>
                )}
            </div>
        </div>
    );
}
