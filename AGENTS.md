## Protocol Documentation Rules

- Treat these as canonical protocol references and always consult them before protocol-related edits:
  - `docs/protocol-spec.md`
  - `docs/protocol-conformance-spec.md`
- Any change that affects protocol behavior must include doc updates in the same change set:
  - Wire/message format
  - Message handlers, validation, sequencing, or state-machine behavior
  - Protocol-level error handling, status semantics, or conformance criteria
- Do not consider protocol-impacting work complete until docs are updated, or explicitly note why no update is required.
- Final task summary should include `Protocol docs impact:` with the files changed (or `none`).
