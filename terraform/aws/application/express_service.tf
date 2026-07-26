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

    aws_logs_configuration {
      log_group = aws_cloudwatch_log_group.app.name
    }
  }

  network_configuration {
    subnets         = [for s in aws_subnet.public : s.id]
    security_groups = [aws_security_group.app.id]
  }

  # Pinned to exactly 1 task, no auto scaling — see ADR 0003 (no shared session store).
  # Do not raise without addressing session sharing first.
  scaling_target {
    min_task_count = 1
    max_task_count = 1
  }

  # Prevents a race during service deletion: if the execution/infrastructure role's policy
  # attachment is destroyed before the service, the service can get stuck DRAINING.
  depends_on = [
    aws_iam_role_policy_attachment.ecs_task_execution_managed,
    aws_iam_role_policy_attachment.ecs_infrastructure_managed,
  ]
}
