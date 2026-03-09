const baseUrl = process.env.BASE_URL;
const loginId = process.env.LOGIN_ID;
const loginPassword = process.env.LOGIN_PASSWORD;

if (!baseUrl || !loginId || !loginPassword) {
  console.error("Usage: BASE_URL=https://... LOGIN_ID=ops01 LOGIN_PASSWORD=demo1234 npm run smoke:attendance");
  process.exit(1);
}

const normalizedBaseUrl = baseUrl.replace(/\/+$/, "");
const requestTimeoutMs = 30000;
const maxAttempts = 3;

async function main() {
  const session = await login();
  const results = [];

  const before = await api(session, `/rest/attendance/${encodeURIComponent(loginId)}`);
  const beforeToday = findTodayRecord(before.body?.listTAA || []);
  results.push({
    step: "history.before",
    workYmd: beforeToday?.workYmd ?? null,
    started: Boolean(beforeToday?.workBgngDt),
    ended: Boolean(beforeToday?.workEndDt),
  });

  let currentRecord = beforeToday;
  if (!currentRecord?.workBgngDt) {
    await api(session, "/rest/attendance", { method: "POST" });
    const afterClockIn = await api(session, `/rest/attendance/${encodeURIComponent(loginId)}`);
    currentRecord = findTodayRecord(afterClockIn.body?.listTAA || []);
    if (!currentRecord?.workBgngDt) {
      throw new Error("Clock-in succeeded but today's attendance record was not created.");
    }
    results.push({
      step: "clockIn",
      workYmd: currentRecord.workYmd,
      started: true,
    });
  }

  const week = await api(session, "/rest/attendance-stats/week");
  if (!week.body?.uwaDTO) {
    throw new Error("Weekly attendance summary returned null.");
  }
  results.push({
    step: "week.summary",
    workDays: week.body.uwaDTO.workDays,
    totalWorkHr: week.body.uwaDTO.totalWorkHr,
  });

  const month = await api(session, "/rest/attendance-stats/month");
  if (!month.body?.umaDTO) {
    throw new Error("Monthly attendance summary returned null.");
  }
  results.push({
    step: "month.summary",
    workDays: month.body.umaDTO.workDays,
    totalWorkHr: month.body.umaDTO.totalWorkHr,
  });

  const department = await api(session, "/rest/attendance-stats/depart");
  const departmentItems = Array.isArray(department.body?.adsDTOList) ? department.body.adsDTOList : [];
  const selfEntry = departmentItems.find((entry) => entry.userId === loginId);
  if (!selfEntry) {
    throw new Error("Department attendance list does not include the current user.");
  }
  results.push({
    step: "department.status",
    workSttsCd: selfEntry.workSttsCd,
    started: Boolean(selfEntry.workBgngDt),
    ended: Boolean(selfEntry.workEndDt),
  });

  if (!currentRecord?.workEndDt) {
    await api(session, "/rest/attendance", {
      method: "PUT",
      body: {
        workYmd: normalizeWorkYmd(currentRecord?.workYmd || todayWorkYmd()),
      },
    });

    const afterClockOut = await api(session, `/rest/attendance/${encodeURIComponent(loginId)}`);
    currentRecord = findTodayRecord(afterClockOut.body?.listTAA || []);
    if (!currentRecord?.workEndDt) {
      throw new Error("Clock-out succeeded but today's attendance record was not completed.");
    }
    results.push({
      step: "clockOut",
      workYmd: currentRecord.workYmd,
      workHr: currentRecord.workHr,
      ended: true,
    });
  }

  console.log(JSON.stringify({
    baseUrl: normalizedBaseUrl,
    user: loginId,
    results,
  }, null, 2));
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

  const body = await parseBody(response);
  if (!response.ok) {
    throw new Error(`Login failed with ${response.status}: ${JSON.stringify(body)}`);
  }

  const cookieHeader = extractCookieHeader(response);
  if (!cookieHeader) {
    throw new Error("Login succeeded but no Set-Cookie header was returned.");
  }

  return { cookieHeader };
}

async function api(session, path, options = {}) {
  const method = options.method || "GET";
  const headers = new Headers(options.headers || {});
  headers.set("cookie", session.cookieHeader);
  headers.set("accept", "application/json");

  let body = options.body;
  if (body && typeof body === "object" && !(body instanceof FormData) && !headers.has("content-type")) {
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

function findTodayRecord(history) {
  const today = todayWorkYmd();
  return history.find((item) => normalizeWorkYmd(item?.workYmd) === today) || null;
}

function normalizeWorkYmd(value) {
  if (!value) {
    return "";
  }

  const digits = String(value).replace(/\D/g, "");
  return digits.length >= 8 ? digits.slice(0, 8) : "";
}

function todayWorkYmd() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const date = String(now.getDate()).padStart(2, "0");
  return `${year}${month}${date}`;
}

function isRetryable(error) {
  return error?.name === "AbortError";
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

main().catch((error) => {
  console.error("Attendance smoke check crashed", error);
  process.exit(1);
});
