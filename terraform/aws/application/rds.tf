resource "aws_db_subnet_group" "app" {
  name       = var.rds_identifier
  subnet_ids = [for s in aws_subnet.private : s.id]

  tags = { Name = var.rds_identifier }
}

resource "aws_security_group" "rds" {
  name        = "${var.rds_identifier}-rds"
  description = "RDS Postgres, inbound only from the app service"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Postgres from the app service"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.rds_identifier}-rds" }
}

# POC cost/iteration posture (see decisions table in ../../../../AWS-DEPLOYMENT-HANDOFF.md):
# no backups, deletable, single-AZ. Do not point this at anything that matters — any data is
# unrecoverable the moment the instance is destroyed.
resource "aws_db_instance" "app" {
  identifier     = var.rds_identifier
  engine         = "postgres"
  engine_version = "18.4" # matches compose.yaml's postgres:latest (observed 18.4)
  instance_class = "db.t4g.micro"

  allocated_storage = 20
  storage_type      = "gp3"

  db_name  = "auction"
  username = "myuser"

  # RDS creates and owns this secret natively (rotation-capable) — no secret of our own to
  # manage for the DB password. ecsTaskExecutionRole reads it via the computed secret ARN.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.app.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period = 0
  deletion_protection     = false
  skip_final_snapshot     = true

  tags = { Name = var.rds_identifier }
}
