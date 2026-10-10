# GCP HA VPN (인터페이스 2개) ↔ AWS VPN Connection 2개 (각 터널 2개) = 터널 4개, BGP 동적 라우팅

# ---------- GCP ----------

resource "google_compute_ha_vpn_gateway" "this" {
  name    = "${var.name}-to-aws"
  region  = var.gcp_region
  network = var.gcp_network_id
}

resource "google_compute_router" "vpn" {
  name    = "${var.name}-vpn-router"
  region  = var.gcp_region
  network = var.gcp_network_id

  bgp {
    asn               = var.gcp_router_asn
    advertise_mode    = "CUSTOM"
    advertised_groups = ["ALL_SUBNETS"]

    dynamic "advertised_ip_ranges" {
      for_each = var.gcp_advertised_ranges
      content {
        range = advertised_ip_ranges.value
      }
    }
  }
}

# ---------- AWS ----------

resource "aws_vpn_gateway" "this" {
  vpc_id          = var.aws_vpc_id
  amazon_side_asn = var.aws_vgw_asn

  tags = {
    Name = "${var.name}-vgw"
  }
}

resource "aws_vpn_gateway_route_propagation" "this" {
  count = length(var.aws_route_table_ids)

  vpn_gateway_id = aws_vpn_gateway.this.id
  route_table_id = var.aws_route_table_ids[count.index]
}

# GCP HA VPN 인터페이스마다 Customer Gateway 1개
resource "aws_customer_gateway" "gcp" {
  count = 2

  bgp_asn    = var.gcp_router_asn
  ip_address = google_compute_ha_vpn_gateway.this.vpn_interfaces[count.index].ip_address
  type       = "ipsec.1"

  tags = {
    Name = "${var.name}-gcp-if${count.index}"
  }
}

resource "aws_vpn_connection" "gcp" {
  count = 2

  vpn_gateway_id      = aws_vpn_gateway.this.id
  customer_gateway_id = aws_customer_gateway.gcp[count.index].id
  type                = "ipsec.1"
  static_routes_only  = false

  tunnel1_ike_versions = ["ikev2"]
  tunnel2_ike_versions = ["ikev2"]

  tags = {
    Name = "${var.name}-gcp-if${count.index}"
  }
}

# ---------- 터널 4개 ----------

locals {
  tunnels = [
    for i in range(4) : {
      gcp_interface = floor(i / 2)
      outside_ip    = i % 2 == 0 ? aws_vpn_connection.gcp[floor(i / 2)].tunnel1_address : aws_vpn_connection.gcp[floor(i / 2)].tunnel2_address
      psk           = i % 2 == 0 ? aws_vpn_connection.gcp[floor(i / 2)].tunnel1_preshared_key : aws_vpn_connection.gcp[floor(i / 2)].tunnel2_preshared_key
      cgw_inside_ip = i % 2 == 0 ? aws_vpn_connection.gcp[floor(i / 2)].tunnel1_cgw_inside_address : aws_vpn_connection.gcp[floor(i / 2)].tunnel2_cgw_inside_address
      vgw_inside_ip = i % 2 == 0 ? aws_vpn_connection.gcp[floor(i / 2)].tunnel1_vgw_inside_address : aws_vpn_connection.gcp[floor(i / 2)].tunnel2_vgw_inside_address
    }
  ]
}

resource "google_compute_external_vpn_gateway" "aws" {
  name            = "${var.name}-aws"
  redundancy_type = "FOUR_IPS_REDUNDANCY"

  dynamic "interface" {
    for_each = local.tunnels
    content {
      id         = interface.key
      ip_address = interface.value.outside_ip
    }
  }
}

resource "google_compute_vpn_tunnel" "this" {
  count = 4

  name                            = "${var.name}-tunnel-${count.index}"
  region                          = var.gcp_region
  vpn_gateway                     = google_compute_ha_vpn_gateway.this.id
  vpn_gateway_interface           = local.tunnels[count.index].gcp_interface
  peer_external_gateway           = google_compute_external_vpn_gateway.aws.id
  peer_external_gateway_interface = count.index
  shared_secret                   = local.tunnels[count.index].psk
  router                          = google_compute_router.vpn.id
  ike_version                     = 2
}

resource "google_compute_router_interface" "this" {
  count = 4

  name       = "${var.name}-if-${count.index}"
  router     = google_compute_router.vpn.name
  region     = var.gcp_region
  ip_range   = "${local.tunnels[count.index].cgw_inside_ip}/30"
  vpn_tunnel = google_compute_vpn_tunnel.this[count.index].name
}

resource "google_compute_router_peer" "this" {
  count = 4

  name                      = "${var.name}-peer-${count.index}"
  router                    = google_compute_router.vpn.name
  region                    = var.gcp_region
  peer_ip_address           = local.tunnels[count.index].vgw_inside_ip
  peer_asn                  = var.aws_vgw_asn
  advertised_route_priority = 100
  interface                 = google_compute_router_interface.this[count.index].name
}
