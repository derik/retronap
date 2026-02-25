# RetroNap Protocol Conformance Spec

## Goal
Define protocol behavior as a testable contract so the suite can grow beyond currently covered user journeys.

This document is implementation-driven and complements `docs/protocol-spec.md`.

## Scope Markers
Use these status tags when planning tests:
- `Implemented`: behavior is explicit in handlers and should be stable.
- `Partial`: behavior exists but has caveats, TODOs, or low confidence.
- `Stub`: message type is routed but intentionally no-op.
- `Unrouted`: message type constant exists but no active handler.

## Session State Model

### States
1. `ConnectedUnauthenticated`
2. `Authenticated`
3. `Terminated`

### Transitions
1. `ConnectedUnauthenticated -> Authenticated`
- Trigger: successful login (`2`) or alt login (`6`) or check-pass flow (`11`)
- Observable: login ack (`3`) or pass-ok (`12`), MOTD (`621`), periodic stats (`214`)

2. `ConnectedUnauthenticated -> Terminated`
- Trigger: failed login (`2`) in normal mode
- Observable: `0` error then socket close

3. `Authenticated -> Terminated`
- Trigger: disconnect, kill, admin disconnect/nuke, socket close
- Side effects:
  - Session removed from manager
  - User removed from channels
  - Shared files deleted by user id
  - Hotlist signoff notifications (`210`) emitted

## Global Validation and Error Semantics

### Framing and token checks
- Framing: `<len:2 little-endian><type:2 little-endian><payload bytes>`
- Token validation: `ValidMessageChecker` uses `MessageTypes` token map and quote-aware parser.

### Error message conventions
1. Not logged in for privileged command
- Response: `0` with text `User tried sending privileged message while not logged in`

2. Invalid payload shape
- Response while unauthenticated: `0` with `The message format is not valid`
- Response while authenticated: `404` with `The message format is not valid`

3. Unknown/unrouted message type
- Response: `404` with `Don't know how to handle that message type <N>`

4. Unhandled exception in handler
- Response: `0` with `Server Error`

## Behavioral Contracts by Domain

## Authentication and Registration

1. `2 CLIENT_LOGIN_REQUEST` (`Implemented`)
- Preconditions: valid message format
- Success:
  - sets authenticated user state
  - replies `3 <email>`
  - sends MOTD (`621` one or more)
  - starts periodic stats thread (`214`)
  - emits session sign-on event
- Failure:
  - replies `0 Login error.(no account or bad password)`
  - session terminated
- Ban failure:
  - replies `0 You have been banned from using this server.`
  - session terminated

2. `6 CLIENT_ALT_LOGIN_REQUEST` (`Implemented`)
- Creates/saves user using payload fields and encoded password
- Replies `3 <email>`
- Sends MOTD and starts status updates

3. `7 CLIENT_REGISTRATION` (`Implemented`)
- Nick check behavior
- Replies `8` if nickname available, else `9`

4. `11 CLIENT_CHECK_PASS` (`Partial`)
- On valid credentials: sets session logged-in and replies `12`
- On invalid credentials: replies `0 Incorrect Password.`
- Notes: TODO in code indicates legacy semantics uncertainty.

5. `14 CLIENT_REGISTRATION_INFO` (`Partial`)
- Parses colon/tab-delimited profile payload and stores `UserInfo`
- No explicit response message
- Fragile parsing assumptions should be covered by negative tests

## MOTD and Stats

1. `621 MESSAGE_OF_THE_DAY` (`Implemented`)
- Emitted by server after successful login
- Source: configured MOTD file, with fallback default line

2. `214 SERVER_STATS` (`Implemented`)
- Sent periodically by status thread after login
- Payload: `<users> <files> <sizeGb>`

## Sharing and Search

1. `100 CLIENT_SHARE_NOTIFICATION` (`Implemented`)
- Adds in-session share and persists with user ownership
- No explicit ack

2. `102 CLIENT_SHARE_REMOVAL` (`Partial`)
- Removes from in-memory session share map
- No persistence delete in handler

3. `870 CLIENT_DIR_SHARE_NOTIFICATION` (`Implemented`)
- Custom validation: `(tokens - 1) % 6 == 0`
- First token is directory, then repeated file tuples
- Persists all shares and adds to in-session map
- On bad tuple count: `404 invalid number of tokens in message 870` (if logged in)

4. `200 CLIENT_SEARCH_REQUEST` (`Implemented` via new handler)
- Parses `FILENAME ...`, `MAX_RESULTS`, optional `LINESPEED`, `BITRATE`
- Emits zero or more `201` results then always `202`
- `MAX_RESULTS` is capped to 100

5. `215 CLIENT_RESUME_REQUEST` (`Implemented`)
- Emits zero or more `216` then `217`

## Browse and Hotlist

1. `211 CLIENT_BROWSE_USER_FILES` (`Implemented`)
- If target session online: emits `212` per share
- Always emits `213 <nick>` end marker
- Offline target currently yields end marker only

2. `207/208/303 HOTLIST` (`Implemented`)
- `208` accepts variable tokens and bulk-adds entries
- Successful add emits `301 <nick>`
- Unknown nick emits `302 <nick>`
- If target already online, immediate sign-on notice `209 <nick> <speed>`
- On session signon/signoff events, stalkers receive `209` / `210`

## Channels and Messaging

1. `400 CLIENT_CHANNEL_JOIN` (`Implemented`)
- Errors:
  - capacity -> `404 Channel is at capacity, you cannot join at this time.`
  - invalid name (contains spaces) -> `404 Invalid channel name.`
  - level not allowed -> `404 You do not meet permission requirements necessary to join that channel.`
- Success:
  - joins/creates channel
  - broadcasts `406` join notify to channel members
  - sends `405 <channel>` to requester
  - sends `410 <channel> <topic>`
  - sends user list entries (`408` or `825` path) and end marker (`409` or `830` path)

2. `401 CLIENT_CHANNEL_LEAVE` (`Implemented`)
- Removes membership and broadcasts `407`

3. `402 CLIENT_CHANNEL_PUBLIC_MESSAGE` (`Implemented`)
- Broadcasts `403 <channel> <sender> <text>`

4. `824 CHANNEL_EMOTE` (`Implemented`)
- Broadcasts emote packet to channel

5. `410 CHANNEL_TOPIC` (`Implemented`)
- Broadcasts updated topic payload

6. `617 LIST_CHANNELS` and `827 FULL_CHANNEL_LIST` (`Implemented`)
- `617` -> repeated `618`, then `617` end marker
- `827` -> repeated `828`, then `827` end marker

7. `205 PRIVATE_MESSAGE` (`Implemented`)
- Online target: forwards `205 <sender> <text>`
- Offline target: `404 User <nick> not currently online`

## Transfer Coordination

1. `203 CLIENT_DOWNLOAD_REQUEST` (`Implemented`)
- If target user+file exists: sends `607` upload request to uploader
- Else: sends `206 <nick> "<filename>"` to requester

2. `608 CLIENT_UPLOAD_ACCEPT` (`Implemented`)
- Sends `204` to downloader with uploader address, port, file, md5, speed

3. `500 CLIENT_ALT_DOWNLOAD_REQUEST` (`Implemented`)
- Sends `501` to requester when target+file found

4. `619 CLIENT_QUEUE_LIMIT` (`Implemented`)
- Sends `620` to downloader with uploader nick, file, filesize, limit

5. `626 CLIENT_DATA_PORT_ERROR` (`Implemented`)
- Relays dataport error to uploader

6. `218/219/220/221` transfer counters (`Implemented`)
- Increment/decrement persistent download/upload counts

## Admin and Moderation

1. Requires logged-in admin/elite for admin handlers (`Implemented`)
- Failing privilege check yields `404 User must be Admin level or above to execute that command!`

2. Admin operations (`Implemented`)
- `610` disconnect user session
- `611` nuke user (kill + delete user)
- `606` change user level
- `753` change user password
- `628` global message broadcast

3. Ban operations (`Implemented`)
- `612` add nick/IP ban
- `614` remove nick/IP ban
- `615` list bans -> `616` and `629` streams

## Known Gaps and Risk Areas
1. `102` share removal appears in-memory only in handler; verify persistence behavior expectations.
2. `300 CLIENT_CHECK_PORT` is routed to `IgnoreCommandHandler` and currently no-op (`Stub`).
3. Some token-map entries are missing for constants that exist (`Unrouted` or legacy).
4. `11` check-pass behavior has TODO about exact protocol semantics.
5. Search parameter operators are parsed but not all operators materially affect query execution.

## Conformance Test Expansion Matrix

Legend:
- `Now`: should be added immediately.
- `Next`: useful after core reliability suite.
- `Later`: lower priority or feature-complete confidence.

1. Authentication
- `Now`: login success emits `3` then `621+`
- `Now`: login failure emits `0` and closes
- `Now`: banned nick/ip rejection path
- `Next`: alt login (`6`) creates user and logs in
- `Next`: check-pass (`11`) success and failure

2. Share/Search
- `Now`: `100` share then `211` browse sees file
- `Now`: `870` quoted filenames with spaces
- `Now`: `870` invalid tuple count -> `404`
- `Now`: `200` search no hits -> only `202`
- `Next`: `MAX_RESULTS` cap behavior
- `Next`: malformed search request tokenization error

3. Hotlist
- `Now`: `208` multi-nick create -> two `301`
- `Now`: add unknown nick -> `302`
- `Next`: signon event -> watcher gets `209`
- `Next`: signoff event -> watcher gets `210`
- `Next`: `303` remove hotlist entry then verify no further notifications

4. Channels
- `Now`: `827` full list returns `828+` and terminating `827`
- `Now`: join/leave basic notifications (`405/406/407`)
- `Next`: join invalid name (space) -> `404`
- `Next`: join permission denied -> `404`
- `Next`: capacity denied -> `404`
- `Next`: `617` list channels contract
- `Next`: `824` emote broadcast
- `Next`: `410` topic update broadcast

5. Transfer
- `Now`: `203` existing file -> uploader gets `607`
- `Now`: `203` missing file -> requester gets `206`
- `Next`: `608` accept -> downloader gets `204`
- `Next`: `500` alt download -> `501`
- `Next`: `619` queue limit -> `620`
- `Later`: `626` dataport relay behavior

6. Messaging and Presence
- `Now`: private message success path (`205`)
- `Now`: private message offline target -> `404`
- `Next`: ping/pong relay success and offline failure (`751/752`)
- `Next`: periodic `214` cadence after login

7. Admin/Ban
- `Next`: privilege failure for non-admin on admin commands
- `Next`: `612/614/615` ban lifecycle
- `Later`: `610/611/606/753/628` admin operations with fixture users

8. Robustness
- `Now`: unknown message type -> `404` fallback
- `Now`: invalid token count unauthenticated -> `0`
- `Now`: invalid token count authenticated -> `404`
- `Later`: abrupt client disconnect cleanup (shares removed)

## Suggested Test Organization
1. `AuthFlowsIT`
2. `ShareSearchFlowsIT`
3. `HotlistFlowsIT`
4. `ChannelFlowsIT`
5. `TransferFlowsIT`
6. `AdminFlowsIT`
7. `ProtocolErrorHandlingIT`

Keep each test focused on one protocol contract and assert:
- exact response message sequence
- side effects in persistence/session state
- absence of unexpected `0`/`404` errors in happy paths

## Observed Flows (Log-Derived)

### OF-CHAN-001: List Channels, Full List, Then Join
Source: runtime logs provided on 2026-02-19 (user `user02`).

Preconditions:
- Session is authenticated (`USER=user02` in logs).
- Channel `General` exists.

Observed message sequence:
1. `C->S 617 ""` (`LIST_CHANNELS`)
2. `S->C 618 "General 0 Welcome to the General channel"` (channel entry)
3. `S->C 617 ""` (list terminator)
4. `C->S 827 ""` (`FULL_CHANNEL_LIST`)
5. `S->C 828 "General 0 0 1 0 \"Welcome to the General channel\""` (full channel entry)
6. `S->C 827 ""` (full list terminator)
7. `S->C 214 "2 5 0"` (periodic status update; asynchronous)
8. `C->S 617 ""` (list channels again)
9. `S->C 618 "General 0 Welcome to the General channel"`
10. `S->C 617 ""`
11. `C->S 400 "General"` (`CLIENT_CHANNEL_JOIN`)
12. `S->C 406 "General user02 2 4"` (join notify)
13. `S->C 405 "General"` (join ack)
14. `S->C 410 "General Welcome to the General channel"` (topic)
15. `S->C 408 "General user02 2 4"` (user list entry)
16. `S->C 409 "General"` (user list end)

Contract distilled from this flow:
- `617` request returns `618*` followed by terminating `617`.
- `827` request returns `828*` followed by terminating `827`.
- `214` may interleave at any time after login and must not invalidate flow assertions.
- Joining via `400` should produce:
  - channel notification `406`
  - join ack `405`
  - topic `410`
  - user list stream `408*` terminated by `409`
- For single-member channel state, one `408` entry is expected for joining user.

### Derived Tests from OF-CHAN-001
1. `channel_listChannels_then_endMarker`
- Send `617`.
- Assert one or more `618` before terminating `617`.

2. `channel_fullList_then_endMarker`
- Send `827`.
- Assert one or more `828` before terminating `827`.

3. `channel_join_existingChannel_happyPath`
- Send `400 General`.
- Assert `405`, `410`, `408+`, `409` for same channel.
- Allow `406` to appear before `405` (as seen in logs).

4. `channel_flow_allows_async_status_updates`
- During list/join flows, tolerate `214` messages without failing message-order assertions.
- Verify flow-specific terminators (`617`, `827`, `409`) are still received.

5. `channel_join_entry_fields_are_consistent`
- Validate `406` and `408` payload field shape:
  - `<channel> <nick> <sharedCount> <linkType>`
- Validate channel and nick match requested join context.

### OF-HOTLIST-001: Add Single Hotlist User While Target Is Online
Source: runtime logs provided on 2026-02-19 (watcher `user03`, target `user02`).

Preconditions:
- Watcher session authenticated (`USER=user03` in logs).
- Target user `user02` exists.
- Target user is currently online.

Observed message sequence:
1. `C->S 207 "user02"` (`CLIENT_HOTLIST_ADD`)
2. `S->C 301 "user02"` (`SERVER_HOTLIST_ACK`)
3. `S->C 209 "user02 4"` (`SERVER_HOTLIST_SIGNON`)

Contract distilled from this flow:
- A valid `207` add request yields `301 <nick>` acknowledgment.
- If the target nick is already online at add time, server immediately sends `209 <nick> <linkType>` after ack.
- `209` is a presence notification and may be emitted without waiting for a future login event when target is already connected.

### Derived Tests from OF-HOTLIST-001
1. `hotlist_add_onlineUser_ack_then_immediate_signon`
- Send `207 user02` from `user03` while `user02` is online.
- Assert `301 user02` then `209 user02 <linkType>`.

2. `hotlist_add_onlineUser_signon_payload_shape`
- Validate `209` payload shape is exactly:
  - `<nick> <linkType>`
- Validate nick equals requested user.

3. `hotlist_add_onlineUser_no_general_error`
- Assert no `0`/`404` is emitted during successful add path.

### OF-TRANSFER-001: Download Request Handshake and Transfer Notifications
Source: runtime logs provided on 2026-02-19 (downloader `user02`, uploader `user03`).

Preconditions:
- Both sessions authenticated.
- Uploader has shared file:
  - `C:\SHARED\Red Hot Chilli Peppers - Californication.mp3`
- Downloader requests that exact file path.

Observed multi-session sequence:
1. `user02 -> S 218 ""` (`CLIENT_DOWNLOAD_NOTIFICATION`)
2. `user02 -> S 203 "user03 \"C:\\SHARED\\Red Hot Chilli Peppers - Californication.mp3\""` (`CLIENT_DOWNLOAD_REQUEST`)
3. `S -> user03 607 "user02 \"C:\\SHARED\\Red Hot Chilli Peppers - Californication.mp3\""` (`SERVER_UPLOAD_REQUEST`)
4. `user03 -> S 600 "user02"` (`CLIENT_LINKSPEED_REQUEST`)
5. `S -> user03 601 "user02 4"` (`SERVER_LINKSPEED_RESPONSE`)
6. `user03 -> S 608 "user02 \"C:\\SHARED\\Red Hot Chilli Peppers - Californication.mp3\""` (`CLIENT_UPLOAD_ACCEPT`)
7. `S -> user02 204 "user03 779790528 6699 \"C:\\SHARED\\Red Hot Chilli Peppers - Californication.mp3\" 49a8df95b81a870aecae030ed1ddf232-7708144 6"` (`SERVER_DOWNLOAD_ACK`)
8. `user03 -> S 220 ""` (`CLIENT_UPLOAD_NOTIFICATION`)
9. `user03 -> S 221 ""` (`CLIENT_UPLOAD_COMPLETE_NOTIFICATION`)
10. `user02 -> S 870 "\"C:\\SHARED\\\" \"Red Hot Chilli Peppers - Californication.mp3\" 49a8df95b81a870aecae030ed1ddf232-7708144 7712492 192 44100 319"` (directory share update)
11. `user02 -> S 219 ""` (`CLIENT_DOWNLOAD_COMPLETE_NOTIFICATION`)

Contract distilled from this flow:
- Successful `203` causes `607` to be sent to uploader (not back to requester).
- `608` from uploader causes `204` to be sent to downloader, containing:
  - uploader nick
  - uploader IP as long integer
  - uploader data port
  - requested filename
  - md5 signature
  - uploader link speed
- Link speed query (`600`) may appear in handshake and should return `601 <nick> <speed>`.
- Transfer activity counters are client-driven notifications:
  - downloader: `218` start, `219` end
  - uploader: `220` start, `221` end
- A post-download `870` refresh from downloader is valid and can appear immediately after transfer.

### Derived Tests from OF-TRANSFER-001
1. `transfer_downloadRequest_routes_uploadRequest_to_uploader`
- Send `203` from downloader for existing uploader file.
- Assert uploader receives `607` with downloader nick + filename.

2. `transfer_uploadAccept_returns_downloadAck_to_downloader`
- After `607`, send `608` from uploader.
- Assert downloader receives `204` with correct field shape and values.

3. `transfer_linkSpeed_request_during_handshake`
- Send `600 <peerNick>`.
- Assert `601 <peerNick> <linkType>` response.

4. `transfer_counters_start_and_complete_notifications`
- Send `218/219/220/221` from appropriate peers.
- Assert no protocol errors and verify counters via whois or persistence checks.

5. `transfer_download_missingFile_returns_206`
- Negative variant:
  - `203` for nonexistent file/peer share.
  - Assert requester gets `206 <nick> \"<filename>\"`.

6. `transfer_post_download_870_update_is_accepted`
- Send `870` after handshake completion.
- Assert no `404` token-format errors and browse/search can see updated share metadata.
