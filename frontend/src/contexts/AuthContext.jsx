import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { authAPI, STORAGE_KEYS } from '../services/api';

/* eslint-disable react-refresh/only-export-components */

const AuthContext = createContext(null);

function isPublicAuthPath(pathname = '') {
    return pathname === '/login'
        || pathname === '/signup'
        || pathname.startsWith('/invite/accept');
}

function hasText(value) {
    return typeof value === 'string' ? value.trim().length > 0 : Boolean(value);
}

function deriveOnboardingComplete(data, fallback = true) {
    if (typeof data?.onboardingComplete === 'boolean') {
        return data.onboardingComplete;
    }

    const hasProfileFields = ['userNm', 'deptId', 'jbgdCd', 'userTelno']
        .some((key) => Object.prototype.hasOwnProperty.call(data ?? {}, key));

    if (!hasProfileFields) {
        return fallback;
    }

    return ['userNm', 'deptId', 'jbgdCd', 'userTelno'].every((key) => hasText(data?.[key]));
}

function clearStoredSession() {
    localStorage.removeItem(STORAGE_KEYS.user);
    localStorage.removeItem(STORAGE_KEYS.session);
}

function normalizeMembership(data = {}) {
    return {
        tenantId: data?.tenantId || '',
        tenantNm: data?.tenantNm || '',
        tenantSlug: data?.tenantSlug || '',
        tenantRoleCd: data?.tenantRoleCd || 'MEMBER',
        userId: data?.userId || '',
        userEmail: data?.userEmail || '',
        deptNm: data?.deptNm || '',
    };
}

function normalizeUser(data, fallbackUserId = '', currentTenant = null, onboardingComplete = true) {
    return {
        userId: data?.userId || fallbackUserId,
        userNm: data?.userNm || fallbackUserId || 'Workspace user',
        deptId: data?.deptId || '',
        deptNm: data?.deptNm || '',
        jbgdCd: data?.jbgdCd || '',
        jbgdNm: data?.jbgdNm || '',
        userEmail: data?.userEmail || '',
        userTelno: data?.userTelno || '',
        hireYmd: data?.hireYmd || '',
        userRole: data?.userRole || '',
        workSttsCd: data?.workSttsCd || '',
        extTel: data?.extTel || '',
        filePath: data?.filePath || '',
        tenantId: data?.tenantId || currentTenant?.tenantId || '',
        tenantNm: data?.tenantNm || currentTenant?.tenantNm || '',
        tenantSlug: data?.tenantSlug || currentTenant?.tenantSlug || '',
        tenantRoleCd: data?.tenantRoleCd || currentTenant?.tenantRoleCd || 'MEMBER',
        onboardingComplete: deriveOnboardingComplete({ ...data, onboardingComplete }, onboardingComplete),
    };
}

function normalizeSession(data, fallbackUserId = '') {
    const rawUser = data?.user || data || {};
    const memberships = Array.isArray(data?.memberships) ? data.memberships.map(normalizeMembership) : [];
    const currentTenant = normalizeMembership(
        data?.currentTenant
        || memberships.find((item) => item.tenantId && item.tenantId === rawUser?.tenantId)
        || {}
    );
    const onboardingComplete = deriveOnboardingComplete(
        { ...rawUser, onboardingComplete: data?.onboardingComplete },
        true
    );

    return {
        user: normalizeUser(rawUser, fallbackUserId, currentTenant, onboardingComplete),
        currentTenant,
        memberships,
    };
}

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);
    const [currentTenant, setCurrentTenant] = useState(null);
    const [memberships, setMemberships] = useState([]);
    const [loading, setLoading] = useState(true);

    function applySession(nextSession) {
        localStorage.setItem(STORAGE_KEYS.session, JSON.stringify(nextSession));
        localStorage.setItem(STORAGE_KEYS.user, JSON.stringify(nextSession.user));
        setUser(nextSession.user);
        setCurrentTenant(nextSession.currentTenant);
        setMemberships(nextSession.memberships);
    }

    function applyAuthSessionData(data, fallbackUserId = user?.userId || '') {
        const nextSession = normalizeSession(data, fallbackUserId);
        applySession(nextSession);
        return nextSession;
    }

    useEffect(() => {
        let active = true;

        async function initializeAuth() {
            const stored = localStorage.getItem(STORAGE_KEYS.session);
            const shouldAttemptSession = Boolean(stored) || !isPublicAuthPath(window.location.pathname);
            let storedUserId = '';
            let hydratedFromStorage = false;

            if (stored) {
                try {
                    const parsedSession = normalizeSession(JSON.parse(stored));
                    storedUserId = parsedSession?.user?.userId || '';
                    setUser(parsedSession.user || null);
                    setCurrentTenant(parsedSession.currentTenant || null);
                    setMemberships(parsedSession.memberships || []);
                    hydratedFromStorage = Boolean(parsedSession.user);
                } catch {
                    clearStoredSession();
                }
            }

            if (active && hydratedFromStorage) {
                setLoading(false);
            }

            if (!shouldAttemptSession) {
                if (active) {
                    setLoading(false);
                }
                return;
            }

            try {
                const response = await authAPI.session();
                if (!active) {
                    return;
                }

                applyAuthSessionData(response.data, storedUserId);
            } catch {
                if (!active) {
                    return;
                }

                clearStoredSession();
                setUser(null);
                setCurrentTenant(null);
                setMemberships([]);
            } finally {
                if (active) {
                    setLoading(false);
                }
            }
        }

        initializeAuth();

        return () => {
            active = false;
        };
    }, []);

    const login = async (identifier, password, tenantId = '') => {
        const response = await authAPI.login(identifier, password, tenantId);
        return applyAuthSessionData(response.data, identifier);
    };

    const switchTenant = async (tenantId) => {
        const response = await authAPI.switchTenant(tenantId);
        return applyAuthSessionData(response.data, user?.userId || '');
    };

    const updateUserProfile = (nextProfile) => {
        const nextUser = normalizeUser(
            { ...user, ...nextProfile },
            user?.userId || nextProfile?.userId || '',
            currentTenant
        );
        const nextCurrentTenant = currentTenant
            ? normalizeMembership({
                ...currentTenant,
                userEmail: nextUser.userEmail,
                deptNm: nextUser.deptNm || currentTenant.deptNm,
            })
            : currentTenant;
        const nextMemberships = memberships.map((membership) => (
            membership.tenantId === nextCurrentTenant?.tenantId
                ? normalizeMembership({
                    ...membership,
                    userEmail: nextUser.userEmail,
                    deptNm: nextUser.deptNm || membership.deptNm,
                })
                : membership
        ));

        applySession({
            user: nextUser,
            currentTenant: nextCurrentTenant,
            memberships: nextMemberships,
        });
    };

    const logout = async () => {
        try {
            await authAPI.logout();
        } catch {
            // Ignore revoke errors during local cleanup.
        }

        clearStoredSession();
        setUser(null);
        setCurrentTenant(null);
        setMemberships([]);
    };

    const needsOnboarding = Boolean(user) && !user.onboardingComplete;

    const value = useMemo(() => ({
        user,
        currentTenant,
        memberships,
        login,
        logout,
        switchTenant,
        updateUserProfile,
        applyAuthSessionData,
        loading,
        needsOnboarding,
    }), [user, currentTenant, memberships, loading, needsOnboarding]);

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
}

export const useAuth = () => useContext(AuthContext);
