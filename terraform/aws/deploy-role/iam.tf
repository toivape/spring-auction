# GitHub OIDC federation — no long-lived AWS keys in GitHub secrets.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]

  # AWS no longer actually validates this thumbprint for GitHub's OIDC provider (GitHub's
  # signing CA has changed since; AWS validates against its own trust store instead), but
  # the resource still requires a value. This is GitHub's long-published thumbprint.
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

data "aws_iam_policy_document" "github_actions_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # Scoped to main only — deploys only ever run from main (workflow_dispatch, main-only gate).
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repo}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "github_actions_ecs" {
  name               = "github-actions-ecs-role"
  assume_role_policy = data.aws_iam_policy_document.github_actions_trust.json
}

locals {
  ecs_task_execution_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${var.ecs_task_execution_role_name}"
  ecs_infrastructure_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${var.ecs_infrastructure_role_name}"
  ecr_repository_arn          = "arn:aws:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/${var.ecr_repository_name}"
  rds_instance_arn            = "arn:aws:rds:${var.aws_region}:${data.aws_caller_identity.current.account_id}:db:${var.rds_identifier}"
  secrets_arn_pattern         = "arn:aws:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:${var.secrets_name_prefix}*"
  state_bucket_arn            = "arn:aws:s3:::${var.state_bucket_name}"
  lock_table_arn              = "arn:aws:dynamodb:${var.aws_region}:${data.aws_caller_identity.current.account_id}:table/${var.lock_table_name}"
}

# CI applies the whole application/ Terraform stack on every deploy (see ADR 0004), so this
# role needs create/update rights on VPC, RDS, Secrets Manager, and IAM — not just ECS/ECR.
# Scoped to specific resource ARNs/name patterns wherever the service supports it; EC2 is the
# one accepted exception (most VPC object types have no ARN-level IAM scoping).
data "aws_iam_policy_document" "github_actions_permissions" {
  # ECS Express Mode — action list confirmed against AWS's own infrastructure-role docs.
  # ECS doesn't support resource-level scoping for these actions (matches AWS's own sample policy).
  statement {
    sid    = "EcsExpressMode"
    effect = "Allow"
    actions = [
      "ecs:CreateExpressGatewayService",
      "ecs:UpdateExpressGatewayService",
      "ecs:DeleteExpressGatewayService",
      "ecs:DescribeExpressGatewayService",
      "ecs:RegisterTaskDefinition",
      "ecs:CreateCluster",
      "ecs:DescribeClusters",
      "ecs:DescribeServices",
      "ecs:ListServiceDeployments",
      "ecs:DescribeServiceDeployments",
      "ecs:TagResource",
      "ecs:UntagResource",
    ]
    resources = ["*"]
  }

  # Express Mode auto-provisions an ALB, target group, ACM cert, auto-scaling policy, and
  # CloudWatch alarm behind the scenes (see AWS's "resources created by Express Mode" docs) —
  # none of these are under our control (names/ARNs are dynamically generated), so this is
  # broad by necessity, same accepted-exception reasoning as the VPC statement above.
  statement {
    sid    = "ExpressModeSupportingServices"
    effect = "Allow"
    actions = [
      "elasticloadbalancing:*",
      "acm:RequestCertificate",
      "acm:DescribeCertificate",
      "acm:DeleteCertificate",
      "acm:ListCertificates",
      "acm:AddTagsToCertificate",
      "acm:ListTagsForCertificate",
      "application-autoscaling:RegisterScalableTarget",
      "application-autoscaling:DeregisterScalableTarget",
      "application-autoscaling:PutScalingPolicy",
      "application-autoscaling:DeleteScalingPolicy",
      "application-autoscaling:DescribeScalableTargets",
      "application-autoscaling:DescribeScalingPolicies",
      "cloudwatch:PutMetricAlarm",
      "cloudwatch:DeleteAlarms",
      "cloudwatch:DescribeAlarms",
      "iam:CreateServiceLinkedRole",
    ]
    resources = ["*"]
  }

  # ECR — push access scoped to the one repo; GetAuthorizationToken must be "*" (AWS requirement).
  statement {
    sid       = "EcrAuth"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid    = "EcrPush"
    effect = "Allow"
    actions = [
      "ecr:BatchGetImage",
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [local.ecr_repository_arn]
  }

  # ECR repository management — Terraform (running as this role) creates/manages the repo
  # itself and its lifecycle policy, not just image pushes.
  statement {
    sid    = "EcrRepository"
    effect = "Allow"
    actions = [
      "ecr:CreateRepository",
      "ecr:DeleteRepository",
      "ecr:DescribeRepositories",
      "ecr:PutLifecyclePolicy",
      "ecr:GetLifecyclePolicy",
      "ecr:DeleteLifecyclePolicy",
      "ecr:PutImageScanningConfiguration",
      "ecr:TagResource",
      "ecr:UntagResource",
      "ecr:ListTagsForResource",
    ]
    resources = [local.ecr_repository_arn]
  }

  # VPC — accepted exception: EC2 doesn't support ARN-level scoping for subnets/route
  # tables/IGWs/security groups, so this is a service-broad grant rather than resource-narrow.
  statement {
    sid       = "Vpc"
    effect    = "Allow"
    actions   = ["ec2:*"]
    resources = ["*"]
  }

  # RDS — mutating actions scoped to the one instance ARN.
  statement {
    sid    = "RdsInstance"
    effect = "Allow"
    actions = [
      "rds:CreateDBInstance",
      "rds:ModifyDBInstance",
      "rds:DeleteDBInstance",
    ]
    resources = [local.rds_instance_arn]
  }

  # RDS subnet groups don't support resource-level scoping by instance ARN; Describe/List
  # actions never support resource-level scoping at all. AddTagsToResource/RemoveTagsFromResource
  # are needed on both the instance and the subnet group (different resource type/ARN), so
  # kept broad here rather than duplicated per-ARN. Kept in one read/discovery statement.
  statement {
    sid    = "RdsSupport"
    effect = "Allow"
    actions = [
      "rds:CreateDBSubnetGroup",
      "rds:ModifyDBSubnetGroup",
      "rds:DeleteDBSubnetGroup",
      "rds:DescribeDBInstances",
      "rds:DescribeDBSubnetGroups",
      "rds:ListTagsForResource",
      "rds:AddTagsToResource",
      "rds:RemoveTagsFromResource",
    ]
    resources = ["*"]
  }

  # Secrets Manager — scoped to this project's secret name prefix.
  statement {
    sid    = "Secrets"
    effect = "Allow"
    actions = [
      "secretsmanager:CreateSecret",
      "secretsmanager:UpdateSecret",
      "secretsmanager:PutSecretValue",
      "secretsmanager:DeleteSecret",
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetSecretValue",
      "secretsmanager:TagResource",
      "secretsmanager:UntagResource",
      "secretsmanager:GetResourcePolicy",
    ]
    resources = [local.secrets_arn_pattern]
  }

  # IAM — limited to the two named roles the Express service uses, plus PassRole conditioned
  # to only pass them to ecs.amazonaws.com.
  statement {
    sid    = "EcsRoles"
    effect = "Allow"
    actions = [
      "iam:CreateRole",
      "iam:GetRole",
      "iam:DeleteRole",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListRolePolicies",
      "iam:ListRoleTags",
    ]
    resources = [
      local.ecs_task_execution_role_arn,
      local.ecs_infrastructure_role_arn,
    ]
  }

  statement {
    sid     = "PassEcsRoles"
    effect  = "Allow"
    actions = ["iam:PassRole"]
    resources = [
      local.ecs_task_execution_role_arn,
      local.ecs_infrastructure_role_arn,
    ]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs.amazonaws.com"]
    }
  }

  # CloudWatch Logs — the log group express_service.tf creates/references (name pattern
  # matches ECS Express Mode's own default: /aws/ecs/<cluster>/<service>-####, plus our
  # explicit group). Broad-ish since the exact auto-suffixed name isn't known ahead of time.
  statement {
    sid    = "Logs"
    effect = "Allow"
    actions = [
      "logs:CreateLogGroup",
      "logs:DeleteLogGroup",
      "logs:PutRetentionPolicy",
      "logs:TagResource",
      "logs:ListTagsForResource",
    ]
    resources = ["arn:aws:logs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:log-group:/aws/ecs/*"]
  }

  # DescribeLogGroups is a list-style action — doesn't support resource-level scoping.
  statement {
    sid       = "LogsDiscovery"
    effect    = "Allow"
    actions   = ["logs:DescribeLogGroups"]
    resources = ["*"]
  }

  # Terraform backend — the specific bucket/table bootstrap/ created, nothing else in S3/DynamoDB.
  statement {
    sid    = "TerraformStateBucket"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:ListBucket",
    ]
    resources = [
      local.state_bucket_arn,
      "${local.state_bucket_arn}/*",
    ]
  }

  statement {
    sid    = "TerraformStateLock"
    effect = "Allow"
    actions = [
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:DeleteItem",
    ]
    resources = [local.lock_table_arn]
  }
}

resource "aws_iam_role_policy" "github_actions" {
  name   = "application-stack-apply"
  role   = aws_iam_role.github_actions_ecs.id
  policy = data.aws_iam_policy_document.github_actions_permissions.json
}
