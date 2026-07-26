---
status: accepted
---

# CI applies the `application` Terraform stack directly, instead of calling an Express Mode API

The original deployment plan had GitHub Actions call the `aws-actions/amazon-ecs-deploy-express-service`
action to update the running Express Mode service's container image directly via the AWS API, with the
`application` Terraform stack (VPC, ECR, RDS, Secrets Manager, IAM, the Express service itself) applied
by hand, same operator model as `deploy-role`. The stated reason at the time was keeping the CI OIDC
role scoped to just ECS/ECR.

That split has a real problem: Terraform's state would keep whatever image was set at the last manual
`apply`, while the Express Mode API call updates the actual running service out from under it. A future
`terraform apply` on `application` would then silently roll the image back to that stale value — state
and reality can drift apart with no warning.

We changed the deploy workflow to run `terraform apply -var="container_image=...:$SHA"` against the
`application` stack directly, on every deploy — Terraform's state *is* the deployed state, so drift is
structurally impossible. `bootstrap` and `deploy-role` stay applied by hand (both are chicken-and-egg:
`deploy-role` creates the very identity that would apply anything, so it can't apply itself).

Trade-off: the CI role can no longer be scoped to "just ECS/ECR" — applying the whole `application`
stack means it also needs create/update rights on VPC (EC2), RDS, Secrets Manager, and IAM (limited to
the two roles the Express service uses). We accepted this, written as service-broad-but-resource-narrow
where AWS's IAM model allows it (RDS, Secrets Manager, IAM roles are all scoped to specific ARNs/name
patterns; EC2 is the one accepted exception, since most VPC object types have no ARN-level IAM scoping
at all). The existing manual `workflow_dispatch` + main-only trigger gate remains the primary safeguard
against this broader role being exercised outside a deliberate deploy.
