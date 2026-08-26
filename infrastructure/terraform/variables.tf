variable "aws_region" {
  description = "AWS region for the Porter platform infrastructure"
  type        = string
  default     = "ap-south-1"
}

variable "project_name" {
  description = "Project identifier used for resource naming"
  type        = string
  default     = "porter"
}

variable "environment" {
  description = "Deployment environment name"
  type        = string
  default     = "prod"
}

