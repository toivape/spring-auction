# Terraform (AWS)

Three configs, two different operator models — see `../../docs/adr/0004-ci-applies-application-stack.md`
and `../../../AWS-DEPLOYMENT-HANDOFF.md` for the full reasoning.

- **`bootstrap/`** — one-time, applied locally with admin creds, local state (can't depend on
  the S3 backend it creates). Creates the shared state bucket + DynamoDB lock table.
- **`deploy-role/`** — applied locally with admin creds. Creates the GitHub OIDC provider +
  `github-actions-ecs-role` that CI assumes — can't apply itself, since CI doesn't have an
  identity until this exists.
- **`application/`** — applied exclusively by CI (`.github/workflows/deploy.yml`), on every
  deploy. VPC, ECR, RDS, Secrets Manager, IAM, and the Express Mode service. (Not yet built —
  see ticket #19/#20.)

## Applying `bootstrap/` and `deploy-role/` by hand

```bash
export AWS_PROFILE=my-tf-profile   # or your own admin-access profile

cd terraform/aws/bootstrap
terraform init
terraform apply -var="state_bucket_name=<globally-unique-bucket-name>"
terraform output   # note state_bucket_name / lock_table_name for the next step

cd ../deploy-role
cp backend.hcl.example backend.hcl   # fill in the bucket/table from the output above
terraform init -backend-config=backend.hcl
terraform apply -var="state_bucket_name=<same-bucket-name>"
terraform output   # note github_actions_role_arn — needed by the deploy workflow
```
