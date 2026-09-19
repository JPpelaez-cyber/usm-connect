// Tiny API client shared by every page. Uses same-origin cookies for auth,
// so no token handling is needed on the client.

const Api = {
  async request(method, path, body) {
    const opts = { method, headers: {}, credentials: "include" };
    if (body !== undefined) {
      opts.headers["Content-Type"] = "application/json";
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(path, opts);
    let data = null;
    try { data = await res.json(); } catch (e) { data = null; }
    if (!res.ok) {
      const message = (data && data.error) ? data.error : `Request failed (${res.status})`;
      throw new Error(message);
    }
    return data;
  },

  get(path) { return this.request("GET", path); },
  post(path, body) { return this.request("POST", path, body === undefined ? {} : body); },
  put(path, body) { return this.request("PUT", path, body === undefined ? {} : body); },

  async uploadPhoto(file) {
    const form = new FormData();
    form.append("photo", file);
    const res = await fetch("/api/profile/photo", {
      method: "POST",
      body: form,
      credentials: "include",
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Upload failed");
    return data;
  },

  async me() {
    try {
      return await this.get("/api/me");
    } catch (e) {
      return null;
    }
  },
};

// Redirects to login if the user isn't authenticated. Returns the user object.
async function requireAuth() {
  const user = await Api.me();
  if (!user) {
    window.location.href = "/index.html";
    return null;
  }
  return user;
}

function initial(name) {
  if (!name) return "?";
  return name.trim().charAt(0).toUpperCase();
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str == null ? "" : String(str);
  return div.innerHTML;
}
