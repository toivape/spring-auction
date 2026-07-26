# 2 AZs minimum — not a real choice, both RDS's future subnet group and the Express Mode
# ALB require it. Public subnets host the Express Mode service (confirmed against AWS's own
# "resources created by Express Mode" docs: public subnets auto-enable assignPublicIp and an
# internet-facing ALB, no NAT Gateway needed). Private subnets are for RDS only (ticket #20) —
# nothing in them needs outbound, so no NAT Gateway here either.

resource "aws_vpc" "main" {
  cidr_block           = "10.20.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "spring-auction" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "spring-auction" }
}

resource "aws_subnet" "public" {
  for_each = { for idx, az in local.azs : az => idx }

  vpc_id                  = aws_vpc.main.id
  availability_zone       = each.key
  cidr_block              = cidrsubnet(aws_vpc.main.cidr_block, 4, each.value)
  map_public_ip_on_launch = true

  tags = { Name = "spring-auction-public-${each.key}" }
}

resource "aws_subnet" "private" {
  for_each = { for idx, az in local.azs : az => idx }

  vpc_id            = aws_vpc.main.id
  availability_zone = each.key
  cidr_block        = cidrsubnet(aws_vpc.main.cidr_block, 4, each.value + 8)

  tags = { Name = "spring-auction-private-${each.key}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "spring-auction-public" }
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

# No NAT Gateway — local-only routes, explicit so private subnets aren't implicitly
# associated with the public route table (and its internet route) by default.
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "spring-auction-private" }
}

resource "aws_route_table_association" "private" {
  for_each = aws_subnet.private

  subnet_id      = each.value.id
  route_table_id = aws_route_table.private.id
}

resource "aws_security_group" "app" {
  name        = "spring-auction-app"
  description = "Express Mode service tasks"
  vpc_id      = aws_vpc.main.id

  egress {
    description = "All outbound (ECR pull, CloudWatch Logs, Secrets Manager, RDS)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "spring-auction-app" }
}
