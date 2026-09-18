const params = new URLSearchParams(window.location.search);
const withId = params.get("with");

let me = null;
let lastTimestamp = 0;
let pollTimer = null;

const messagesEl = document.getElementById("messages");
const sendForm = document.getElementById("sendForm");
const messageInput = document.getElementById("messageInput");
const deniedNotice = document.getElementById("deniedNotice");

document.getElementById("logoutLink") && document.getElementById("logoutLink").addEventListener("click", async (e) => {
  e.preventDefault();
  await Api.post("/api/logout");
  window.location.href = "/index.html";
});

document.getElementById("backBtn").addEventListener("click", () => {
  window.location.href = "/messages.html";
});

function scrollToBottom() {
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

function formatTime(ts) {
  const d = new Date(ts);
  return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function appendMessages(msgs) {
  for (const m of msgs) {
    const mine = m.from === me.id;
    const row = document.createElement("div");
    row.className = "bubble-row " + (mine ? "mine" : "theirs");
    row.innerHTML = `
      <div>
        <div class="bubble">${escapeHtml(m.text)}</div>
        <div class="bubble-time">${formatTime(m.createdAt)}</div>
      </div>`;
    messagesEl.appendChild(row);
    if (m.createdAt > lastTimestamp) lastTimestamp = m.createdAt;
  }
  if (msgs.length > 0) scrollToBottom();
}

async function loadInitialHistory() {
  try {
    const msgs = await Api.get(`/api/chat/messages?with=${encodeURIComponent(withId)}`);
    appendMessages(msgs);
  } catch (err) {
    deniedNotice.classList.remove("hidden");
    sendForm.querySelector("button").disabled = true;
    messageInput.disabled = true;
  }
}

async function pollForNew() {
  try {
    const msgs = await Api.get(`/api/chat/messages?with=${encodeURIComponent(withId)}&since=${lastTimestamp}`);
    appendMessages(msgs);
  } catch (err) {
    // Permission likely already denied on initial load; stop polling silently.
  }
}

async function loadPartnerInfo() {
  const conversations = await Api.get("/api/chat/conversations");
  const partner = conversations.find((u) => u.id === withId);
  if (!partner) {
    document.getElementById("whoName").textContent = "Unavailable";
    document.getElementById("whoMeta").textContent = "You can no longer message this person.";
    return;
  }
  document.getElementById("whoName").textContent = partner.fullName;
  document.getElementById("whoMeta").textContent = `${partner.course || ""} · ${partner.yearLevel || ""}`;
  document.getElementById("whoAvatar").innerHTML = partner.photoPath
    ? `<img src="${escapeHtml(partner.photoPath)}" alt="${escapeHtml(partner.fullName)}">`
    : escapeHtml(initial(partner.fullName));
}

sendForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const text = messageInput.value.trim();
  if (!text) return;
  messageInput.value = "";
  try {
    const msg = await Api.post("/api/chat/send", { toUserId: withId, text });
    appendMessages([msg]);
  } catch (err) {
    alert(err.message);
  }
});

async function init() {
  me = await requireAuth();
  if (!me) return;
  if (!withId) {
    window.location.href = "/messages.html";
    return;
  }
  await loadPartnerInfo();
  await loadInitialHistory();
  pollTimer = setInterval(pollForNew, 3000);
}

window.addEventListener("beforeunload", () => {
  if (pollTimer) clearInterval(pollTimer);
});

init();
