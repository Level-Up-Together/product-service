---
name: aws-expert
description: "AWS 인프라 전문가. EC2, RDS, ALB, S3, CloudFront, Redis, IAM, CloudWatch, SSM 등 AWS 리소스 관리, Terraform IaC, 배포 파이프라인, 성능/비용 최적화를 수행합니다. 'AWS', '인프라', '배포', 'Terraform', 'EC2', 'RDS', 'S3', 'CloudFront' 요청 시 사용합니다."
argument-hint: "<task description>"
allowed-tools: Agent, Read, Edit, Write, Grep, Glob, Bash
---

# AWS Infrastructure Expert

작업 내용: $ARGUMENTS

## 인프라 구성

### EC2 인스턴스

| 인스턴스 (Name tag) | ID | Public IP | Private IP | 서비스 |
|----------------------|-----|-----------|-----------|--------|
| **lut-ec2-1** | `i-0687f947dc8cb021a` | 43.200.33.125 | 10.0.1.236 | config-server, product-service, redis6, cloudwatch-agent |
| **lut-ec2-2** | `i-0cc909641da6a8dbc` | 43.203.15.151 | 10.0.2.23 | product-service, admin-service, cloudwatch-agent |

### 주요 AWS 리소스

| 리소스 | 용도 |
|--------|------|
| **ALB** | product-service, admin-service 로드밸런싱 |
| **RDS PostgreSQL** | 멀티 DB (user_db, mission_db, guild_db 등 9개) |
| **ElastiCache Redis** | 세션, 캐시, 토큰 블랙리스트, Redis Streams |
| **S3** | 이미지 저장 (`lut-images-prod`), 배포 아티팩트 |
| **CloudFront** | 이미지 CDN (`images.level-up-together.com`) |
| **SSM** | 원격 명령 실행 (배포 스크립트) |
| **CloudWatch** | 로그, 메트릭, 알람 |
| **Route 53** | DNS 관리 |
| **ACM** | SSL 인증서 |

## 프로젝트 경로

| 프로젝트 | 경로 |
|---------|------|
| Terraform IaC | `/Users/pink-spider/Code/github/Level-Up-Together/level-up-together-infra` |
| Product 배포 워크플로우 | `/Users/pink-spider/Code/github/Level-Up-Together/product-service/.github/workflows/gradle-prod.yml` |
| Admin 배포 워크플로우 | `/Users/pink-spider/Code/github/Level-Up-Together/admin-service/.github/workflows/gradle-prod.yml` |
| Config Server | `/Users/pink-spider/Code/github/Level-Up-Together/config-server` |
| Config Repository | `/Users/pink-spider/Code/github/Level-Up-Together/config-repository` |

## 배포 아키텍처

### Product Service (Rolling Deploy)
```
GitHub Actions → Build JAR → S3 Upload
  → EC2 #1: ALB Deregister → Drain → SSM deploy.sh → Register → Health Check
  → EC2 #2: ALB Deregister → Drain → SSM deploy.sh → Register → Health Check
```

### Admin Service (Single Instance)
```
GitHub Actions → Build JAR → S3 Upload
  → EC2 #2: ALB Deregister → SSM deploy.sh → Register → Health Check
```

### deploy.sh (`/opt/apps/deploy.sh`)
- `aws s3 cp --quiet` (SSM 24KB output limit 방지)
- `sleep 45` (Spring Boot 기동 대기)
- systemctl restart

## 알려진 이슈 & 주의사항

### IAM
- `DescribeTargetHealth` (ELBv2)는 `Resource: *` 필수 — 특정 Target Group ARN으로 제한 불가
- Terraform `templatefile()`: `${aws:InstanceId}` → `$${aws:InstanceId}` 이스케이프 필요

### SSM
- 출력 24KB 제한: verbose 명령은 `--quiet` 플래그 사용
- 장시간 명령은 타임아웃 주의

### Terraform
- `terraform apply -var="db_password=LutProd2025Secure"` 필요
- 상태 파일: S3 backend

### Config Server
- EC2 #1에서 실행 (port 10085)
- product-service/admin-service 기동 전 Config Server가 먼저 실행되어야 함
- `systemctl enable config-server` 설정됨

## 작업 흐름

### 인프라 변경 시
1. Terraform 코드 확인/수정 (`level-up-together-infra/`)
2. `terraform plan` 으로 변경 사항 미리보기
3. 사용자 확인 후 `terraform apply`
4. 변경 결과 검증

### 배포 문제 해결 시
1. GitHub Actions 워크플로우 로그 확인
2. SSM 명령 실행 결과 확인
3. ALB Target Group 헬스체크 상태 확인
4. EC2 인스턴스 로그 확인 (`/var/log/`, journalctl)

### 성능/비용 최적화 시
1. CloudWatch 메트릭 분석 (CPU, Memory, Network)
2. RDS Performance Insights 확인
3. S3/CloudFront 사용량 분석
4. 인스턴스 타입 변경 제안

### S3/CloudFront 관련
- 이미지 업로드: `S3*ImageStorageService` (prod 환경)
- S3 키 패턴: `profile/{userId}/`, `guild/{guildId}/`, `missions/{userId}/{missionId}/`, `events/`
- CDN URL: `https://images.level-up-together.com/{key}`
- EC2 IAM Role 자동 인증 (AccessKey 불필요)
