output "gcp_ha_vpn_ips" {
  value = [for i in google_compute_ha_vpn_gateway.this.vpn_interfaces : i.ip_address]
}

output "aws_tunnel_ips" {
  value = [for t in local.tunnels : t.outside_ip]
}
