# RetroNap Protocol Specification

## Purpose
This document describes the protocol behavior implemented by this project (`retronap`), a server compatible with older Napster-era clients, specifically Napster 2.0 Beta 6.

`docs/napster.txt` is the historical baseline, but it is incomplete and occasionally inaccurate for current implementation details. This file documents what RetroNap currently does.
For test-planning and conformance contracts, see `docs/protocol-conformance-spec.md`.

## Sources of Truth
1. Runtime implementation in `src/main/java/com/deriklima/retronap`.
2. Message constants/token validation in `src/main/java/com/deriklima/retronap/message/MessageTypes.java`.
3. Integration flows in `src/test/java/com/deriklima/retronap/UserJourneyTests.java`.
4. Historical background in `docs/napster.txt`.

## Transport and Framing
RetroNap uses TCP.

Each packet is:
- `length` (2 bytes, little-endian)
- `type` (2 bytes, little-endian)
- `data` (`length` bytes, ISO-8859-1 string payload)

Notes:
- There is no CRLF line delimiter. Packet framing is entirely length-based.
- RetroNap currently writes packets in little-endian format.
- The original ecosystem had variants using big-endian; RetroNap behavior should be considered little-endian unless explicitly changed.

## Payload Tokenization Rules
RetroNap tokenizes payloads by spaces, with quoted fields treated as single tokens.

Rules implemented by `Message` parsing:
- Space separates tokens unless inside double quotes.
- Quote characters are delimiters and are not included in resulting token values.
- Unclosed trailing token content is still captured.
- Escaping embedded quotes is not supported.

Practical guidance:
- Quote any filename/path value containing spaces.
- For compound commands (for example search expressions), preserve keyword order exactly.

## Session Lifecycle
Typical session:
1. Client opens TCP connection.
2. Client sends login (`2`).
3. Server sends login ack (`3`) on success.
4. Server sends one or more MOTD messages (`621`).
5. Client sends feature messages (hotlist/share/search/browse/channel/etc.).
6. Session closes.

## Message Reference (Implemented/Used Flows)
The list below focuses on message types exercised in current integration tests and commonly used in RetroNap.

- `0` `SERVER_ERROR`
  - Login-time or generic server error text.
- `2` `CLIENT_LOGIN_REQUEST`
  - Format: `<nick> <password> <port> "<client-info>" <link-type> [build]`
- `3` `SERVER_LOGIN_ACK`
  - Format: `<email> [number]`
- `100` `CLIENT_SHARE_NOTIFICATION`
  - Format: `<absolute-path> <md5> <size> <bitrate> <frequency> <seconds>`
- `200` `CLIENT_SEARCH_REQUEST`
  - Example: `FILENAME CONTAINS "blink" MAX_RESULTS 100`
- `201` `SERVER_SEARCH_RESPONSE`
  - Format: `"<absolute-path>" <md5> <size> <bitrate> <frequency> <seconds> <nick> <ip> <link-type>`
- `202` `SERVER_END_SEARCH_RESULTS`
  - Empty payload.
- `208` `CLIENT_HOTLIST_CREATE`
  - Variable tokens (one or more nicks).
- `211` `CLIENT_BROWSE_USER_FILES`
  - Format: `<nick>`
- `212` `SERVER_BROWSE_USER_RESPONSE`
  - Contains one shared file entry for the requested user.
- `213` `SERVER_BROWSE_USER_END_RESPONSE`
  - End marker for browse responses.
- `301` `SERVER_HOTLIST_ACK`
  - Format: `<nick>` acknowledged.
- `404` `SERVER_GENERAL_ERROR`
  - Logged-in generic error text.
- `621` `MESSAGE_OF_THE_DAY`
  - Variable text messages sent after login.
- `827` `FULL_CHANNEL_LIST`
  - Used as client request and end-of-list marker.
- `828` `SERVER_FULL_CHANNEL_INFO`
  - Channel entry in full list response.
- `870` `CLIENT_DIR_SHARE_NOTIFICATION`
  - Format: `<dir> (<filename> <md5> <size> <bitrate> <frequency> <seconds>)+`
  - Filenames with spaces must be quoted.

## Core Client/Server Exchange Flows

### Flow A: Login + MOTD
1. Client sends `2` login request.
2. Server sends `3` login ack.
3. Server sends one or more `621` MOTD payloads.

Expected client behavior:
- Read at least one `621` before tearing down the socket to avoid write-side race noise.

### Flow B: Full Channel List
1. Client logs in (`2` -> `3`, `621`).
2. Client sends `827` full channel list request.
3. Server sends repeated `828` channel rows.
4. Server sends `827` end-of-list marker.

### Flow C: Hotlist + Share + Browse
1. Client logs in.
2. Client sends `208` with one or more nicks.
3. Server sends `301` for each acknowledged nick.
4. Client sends `100` to share one file.
5. Client sends `211 <nick>` to browse user shares.
6. Server sends one or more `212` entries.
7. Server sends `213` end marker.

Error handling:
- Server may send `0` or `404` if payload is invalid or processing fails.

### Flow D: Directory Share + Browse (`870`)
1. Client logs in.
2. Client sends `870` with directory + repeated file tuples.
3. Client sends `211 <nick>`.
4. Server returns `212` entries and then `213`.

Important:
- Token count must match the tuple structure exactly.
- Quote filenames with spaces.

### Flow E: Search (`200` -> `201`/`202`)
1. Client logs in.
2. Client shares files (for example via `870` or `100`).
3. Client sends `200` search request.
4. Server sends zero or more `201` results.
5. Server always ends with `202`.

## Validation and Compatibility Notes
- Message validity is enforced using token counts in `MessageTypes`.
- Message types marked with `VARIABLETOKENS` bypass fixed token-count checks.
- Search currently centers on filename token matching; some parsed optional operators are placeholders in current code.
- `docs/napster.txt` includes broader protocol surface than currently implemented.

## Testing Coverage Map
`UserJourneyTests` currently covers:
- Login and MOTD.
- Full channel list request/response.
- Hotlist creation acknowledgements.
- Single file share + browse.
- Directory share (`870`) + browse.
- Search request (`200`) with result (`201`) and end marker (`202`).

## Future Spec Expansion
Recommended next sections to add over time:
1. Complete message catalog for every handler currently wired.
2. Error code taxonomy and retry guidance.
3. Stateful behavior for session cleanup and share lifecycle.
4. Compatibility differences across Napster/OpenNap/SlavaNap semantics.

## Appendix A: Complete Message Type Catalog
This table is generated from `MessageTypes` constants and `addToken(...)` rules.

| Type | Constant | Token Rule |
|---:|---|---|
| `0` | `SERVER_ERROR` | `1` |
| `2` | `CLIENT_LOGIN_REQUEST` | `VARIABLETOKENS` |
| `3` | `SERVER_LOGIN_ACK` | `1` |
| `4` | `SERVER_VERSION_CHECK` | `(no static token rule)` |
| `6` | `CLIENT_ALT_LOGIN_REQUEST` | `7` |
| `7` | `CLIENT_REGISTRATION` | `1` |
| `8` | `SERVER_REGISTRATION_SUCCESS` | `0` |
| `9` | `SERVER_NICKNAME_ALREADY_REGISTERED` | `0` |
| `11` | `CLIENT_CHECK_PASS` | `(no static token rule)` |
| `12` | `SERVER_PASS_OK` | `(no static token rule)` |
| `14` | `CLIENT_REGISTRATION_INFO` | `(no static token rule)` |
| `100` | `CLIENT_SHARE_NOTIFICATION` | `6` |
| `102` | `CLIENT_SHARE_REMOVAL` | `1` |
| `200` | `CLIENT_SEARCH_REQUEST` | `VARIABLETOKENS` |
| `201` | `SERVER_SEARCH_RESPONSE` | `9` |
| `202` | `SERVER_END_SEARCH_RESULTS` | `0` |
| `203` | `CLIENT_DOWNLOAD_REQUEST` | `2` |
| `204` | `SERVER_DOWNLOAD_ACK` | `6` |
| `205` | `PRIVATE_MESSAGE` | `VARIABLETOKENS` |
| `206` | `SERVER_DOWNLOAD_ERROR` | `2` |
| `207` | `CLIENT_HOTLIST_ADD` | `1` |
| `208` | `CLIENT_HOTLIST_CREATE` | `VARIABLETOKENS` |
| `209` | `SERVER_HOTLIST_SIGNON` | `2` |
| `210` | `SERVER_HOTLIST_SIGNOFF` | `1` |
| `211` | `CLIENT_BROWSE_USER_FILES` | `1` |
| `212` | `SERVER_BROWSE_USER_RESPONSE` | `7` |
| `213` | `SERVER_BROWSE_USER_END_RESPONSE` | `1` |
| `214` | `SERVER_STATS` | `0` |
| `215` | `CLIENT_RESUME_REQUEST` | `2` |
| `216` | `SERVER_RESUME_LIST_ENTRY` | `(no static token rule)` |
| `217` | `SERVER_RESUME_LIST_END` | `(no static token rule)` |
| `218` | `CLIENT_DOWNLOAD_NOTIFICATION` | `0` |
| `219` | `CLIENT_DOWNLOAD_COMPLETE_NOTIFICATION` | `0` |
| `220` | `CLIENT_UPLOAD_NOTIFICATION` | `0` |
| `221` | `CLIENT_UPLOAD_COMPLETE_NOTIFICATION` | `0` |
| `300` | `CLIENT_CHECK_PORT` | `(no static token rule)` |
| `301` | `SERVER_HOTLIST_ACK` | `1` |
| `302` | `SERVER_HOTLIST_ERROR` | `1` |
| `303` | `CLIENT_HOTLIST_REMOVE` | `1` |
| `400` | `CLIENT_CHANNEL_JOIN` | `1` |
| `401` | `CLIENT_CHANNEL_LEAVE` | `1` |
| `402` | `CLIENT_CHANNEL_PUBLIC_MESSAGE` | `VARIABLETOKENS` |
| `403` | `SERVER_CHANNEL_PUBLIC_MESSAGE` | `(no static token rule)` |
| `404` | `SERVER_GENERAL_ERROR` | `(no static token rule)` |
| `405` | `SERVER_CHANNEL_JOIN_ACK` | `(no static token rule)` |
| `406` | `SERVER_CHANNEL_JOIN_NOTIFY` | `(no static token rule)` |
| `407` | `SERVER_CHANNEL_LEAVE_NOTIFY` | `(no static token rule)` |
| `408` | `SERVER_CHANNEL_USER_LIST_ENTRY` | `(no static token rule)` |
| `409` | `SERVER_CHANNEL_USER_LIST_END` | `(no static token rule)` |
| `410` | `CHANNEL_TOPIC` | `2` |
| `500` | `CLIENT_ALT_DOWNLOAD_REQUEST` | `2` |
| `501` | `SERVER_ALT_DOWNLOAD_ACK` | `6` |
| `600` | `CLIENT_LINKSPEED_REQUEST` | `1` |
| `601` | `SERVER_LINKSPEED_RESPONSE` | `2` |
| `603` | `CLIENT_WHOIS_REQUEST` | `1` |
| `604` | `SERVER_WHOIS_RESPONSE` | `6` |
| `605` | `SERVER_WHOWAS_RESPONSE` | `3` |
| `606` | `CLIENT_CHANGE_USER_LEVEL` | `2` |
| `607` | `SERVER_UPLOAD_REQUEST` | `2` |
| `608` | `CLIENT_UPLOAD_ACCEPT` | `2` |
| `610` | `CLIENT_ADMIN_DISCONNECT` | `1` |
| `611` | `CLIENT_ADMIN_NUKE` | `1` |
| `612` | `CLIENT_ADMIN_BAN_USER` | `1` |
| `613` | `CLIENT_CHANGE_USER_DATAPORT` | `2` |
| `614` | `CLIENT_ADMIN_UNBAN_USER` | `1` |
| `615` | `CLIENT_ADMIN_SHOW_BANS` | `0` |
| `616` | `SERVER_BAN_LIST_ENTRY` | `(no static token rule)` |
| `617` | `LIST_CHANNELS` | `0` |
| `618` | `LIST_CHANNELS_ENTRY` | `(no static token rule)` |
| `619` | `CLIENT_QUEUE_LIMIT` | `3` |
| `620` | `SERVER_QUEUE_LIMIT` | `(no static token rule)` |
| `621` | `MESSAGE_OF_THE_DAY` | `VARIABLETOKENS` |
| `625` | `CLIENT_CHANGE_USER_LINKSPEED` | `2` |
| `626` | `CLIENT_DATA_PORT_ERROR` | `2` |
| `628` | `CLIENT_ADMIN_GLOBAL_MESSAGE` | `1` |
| `629` | `SERVER_BAN_LIST_USER_ENTRY` | `(no static token rule)` |
| `700` | `CLIENT_CHANGE_LINKSPEED` | `1` |
| `701` | `CLIENT_CHANGE_PASSWORD` | `1` |
| `702` | `CLIENT_CHANGE_EMAIL` | `1` |
| `703` | `CLIENT_CHANGE_DATAPORT` | `1` |
| `751` | `PING` | `1` |
| `752` | `PONG` | `1` |
| `753` | `CLIENT_CHANGE_USER_PASSWORD` | `2` |
| `824` | `CHANNEL_EMOTE` | `2` |
| `825` | `SERVER_CHANNEL_USER_LIST_ENTRY_2` | `(no static token rule)` |
| `827` | `FULL_CHANNEL_LIST` | `(no static token rule)` |
| `828` | `SERVER_FULL_CHANNEL_INFO` | `(no static token rule)` |
| `830` | `CLIENT_CHANNEL_USER_LIST` | `1` |
| `870` | `CLIENT_DIR_SHARE_NOTIFICATION` | `VARIABLETOKENS` |

Token rule meanings:
- Numeric value: exact number of parsed payload tokens expected by `Message.isValid()`.
- `VARIABLETOKENS`: no fixed token-count validation.
- `(no static token rule)`: constant exists but no explicit entry in the token map.

## Appendix B: Active Handler Routing Map
This table maps message types to currently wired handlers via `MessageHandlerFactory`.

| Type | Constant | Active Handler(s) |
|---:|---|---|
| `2` | `CLIENT_LOGIN_REQUEST` | `LoginHandler, OpenDoorLoginHandler` |
| `4` | `SERVER_VERSION_CHECK` | `VersionCheckHandler` |
| `6` | `CLIENT_ALT_LOGIN_REQUEST` | `AltLoginHandler` |
| `7` | `CLIENT_REGISTRATION` | `ClientRegistrationHandler` |
| `11` | `CLIENT_CHECK_PASS` | `CheckPassHandler` |
| `14` | `CLIENT_REGISTRATION_INFO` | `ClientInfoRegistrationHandler` |
| `100` | `CLIENT_SHARE_NOTIFICATION` | `ClientShareNotificationHandler` |
| `102` | `CLIENT_SHARE_REMOVAL` | `ClientShareRemovalHandler` |
| `200` | `CLIENT_SEARCH_REQUEST` | `NewClientSearchRequestHandler` |
| `203` | `CLIENT_DOWNLOAD_REQUEST` | `DownloadRequestHandler` |
| `205` | `PRIVATE_MESSAGE` | `PrivateMessageHandler` |
| `207` | `CLIENT_HOTLIST_ADD` | `HotlistHandler` |
| `208` | `CLIENT_HOTLIST_CREATE` | `HotlistHandler` |
| `211` | `CLIENT_BROWSE_USER_FILES` | `ClientBrowseUserHandler` |
| `214` | `SERVER_STATS` | `StatusHandler` |
| `215` | `CLIENT_RESUME_REQUEST` | `ResumeRequestHandler` |
| `218` | `CLIENT_DOWNLOAD_NOTIFICATION` | `TransferMessageHandler` |
| `219` | `CLIENT_DOWNLOAD_COMPLETE_NOTIFICATION` | `TransferMessageHandler` |
| `220` | `CLIENT_UPLOAD_NOTIFICATION` | `TransferMessageHandler` |
| `221` | `CLIENT_UPLOAD_COMPLETE_NOTIFICATION` | `TransferMessageHandler` |
| `300` | `CLIENT_CHECK_PORT` | `IgnoreCommandHandler` |
| `303` | `CLIENT_HOTLIST_REMOVE` | `HotlistHandler` |
| `400` | `CLIENT_CHANNEL_JOIN` | `ChannelHandler` |
| `401` | `CLIENT_CHANNEL_LEAVE` | `ChannelHandler` |
| `402` | `CLIENT_CHANNEL_PUBLIC_MESSAGE` | `ChannelHandler` |
| `410` | `CHANNEL_TOPIC` | `ChannelHandler` |
| `500` | `CLIENT_ALT_DOWNLOAD_REQUEST` | `AltDownloadRequestHandler` |
| `600` | `CLIENT_LINKSPEED_REQUEST` | `RequestLinkSpeedHandler` |
| `603` | `CLIENT_WHOIS_REQUEST` | `WhoisHandler` |
| `606` | `CLIENT_CHANGE_USER_LEVEL` | `AdminActionHandler` |
| `608` | `CLIENT_UPLOAD_ACCEPT` | `UploadAcceptHandler` |
| `610` | `CLIENT_ADMIN_DISCONNECT` | `AdminActionHandler` |
| `611` | `CLIENT_ADMIN_NUKE` | `AdminActionHandler` |
| `612` | `CLIENT_ADMIN_BAN_USER` | `BanHandler` |
| `613` | `CLIENT_CHANGE_USER_DATAPORT` | `AdminUserOptionHandler` |
| `614` | `CLIENT_ADMIN_UNBAN_USER` | `BanHandler` |
| `615` | `CLIENT_ADMIN_SHOW_BANS` | `BanHandler` |
| `617` | `LIST_CHANNELS` | `ChannelHandler` |
| `619` | `CLIENT_QUEUE_LIMIT` | `QueueLimitHandler` |
| `621` | `MESSAGE_OF_THE_DAY` | `MessageOfTheDayHandler` |
| `625` | `CLIENT_CHANGE_USER_LINKSPEED` | `AdminUserOptionHandler` |
| `626` | `CLIENT_DATA_PORT_ERROR` | `DataPortErrorHandler` |
| `628` | `CLIENT_ADMIN_GLOBAL_MESSAGE` | `AdminActionHandler` |
| `700` | `CLIENT_CHANGE_LINKSPEED` | `ChangeOptionHandler` |
| `701` | `CLIENT_CHANGE_PASSWORD` | `ChangeOptionHandler` |
| `702` | `CLIENT_CHANGE_EMAIL` | `ChangeOptionHandler` |
| `703` | `CLIENT_CHANGE_DATAPORT` | `ChangeOptionHandler` |
| `751` | `PING` | `PingHandler` |
| `752` | `PONG` | `PingHandler` |
| `753` | `CLIENT_CHANGE_USER_PASSWORD` | `AdminActionHandler` |
| `824` | `CHANNEL_EMOTE` | `ChannelHandler` |
| `827` | `FULL_CHANNEL_LIST` | `ChannelHandler` |
| `830` | `CLIENT_CHANNEL_USER_LIST` | `ChannelHandler` |
| `870` | `CLIENT_DIR_SHARE_NOTIFICATION` | `ClientDirShareHandler` |

Routing notes:
- `CLIENT_LOGIN_REQUEST (2)` has two conditional handlers:
  - `LoginHandler` when `retronap.policy.opendoor=false` (default).
  - `OpenDoorLoginHandler` when `retronap.policy.opendoor=true`.
- `ClientSearchRequestHandler` is deprecated and currently returns `List.of()`; `NewClientSearchRequestHandler` is the active handler for `200`.
- Types not listed in Appendix B are handled by `UnknownMessageHandler` and result in `SERVER_GENERAL_ERROR (404)`.
