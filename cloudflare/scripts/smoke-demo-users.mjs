const baseUrl = process.env.BASE_URL;
const adminId = process.env.LOGIN_ID;
const adminPassword = process.env.LOGIN_PASSWORD;

if (!baseUrl || !adminId || !adminPassword) {
  console.error("Usage: BASE_URL=https://... LOGIN_ID=admin LOGIN_PASSWORD=admin1234 npm run smoke:demo-users");
  process.exit(1);
}

const normalizedBaseUrl = baseUrl.replace(/\/+$/, "");
const requestTimeoutMs = 30000;
const maxAttempts = 3;
const demoUserPassword = "demo1234";

const DEMO_ACCOUNTS = [
  { userId: adminId, password: adminPassword, approvalSection: "inbox" },
  { userId: "devlead", password: demoUserPassword, approvalSection: "draft" },
  { userId: "opslead", password: demoUserPassword, approvalSection: "inbox" },
  { userId: "ops01", password: demoUserPassword, approvalSection: "draft" },
];

async function main() {
  const results = [];

  for (const account of DEMO_ACCOUNTS) {
    await record(results, account.userId, async () => {
      const session = await loginAs(account.userId, account.password);

      const profile = await api(session, "/rest/mypage");
      const dashboard = await api(session, "/rest/dashboard/feed?scope=all&category=all&sort=recent&view=summary&page=1");
      const projects = await api(session, "/rest/project");
      const panel = await api(session, "/chat/panel");
      const approval = await api(session, `/rest/approval-documents?section=${account.approvalSection}&page=1`);

      const summary = {
        profileUserId: profile.body?.userId ?? profile.body?.username ?? "",
        dashboardFeedCount: collectItems(dashboard.body).length,
        projectCount: asArray(projects.body).length,
        messengerRoomCount: collectItems(panel.body).length,
        approvalCount: collectItems(approval.body).length,
        approvalSection: account.approvalSection,
      };

      if (summary.profileUserId !== account.userId) {
        throw new Error(`Profile returned ${summary.profileUserId || "<empty>"} instead of ${account.userId}`);
      }

      if (summary.dashboardFeedCount < 1) {
        throw new Error("Dashboard feed is empty");
      }

      if (summary.projectCount < 1) {
        throw new Error("Project list is empty");
      }

      if (summary.messengerRoomCount < 1) {
        throw new Error("Messenger panel has no rooms");
      }

      if (summary.approvalCount < 1) {
        throw new Error(`Approval ${account.approvalSection} list is empty`);
      }

      return summary;
    });
  }

  const failed = results.filter((item) => !item.ok);
  console.log(JSON.stringify({
    baseUrl: normalizedBaseUrl,
    total: results.length,
    passed: results.length - failed.length,
    failed: failed.length,
    results,
  }, null, 2));

  if (failed.length > 0) {
    process.exit(1);
  }
}

async function loginAs(username, password) {
  const response = await request("/common/auth", {
    method: "POST",
    headers: {
      "content-type": "application/json",
    },
    body: JSON.stringify({
      username,
      password,
    }),
  });

  if (!response.ok) {
    throw new Error(`Login failed with ${response.status}: ${await response.text()}`);
  }

  const cookieHeader = extractCookieHeader(response);
  if (!cookieHeader) {
    throw new Error("Login succeeded but no Set-Cookie header was returned");
  }

  return { cookieHeader };
}

async function api(session, path, options = {}) {
  const method = options.method || "GET";
  const headers = new Headers(options.headers || {});
  headers.set("cookie", session.cookieHeader);
  headers.set("accept", "application/json");

  let body = options.body;
  if (body && typeof body === "object" && !(body instanceof FormData) && !(body instanceof Uint8Array) && !headers.has("content-type")) {
    headers.set("content-type", "application/json");
    body = JSON.stringify(body);
  }

  const response = await request(path, {
    method,
    headers,
    body,
  });

  const parsedBody = await parseBody(response);
  if (!response.ok) {
    throw new Error(`${method} ${path} failed with ${response.status}: ${JSON.stringify(parsedBody)}`);
  }

  return {
    status: response.status,
    body: parsedBody,
  };
}

async function request(path, init, attempt = 1) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), requestTimeoutMs);
  try {
    return await fetch(`${normalizedBaseUrl}${path}`, {
      ...init,
      signal: controller.signal,
    });
  } catch (error) {
    if (attempt < maxAttempts && isRetryable(error)) {
      await sleep(attempt * 1500);
      return request(path, init, attempt + 1);
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

async function parseBody(response) {
  const contentType = String(response.headers.get("content-type") || "");
  if (contentType.includes("application/json")) {
    return response.json();
  }
  return response.text();
}

function extractCookieHeader(response) {
  const setCookies = typeof response.headers.getSetCookie === "function"
    ? response.headers.getSetCookie()
    : [response.headers.get("set-cookie")].filter(Boolean);

  return setCookies
    .map((entry) => entry.split(";")[0])
    .join("; ");
}

async function record(results, name, fn) {
  try {
    const summary = await fn();
    results.push({
      name,
      ok: true,
      summary,
    });
  } catch (error) {
    results.push({
      name,
      ok: false,
      error: error instanceof Error ? error.message : String(error),
    });
  }
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function collectItems(value) {
  if (Array.isArray(value)) {
    return value;
  }

  if (!value || typeof value !== "object") {
    return [];
  }

  for (const key of Object.keys(value)) {
    if (Array.isArray(value[key])) {
      return value[key];
    }
  }

  return [];
}

function isRetryable(error) {
  return error?.name === "AbortError";
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

main().catch((error) => {
  console.error("Demo user smoke failed", error);
  process.exit(1);
});
