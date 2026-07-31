---
status: accepted
---

# Keep Mailjet as the email transport on AWS instead of switching to SES

The `mailjet` notification transport (`MailjetEmailSender`) was originally chosen for the planned
GCP deployment because GCP has no native email-sending service and blocks outbound SMTP port 25.
Moving the deployment target to AWS, that reasoning only half-holds: AWS also blocks outbound
port 25 by default, but unlike GCP, AWS *does* have a native email service (SES) with its own
HTTPS API — the more obvious choice for a reader looking at an AWS deployment.

We decided to keep Mailjet unchanged rather than add an SES transport now. `MailjetEmailSender`
is already transport-agnostic to cloud provider — nothing about it needs to change beyond
sourcing its credentials from AWS Secrets Manager instead of Google Secret Manager. Adding SES
would mean a new `EmailSender` implementation, new IAM permissions, and sending-identity/domain
verification — real work with no functional benefit for this migration. This is a deliberate
deferral: SES remains a candidate for a future, separate improvement, not a gap in this plan.
