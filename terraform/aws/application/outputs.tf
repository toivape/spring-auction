output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "service_arn" {
  value = aws_ecs_express_gateway_service.app.service_arn
}

output "ingress_paths" {
  value       = aws_ecs_express_gateway_service.app.ingress_paths
  description = "Includes the auto-generated public URL"
}
