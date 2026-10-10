# 감지 시간 ≈ request_interval × failure_threshold = 10s × 3 = 약 30초
resource "aws_route53_health_check" "primary" {
  ip_address        = var.primary_ip
  port              = var.health_check_port
  type              = "HTTP"
  resource_path     = var.health_check_path
  request_interval  = 10
  failure_threshold = 3

  tags = {
    Name = "gcp-primary"
  }
}

resource "aws_route53_record" "primary" {
  zone_id         = var.zone_id
  name            = var.record_name
  type            = "A"
  ttl             = var.ttl
  records         = [var.primary_ip]
  set_identifier  = "gcp-primary"
  health_check_id = aws_route53_health_check.primary.id

  failover_routing_policy {
    type = "PRIMARY"
  }
}

resource "aws_route53_record" "standby" {
  zone_id        = var.zone_id
  name           = var.record_name
  type           = "A"
  set_identifier = "aws-standby"

  alias {
    name                   = var.standby_alb_dns_name
    zone_id                = var.standby_alb_zone_id
    evaluate_target_health = true
  }

  failover_routing_policy {
    type = "SECONDARY"
  }
}
