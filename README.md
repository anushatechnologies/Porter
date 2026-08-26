# Porter Platform Monorepo

This repository is the Day 1 scaffold for the Porter-like logistics platform.

## Layout

- `services/` - Spring Boot backend services
- `frontend/` - React + TypeScript applications
- `infrastructure/` - Terraform, Kubernetes, and Helm assets
- `docs/` - architecture notes, ADRs, and implementation docs
- `scripts/` - helper scripts for setup and maintenance
- `.github/workflows/` - CI/CD pipelines

## Day 1 Status

Completed in this scaffold:

- Monorepo structure initialized
- Top-level folders created for backend, frontend, infra, and docs

Still external to the repo and not automatable from here:

- AWS account setup
- Domain registration
- GitHub organization setup
- Team access / permissions

## Next Steps

1. Add service-level build files for each backend service.
2. Add frontend package manifests for each app and shared package.
3. Add Terraform and Kubernetes starter modules.
4. Add CI/CD workflow files.
