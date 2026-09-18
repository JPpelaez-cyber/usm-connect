# USM Connect

A matching/dating web app for students of the **University of Southern
Mindanao (USM)** — sign up, build a profile (course, year level, hobbies,
games, favorite Filipino foods), discover other students, like/pass, get
matched when the like is mutual, add platonic friends separately from
matching, and chat with your matches and friends.

```
usm-connect/
├── backend/                 Java server (no external libraries required)
│   ├── src/com/usmconnect/
│   │   ├── Main.java             entry point / HTTP routing
│   │   ├── config/OptionsConfig  editable courses/hobbies/games/foods
│   │   ├── db/JsonStore          file-based "database"
│   │   ├── service/               AuthService, MatchService, FriendService,
│   │   │                          ChatService (business logic)
│   │   ├── handler/               one HTTP handler per API area
│   │   └── util/                  JSON, passwords, sessions, multipart parsing
│   ├── data/                (created at runtime) users.json, likes.json, ...
│   └── uploads/              uploaded profile photos
└── frontend/                 Plain HTML/CSS/JS, no build step
    ├── index.html            login
    ├── register.html
    ├── setup.html            profile wizard (photo, course, hobbies, games, foods)
    ├── discover.html         swipe/like/pass feed (+ Add Friend)
    ├── matches.html          mutual romantic matches
    ├── friends.html          friend requests + friends list
    ├── messages.html         list of everyone you can chat with
    ├── chat.html             a single chat thread
    ├── profile.html          view/edit your own profile
    ├── admin.html            admin dashboard (key-protected)
    └── css/, js/
```

## Running it

Requires a JDK (21 is what this was built and tested against; anything 11+
should work). No Maven, no npm, no database server to install.

```bash
cd usm-connect/backend
mkdir -p out
javac -d out $(find src -name "*.java")
java -cp out com.usmconnect.Main
```

Then open **http://localhost:8080** in your browser.

To use a different port: `PORT=3000 java -cp out com.usmconnect.Main`.

The server serves both the API (`/api/...`) and the frontend (everything
else) from the same process, so there's nothing separate to start or CORS to
configure.

## Trying it out

1. Open `http://localhost:8080`, click **Create an account**.
2. Fill in name / username / email / password.
3. Complete the profile wizard: photo, college + course + year level,
   hobbies, games, and favorite Filipino dishes.
4. You'll land on **Discover** — but you need at least one other complete
   profile to see anyone. Open an incognito window (or a second browser) and
   register a second account the same way.
5. Like each other from both accounts → you'll get the match celebration
   and both accounts will see each other on the **Matches** page.

### Admin dashboard

Go to `http://localhost:8080/admin.html`. Default key is:

```
usm-admin-2024
```

Change it by setting the `ADMIN_KEY` environment variable before starting
the server, e.g. `ADMIN_KEY=something-long-and-random java -cp out com.usmconnect.Main`.

From the admin dashboard you can:
- See every registered user and block/unblock them
- See filed reports
- Edit the courses (grouped by USM college), year levels, hobbies, games,
  and Filipino foods as JSON — no code changes or restart needed

## How matching works

`MatchService.computeCompatibility` scores two profiles:

| Signal                    | Points |
|----------------------------|--------|
| Each shared hobby           | 6      |
| Each shared game             | 6      |
| Each shared favorite food    | 8      |
| Same course                 | 15     |
| Same college (if not same course) | 8 |
| Same year level             | 7      |

The score is capped at 99% and floored at 5%, and the discover feed is
sorted highest-compatibility first. The specific shared items are returned
alongside the score so the UI can show *why* two people matched, not just
the number.

A **Like** is recorded immediately; when the other person likes back, a
**Match** record is created and both users see each other on the Matches
page. **Pass** and **Block** both remove a profile from your future discover
feed — Block does it permanently and mutually (neither of you will see the
other again) and is logged separately from a plain pass.

## Friends (separate from matching)

Friendship is deliberately independent of the romantic Like/Match system —
two students can be friends without ever liking each other romantically,
and vice versa. From any card on **Discover** you can hit **🤝 Add Friend**
in addition to Like/Pass; this doesn't affect your discover feed at all.

- A friend request starts as `pending`.
- The recipient sees it on **Friends** and can **Accept** or **Decline**.
- If the recipient had *already* sent you a pending request first, your
  request instead auto-accepts theirs immediately — the same "mutual intent
  = instant connection" idea as a romantic Match.
- Accepted friends appear on the **Friends** page with a **Remove** option
  (which just marks the friendship as ended — no data is deleted) and a
  **Chat** button.

This all lives in `service/FriendService.java` and is exposed through
`handler/FriendHandler.java` at `/api/friends`, `/api/friends/requests`,
`/api/friends/request`, `/api/friends/respond`, and `/api/friends/remove`.

## Chat

Two users can message each other **only if they are a Match or Friends** —
this is enforced server-side on every message send and every message fetch,
not just hidden in the UI. Messages are stored per conversation (a stable id
derived from the two participant ids, independent of who messages first) in
`backend/data/messages.json`.

- **Messages** (`/messages.html`) lists everyone you can currently chat
  with — the union of your matches and your friends.
- **Chat** (`/chat.html?with=<userId>`) shows the thread and lets you send
  new messages. The page polls the server every 3 seconds for new messages
  (`GET /api/chat/messages?with=<id>&since=<timestamp>`) rather than using
  WebSockets, since the built-in `com.sun.net.httpserver` used for the
  backend doesn't support WebSockets — polling was the simplest option that
  keeps the "no external libraries" constraint intact.
- If the permission check fails (e.g. you un-friend someone, or a report
  leads to a block), the chat page shows a clear "you can no longer message
  this person" notice instead of silently failing.

This all lives in `service/ChatService.java` and `handler/ChatHandler.java`
at `/api/chat/conversations`, `/api/chat/messages`, and `/api/chat/send`.

## Data storage

There's no external database server. `JsonStore` persists each collection
(`users`, `likes`, `passes`, `blocks`, `matches`, `reports`, `friend_requests`,
`messages`) as its own JSON file under `backend/data/`, guarded by a
read-write lock so concurrent requests can't corrupt a file. This was a
deliberate simplification for this environment (no network access to pull in
a JDBC driver or a real database engine) — everything else in the app
(handlers, services) only talks to `JsonStore`'s methods, so swapping in real
SQLite/Postgres/MySQL later means rewriting that one class, not the rest of
the app.

Data survives a server restart (I tested this directly — killed the process
mid-session and relaunched it; all accounts, profiles, and uploaded photos
were still there). Login **sessions** are kept in memory only, so after a
restart everyone needs to log back in — their data is untouched.

## Security notes / what's simplified here

This is meant to be a genuinely working prototype, not a hardened production
system. Things worth knowing before deploying it anywhere real:

- **Password hashing** uses salted SHA-256 with 120,000 rounds rather than
  bcrypt/argon2, because this sandbox has no network access to Maven Central
  to pull in a bcrypt library. `util/PasswordUtil.java` is a clean drop-in
  point to swap in a real bcrypt implementation later.
- **Sessions** are opaque random tokens in an in-memory map with no
  expiry — fine for a demo, but you'd want expiry + rotation for production.
- **Admin auth** is a single shared key, not per-admin accounts — enough to
  demonstrate the moderation workflow, not enough for a real multi-admin team.
- Emails are never returned for any user other than yourself
  (`AuthService.sanitize` strips them); passwords/hashes are never returned
  to any client, ever.
- Photo uploads are capped at 5MB and restricted to
  JPEG/PNG/WEBP/GIF by their actual declared content type.
- Favorite-food cards use emoji instead of hotlinked photos. That was a
  deliberate choice to avoid shipping copyrighted photos or dead image links
  — swap in your own licensed images by editing the `foods` list returned
  from `/api/options` (via the admin panel or `OptionsConfig.java`) to add
  an `"imageUrl"` field, and updating the `.food-card` rendering in
  `js/setup.js` to use `<img>` instead of the emoji `<span>`.

## Sample account

There's no hard-coded account baked into the app — everyone registers
themselves. If you want to recreate the sample profile from the spec by
hand: name **Jhon Paul Pelaez**, course **BS Computer Science**, college
**College of Information and Computing Sciences**.
