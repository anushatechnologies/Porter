# run-all.ps1
# Script to build and run all services locally using Docker Compose

Write-Host "Starting Porter services..." -ForegroundColor Green

# Navigate to the root directory where the script is located
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = (Get-Item $ScriptDir).Parent.FullName
Set-Location -Path $RootDir

# Build and start services in detached mode
docker-compose up --build -d

if ($LASTEXITCODE -eq 0) {
    Write-Host "Services started successfully!" -ForegroundColor Green
    Write-Host "API Gateway is available at http://localhost:8080"
    Write-Host "Customer App is available at http://localhost:3001"
} else {
    Write-Host "Failed to start services. Check the logs above." -ForegroundColor Red
}
