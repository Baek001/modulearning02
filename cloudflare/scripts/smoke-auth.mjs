const baseUrl = process.env.BASE_URL;
const loginId = process.env.LOGIN_ID;
const loginPassword = process.env.LOGIN_PASSWORD;

if (!baseUrl || !loginId || !loginPassword) {
  console.error("Usage: BASE_URL=https://... LOGIN_ID=admin LOGIN_PASSWORD=admin1234 npm run smoke:auth");
  process.exit(1);
}

const normalizedBaseUrl = baseUrl.replace(/\/+$/, "");
const requestTimeoutMs = 30000;
const maxAttempts = 3;

async function main() {
  const loginResponse = await request("/common/auth", {
    method: "POST",
    headers: {
      "content-type": "application/json",
    },
    body: JSON.stringify({
      username: loginId,
      password: loginPassword,
    }),
  });

  const loginBody = await readBody(loginResponse);
  if (!loginResponse.ok) {
    console.error("Login failed", {
      status: loginResponse.status,
      body: loginBody,
    });
    process.exit(1);
  }

  const cookieHeader = extractCookieHeader(loginResponse);
  if (!cookieHeader) {
    console.error("Login succeeded but no Set-Cookie header was returned");
    process.exit(1);
  }

  const mypageResponse = await request("/rest/mypage", {
    headers: {
      cookie: cookieHeader,
    },
  });
  const mypageBody = await readBody(mypageResponse);

  if (!mypageResponse.ok) {
    console.error("Session check failed", {
      status: mypageResponse.status,
      body: mypageBody,
    });
    process.exit(1);
  }

  console.log("Auth smoke check passed");
  console.log(JSON.stringify({
    baseUrl: normalizedBaseUrl,
    user: loginId,
    mypageStatus: mypageResponse.status,
  }, null, 2));
}

function extractCookieHeader(response) {
  const setCookies = typeof response.headers.getSetCookie === "function"
    ? response.headers.getSetCookie()
    : [response.headers.get("set-cookie")].filter(Boolean);

  if (!setCookies.length) {
    return "";
  }

  return setCookies
    .map((entry) => entry.split(";")[0])
    .join("; ");
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

function isRetryable(error) {
  return error?.name === "AbortError";
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function readBody(response) {
  const contentType = String(response.headers.get("content-type") || "");
  if (contentType.includes("application/json")) {
    return response.json();
  }
  return response.text();
}

main().catch((error) => {
  console.error("Auth smoke check crashed", error);
  process.exit(1);
});
