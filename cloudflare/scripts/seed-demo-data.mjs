const baseUrl = process.env.BASE_URL;
const loginId = process.env.LOGIN_ID;
const loginPassword = process.env.LOGIN_PASSWORD;

if (!baseUrl || !loginId || !loginPassword) {
  console.error("Usage: BASE_URL=https://... LOGIN_ID=admin LOGIN_PASSWORD=admin1234 npm run seed:demo");
  process.exit(1);
}

const normalizedBaseUrl = baseUrl.replace(/\/+$/, "");
const requestTimeoutMs = 30000;
const maxAttempts = 3;
const demoStamp = "2026-03";
const demoTag = `[DEMO ${demoStamp}]`;

const TITLES = {
  dashboardNotice: `${demoTag} Platform notice`,
  dashboardFeed: `${demoTag} Weekly release brief`,
  dashboardTodo: `${demoTag} Dashboard QA checklist`,
  communityName: `${demoTag} Platform TF`,
  communityStory: `${demoTag} Community update`,
  communityTodo: `${demoTag} Community action item`,
  projectName: `${demoTag} Cloud rollout project`,
  projectTask: `${demoTag} Validate dashboard widgets`,
  userSchedule: `${demoTag} Admin review block`,
  deptSchedule: `${demoTag} Team sync schedule`,
  approvalTitle: `${demoTag} admin inbox approval`,
  meetingRoom: `${demoTag} Ops room`,
  meetingReservation: `${demoTag} Daily ops standup`,
  messengerRoom: `${demoTag} Ops chat room`,
  contractTemplate: `${demoTag} Standard e-contract`,
  contractTitle: `${demoTag} Sample contract`,
  emailSubject: `${demoTag} demo mail`,
};

async function main() {
  const session = await login();
  const users = await getUsers(session);
  const adminUser = users.find((user) => user.userId === loginId) || users[0];
  const peerUsers = users.filter((user) => user.userId !== adminUser?.userId);
  const primaryPeer = peerUsers[0] || adminUser;
  const secondaryPeer = peerUsers[1] || primaryPeer || adminUser;
  const summary = [];

  await seedDashboard(session, summary);
  const community = await seedCommunity(session, summary, primaryPeer, secondaryPeer);
  await seedProject(session, summary, primaryPeer, secondaryPeer);
  await seedCalendar(session, summary);
  await seedApproval(session, summary, primaryPeer, secondaryPeer);
  await seedMeeting(session, summary);
  await seedMessenger(session, summary, primaryPeer, secondaryPeer);
  await seedContract(session, summary, adminUser, primaryPeer);
  summary.push({
    name: "email.inbox",
    status: "skipped",
    reason: "Email demo data is intentionally deferred for a later phase.",
  });

  console.log("Demo seed completed");
  console.log(JSON.stringify(summary, null, 2));
}

async function seedDashboard(session, summary) {
  const feed = await api(session, `/rest/dashboard/feed?scope=all&category=all&sort=recent&view=summary&page=1&q=${encodeURIComponent(demoTag)}`);
  const feedTitles = new Set(collectItems(feed.body).map((item) => item.title || item.pstTtl).filter(Boolean));

  await ensure(summary, "dashboard.notice", feedTitles.has(TITLES.dashboardNotice), async () => {
    await api(session, "/rest/board", {
      method: "POST",
      body: {
        bbsCtgrCd: "F101",
        pstTtl: TITLES.dashboardNotice,
        contents: "Cloudflare production environment guide and release notice.",
        fixedYn: "Y",
      },
    });
  });

  await ensure(summary, "dashboard.feedPost", feedTitles.has(TITLES.dashboardFeed), async () => {
    await api(session, "/rest/board", {
      method: "POST",
      body: {
        bbsCtgrCd: "F104",
        pstTtl: TITLES.dashboardFeed,
        contents: "Dashboard and workspace demo content for the production smoke walkthrough.",
        fixedYn: "N",
      },
    });
  });

  const todos = await api(session, "/rest/dashboard/todos");
  await ensure(summary, "dashboard.todo", asArray(todos.body).some((todo) => todo.todoTtl === TITLES.dashboardTodo), async () => {
    await api(session, "/rest/dashboard/todos", {
      method: "POST",
      body: {
        todoTtl: TITLES.dashboardTodo,
        todoCn: "Review the production dashboard cards and sample data blocks.",
      },
    });
  });
}

async function seedCommunity(session, summary, primaryPeer, secondaryPeer) {
  const communityList = await api(session, "/rest/communities?view=joined");
  let community = asArray(communityList.body).find((item) => item.communityNm === TITLES.communityName);

  await ensure(summary, "community.create", Boolean(community), async () => {
    const response = await api(session, "/rest/communities", {
      method: "POST",
      body: {
        communityNm: TITLES.communityName,
        communityDesc: "Community data for dashboard, workspace, and membership screens.",
        communityTypeCd: "general",
        visibilityCd: "public",
        joinPolicyCd: "instant",
        introText: "Demo community for production walkthroughs.",
        postTemplateHtml: "<p>Use this board for release notes, checklists, and operations updates.</p>",
        operatorUserIds: [primaryPeer.userId],
        memberUserIds: uniqueUserIds([primaryPeer.userId, secondaryPeer.userId]),
      },
    });
    community = response.body;
  });

  if (!community?.communityId) {
    const refreshed = await api(session, "/rest/communities?view=joined");
    community = asArray(refreshed.body).find((item) => item.communityNm === TITLES.communityName) || community;
  }

  if (!community?.communityId) {
    return null;
  }

  const workspace = await api(session, `/rest/boards?communityId=${encodeURIComponent(community.communityId)}&page=1&sort=recent`);
  const workspaceItems = asArray(workspace.body?.items);

  await ensure(summary, "community.board.story", workspaceItems.some((item) => item.pstTtl === TITLES.communityStory), async () => {
    await api(session, "/rest/boards", {
      method: "POST",
      body: {
        bbsCtgrCd: "F104",
        communityId: Number(community.communityId),
        pstTtl: TITLES.communityStory,
        contents: "Community story item for the dashboard and community workspace.",
        pstTypeCd: "story",
        visibilityCd: "community",
        importanceCd: "important",
        fixedYn: "N",
        mentionUserIds: [],
      },
    });
  });

  await ensure(summary, "community.board.todo", workspaceItems.some((item) => item.pstTtl === TITLES.communityTodo), async () => {
    await api(session, "/rest/boards", {
      method: "POST",
      body: {
        bbsCtgrCd: "F104",
        communityId: Number(community.communityId),
        pstTtl: TITLES.communityTodo,
        contents: "Community todo item for workspace task cards.",
        pstTypeCd: "todo",
        visibilityCd: "community",
        importanceCd: "normal",
        fixedYn: "N",
        mentionUserIds: [],
        todo: {
          dueDt: futureIso(3),
          assignees: uniqueUserIds([primaryPeer.userId, secondaryPeer.userId]).map((userId) => ({ userId })),
        },
      },
    });
  });

  return community;
}

async function seedProject(session, summary, primaryPeer, secondaryPeer) {
  const projects = await api(session, "/rest/project");
  let project = asArray(projects.body).find((item) => item.bizNm === TITLES.projectName);

  await ensure(summary, "project.create", Boolean(project), async () => {
    const response = await api(session, "/rest/project", {
      method: "POST",
      body: {
        bizNm: TITLES.projectName,
        bizTypeCd: "B201",
        bizSttsCd: "B301",
        bizGoal: "Consolidate Cloudflare deployment, smoke checks, and demo data validation.",
        bizDetail: "Project used to exercise the dashboard, project board, and tasks.",
        bizScope: "Dashboard, messaging, meetings, contracts, and release walkthrough.",
        bizBdgt: 500000,
        bizPrgrs: 0,
        strtBizDt: futureLocal(0),
        endBizDt: futureLocal(14),
        members: uniqueUserIds([primaryPeer.userId, secondaryPeer.userId]).map((bizUserId) => ({ bizUserId, bizAuthCd: "B102" })),
      },
    });
    project = response.body;
  });

  if (!project?.bizId) {
    const refreshed = await api(session, "/rest/project");
    project = asArray(refreshed.body).find((item) => item.bizNm === TITLES.projectName) || project;
  }

  if (!project?.bizId) {
    return;
  }

  const tasks = await api(session, `/rest/project/${encodeURIComponent(project.bizId)}/tasks`);
  await ensure(summary, "project.task", asArray(tasks.body).some((item) => item.taskNm === TITLES.projectTask), async () => {
    await api(session, `/rest/project/${encodeURIComponent(project.bizId)}/tasks`, {
      method: "POST",
      body: {
        taskNm: TITLES.projectTask,
        bizUserId: primaryPeer.userId,
        taskSttsCd: "B401",
        strtTaskDt: futureLocal(0),
        endTaskDt: futureLocal(5),
        taskDetail: "Verify dashboard widgets and quick actions with seeded demo records.",
        taskPrgrs: 0,
      },
    });
  });
}

async function seedCalendar(session, summary) {
  const userCalendar = await api(session, "/rest/calendar-user");
  await ensure(summary, "calendar.user", asArray(userCalendar.body).some((item) => item.schdTtl === TITLES.userSchedule), async () => {
    await api(session, "/rest/calendar-user", {
      method: "POST",
      body: {
        schdTtl: TITLES.userSchedule,
        schdStrtDt: futureIso(1, 9),
        schdEndDt: futureIso(1, 10),
        allday: "N",
        userSchdExpln: "Personal review block for the release walkthrough.",
      },
    });
  });

  const deptCalendar = await api(session, "/rest/calendar-depart");
  await ensure(summary, "calendar.department", asArray(deptCalendar.body).some((item) => item.schdTtl === TITLES.deptSchedule), async () => {
    await api(session, "/rest/calendar-depart", {
      method: "POST",
      body: {
        schdTtl: TITLES.deptSchedule,
        schdStrtDt: futureIso(2, 14),
        schdEndDt: futureIso(2, 15),
        allday: "N",
        deptSchdExpln: "Department schedule for the seeded calendar widgets.",
      },
    });
  });
}

async function seedApproval(session, summary, primaryPeer, secondaryPeer) {
  const inbox = await api(session, "/rest/approval-documents?section=inbox&page=1");
  await ensure(summary, "approval.document", collectItems(inbox.body).some((item) => item.atrzDocTtl === TITLES.approvalTitle), async () => {
    const templates = await api(session, "/rest/approval-template");
    const template = asArray(templates.body).find((item) => item.atrzDocCd === "EXP_APPROVAL") || asArray(templates.body)[0];
    if (!template) {
      throw new Error("No approval template available for demo seeding");
    }

    const htmlData = fillTemplateCells(template.htmlContents, [
      futureDate(0),
      "150000",
      "Operations",
      "Admin inbox seed document",
    ]);

    await api(session, "/rest/approval-documents", {
      method: "POST",
      body: {
        atrzDocTmplId: template.atrzDocTmplId,
        atrzDocTtl: TITLES.approvalTitle,
        htmlData,
        openYn: "N",
        atrzFileId: "",
        receiverIds: uniqueUserIds([secondaryPeer.userId]),
        approvalLines: [
          {
            atrzApprUserId: loginId,
            apprAtrzYn: "N",
          },
        ],
      },
    });
  });
}

async function seedMeeting(session, summary) {
  const rooms = await api(session, "/rest/meeting/room");
  let room = asArray(rooms.body).find((item) => item.roomName === TITLES.meetingRoom);

  await ensure(summary, "meeting.room", Boolean(room), async () => {
    const response = await api(session, "/rest/meeting/room", {
      method: "POST",
      body: {
        roomName: TITLES.meetingRoom,
        location: "HQ Floor 5",
        capacity: 8,
        useYn: "Y",
      },
    });
    room = response.body;
  });

  if (!room?.roomId) {
    const refreshed = await api(session, "/rest/meeting/room");
    room = asArray(refreshed.body).find((item) => item.roomName === TITLES.meetingRoom) || room;
  }

  if (!room?.roomId) {
    return;
  }

  const meetingDate = futureDate(0);
  const reservations = await api(session, `/rest/meeting/reservations?date=${meetingDate}&role=admin`);
  await ensure(summary, "meeting.reservation", asArray(reservations.body).some((item) => item.title === TITLES.meetingReservation), async () => {
    await api(session, "/rest/meeting", {
      method: "POST",
      body: {
        roomId: room.roomId,
        title: TITLES.meetingReservation,
        meetingDate,
        startTime: 10,
        endTime: 11,
      },
    });
  });
}

async function seedMessenger(session, summary, primaryPeer, secondaryPeer) {
  const rooms = await api(session, "/chat/rooms");
  await ensure(summary, "messenger.room", asArray(rooms.body).some((item) => item.msgrNm === TITLES.messengerRoom), async () => {
    await api(session, "/chat/room/create", {
      method: "POST",
      body: {
        roomNm: TITLES.messengerRoom,
        userIds: uniqueUserIds([primaryPeer.userId, secondaryPeer.userId]),
        isGroup: true,
        roomTypeCd: "group",
      },
    });
  });
}

async function seedContract(session, summary, adminUser, primaryPeer) {
  const templates = await api(session, "/rest/contracts/templates");
  let template = asArray(templates.body).find((item) => item.templateNm === TITLES.contractTemplate);

  await ensure(summary, "contract.template", Boolean(template), async () => {
    const response = await api(session, "/rest/contracts/templates", {
      method: "POST",
      body: {
        templateNm: TITLES.contractTemplate,
        templateCode: `DEMO_${Date.now()}`,
        templateDesc: "Template used for contract workspace demo data.",
        templateCategoryCd: "general",
        versionLabel: "v1.0",
        versionNote: "Automated demo template version",
        schema: {
          pages: [{ pageId: "page-1", label: "1" }],
          fields: [
            { fieldId: "field-company", fieldKey: "companyName", label: "Company", fieldTypeCd: "text", assignmentCd: "creator", signerOrder: 1, x: 72, y: 160, width: 240, height: 44 },
            { fieldId: "field-signer", fieldKey: "signerName", label: "Signer", fieldTypeCd: "text", assignmentCd: "signer", signerOrder: 1, x: 72, y: 240, width: 240, height: 44 },
          ],
        },
        layout: {
          canvas: { width: 820, height: 1160, padding: 48, backgroundColor: "#ffffff" },
        },
      },
    });
    template = response.body;
  });

  if (!template?.templateId) {
    const refreshed = await api(session, "/rest/contracts/templates");
    template = asArray(refreshed.body).find((item) => item.templateNm === TITLES.contractTemplate) || template;
  }

  if (!template?.templateId) {
    return;
  }

  const detail = await api(session, `/rest/contracts/templates/${encodeURIComponent(template.templateId)}`);
  if (!detail.body?.publishedVersionId) {
    await api(session, `/rest/contracts/templates/${encodeURIComponent(template.templateId)}/publish`, {
      method: "POST",
      body: {},
    });
  }

  const contracts = await api(session, "/rest/contracts");
  let contract = collectItems(contracts.body).find((item) => item.contractTitle === TITLES.contractTitle);
  await ensure(summary, "contract.create", Boolean(contract), async () => {
    const created = await api(session, "/rest/contracts", {
      method: "POST",
      body: {
        templateId: Number(template.templateId),
        contractTitle: TITLES.contractTitle,
        contractMessage: "Contract workspace, counters, and links demo record.",
        sendTypeCd: "remote",
        signingFlowCd: "parallel",
        statusCd: "draft",
        senderNm: adminUser?.userNm || "Admin",
        senderEmail: adminUser?.userEmail || "admin@modulearning.local",
        senderTelno: adminUser?.userTelno || "010-0000-0000",
        expiresDt: futureIso(7, 18),
        signers: [
          {
            signerNm: primaryPeer.userNm || "Demo Signer",
            signerEmail: primaryPeer.userEmail || "demo-signer@example.com",
            signerTelno: primaryPeer.userTelno || "010-0000-0000",
          },
        ],
      },
    });
    contract = created.body;
  });

  const contractId = contract?.contractId;
  if (!contractId) {
    return;
  }

  const contractDetail = await api(session, `/rest/contracts/${encodeURIComponent(contractId)}`);
  if (contractDetail.body?.statusCd === "draft" || contractDetail.body?.statusCd === "ready") {
    await ensure(summary, "contract.send", false, async () => {
      await api(session, `/rest/contracts/${encodeURIComponent(contractId)}/send`, {
        method: "POST",
        body: {},
      });
    });
  } else {
    summary.push({ name: "contract.send", status: "skipped" });
  }
}

async function getUsers(session) {
  const response = await api(session, "/rest/comm-user");
  const users = asArray(response.body);
  if (users.length === 0) {
    throw new Error("No users are available for demo seeding");
  }
  return users;
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

async function ensure(summary, name, exists, create) {
  if (exists) {
    summary.push({ name, status: "skipped" });
    return;
  }

  try {
    await create();
    summary.push({ name, status: "created" });
  } catch (error) {
    summary.push({
      name,
      status: "failed",
      error: error instanceof Error ? error.message : String(error),
    });
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

function uniqueUserIds(values) {
  return [...new Set(values.filter(Boolean))];
}

function fillTemplateCells(html, values) {
  let next = String(html || "");
  for (const value of values) {
    next = next.replace("<td></td>", `<td>${escapeHtml(value)}</td>`);
  }
  return next;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function futureIso(daysFromNow, hour = 9) {
  const date = new Date();
  date.setDate(date.getDate() + daysFromNow);
  date.setHours(hour, 0, 0, 0);
  return date.toISOString();
}

function futureLocal(daysFromNow, hour = 9) {
  const date = new Date();
  date.setDate(date.getDate() + daysFromNow);
  date.setHours(hour, 0, 0, 0);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

function futureDate(daysFromNow) {
  const date = new Date();
  date.setDate(date.getDate() + daysFromNow);
  return date.toISOString().slice(0, 10);
}

function isRetryable(error) {
  return error?.name === "AbortError";
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

main().catch((error) => {
  console.error("Demo seed failed", error);
  process.exit(1);
});
