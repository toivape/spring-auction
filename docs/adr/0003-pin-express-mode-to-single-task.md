---
status: accepted
---

# Pin the Express Mode service to exactly 1 task, no auto scaling

The app has no distributed session store: sessions and the CSRF token repository are both
Spring's default in-memory, session-bound implementations (no `spring-session-*` dependency, no
custom `CsrfTokenRepository`). Google's OAuth2 login handshake also stores its
`OAuth2AuthorizationRequest` in the HTTP session mid-flow. None of this state is shared across
nodes.

If the Express Mode service ever ran more than one Fargate task behind its ALB without sticky
sessions, a user could be routed to a different node between requests and lose their session —
breaking the admin login, the Google OAuth2 handshake, or CSRF validation on a bid/pay POST,
intermittently and confusingly.

We pinned `desired_count`/min/max to exactly 1 task rather than adding ALB sticky sessions or a
shared session store (e.g. `spring-session-jdbc`) — either would remove this constraint, but both
are more scope than a cloud-provider migration and are natural candidates for a future, separate
decision if real horizontal scaling is ever needed. Until then: **do not raise the task count
above 1** without first addressing session sharing.
