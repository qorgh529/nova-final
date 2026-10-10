output "network_id" {
  value = google_compute_network.this.id
}

output "gke_name" {
  value = google_container_cluster.this.name
}

output "cloudsql_instance_name" {
  value = google_sql_database_instance.this.name
}

output "cloudsql_private_ip" {
  value = google_sql_database_instance.this.private_ip_address
}

output "admin_password" {
  value     = random_password.admin.result
  sensitive = true
}
