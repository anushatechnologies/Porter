# Day 3 Infrastructure Design

Day 3 turns the architecture into cloud infrastructure foundations.

## Goals

- Build the AWS network foundation
- Create the EKS cluster baseline
- Provision primary data services
- Set up registry and deployment namespaces
- Prepare Kubernetes bootstrap manifests

## Scope

### AWS Infrastructure

- VPC with public and private subnets
- NAT gateways for outbound traffic
- EKS worker and system subnet placement
- RDS MySQL Multi-AZ
- ElastiCache Redis
- ECR repositories for services
- S3 buckets for documents and static assets

### Kubernetes Foundations

- Cluster namespaces
- External secrets bootstrap
- Ingress base manifests
- Service deployment templates
- ConfigMap and Secret conventions

## Deliverable

- AWS infra up

## Notes

- The actual AWS provisioning is usually driven by Terraform modules.
- Kubernetes manifests should stay environment-aware from the start.
- Secrets must be sourced from AWS Secrets Manager, not committed into git.

