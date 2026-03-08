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
const demoUserPassword = "demo1234";

const DEMO_USERS = [
  {
    userId: "devlead",
    userPswd: demoUserPassword,
    userNm: "Dev Lead",
    userEmail: "devlead@modulearning.local",
    userTelno: "010-0000-2101",
    extTel: "2101",
    deptId: "DP001002",
    jbgdCd: "J003",
  },
  {
    userId: "opslead",
    userPswd: demoUserPassword,
    userNm: "Ops Lead",
    userEmail: "opslead@modulearning.local",
    userTelno: "010-0000-1101",
    extTel: "1101",
    deptId: "DP001001",
    jbgdCd: "J003",
  },
  {
    userId: "ops01",
    userPswd: demoUserPassword,
    userNm: "Ops Staff",
    userEmail: "ops01@modulearning.local",
    userTelno: "010-0000-1102",
    extTel: "1102",
    deptId: "DP001001",
    jbgdCd: "J001",
  },
];

const ACCOUNT_PASSWORDS = {
  admin: loginPassword,
  user01: "user1234",
  user02: "user2234",
  devlead: demoUserPassword,
  opslead: demoUserPassword,
  ops01: demoUserPassword,
};

const TITLES = {
  dashboardNotice: `${demoTag} Platform notice`,
  dashboardFeedEngineering: `${demoTag} Engineering release brief`,
  dashboardFeedOperations: `${demoTag} Operations handoff note`,
  dashboardTodoAdmin: `${demoTag} Dashboard QA checklist`,
  dashboardTodoOps: `${demoTag} Ops shift handover`,
  communityName: `${demoTag} Cross-team launch room`,
  communityStory: `${demoTag} Community launch update`,
  communityTodo: `${demoTag} Community action item`,
  projectName: `${demoTag} Cross-team rollout project`,
  projectTaskEngineering: `${demoTag} Validate dashboard widgets`,
  projectTaskOperations: `${demoTag} Confirm rollout checklist`,
  userScheduleDev: `${demoTag} Dev review block`,
  userScheduleOps: `${demoTag} Ops readiness review`,
  deptScheduleDev: `${demoTag} Dev release sync`,
  deptScheduleOps: `${demoTag} Ops desk sync`,
  approvalOpsLeadDraft: `${demoTag} opslead rollout approval`,
  approvalDevLeadDraft: `${demoTag} devlead rollout approval`,
  approvalOpsStaffDraft: `${demoTag} ops01 shift approval`,
  meetingRoom: `${demoTag} Ops room`,
  meetingReservationAdmin: `${demoTag} Daily ops standup`,
  meetingReservationOps: `${demoTag} Rollout checkpoint`,
  messengerRoom: `${demoTag} Cross-team chat room`,
  contractTemplate: `${demoTag} Standard e-contract`,
  contractTitle: `${demoTag} Sample contract`,
};

async function main() {
  const summary = [];
  const adminSession = await loginAs(loginId, loginPassword);

  let users = await getUsers(adminSession);
  users = await ensureDemoUsers(adminSession, users, summary);

  const actors = resolveActors(users);
  const sessions = await createSessions(actors);

  await seedDashboard(sessions, summary, actors);
  await seedCommunity(sessions, summary, actors);
  await seedProject(sessions, summary, actors);
  await seedCalendar(sessions, summary, actors);
  await seedApproval(sessions, summary, actors);
  await seedMeeting(sessions, summary);
  await seedMessenger(sessions, summary, actors);
  await seedContract(sessions.admin, summary, actors.admin, actors.opsLead);

  summary.push({
    name: "email.inbox",
    status: "skipped",
    reason: "Email demo data is intentionally deferred for a later phase.",
  });

  console.log("Demo seed completed");
  console.log(JSON.stringify(summary, null, 2));
}

async function ensureDemoUsers(session, users, summary) {
  for (const demoUser of DEMO_USERS) {
    const existing = users.find((user) => user.userId === demoUser.userId);
    const payload = {
      ...demoUser,
      hireYmd: today(),
    };

    await ensure(summary, `users.${demoUser.userId}`, false, async () => {
      if (existing) {
        await api(session, `/rest/comm-user/${encodeURIComponent(demoUser.userId)}`, {
          method: "PUT",
          body: payload,
        });
        return "updated";
      }

      await api(session, "/rest/comm-user", {
        method: "POST",
        body: payload,
      });
      return "created";
    });
  }

  return getUsers(session);
}

async function seedDashboard(sessions, summary) {
  const feed = await api(sessions.admin, `/rest/dashboard/feed?scope=all&category=all&sort=recent&view=summary&page=1&q=${encodeURIComponent(demoTag)}`);
  const feedTitles = new Set(collectItems(feed.body).map((item) => item.title || item.pstTtl).filter(Boolean));

  await ensure(summary, "dashboard.notice", feedTitles.has(TITLES.dashboardNotice), async () => {
    await api(sessions.admin, "/rest/board", {
      method: "POST",
      body: {
        bbsCtgrCd: "F101",
        pstTtl: TITLES.dashboardNotice,
        contents: "Cloudflare production environment guide and release notice.",
        fixedYn: "Y",
      },
    });
  });

  await ensure(summary, "dashboard.feed.devlead", feedTitles.has(TITLES.dashboardFeedEngineering), async () => {
    await api(sessions.devLead, "/rest/board", {
      method: "POST",
      body: {
        bbsCtgrCd: "F104",
        pstTtl: TITLES.dashboardFeedEngineering,
        contents: "Engineering handoff note for the rollout walkthrough and dashboard feed.",
        fixedYn: "N",
      },
    });
  });

  await ensure(summary, "dashboard.feed.opslead", feedTitles.has(TITLES.dashboardFeedOperations), async () => {
    await api(sessions.opsLead, "/rest/board", {
      method: "POST",
      body: {
        bbsCtgrCd: "F104",
        pstTtl: TITLES.dashboardFeedOperations,
        contents: "Operations handoff note that shows a second real author in the feed.",
        fixedYn: "N",
      },
    });
  });

  const adminTodos = await api(sessions.admin, "/rest/dashboard/todos");
  await ensure(summary, "dashboard.todo.admin", asArray(adminTodos.body).some((todo) => todo.todoTtl === TITLES.dashboardTodoAdmin), async () => {
    await api(sessions.admin, "/rest/dashboard/todos", {
      method: "POST",
      body: {
        todoTtl: TITLES.dashboardTodoAdmin,
        todoCn: "Review the production dashboard cards and seeded walkthrough blocks.",
      },
    });
  });

  const opsTodos = await api(sessions.opsLead, "/rest/dashboard/todos");
  await ensure(summary, "dashboard.todo.opslead", asArray(opsTodos.body).some((todo) => todo.todoTtl === TITLES.dashboardTodoOps), async () => {
    await api(sessions.opsLead, "/rest/dashboard/todos", {
      method: "POST",
      body: {
        todoTtl: TITLES.dashboardTodoOps,
        todoCn: "Confirm operations readiness, meeting room status, and approval inbox.",
      },
    });
  });
}

async function seedCommunity(sessions, summary, actors) {
  const communityList = await api(sessions.admin, "/rest/communities?view=joined");
  let community = asArray(communityList.body).find((item) => item.communityNm === TITLES.communityName);

  await ensure(summary, "community.create", Boolean(community), async () => {
    await api(sessions.admin, "/rest/communities", {
      method: "POST",
      body: {
        communityNm: TITLES.communityName,
        communityDesc: "Community data for dashboard, workspace, and cross-team launch screens.",
        communityTypeCd: "general",
        visibilityCd: "public",
        joinPolicyCd: "instant",
        introText: "Launch coordination space for engineering and operations.",
        postTemplateHtml: "<p>Use this room for rollout notes, action items, and handoff updates.</p>",
        operatorUserIds: uniqueUserIds([actors.devLead.userId, actors.opsLead.userId]),
        memberUserIds: uniqueUserIds([
          actors.devLead.userId,
          actors.opsLead.userId,
          actors.opsStaff.userId,
          actors.devMember.userId,
          actors.devMember2.userId,
        ]),
      },
    });
  });

  if (!community?.communityId) {
    const refreshed = await api(sessions.admin, "/rest/communities?view=joined");
    community = asArray(refreshed.body).find((item) => item.communityNm === TITLES.communityName) || community;
  }

  if (!community?.communityId) {
    return;
  }

  const workspace = await api(sessions.admin, `/rest/boards?communityId=${encodeURIComponent(community.communityId)}&page=1&sort=recent`);
  const workspaceItems = asArray(workspace.body?.items);

  await ensure(summary, "community.board.story", workspaceItems.some((item) => item.pstTtl === TITLES.communityStory), async () => {
    await api(sessions.devLead, "/rest/boards", {
      method: "POST",
      body: {
        bbsCtgrCd: "F104",
        communityId: Number(community.communityId),
        pstTtl: TITLES.communityStory,
        contents: "Engineering posted update for the cross-team community workspace.",
        pstTypeCd: "story",
        visibilityCd: "community",
        importanceCd: "important",
        fixedYn: "N",
        mentionUserIds: uniqueUserIds([actors.opsLead.userId]),
      },
    });
  });

  await ensure(summary, "community.board.todo", workspaceItems.some((item) => item.pstTtl === TITLES.communityTodo), async () => {
    await api(sessions.opsLead, "/rest/boards", {
      method: "POST",
      body: {
        bbsCtgrCd: "F104",
        communityId: Number(community.communityId),
        pstTtl: TITLES.communityTodo,
        contents: "Operations action item seeded from a second real account.",
        pstTypeCd: "todo",
        visibilityCd: "community",
        importanceCd: "normal",
        fixedYn: "N",
        mentionUserIds: uniqueUserIds([actors.devLead.userId]),
        todo: {
          dueDt: futureIso(3),
          assignees: uniqueUserIds([actors.opsStaff.userId, actors.devMember.userId]).map((userId) => ({ userId })),
        },
      },
    });
  });
}

async function seedProject(sessions, summary, actors) {
  const projects = await api(sessions.admin, "/rest/project");
  let project = asArray(projects.body).find((item) => item.bizNm === TITLES.projectName);

  await ensure(summary, "project.create", Boolean(project), async () => {
    await api(sessions.admin, "/rest/project", {
      method: "POST",
      body: {
        bizNm: TITLES.projectName,
        bizTypeCd: "B202",
        bizSttsCd: "B302",
        bizGoal: "Run a cross-team Cloudflare rollout walkthrough with live demo accounts.",
        bizDetail: "Project used to exercise project board, tasks, calendar, approval, and messenger flows.",
        bizScope: "Dashboard, projects, approvals, meetings, communities, and contracts.",
        bizBdgt: 1250000,
        bizPrgrs: 35,
        strtBizDt: futureLocal(-2),
        endBizDt: futureLocal(12),
        members: [
          { bizUserId: actors.devLead.userId, bizAuthCd: "B101" },
          { bizUserId: actors.opsLead.userId, bizAuthCd: "B102" },
          { bizUserId: actors.devMember.userId, bizAuthCd: "B102" },
          { bizUserId: actors.opsStaff.userId, bizAuthCd: "B103" },
          { bizUserId: actors.devMember2.userId, bizAuthCd: "B103" },
        ],
      },
    });
  });

  if (!project?.bizId) {
    const refreshed = await api(sessions.admin, "/rest/project");
    project = asArray(refreshed.body).find((item) => item.bizNm === TITLES.projectName) || project;
  }

  if (!project?.bizId) {
    return;
  }

  const tasks = await api(sessions.admin, `/rest/project/${encodeURIComponent(project.bizId)}/tasks`);
  const taskItems = asArray(tasks.body?.tasks || tasks.body);

  await ensure(summary, "project.task.devlead", taskItems.some((item) => item.taskNm === TITLES.projectTaskEngineering), async () => {
    await api(sessions.admin, `/rest/project/${encodeURIComponent(project.bizId)}/tasks`, {
      method: "POST",
      body: {
        taskNm: TITLES.projectTaskEngineering,
        bizUserId: actors.devLead.userId,
        taskSttsCd: "B402",
        strtTaskDt: futureLocal(0),
        endTaskDt: futureLocal(5),
        taskDetail: "Validate dashboard widgets and approval list layout in the rollout build.",
        taskPrgrs: 45,
      },
    });
  });

  await ensure(summary, "project.task.opslead", taskItems.some((item) => item.taskNm === TITLES.projectTaskOperations), async () => {
    await api(sessions.admin, `/rest/project/${encodeURIComponent(project.bizId)}/tasks`, {
      method: "POST",
      body: {
        taskNm: TITLES.projectTaskOperations,
        bizUserId: actors.opsLead.userId,
        taskSttsCd: "B401",
        strtTaskDt: futureLocal(1),
        endTaskDt: futureLocal(6),
        taskDetail: "Confirm operations checklist, approvals, meeting rooms, and messenger readiness.",
        taskPrgrs: 10,
      },
    });
  });
}

async function seedCalendar(sessions, summary) {
  const devCalendar = await api(sessions.devLead, "/rest/calendar-user");
  await ensure(summary, "calendar.user.devlead", asArray(devCalendar.body).some((item) => item.schdTtl === TITLES.userScheduleDev), async () => {
    await api(sessions.devLead, "/rest/calendar-user", {
      method: "POST",
      body: {
        schdTtl: TITLES.userScheduleDev,
        schdStrtDt: futureIso(1, 10),
        schdEndDt: futureIso(1, 11),
        allday: "N",
        userSchdExpln: "Engineering release review block for the demo accounts.",
      },
    });
  });

  const opsCalendar = await api(sessions.opsLead, "/rest/calendar-user");
  await ensure(summary, "calendar.user.opslead", asArray(opsCalendar.body).some((item) => item.schdTtl === TITLES.userScheduleOps), async () => {
    await api(sessions.opsLead, "/rest/calendar-user", {
      method: "POST",
      body: {
        schdTtl: TITLES.userScheduleOps,
        schdStrtDt: futureIso(1, 14),
        schdEndDt: futureIso(1, 15),
        allday: "N",
        userSchdExpln: "Operations readiness review for the walkthrough schedule.",
      },
    });
  });

  const devDeptCalendar = await api(sessions.devLead, "/rest/calendar-depart");
  await ensure(summary, "calendar.department.devlead", asArray(devDeptCalendar.body).some((item) => item.schdTtl === TITLES.deptScheduleDev), async () => {
    await api(sessions.devLead, "/rest/calendar-depart", {
      method: "POST",
      body: {
        schdTtl: TITLES.deptScheduleDev,
        schdStrtDt: futureIso(2, 15),
        schdEndDt: futureIso(2, 16),
        allday: "N",
        deptSchdExpln: "Department sync for the engineering rollout checklist.",
      },
    });
  });

  const opsDeptCalendar = await api(sessions.opsLead, "/rest/calendar-depart");
  await ensure(summary, "calendar.department.opslead", asArray(opsDeptCalendar.body).some((item) => item.schdTtl === TITLES.deptScheduleOps), async () => {
    await api(sessions.opsLead, "/rest/calendar-depart", {
      method: "POST",
      body: {
        schdTtl: TITLES.deptScheduleOps,
        schdStrtDt: futureIso(3, 9),
        schdEndDt: futureIso(3, 10),
        allday: "N",
        deptSchdExpln: "Operations desk sync for handoff and room readiness.",
      },
    });
  });
}

async function seedApproval(sessions, summary, actors) {
  const templates = asArray((await api(sessions.admin, "/rest/approval-template")).body);
  const expenseTemplate = pickTemplate(templates, "EXP_APPROVAL");
  const projectTemplate = pickTemplate(templates, "PRO_PROPOSAL") || expenseTemplate;

  if (!expenseTemplate) {
    throw new Error("No approval template available for demo seeding");
  }

  await ensureApprovalDraft(summary, "approval.draft.opslead", sessions.opsLead, TITLES.approvalOpsLeadDraft, {
    template: expenseTemplate,
    receiverIds: uniqueUserIds([actors.admin.userId]),
    approvalLines: [{ atrzApprUserId: actors.admin.userId, apprAtrzYn: "N" }],
    htmlValues: [today(), "280000", "Operations", "Operations rollout approval for the live demo accounts"],
  });

  await ensureApprovalDraft(summary, "approval.draft.devlead", sessions.devLead, TITLES.approvalDevLeadDraft, {
    template: projectTemplate,
    receiverIds: uniqueUserIds([actors.opsLead.userId]),
    approvalLines: [{ atrzApprUserId: actors.admin.userId, apprAtrzYn: "N" }],
    htmlValues: ["Cross-team rollout", `${today()} ~ ${futureDate(7)}`, "Stabilize seeded demo flows and approval UX", "Dashboard, approvals, messenger, and operations handoff"],
  });

  await ensureApprovalDraft(summary, "approval.draft.ops01", sessions.opsStaff, TITLES.approvalOpsStaffDraft, {
    template: expenseTemplate,
    receiverIds: uniqueUserIds([actors.admin.userId]),
    approvalLines: [{ atrzApprUserId: actors.opsLead.userId, apprAtrzYn: "N" }],
    htmlValues: [today(), "85000", "Shift handover", "Desk coverage and handoff checklist for launch day"],
  });
}

async function seedMeeting(sessions, summary) {
  const rooms = await api(sessions.admin, "/rest/meeting/room");
  let room = asArray(rooms.body).find((item) => item.roomName === TITLES.meetingRoom);

  await ensure(summary, "meeting.room", Boolean(room), async () => {
    await api(sessions.admin, "/rest/meeting/room", {
      method: "POST",
      body: {
        roomName: TITLES.meetingRoom,
        location: "HQ Floor 5",
        capacity: 8,
        useYn: "Y",
      },
    });
  });

  if (!room?.roomId) {
    const refreshed = await api(sessions.admin, "/rest/meeting/room");
    room = asArray(refreshed.body).find((item) => item.roomName === TITLES.meetingRoom) || room;
  }

  if (!room?.roomId) {
    return;
  }

  const meetingDate = futureDate(1);
  const reservations = await api(sessions.admin, `/rest/meeting/reservations?date=${meetingDate}&role=admin`);
  const reservationTitles = new Set(asArray(reservations.body).map((item) => item.title));

  await ensure(summary, "meeting.reservation.admin", reservationTitles.has(TITLES.meetingReservationAdmin), async () => {
    await api(sessions.admin, "/rest/meeting", {
      method: "POST",
      body: {
        roomId: room.roomId,
        title: TITLES.meetingReservationAdmin,
        meetingDate,
        startTime: 10,
        endTime: 11,
      },
    });
  });

  await ensure(summary, "meeting.reservation.opslead", reservationTitles.has(TITLES.meetingReservationOps), async () => {
    await api(sessions.opsLead, "/rest/meeting", {
      method: "POST",
      body: {
        roomId: room.roomId,
        title: TITLES.meetingReservationOps,
        meetingDate,
        startTime: 15,
        endTime: 16,
      },
    });
  });
}

async function seedMessenger(sessions, summary, actors) {
  const rooms = await api(sessions.admin, "/chat/rooms");
  await ensure(summary, "messenger.room.group", asArray(rooms.body).some((item) => item.msgrNm === TITLES.messengerRoom), async () => {
    await api(sessions.admin, "/chat/room/create", {
      method: "POST",
      body: {
        roomNm: TITLES.messengerRoom,
        userIds: uniqueUserIds([actors.devLead.userId, actors.opsLead.userId, actors.opsStaff.userId]),
        isGroup: true,
        roomTypeCd: "group",
      },
    });
  });

  await api(sessions.admin, `/chat/room/findOrCreate?userId=${encodeURIComponent(actors.opsLead.userId)}`);
  summary.push({ name: "messenger.room.private.admin-opslead", status: "ensured" });

  await api(sessions.devLead, `/chat/room/findOrCreate?userId=${encodeURIComponent(actors.opsStaff.userId)}`);
  summary.push({ name: "messenger.room.private.devlead-ops01", status: "ensured" });
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

async function ensureApprovalDraft(summary, name, session, title, config) {
  const draftList = await api(session, "/rest/approval-documents?section=draft&page=1");
  await ensure(summary, name, collectItems(draftList.body).some((item) => item.atrzDocTtl === title), async () => {
    await api(session, "/rest/approval-documents", {
      method: "POST",
      body: {
        atrzDocTmplId: config.template.atrzDocTmplId,
        atrzDocTtl: title,
        htmlData: fillTemplateCells(config.template.htmlContents, config.htmlValues),
        openYn: config.openYn || "N",
        atrzFileId: "",
        receiverIds: config.receiverIds || [],
        approvalLines: config.approvalLines || [],
      },
    });
  });
}

function pickTemplate(templates, code) {
  return asArray(templates).find((item) => item.atrzDocCd === code) || asArray(templates)[0] || null;
}

function resolveActors(users) {
  return {
    admin: requireUser(users, loginId, "admin"),
    devLead: requireUser(users, "devlead", "devlead"),
    opsLead: requireUser(users, "opslead", "opslead"),
    opsStaff: requireUser(users, "ops01", "ops01"),
    devMember: requireUser(users, "user01", "user01"),
    devMember2: requireUser(users, "user02", "user02"),
  };
}

async function createSessions(actors) {
  return {
    admin: await loginAs(actors.admin.userId, resolvePassword(actors.admin.userId)),
    devLead: await loginAs(actors.devLead.userId, resolvePassword(actors.devLead.userId)),
    opsLead: await loginAs(actors.opsLead.userId, resolvePassword(actors.opsLead.userId)),
    opsStaff: await loginAs(actors.opsStaff.userId, resolvePassword(actors.opsStaff.userId)),
    devMember: await loginAs(actors.devMember.userId, resolvePassword(actors.devMember.userId)),
    devMember2: await loginAs(actors.devMember2.userId, resolvePassword(actors.devMember2.userId)),
  };
}

function resolvePassword(userId) {
  if (userId === loginId) {
    return loginPassword;
  }

  const password = ACCOUNT_PASSWORDS[userId];
  if (!password) {
    throw new Error(`No password configured for ${userId}`);
  }
  return password;
}

function requireUser(users, userId, label) {
  const user = asArray(users).find((entry) => entry.userId === userId);
  if (!user) {
    throw new Error(`Required demo user ${label} (${userId}) is missing`);
  }
  return user;
}

async function getUsers(session) {
  const response = await api(session, "/rest/comm-user");
  const users = asArray(response.body);
  if (users.length === 0) {
    throw new Error("No users are available for demo seeding");
  }
  return users;
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
    throw new Error(`Login failed for ${username} with ${response.status}: ${await response.text()}`);
  }

  const cookieHeader = extractCookieHeader(response);
  if (!cookieHeader) {
    throw new Error(`Login succeeded for ${username} but no Set-Cookie header was returned`);
  }

  return { cookieHeader, userId: username };
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
    const action = await create();
    summary.push({
      name,
      status: action === "updated" ? "updated" : "created",
    });
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

function today() {
  return new Date().toISOString().slice(0, 10);
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
