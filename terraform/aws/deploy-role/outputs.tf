output "github_actions_role_arn" {
  value       = aws_iam_role.github_actions_ecs.arn
  description = "Feed into the deploy workflow's role-to-assume input"
}
