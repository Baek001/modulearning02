const baseUrl = process.env.BASE_URL;
const loginId = process.env.LOGIN_ID;
const loginPassword = process.env.LOGIN_PASSWORD;

if (!baseUrl || !loginId || !loginPassword) {
  console.error("Usage: BASE_URL=https://... LOGIN_ID=admin LOGIN_PASSWORD=admin1234 npm run smoke:all");
  process.exit(1);
}

const normalizedBaseUrl = baseUrl.replace(/\/+$/, "");
const requestTimeoutMs = 30000;
const maxAttempts = 3;

async function main() {
  const session = await login();
  const results = [];

  await record(results, "auth.session", async () => {
    const profile = await api(session, "/rest/mypage");
    return {
      userId: profile.body?.userId ?? profile.body?.username ?? "<unknown>",
      status: profile.status,
    };
  });

  await record(results, "organization.users", async () => {
    const response = await api(session, "/rest/comm-user");
    return summarize(response.body);
  });

  await record(results, "organization.departments", async () => {
    const response = await api(session, "/rest/comm-depart");
    return summarize(response.body);
  });

  await record(results, "dashboard.bootstrap", async () => {
    const response = await api(session, "/rest/dashboard/bootstrap");
    return summarize(response.body);
  });

  await record(results, "dashboard.feed", async () => {
    const response = await api(session, "/rest/dashboard/feed?scope=all&category=all&sort=recent&view=summary&page=1");
    return summarize(response.body);
  });

  await record(results, "dashboard.todoMutation", async () => {
    const created = await api(session, "/rest/dashboard/todos", {
      method: "POST",
      body: {
        todoTtl: `Smoke test ${Date.now()}`,
        todoCn: "Automated smoke test entry",
      },
    });
    const todoId = created.body?.todoId;
    if (!todoId) {
      throw new Error(`Todo create did not return todoId: ${JSON.stringify(created.body)}`);
    }

    const updated = await api(session, `/rest/dashboard/todos/${todoId}`, {
      method: "PATCH",
      body: {
        todoTtl: `Smoke test ${Date.now()} updated`,
        doneYn: "Y",
      },
    });

    await api(session, `/rest/dashboard/todos/${todoId}`, {
      method: "DELETE",
    });

    return {
      todoId,
      updatedDoneYn: updated.body?.doneYn ?? "<unknown>",
    };
  });

  const projectList = await record(results, "project.list", async () => {
    const response = await api(session, "/rest/project");
    return summarize(response.body);
  });

  const firstProjectId = findFirstId(projectList?.raw, ["bizId"]);
  if (firstProjectId) {
    await record(results, "project.detail", async () => {
      const response = await api(session, `/rest/project/${encodeURIComponent(firstProjectId)}`);
      return summarize(response.body);
    });
    await record(results, "project.tasks", async () => {
      const response = await api(session, `/rest/project/${encodeURIComponent(firstProjectId)}/tasks`);
      return summarize(response.body);
    });
  }

  const { startDate, endDate } = currentMonthRange();
  await record(results, "calendar.events", async () => {
    const response = await api(session, `/rest/calendar/events?start=${startDate}&end=${endDate}`);
    return summarize(response.body);
  });

  await record(results, "calendar.user", async () => {
    const response = await api(session, "/rest/calendar-user");
    return summarize(response.body);
  });

  await record(results, "calendar.department", async () => {
    const response = await api(session, "/rest/calendar-depart");
    return summarize(response.body);
  });

  const boardWorkspace = await record(results, "board.workspace", async () => {
    const response = await api(session, "/rest/boards?page=1");
    return summarize(response.body);
  });
  const firstBoardId = findFirstId(boardWorkspace?.raw, ["pstId"]);
  if (firstBoardId) {
    await record(results, "board.detail", async () => {
      const response = await api(session, `/rest/boards/${encodeURIComponent(firstBoardId)}`);
      return summarize(response.body);
    });
  }

  await record(results, "attendance.week", async () => {
    const response = await api(session, "/rest/attendance-stats/week");
    return summarize(response.body);
  });

  await record(results, "attendance.month", async () => {
    const response = await api(session, "/rest/attendance-stats/month");
    return summarize(response.body);
  });

  await record(results, "meeting.rooms", async () => {
    const response = await api(session, "/rest/meeting/room");
    return summarize(response.body);
  });

  await record(results, "meeting.reservations", async () => {
    const response = await api(session, `/rest/meeting/reservations?date=${today()}&role=admin`);
    return summarize(response.body);
  });

  await record(results, "approval.summary", async () => {
    const response = await api(session, "/rest/approval-documents/summary");
    return summarize(response.body);
  });

  const approvalList = await record(results, "approval.list", async () => {
    const response = await api(session, "/rest/approval-documents?section=inbox&page=1");
    return summarize(response.body);
  });
  const firstApprovalId = findFirstId(approvalList?.raw, ["atrzDocId"]);
  if (firstApprovalId) {
    await record(results, "approval.detail", async () => {
      const response = await api(session, `/rest/approval-documents/${encodeURIComponent(firstApprovalId)}`);
      return summarize(response.body);
    });
  }

  await record(results, "email.counts", async () => {
    const response = await api(session, "/mail/counts");
    return summarize(response.body);
  });

  await record(results, "email.inbox", async () => {
    const response = await api(session, "/mail/listData/G101?page=1");
    return summarize(response.body);
  });

  await record(results, "messenger.currentUser", async () => {
    const response = await api(session, "/chat/current-user");
    return summarize(response.body);
  });

  await record(results, "messenger.panel", async () => {
    const response = await api(session, "/chat/panel");
    return summarize(response.body);
  });

  await record(results, "messenger.rooms", async () => {
    const response = await api(session, "/chat/rooms");
    return summarize(response.body);
  });

  await record(results, "community.list", async () => {
    const response = await api(session, "/rest/communities");
    return summarize(response.body);
  });

  await record(results, "contract.dashboard", async () => {
    const response = await api(session, "/rest/contracts/dashboard");
    return summarize(response.body);
  });

  const contractTemplates = await record(results, "contract.templates", async () => {
    const response = await api(session, "/rest/contracts/templates");
    return summarize(response.body);
  });
  const firstTemplateId = findFirstId(contractTemplates?.raw, ["templateId"]);
  if (firstTemplateId) {
    await record(results, "contract.templateDetail", async () => {
      const response = await api(session, `/rest/contracts/templates/${encodeURIComponent(firstTemplateId)}`);
      return summarize(response.body);
    });
  }

  const failed = results.filter((item) => !item.ok);

  console.log(JSON.stringify({
    baseUrl: normalizedBaseUrl,
    user: loginId,
    total: results.length,
    passed: results.length - failed.length,
    failed: failed.length,
    results,
  }, null, 2));

  if (failed.length > 0) {
    process.exit(1);
  }
}

async function login() {
  const response = await request("/common/auth", {
    method: "POST",
    headers: {
      "content-type": "application/json",
    },
    body: JSON.stringify({
      username: loginId,
      password: loginPassword,
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
    const raw = await fn();
    const summary = raw && typeof raw === "object" && "summary" in raw
      ? raw.summary
      : raw;

    const rawValue = raw && typeof raw === "object" && "raw" in raw
      ? raw.raw
      : raw;

    results.push({
      name,
      ok: true,
      summary,
    });

    return {
      summary,
      raw: rawValue,
    };
  } catch (error) {
    results.push({
      name,
      ok: false,
      error: error instanceof Error ? error.message : String(error),
    });
    return null;
  }
}

function summarize(value) {
  if (Array.isArray(value)) {
    return {
      raw: value,
      summary: {
        type: "array",
        count: value.length,
      },
    };
  }

  if (value && typeof value === "object") {
    const listCandidate = findFirstArray(value);
    return {
      raw: value,
      summary: {
        type: "object",
        keys: Object.keys(value).slice(0, 8),
        listCount: listCandidate ? listCandidate.length : undefined,
      },
    };
  }

  return {
    raw: value,
    summary: {
      type: typeof value,
      value,
    },
  };
}

function findFirstArray(value) {
  if (!value || typeof value !== "object") {
    return null;
  }

  for (const key of Object.keys(value)) {
    if (Array.isArray(value[key])) {
      return value[key];
    }
  }

  return null;
}

function findFirstId(value, candidateKeys) {
  const items = Array.isArray(value)
    ? value
    : findFirstArray(value);

  if (!Array.isArray(items) || items.length === 0) {
    return null;
  }

  const first = items[0];
  if (!first || typeof first !== "object") {
    return null;
  }

  for (const key of candidateKeys) {
    if (first[key] !== undefined && first[key] !== null && `${first[key]}`.length > 0) {
      return `${first[key]}`;
    }
  }

  return null;
}

function currentMonthRange() {
  const now = new Date();
  const start = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
  const end = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 0));
  return {
    startDate: toDateOnly(start),
    endDate: toDateOnly(end),
  };
}

function today() {
  return toDateOnly(new Date());
}

function toDateOnly(value) {
  return value.toISOString().slice(0, 10);
}

function isRetryable(error) {
  return error?.name === "AbortError";
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

main().catch((error) => {
  console.error("Smoke suite crashed", error);
  process.exit(1);
});
