import { Container } from "@cloudflare/containers";

const BACKEND_PREFIXES = [
  "/rest",
  "/common",
  "/public",
  "/mail",
  "/chat",
  "/file",
  "/folder",
  "/actuator",
];

function isBackendRequest(pathname) {
  if (pathname === "/starworks-groupware-websocket") {
    return true;
  }
  return BACKEND_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

function pickDefined(values) {
  return Object.fromEntries(
    Object.entries(values).filter(([, value]) => typeof value === "string" && value.length > 0),
  );
}

function backendEnvVars(env) {
  return pickDefined({
    DB_URL: env.DB_URL,
    DB_USERNAME: env.DB_USERNAME,
    DB_PASSWORD: env.DB_PASSWORD,
    DB_DRIVER: env.DB_DRIVER || "org.postgresql.Driver",
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: env.SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE,
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: env.SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE,
    APP_JOBS_BOARD_WORKSPACE_ENABLED: env.APP_JOBS_BOARD_WORKSPACE_ENABLED,
    APP_JOBS_ATTENDANCE_ENABLED: env.APP_JOBS_ATTENDANCE_ENABLED,
    JWT_SECRET_KEY: env.JWT_SECRET_KEY,
    APP_FRONTEND_BASE_URL: env.APP_FRONTEND_BASE_URL,
    CORS_ALLOW_ORIGINS: env.CORS_ALLOW_ORIGINS,
    COOKIE_DOMAIN: env.COOKIE_DOMAIN,
    COOKIE_SECURE: env.COOKIE_SECURE || "true",
    COOKIE_SAME_SITE: env.COOKIE_SAME_SITE || "Lax",
    COOKIE_MAX_AGE: env.COOKIE_MAX_AGE || "86400",
    SIGNUP_TURNSTILE_ENABLED: env.SIGNUP_TURNSTILE_ENABLED,
    SIGNUP_TURNSTILE_SITE_KEY: env.SIGNUP_TURNSTILE_SITE_KEY,
    SIGNUP_TURNSTILE_SECRET_KEY: env.SIGNUP_TURNSTILE_SECRET_KEY,
    SIGNUP_RATE_LIMIT_IP_MAX_ATTEMPTS: env.SIGNUP_RATE_LIMIT_IP_MAX_ATTEMPTS,
    SIGNUP_RATE_LIMIT_IP_WINDOW_SECONDS: env.SIGNUP_RATE_LIMIT_IP_WINDOW_SECONDS,
    SIGNUP_RATE_LIMIT_EMAIL_MAX_ATTEMPTS: env.SIGNUP_RATE_LIMIT_EMAIL_MAX_ATTEMPTS,
    SIGNUP_RATE_LIMIT_EMAIL_WINDOW_SECONDS: env.SIGNUP_RATE_LIMIT_EMAIL_WINDOW_SECONDS,
    SIGNUP_RATE_LIMIT_SLUG_MAX_ATTEMPTS: env.SIGNUP_RATE_LIMIT_SLUG_MAX_ATTEMPTS,
    SIGNUP_RATE_LIMIT_SLUG_WINDOW_SECONDS: env.SIGNUP_RATE_LIMIT_SLUG_WINDOW_SECONDS,
    FILE_STORAGE_MODE: env.FILE_STORAGE_MODE || "s3",
    FILE_STORAGE_URL: env.FILE_STORAGE_URL || "/starworks_medias",
    FILE_STORAGE_PATH: env.FILE_STORAGE_PATH,
    FORWARD_HEADERS_STRATEGY: env.FORWARD_HEADERS_STRATEGY || "framework",
    AWS_ACCESS_KEY: env.AWS_ACCESS_KEY,
    AWS_SECRET_KEY: env.AWS_SECRET_KEY,
    AWS_REGION: env.AWS_REGION || "auto",
    AWS_BUCKET: env.AWS_BUCKET,
    AWS_ENDPOINT: env.AWS_ENDPOINT,
    AWS_PUBLIC_BASE_URL: env.AWS_PUBLIC_BASE_URL,
    AWS_PATH_STYLE_ACCESS: env.AWS_PATH_STYLE_ACCESS || "true",
    APP_LOG_LEVEL: env.APP_LOG_LEVEL || "info",
    REQUEST_MAPPING_LOG_LEVEL: env.REQUEST_MAPPING_LOG_LEVEL || "warn",
    SPRING_VIEW_LOG_LEVEL: env.SPRING_VIEW_LOG_LEVEL || "warn",
    SECURITY_LOG_LEVEL: env.SECURITY_LOG_LEVEL || "info",
    SPRING_AI_LOG_LEVEL: env.SPRING_AI_LOG_LEVEL || "warn",
    SPRING_AI_VERTEX_AI_GEMINI_PROJECT_ID: env.SPRING_AI_VERTEX_AI_GEMINI_PROJECT_ID,
    SPRING_AI_VERTEX_AI_GEMINI_LOCATION: env.SPRING_AI_VERTEX_AI_GEMINI_LOCATION,
    SPRING_AI_VERTEX_AI_EMBEDDING_PROJECT_ID: env.SPRING_AI_VERTEX_AI_EMBEDDING_PROJECT_ID,
    SPRING_AI_VERTEX_AI_EMBEDDING_LOCATION: env.SPRING_AI_VERTEX_AI_EMBEDDING_LOCATION,
    SERVER_PORT: env.BACKEND_INTERNAL_PORT || "18080",
  });
}

export class BackendContainer extends Container {
  defaultPort = 18080;
  requiredPorts = [18080];
  sleepAfter = "20m";
  pingEndpoint = "containerstarthealthcheck/actuator/health";
  enableInternet = true;

  constructor(ctx, env) {
    super(ctx, env);
    this.envVars = backendEnvVars(env);
  }

  onStart() {
    console.log("Backend container started");
  }

  onError(error) {
    console.error("Backend container error", error);
    throw error;
  }
}

async function forwardToBackend(request, env) {
  const url = new URL(request.url);
  const headers = new Headers(request.headers);
  headers.set("x-forwarded-host", url.host);
  headers.set("x-forwarded-proto", url.protocol.replace(":", ""));

  const backendRequest = new Request(request, { headers });
  const containerName = env.BACKEND_CONTAINER_NAME || "primary";
  const backend = env.BACKEND.getByName(containerName);
  const backendPort = Number(env.BACKEND_INTERNAL_PORT || "18080");

  try {
    await backend.startAndWaitForPorts({
      ports: backendPort,
      startOptions: {
        envVars: backendEnvVars(env),
      },
      cancellationOptions: {
        instanceGetTimeoutMS: 120000,
        portReadyTimeoutMS: 120000,
        waitInterval: 1000,
      },
    });

    return await backend.fetch(backendRequest);
  } catch (error) {
    console.error("Backend request failed", error);
    const message = error instanceof Error ? error.message : String(error);
    return new Response(
      JSON.stringify({
        error: "BACKEND_CONTAINER_FAILURE",
        message,
      }),
      {
        status: 500,
        headers: {
          "content-type": "application/json; charset=utf-8",
        },
      },
    );
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (isBackendRequest(url.pathname)) {
      return forwardToBackend(request, env);
    }
    return env.ASSETS.fetch(request);
  },
};
