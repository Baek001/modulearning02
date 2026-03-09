import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import UserAvatar from '../../components/UserAvatar';
import { useAuth } from '../../contexts/AuthContext';
import { myPageAPI } from '../../services/api';

function createProfileForm(nextUser = {}) {
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

export default function MyPage() {
    const navigate = useNavigate();
    const { user, currentTenant, logout, updateUserProfile } = useAuth();
    const [profile, setProfile] = useState(null);
    const [departments, setDepartments] = useState([]);
    const [jobGrades, setJobGrades] = useState([]);
    const [jobGradeFallbackRequired, setJobGradeFallbackRequired] = useState(false);
    const [loading, setLoading] = useState(true);
    const [savingProfile, setSavingProfile] = useState(false);
    const [savingPassword, setSavingPassword] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [previewUrl, setPreviewUrl] = useState('');
    const [profileForm, setProfileForm] = useState(createProfileForm());
    const [passwordForm, setPasswordForm] = useState({
        currentPassword: '',
        newPassword: '',
        confirmPassword: '',
    });

    useEffect(() => {
        loadProfile();
    }, []);

    useEffect(() => {
        if (!profileForm.profileImage) {
            setPreviewUrl('');
            return undefined;
        }

        const nextPreviewUrl = URL.createObjectURL(profileForm.profileImage);
        setPreviewUrl(nextPreviewUrl);
        return () => URL.revokeObjectURL(nextPreviewUrl);
    }, [profileForm.profileImage]);

    const currentUser = profile || user || {};
    const currentAvatarPath = previewUrl || currentUser.filePath || user?.filePath || '';
    const selectedFileName = profileForm.profileImage?.name || '선택된 파일이 없습니다.';
    const infoRows = useMemo(
        () => ([
            ['아이디', currentUser.userId || '-'],
            ['워크스페이스', currentTenant?.tenantNm || currentUser.tenantNm || '-'],
            ['테넌트 역할', currentUser.tenantRoleCd || '-'],
            ['권한', currentUser.userRole || '-'],
            ['입사일', currentUser.hireYmd || '-'],
            ['부서', currentUser.deptNm || '-'],
            ['직급', currentUser.jbgdNm || '-'],
        ]),
        [currentTenant, currentUser]
    );

    async function loadProfile() {
        setLoading(true);
        setError('');

        try {
            const response = await myPageAPI.onboarding();
            const data = response.data || {};
            const nextProfile = data.user || user || {};

            setProfile(nextProfile);
            setDepartments(Array.isArray(data.departments) ? data.departments : []);
            setJobGrades(Array.isArray(data.jobGrades) ? data.jobGrades : []);
            setJobGradeFallbackRequired(Boolean(data.jobGradeFallbackRequired));
            setProfileForm(createProfileForm(nextProfile));
        } catch (requestError) {
            setError(requestError.response?.data?.message || '마이페이지 정보를 불러오지 못했습니다.');
        } finally {
            setLoading(false);
        }
    }

    async function handleProfileSubmit(event) {
        event.preventDefault();
        setSavingProfile(true);
        setError('');
        setNotice('');

        try {
            const response = await myPageAPI.updateProfile(profileForm);
            const nextProfile = response.data;

            setProfile(nextProfile);
            updateUserProfile(nextProfile);
            setProfileForm(createProfileForm(nextProfile));
            setNotice('프로필 정보를 저장했습니다.');
        } catch (requestError) {
            setError(requestError.response?.data?.message || '프로필 저장에 실패했습니다.');
        } finally {
            setSavingProfile(false);
        }
    }

    async function handlePasswordSubmit(event) {
        event.preventDefault();

        if (!passwordForm.currentPassword || !passwordForm.newPassword) {
            setError('현재 비밀번호와 새 비밀번호를 입력하세요.');
            return;
        }

        if (passwordForm.newPassword !== passwordForm.confirmPassword) {
            setError('새 비밀번호와 확인 값이 일치하지 않습니다.');
            return;
        }

        setSavingPassword(true);
        setError('');
        setNotice('');

        try {
            await myPageAPI.changePassword({
                currentPassword: passwordForm.currentPassword,
                newPassword: passwordForm.newPassword,
            });
            await logout();
            navigate('/login');
        } catch (requestError) {
            setError(requestError.response?.data?.message || '비밀번호 변경에 실패했습니다.');
        } finally {
            setSavingPassword(false);
        }
    }

    return (
        <div className="animate-slide-up">
            <div className="page-header">
                <div>
                    <h2>마이페이지</h2>
                    <p>프로필, 조직 정보, 프로필 사진과 비밀번호를 현재 계정 정보에 맞춰 관리합니다.</p>
                </div>
            </div>

            {error && (
                <div className="card" style={{ marginBottom: 'var(--spacing-md)' }}>
                    <div className="card-body" style={{ color: 'var(--danger)' }}>{error}</div>
                </div>
            )}
            {notice && (
                <div className="card" style={{ marginBottom: 'var(--spacing-md)' }}>
                    <div className="card-body" style={{ color: 'var(--success)' }}>{notice}</div>
                </div>
            )}

            <div className="grid-2" style={{ alignItems: 'start' }}>
                <div className="card">
                    <div className="card-body" style={{ padding: 'var(--spacing-2xl)' }}>
                        <div style={{ display: 'grid', gap: 'var(--spacing-lg)' }}>
                            <div style={{ textAlign: 'center' }}>
                                <UserAvatar
                                    className="avatar avatar-lg"
                                    userName={currentUser.userNm || currentUser.userId || 'User'}
                                    filePath={currentAvatarPath}
                                    style={{
                                        width: '96px',
                                        height: '96px',
                                        borderRadius: '28px',
                                        margin: '0 auto var(--spacing-md)',
                                        background: 'linear-gradient(135deg, #0f766e, #0284c7)',
                                        color: '#fff',
                                        fontSize: '40px',
                                        fontWeight: 700,
                                    }}
                                />
                                <h3 style={{ fontSize: 'var(--font-size-xl)', fontWeight: 700, marginBottom: 'var(--spacing-xs)' }}>
                                    {loading ? '불러오는 중...' : currentUser.userNm || '-'}
                                </h3>
                                <p style={{ color: 'var(--gray-500)' }}>
                                    {[currentTenant?.tenantNm || currentUser.tenantNm, currentUser.deptNm || '-', currentUser.jbgdNm || '-'].filter(Boolean).join(' / ')}
                                </p>
                            </div>

                            <div className="form-section">
                                <div className="form-section-title">계정 정보</div>
                                <div className="form-grid">
                                    {infoRows.map(([label, value]) => (
                                        <div key={label} className="form-group">
                                            <label className="form-label">{label}</label>
                                            <div className="form-static">{value}</div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div className="card">
                    <div className="card-header">
                        <h3>프로필 수정</h3>
                    </div>
                    <div className="card-body">
                        <form className="form-shell" onSubmit={handleProfileSubmit}>
                            <div className="form-section">
                                <div className="form-grid">
                                    <div className="form-group">
                                        <label className="form-label" htmlFor="mypage-user-name">이름</label>
                                        <input
                                            id="mypage-user-name"
                                            type="text"
                                            className="form-input"
                                            value={profileForm.userNm}
                                            onChange={(event) => setProfileForm((current) => ({ ...current, userNm: event.target.value }))}
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label className="form-label" htmlFor="mypage-user-email">이메일</label>
                                        <input
                                            id="mypage-user-email"
                                            type="email"
                                            className="form-input"
                                            value={profileForm.userEmail}
                                            onChange={(event) => setProfileForm((current) => ({ ...current, userEmail: event.target.value }))}
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label className="form-label" htmlFor="mypage-user-phone">휴대폰</label>
                                        <input
                                            id="mypage-user-phone"
                                            type="text"
                                            className="form-input"
                                            value={profileForm.userTelno}
                                            onChange={(event) => setProfileForm((current) => ({ ...current, userTelno: event.target.value }))}
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label className="form-label" htmlFor="mypage-user-ext">내선 번호</label>
                                        <input
                                            id="mypage-user-ext"
                                            type="text"
                                            className="form-input"
                                            value={profileForm.extTel}
                                            onChange={(event) => setProfileForm((current) => ({ ...current, extTel: event.target.value }))}
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label className="form-label" htmlFor="mypage-user-department">부서</label>
                                        <select
                                            id="mypage-user-department"
                                            className="form-input"
                                            value={profileForm.deptId}
                                            onChange={(event) => setProfileForm((current) => ({ ...current, deptId: event.target.value }))}
                                        >
                                            <option value="">부서를 선택해 주세요.</option>
                                            {departments.map((department) => (
                                                <option key={department.deptId} value={department.deptId}>
                                                    {department.deptNm}
                                                </option>
                                            ))}
                                        </select>
                                    </div>
                                    <div className="form-group">
                                        <label className="form-label" htmlFor="mypage-user-job-grade">
                                            {jobGradeFallbackRequired ? '직급 입력' : '직급'}
                                        </label>
                                        {jobGradeFallbackRequired ? (
                                            <input
                                                id="mypage-user-job-grade"
                                                type="text"
                                                className="form-input"
                                                value={profileForm.jobGradeName}
                                                onChange={(event) => setProfileForm((current) => ({ ...current, jobGradeName: event.target.value }))}
                                            />
                                        ) : (
                                            <select
                                                id="mypage-user-job-grade"
                                                className="form-input"
                                                value={profileForm.jbgdCd}
                                                onChange={(event) => setProfileForm((current) => ({ ...current, jbgdCd: event.target.value }))}
                                            >
                                                <option value="">직급을 선택해 주세요.</option>
                                                {jobGrades.map((jobGrade) => (
                                                    <option key={jobGrade.jbgdCd} value={jobGrade.jbgdCd}>
                                                        {jobGrade.jbgdNm}
                                                    </option>
                                                ))}
                                            </select>
                                        )}
                                    </div>
                                    <div className="form-group form-span-2">
                                        <label className="form-label">프로필 이미지</label>
                                        <div className="form-file">
                                            <label className="form-file-control" htmlFor="mypage-profile-image">
                                                <span className="form-file-button">파일 선택</span>
                                                <span className="form-file-name">{selectedFileName}</span>
                                            </label>
                                            <input
                                                id="mypage-profile-image"
                                                className="form-file-input"
                                                type="file"
                                                accept="image/*"
                                                onChange={(event) => setProfileForm((current) => ({ ...current, profileImage: event.target.files?.[0] || null }))}
                                            />
                                            <div className="form-helper">이미지 파일만 업로드할 수 있습니다.</div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div className="form-actions">
                                <button type="submit" className="btn btn-primary" disabled={savingProfile || loading}>
                                    {savingProfile ? '저장 중...' : '프로필 저장'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            </div>

            <div className="card" style={{ marginTop: 'var(--spacing-md)' }}>
                <div className="card-header">
                    <h3>비밀번호 변경</h3>
                </div>
                <div className="card-body">
                    <form className="form-shell" onSubmit={handlePasswordSubmit}>
                        <div className="form-section">
                            <div className="form-grid">
                                <div className="form-group">
                                    <label className="form-label" htmlFor="mypage-current-password">현재 비밀번호</label>
                                    <input
                                        id="mypage-current-password"
                                        type="password"
                                        className="form-input"
                                        value={passwordForm.currentPassword}
                                        onChange={(event) => setPasswordForm((current) => ({ ...current, currentPassword: event.target.value }))}
                                    />
                                </div>
                                <div className="form-group">
                                    <label className="form-label" htmlFor="mypage-new-password">새 비밀번호</label>
                                    <input
                                        id="mypage-new-password"
                                        type="password"
                                        className="form-input"
                                        value={passwordForm.newPassword}
                                        onChange={(event) => setPasswordForm((current) => ({ ...current, newPassword: event.target.value }))}
                                    />
                                </div>
                                <div className="form-group form-span-2">
                                    <label className="form-label" htmlFor="mypage-confirm-password">새 비밀번호 확인</label>
                                    <input
                                        id="mypage-confirm-password"
                                        type="password"
                                        className="form-input"
                                        value={passwordForm.confirmPassword}
                                        onChange={(event) => setPasswordForm((current) => ({ ...current, confirmPassword: event.target.value }))}
                                    />
                                    <div className="form-helper">비밀번호를 변경하면 보안을 위해 다시 로그인합니다.</div>
                                </div>
                            </div>
                        </div>

                        <div className="form-actions">
                            <button type="submit" className="btn btn-secondary" disabled={savingPassword || loading}>
                                {savingPassword ? '변경 중...' : '비밀번호 변경 후 재로그인'}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
}
