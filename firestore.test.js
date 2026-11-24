const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, email = "alice@iba-suk.edu.pk", timestamp = new Date(Date.now() - 1000)) {
  return {
    uid,
    fullName: "Alice Khan",
    email,
    university: "Sukkur IBA University",
    department: "Computer Science",
    semester: "5th Semester",
    phoneNumber: "+923001234567",
    profileImage: "https://example.com/avatar.png",
    createdAt: timestamp,
    updatedAt: timestamp,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read, list, create, update, or delete any user profile or workspace items", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("courses").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("timetable").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("assignments").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("quizzes").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("exams").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("calendarEvents").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("scheduledReminders").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notifications").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("attendanceLogs").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("academicSettings").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("semesterGpaRecords").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("studySessions").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("studyPlannerSettings").get());
});

test("Authenticated owner: valid Kotlin create & partial update (including phoneNumber) succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, "alice@iba-suk.edu.pk", created))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      fullName: "Alice K.",
      semester: "6th Semester",
      phoneNumber: "+923009876543",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: workspace subcollections (tasks, courses, notes, reminders, goals) CRUD & validation succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Tasks
  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task1");
  await assertSucceeds(
    taskRef.set({
      uid: ALICE_UID,
      title: "Compiler Design Lab 4",
      courseName: "CS-312",
      category: "ASSIGNMENT",
      dueDateText: "Tomorrow, 11:59 PM",
      completed: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(taskRef.update({ completed: true, updatedAt: new Date(Date.now() - 500) }));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get());

  // Invalid task category or extra id field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("taskBad").set({
      id: "taskBad",
      uid: ALICE_UID,
      title: "Invalid",
      courseName: "CS",
      category: "HOMEWORK",
      dueDateText: "Today",
      completed: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Courses with full metadata & attendance bounds
  const courseRef = aliceDb.collection("users").doc(ALICE_UID).collection("courses").doc("c1");
  await assertSucceeds(
    courseRef.set({
      uid: ALICE_UID,
      courseCode: "CS-312",
      courseName: "Compiler Construction",
      creditHours: 3.0,
      instructor: "Dr. Sara Ahmed",
      semester: "6th Semester",
      description: "Lexical analysis, parsing, semantic checks, and code generation.",
      classroom: "AB-2 Room 204",
      schedule: "Mon & Wed • 09:00 AM - 10:30 AM",
      attendedClasses: 9,
      totalClasses: 10,
      colorIndex: 2,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    courseRef.update({
      creditHours: 4.0,
      instructor: "Prof. Sara Ahmed",
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertFails(
    courseRef.update({ attendedClasses: 11, totalClasses: 10, updatedAt: new Date(Date.now() - 300) })
  );

  // Timetable classes CRUD & validation
  const ttRef = aliceDb.collection("users").doc(ALICE_UID).collection("timetable").doc("tt1");
  await assertSucceeds(
    ttRef.set({
      uid: ALICE_UID,
      dayOfWeek: "MONDAY",
      subjectName: "Compiler Construction",
      courseCode: "CS-312",
      instructor: "Dr. Sara Ahmed",
      classroom: "AB-2 Room 204",
      startTimeMinutes: 540,
      endTimeMinutes: 630,
      colorIndex: 2,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    ttRef.update({
      classroom: "CS Lab 1",
      endTimeMinutes: 645,
      updatedAt: new Date(Date.now() - 250),
    })
  );
  // Reject invalid day or endTime <= startTime
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("timetable").doc("ttBad").set({
      uid: ALICE_UID,
      dayOfWeek: "FUNDAY",
      subjectName: "Invalid",
      courseCode: "XX-100",
      instructor: "Nobody",
      classroom: "Room 1",
      startTimeMinutes: 600,
      endTimeMinutes: 540,
      colorIndex: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("timetable").get());
  await assertSucceeds(ttRef.delete());

  // Notes CRUD (legacy + rich fields: pinned, favorite, attachments, update, delete)
  const noteRef = aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("n1");
  await assertSucceeds(
    noteRef.set({
      uid: ALICE_UID,
      title: "LL(1) Parsing",
      courseName: "CS-312",
      content: "FIRST and FOLLOW sets summary.",
      pinned: true,
      favorite: false,
      attachments: [
        { name: "lecture4.pdf", uri: "content://docs/lecture4.pdf", mimeType: "application/pdf", sizeBytes: 102400 },
      ],
      clientUpdatedAtMillis: Date.now() - 5000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    noteRef.update({
      title: "LL(1) Parsing Updated",
      favorite: true,
      pinned: false,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    noteRef.update({ pinned: "yes", updatedAt: new Date(Date.now() - 100) })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertSucceeds(noteRef.delete());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("reminders").doc("r1").set({
      uid: ALICE_UID,
      title: "Submit project proposal",
      remindAtText: "Friday 5:00 PM",
      done: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("goals").doc("g1").set({
      uid: ALICE_UID,
      title: "Solve 5 DP problems",
      completedSteps: 2,
      targetSteps: 5,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Assignments CRUD & validation
  const assignRef = aliceDb.collection("users").doc(ALICE_UID).collection("assignments").doc("a1");
  const futureDeadline = Date.now() + 86400000;
  await assertSucceeds(
    assignRef.set({
      uid: ALICE_UID,
      title: "Graph Traversal Report",
      description: "Compare BFS and Dijkstra performance on sparse graphs.",
      courseName: "CS-301",
      deadlineMillis: futureDeadline,
      priority: "HIGH",
      status: "PENDING",
      attachments: [
        { name: "rubric.pdf", uri: "https://example.com/rubric.pdf", mimeType: "application/pdf" },
      ],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    assignRef.update({ status: "COMPLETED", priority: "MEDIUM", updatedAt: new Date(Date.now() - 200) })
  );
  await assertFails(
    assignRef.update({ priority: "URGENT", updatedAt: new Date(Date.now() - 100) })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("assignments").get());
  await assertSucceeds(assignRef.delete());

  // Quizzes CRUD & validation
  const quizRef = aliceDb.collection("users").doc(ALICE_UID).collection("quizzes").doc("q1");
  await assertSucceeds(
    quizRef.set({
      uid: ALICE_UID,
      title: "Quiz 2: Normalization",
      courseName: "CS-310",
      scheduledAtMillis: futureDeadline,
      totalMarks: 20.0,
      obtainedMarks: 0.0,
      scoreRecorded: false,
      preparationStatus: "IN_PROGRESS",
      notes: "Focus on BCNF and 3NF decomposition.",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    quizRef.update({
      scoreRecorded: true,
      obtainedMarks: 18.5,
      preparationStatus: "COMPLETED",
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    quizRef.update({ obtainedMarks: 25.0, updatedAt: new Date(Date.now() - 100) })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("quizzes").get());
  await assertSucceeds(quizRef.delete());

  // Exams CRUD & validation
  const examRef = aliceDb.collection("users").doc(ALICE_UID).collection("exams").doc("e1");
  await assertSucceeds(
    examRef.set({
      uid: ALICE_UID,
      title: "Operating Systems Midterm",
      examType: "MIDTERM",
      courseName: "CS-320",
      examAtMillis: futureDeadline + 86400000,
      room: "Auditorium B",
      preparationStatus: "REVISING",
      revisionChecklist: [
        { id: "t1", title: "CPU Scheduling", done: true },
        { id: "t2", title: "Deadlocks & Banker Algorithm", done: false },
      ],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    examRef.update({
      preparationStatus: "READY",
      revisionChecklist: [
        { id: "t1", title: "CPU Scheduling", done: true },
        { id: "t2", title: "Deadlocks & Banker Algorithm", done: true },
      ],
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    examRef.update({ examType: "POP_QUIZ", updatedAt: new Date(Date.now() - 100) })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("exams").get());
  await assertSucceeds(examRef.delete());

  // Calendar events CRUD & validation
  const eventRef = aliceDb.collection("users").doc(ALICE_UID).collection("calendarEvents").doc("ev1");
  await assertSucceeds(
    eventRef.set({
      uid: ALICE_UID,
      title: "Annual Convocation Seminar",
      description: "Main Auditorium keynote",
      category: "UNIVERSITY_EVENTS",
      locationOrCourse: "Main Auditorium",
      startAtMillis: futureDeadline,
      endAtMillis: futureDeadline + 3600000,
      allDay: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    eventRef.update({
      category: "HOLIDAYS",
      allDay: true,
      updatedAt: new Date(Date.now() - 150),
    })
  );
  await assertFails(
    eventRef.update({ category: "UNKNOWN_CATEGORY", updatedAt: new Date(Date.now() - 100) })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("calendarEvents").get());
  await assertSucceeds(eventRef.delete());

  // Scheduled reminders CRUD & validation
  const schedRemRef = aliceDb.collection("users").doc(ALICE_UID).collection("scheduledReminders").doc("sr1");
  await assertSucceeds(
    schedRemRef.set({
      uid: ALICE_UID,
      title: "Study Operating Systems Deadlocks",
      notes: "Revise chapters 7-8",
      category: "STUDY",
      triggerAtMillis: futureDeadline,
      leadMinutes: 15,
      enabled: true,
      completed: false,
      workRequestId: "work-uuid-123",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    schedRemRef.update({
      enabled: false,
      completed: true,
      workRequestId: "",
      updatedAt: new Date(Date.now() - 150),
    })
  );
  await assertFails(
    schedRemRef.update({ category: "INVALID_REM", updatedAt: new Date(Date.now() - 100) })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("scheduledReminders").get());
  await assertSucceeds(schedRemRef.delete());

  // App notifications CRUD & validation
  const notifRef = aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("n1");
  await assertSucceeds(
    notifRef.set({
      uid: ALICE_UID,
      title: "Class Reminder: CS-301",
      body: "Starts in 15 minutes at AB-2 Room 204",
      category: "CLASS",
      sourceType: "REMINDER",
      sourceId: "sr1",
      deliveredAtMillis: Date.now() - 1000,
      read: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    notifRef.update({
      read: true,
      updatedAt: new Date(Date.now() - 120),
    })
  );
  await assertFails(
    notifRef.update({ title: "Tampered", updatedAt: new Date(Date.now() - 100) })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notifications").get());
  await assertSucceeds(notifRef.delete());

  // Attendance logs CRUD & validation
  const attRef = aliceDb.collection("users").doc(ALICE_UID).collection("attendanceLogs").doc("att1");
  await assertSucceeds(
    attRef.set({
      uid: ALICE_UID,
      courseId: "c1",
      courseCode: "CS-312",
      courseName: "Compiler Construction",
      status: "PRESENT",
      sessionDateMillis: Date.now() - 2000,
      monthKey: "2026-11",
      notes: "Lecture 12",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(attRef.update({ status: "ABSENT", notes: "Medical leave", updatedAt: new Date(Date.now() - 100) }));
  await assertFails(attRef.update({ status: "LATE", updatedAt: new Date(Date.now() - 50) }));
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("attendanceLogs").doc("attBad").set({
      uid: ALICE_UID,
      courseId: "c1",
      courseCode: "CS-312",
      courseName: "Compiler Construction",
      status: "PRESENT",
      sessionDateMillis: Date.now() - 2000,
      monthKey: "Nov-2026",
      notes: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("attendanceLogs").get());
  await assertSucceeds(attRef.delete());

  // Academic settings CRUD & bounds validation
  const settingsRef = aliceDb.collection("users").doc(ALICE_UID).collection("academicSettings").doc("preferences");
  await assertSucceeds(
    settingsRef.set({
      uid: ALICE_UID,
      attendanceThresholdPercent: 75,
      scaleName: "Standard 4.0 Template (Customizable)",
      customScale: false,
      gradingScale: [
        { letter: "A", points: 4.0 },
        { letter: "B+", points: 3.3 },
        { letter: "F", points: 0.0 },
      ],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    settingsRef.update({
      attendanceThresholdPercent: 80,
      customScale: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    settingsRef.update({ attendanceThresholdPercent: 120, updatedAt: new Date(Date.now() - 50) })
  );

  // Semester GPA records CRUD & validation
  const gpaRef = aliceDb.collection("users").doc(ALICE_UID).collection("semesterGpaRecords").doc("sem1");
  await assertSucceeds(
    gpaRef.set({
      uid: ALICE_UID,
      semesterName: "Fall 2025 (Sem 4)",
      orderIndex: 1,
      activeSemester: false,
      entryMode: "SUMMARY",
      summaryCredits: 16.0,
      summaryGpa: 3.62,
      courses: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    gpaRef.update({
      entryMode: "COURSES",
      courses: [
        {
          id: "gc1",
          courseId: "c1",
          courseCode: "CS-312",
          courseName: "Compiler Construction",
          creditHours: 3.0,
          letterGrade: "A",
          gradePoints: 4.0,
        },
      ],
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    gpaRef.update({ entryMode: "INVALID_MODE", updatedAt: new Date(Date.now() - 50) })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("semesterGpaRecords").get());
  await assertSucceeds(gpaRef.delete());

  // Study sessions CRUD & bounds validation
  const sessionRef = aliceDb.collection("users").doc(ALICE_UID).collection("studySessions").doc("ss1");
  await assertSucceeds(
    sessionRef.set({
      uid: ALICE_UID,
      title: "Pomodoro Focus: Graph Algorithms",
      subject: "CS-301",
      durationMinutes: 25,
      sessionMode: "POMODORO",
      completed: true,
      scheduledAtMillis: Date.now() - 2000,
      completedAtMillis: Date.now() - 1000,
      studyDateKey: "2026-11-24",
      notes: "Completed Dijkstra and Bellman-Ford proofs.",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    sessionRef.update({
      durationMinutes: 45,
      completed: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    sessionRef.update({ sessionMode: "INVALID_MODE", updatedAt: new Date(Date.now() - 50) })
  );
  await assertFails(
    sessionRef.update({ studyDateKey: "24-11-2026", updatedAt: new Date(Date.now() - 50) })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("studySessions").get());
  await assertSucceeds(sessionRef.delete());

  // Study planner goal & Pomodoro settings CRUD & bounds validation
  const plannerSetRef = aliceDb.collection("users").doc(ALICE_UID).collection("studyPlannerSettings").doc("goals");
  await assertSucceeds(
    plannerSetRef.set({
      uid: ALICE_UID,
      dailyGoalMinutes: 120,
      weeklyGoalMinutes: 840,
      pomodoroFocusMinutes: 25,
      pomodoroBreakMinutes: 5,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    plannerSetRef.update({
      dailyGoalMinutes: 150,
      pomodoroFocusMinutes: 50,
      pomodoroBreakMinutes: 10,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    plannerSetRef.update({ dailyGoalMinutes: 5, updatedAt: new Date(Date.now() - 50) })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("studyPlannerSettings").get());
});

test("Security bounds: reject password field, ghost fields, oversized phoneNumber, future timestamps, and immutable mutations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfilePayload(ALICE_UID, "alice@iba-suk.edu.pk", created),
      password: "secret123",
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfilePayload(ALICE_UID, "alice@iba-suk.edu.pk", created),
      isAdmin: true,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(
      validProfilePayload(ALICE_UID, "alice@iba-suk.edu.pk", new Date(Date.now() + 60000))
    )
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, "alice@iba-suk.edu.pk", created))
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Multi-user isolation: cannot list users or access another user's profile or workspace subcollections", async () => {
  const ts = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).set(
      validProfilePayload(BOB_UID, "bob@iba-suk.edu.pk", ts)
    );
    await context.firestore().collection("users").doc(BOB_UID).collection("tasks").doc("t1").set({
      uid: BOB_UID,
      title: "Bob Private Task",
      courseName: "MATH-101",
      category: "ASSIGNMENT",
      dueDateText: "Today",
      completed: false,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("t1").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("timetable").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("assignments").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("quizzes").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("exams").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("calendarEvents").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("scheduledReminders").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("notifications").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("attendanceLogs").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("academicSettings").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("semesterGpaRecords").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("notes").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("studySessions").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("studyPlannerSettings").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).delete());
});
