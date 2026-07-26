resource "aws_cloudwatch_log_group" "app" {
  name              = "/aws/ecs/${var.service_name}"
  retention_in_days = 14
}

resource "aws_ecs_express_gateway_service" "app" {
  service_name            = var.service_name
  execution_role_arn      = aws_iam_role.ecs_task_execution.arn
  infrastructure_role_arn = aws_iam_role.ecs_infrastructure.arn
  cpu                     = "256"
  memory                  = "512"
  health_check_path       = var.health_check_path

  primary_container {
    image          = var.container_image
    container_port = var.container_port

    # log_stream_prefix reads as optional in the Terraform docs, but the actual ECS API
    # rejects CreateExpressGatewayService without it — a real API/docs discrepancy.
    aws_logs_configuration {
      log_group         = aws_cloudwatch_log_group.app.name
      log_stream_prefix = "ecs"
    }
  }

  network_configuration {
    subnets         = [for s in aws_subnet.public : s.id]
    security_groups = [aws_security_group.app.id]
  }

  # Pinned to exactly 1 task, no auto scaling — see ADR 0003 (no shared session store).
  # Do not raise without addressing session sharing first.
  # auto_scaling_metric/auto_scaling_target_value are set explicitly (matching AWS's own
  # defaults) even though min=max=1 makes them moot — leaving them null causes a provider
  # "produced inconsistent result after apply" error, since the API fills them in anyway.
  scaling_target {
    min_task_count            = 1
    max_task_count            = 1
    auto_scaling_metric       = "AVERAGE_CPU"
    auto_scaling_target_value = 60
  }

  # Prevents a race during service deletion: if the execution/infrastructure role's policy
  # attachment is destroyed before the service, the service can get stuck DRAINING.
  depends_on = [
    aws_iam_role_policy_attachment.ecs_task_execution_managed,
    aws_iam_role_policy_attachment.ecs_infrastructure_managed,
  ]
}
