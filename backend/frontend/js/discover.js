let me = null;
let candidates = [];
let index = 0;
const stage = document.getElementById("stage");

document.getElementById("logoutLink").addEventListener("click", async (e) => {
  e.preventDefault();
  await Api.post("/api/logout");
  window.location.href = "/index.html";
});

async function init() {
  me = await requireAuth();
  if (!me) return;
  if (!me.profileComplete) {
    window.location.href = "/setup.html";
    return;
  }
  await loadCandidates();
  render();
}

async function loadCandidates() {
  candidates = await Api.get("/api/discover");
  index = 0;
}

function avatarMarkup(user, sizeClass) {
  if (user.photoPath) {
    return `<img src="${escapeHtml(user.photoPath)}" alt="${escapeHtml(user.fullName)}">`;
  }
  return `<div class="${sizeClass}">${escapeHtml(initial(user.fullName))}</div>`;
}

function render() {
  if (index >= candidates.length) {
    stage.innerHTML = `
      <div class="empty-state">
        <span class="big-emoji">🔎</span>
        <h2>No more profiles right now</h2>
        <p>Check back later as more Kutabatanons complete their profiles!</p>
        <button class="btn btn-primary" id="refreshBtn" style="margin-top:14px;">Refresh</button>
      </div>`;
    document.getElementById("refreshBtn").addEventListener("click", async () => {
      stage.innerHTML = `<div class="loader"></div>`;
      await loadCandidates();
      render();
    });
    return;
  }

  const user = candidates[index];
  const tagList = (user.commonInterests || []).slice(0, 6)
    .map((t) => `<span class="tag">${escapeHtml(t)}</span>`).join("");

  stage.innerHTML = `
    <div class="match-card" id="card">
      <div class="match-photo">
        ${user.photoPath
          ? `<img src="${escapeHtml(user.photoPath)}" alt="${escapeHtml(user.fullName)}">`
          : `<span class="avatar-fallback">🎓</span>`}
        <div class="compat-badge">${user.compatibility}% Match</div>
      </div>
      <div class="match-body">
        <div class="flex-between">
          <h2>${escapeHtml(user.fullName)}</h2>
          ${friendButtonHtml(user)}
        </div>
        <div class="meta">${escapeHtml(user.course || "")} · ${escapeHtml(user.yearLevel || "")}</div>
        ${user.bio ? `<div class="bio">${escapeHtml(user.bio)}</div>` : ""}
        ${tagList ? `<div class="tag-row">${tagList}</div>` : `<p class="helper-text">No shared interests yet — could be a fun first conversation!</p>`}
      </div>
      <div class="action-row">
        <button class="action-btn action-pass" id="passBtn" title="Pass">✕</button>
        <button class="action-btn action-more" id="reportBtn" title="Report / Block">⚑</button>
        <button class="action-btn action-like" id="likeBtn" title="Like">♥</button>
      </div>
    </div>
  `;

  document.getElementById("passBtn").addEventListener("click", () => act("pass"));
  document.getElementById("likeBtn").addEventListener("click", () => act("like"));
  document.getElementById("reportBtn").addEventListener("click", () => openReportMenu(user));

  const friendBtn = document.getElementById("friendBtn");
  if (friendBtn) friendBtn.addEventListener("click", () => sendFriendRequest(user));
}

function friendButtonHtml(user) {
  if (user.friendStatus === "friends") {
    return `<span class="friend-btn friends">✓ Friends</span>`;
  }
  if (user.friendStatus === "pending_outgoing") {
    return `<span class="friend-btn pending">Request Sent</span>`;
  }
  if (user.friendStatus === "pending_incoming") {
    return `<a href="/friends.html" class="friend-btn">Respond to Request</a>`;
  }
  return `<button class="friend-btn" id="friendBtn" title="Add as friend">🤝 Add Friend</button>`;
}

async function sendFriendRequest(user) {
  try {
    const result = await Api.post("/api/friends/request", { targetUserId: user.id });
    user.friendStatus = result.status === "accepted" ? "friends" : "pending_outgoing";
    render();
  } catch (err) {
    alert(err.message);
  }
}

async function act(kind) {
  const user = candidates[index];
  const card = document.getElementById("card");
  card.classList.add(kind === "like" ? "card-out-right" : "card-out-left");

  try {
    if (kind === "like") {
      const result = await Api.post("/api/like", { targetUserId: user.id });
      if (result.matched) {
        setTimeout(() => showMatchModal(user), 300);
      }
    } else {
      await Api.post("/api/pass", { targetUserId: user.id });
    }
  } catch (err) {
    console.error(err);
  }

  setTimeout(() => {
    index++;
    render();
  }, 320);
}

function openReportMenu(user) {
  const choice = window.prompt(
    `Report or block ${user.fullName}?\nType "report" to report, "block" to block, or Cancel to go back.`
  );
  if (!choice) return;
  const normalized = choice.trim().toLowerCase();
  if (normalized === "block") {
    Api.post("/api/block", { targetUserId: user.id }).then(() => act("pass"));
  } else if (normalized === "report") {
    const reason = window.prompt("Briefly, what's the issue?") || "Not specified";
    Api.post("/api/report", { targetUserId: user.id, reason }).then(() => act("pass"));
  }
}

function showMatchModal(other) {
  document.getElementById("avSelf").innerHTML = avatarMarkup(me, "");
  document.getElementById("avOther").innerHTML = avatarMarkup(other, "");
  document.getElementById("matchText").textContent =
    `You and ${other.fullName} liked each other. Say hi at your next USM event!`;
  document.getElementById("matchModal").classList.remove("hidden");
}

document.getElementById("keepSwiping").addEventListener("click", () => {
  document.getElementById("matchModal").classList.add("hidden");
});
document.getElementById("goToMatches").addEventListener("click", () => {
  window.location.href = "/matches.html";
});

init();
