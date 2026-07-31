---
status: accepted
---

# Use ECS Express Mode instead of vanilla ECS Fargate + hand-rolled ALB

We deploy on Amazon ECS Express Mode (Fargate-based) rather than the traditional pattern of a
Fargate service with a manually defined ALB, target group, and listener. This is a deliberate
deviation from the long-established pattern, not an oversight: our original plan was AWS App
Runner (the simplest managed option), but App Runner moved to maintenance mode in April 2026
(no new customers), and AWS's own stated migration path for App Runner workloads is ECS Express
Mode (GA'd November 2025). Express Mode gives us the same "just point at a container image"
simplicity — auto-provisioned ALB with TLS, auto scaling, health checks, and networking — while
being a first-class part of ECS rather than a separate, now-deprecated service.

Trade-off: Express Mode is new (~8 months old at time of writing) and less battle-tested than
plain ECS Fargate + ALB, and its Terraform provider support is thinner. We accepted this given
the small blast radius of a POC/dev-only deployment.
