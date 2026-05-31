-include .env
export

BINARY          := engine
CMD             := ./cmd/engine
KEY_DIR         := keys
SIGNING_KEY     := $(KEY_DIR)/signing.key
SIGNING_CERT    := $(KEY_DIR)/signing.crt
PORT            ?= 8080
THUNDERID_ISSUER ?= http://127.0.0.1:$(PORT)
THUNDERID_DATA_DIR ?= ./data
THUNDERID_SIGNING_KEY ?= ./$(SIGNING_KEY)
DOCKER_IMAGE    ?= esignet:latest

GOLANGCI_LINT       ?= golangci-lint
GOLANGCI_LINT_VERSION ?= latest

.PHONY: help all keys build run dev test lint lint-install smoke tidy \
        docker-build docker-run clean distclean

.DEFAULT_GOAL := help

##@ Build
all: build ## Alias for build

keys: ## Generate local TLS signing key and certificate
	@mkdir -p $(KEY_DIR)
	@if [ -f "$(SIGNING_KEY)" ] && [ -f "$(SIGNING_CERT)" ]; then \
		echo "keys: $(SIGNING_KEY) and $(SIGNING_CERT) already exist"; \
	else \
		openssl req -x509 -newkey rsa:2048 \
			-keyout "$(SIGNING_KEY)" -out "$(SIGNING_CERT)" \
			-days 3650 -nodes -subj "/CN=esignet"; \
		echo "keys: wrote $(SIGNING_KEY) and $(SIGNING_CERT)"; \
	fi

build: keys ## Compile production binary (./engine)
	CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o $(BINARY) $(CMD)

##@ Run
run: keys ## Run with go run (development)
	PORT=$(PORT) \
	THUNDERID_ISSUER=$(THUNDERID_ISSUER) \
	THUNDERID_DATA_DIR=$(THUNDERID_DATA_DIR) \
	THUNDERID_SIGNING_KEY=$(THUNDERID_SIGNING_KEY) \
	go run $(CMD)

dev: run ## Alias for run

##@ Test
test: keys ## Run unit tests (race + coverage)
	go test -race -cover ./...

lint: ## Run golangci-lint
	@PATH="$$(go env GOPATH)/bin:$$PATH"; \
	export GOLANGCI_LINT_CACHE="$(CURDIR)/.golangci-lint-cache"; \
	if command -v golangci-lint >/dev/null 2>&1; then \
		golangci-lint run ./...; \
	else \
		echo "golangci-lint not on PATH; running via go run (use make lint-install to install)"; \
		go run github.com/golangci/golangci-lint/v2/cmd/golangci-lint@$(GOLANGCI_LINT_VERSION) run ./...; \
	fi

lint-install: ## Install golangci-lint (GOLANGCI_LINT_VERSION=$(GOLANGCI_LINT_VERSION))
	go install github.com/golangci/golangci-lint/v2/cmd/golangci-lint@$(GOLANGCI_LINT_VERSION)
	@echo "installed: $$(go env GOPATH)/bin/golangci-lint"

smoke: ## End-to-end OAuth smoke test (server must be running)
	BASE_URL=$(or $(BASE_URL),$(THUNDERID_ISSUER)) ./scripts/oauth-smoke.sh

##@ Docker
docker-build: ## Build container image (esignet:latest by default)
	docker build -f Dockerfile -t $(DOCKER_IMAGE) .

docker-run: docker-build ## Run container mapped to PORT (default 8080)
	docker run --rm -p $(PORT):8080 \
		-e THUNDERID_ISSUER=$(THUNDERID_ISSUER) \
		$(DOCKER_IMAGE)

##@ Maintenance
tidy: ## Run go mod tidy
	go mod tidy

clean: ## Remove build artefacts and coverage output
	rm -f $(BINARY)
	rm -rf bin/
	rm -f coverage.out coverage.html

distclean: clean ## Also remove generated signing keys under keys/
	rm -f $(SIGNING_KEY) $(SIGNING_CERT)
	@rmdir $(KEY_DIR) 2>/dev/null || true

##@ Help
help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage: make <target>\n"} \
		/^##@/ { printf "\n%s\n", substr($$0, 5) } \
		/^[a-zA-Z0-9_.-]+:.*##/ { printf "  %-18s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@echo ""
	@echo "Environment (override on the command line or in .env):"
	@echo "  PORT=$(PORT)"
	@echo "  THUNDERID_ISSUER=$(THUNDERID_ISSUER)"
	@echo "  THUNDERID_DATA_DIR=$(THUNDERID_DATA_DIR)"
	@echo "  THUNDERID_SIGNING_KEY=$(THUNDERID_SIGNING_KEY)"
	@echo "  THUNDERID_AUTHN_PROVIDER (catalog|mosip, default catalog)"
	@echo "  MOSIP_API_BASE_URL (optional, default https://api-internal.collab.mosip.net)"
	@echo "  MOSIP_LICENSE_KEY (optional, used in IDA endpoint paths)"
	@echo "  MOSIP_IDA_PARTNER_CERT_URL (optional override)"
	@echo "  MOSIP_SEND_OTP_BASE_URL (optional override)"
	@echo "  MOSIP_KYC_AUTH_BASE_URL (optional override)"
	@echo "  MOSIP_KYC_EXCHANGE_BASE_URL (optional override)"
	@echo "  MOSIP_P12_PATH (required for MOSIP auth)"
	@echo "  MOSIP_P12_PASSWORD (required for MOSIP auth)"
	@echo ""
