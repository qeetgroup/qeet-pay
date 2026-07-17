# qp — Qeet Pay CLI (standalone Go module; the qeet-pay backend is Java).
GO        ?= go
BUILD_DIR ?= bin
BINARY    := qp

.PHONY: help build install test vet fmt fmt-check clean

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

build: ## Build the qp binary into bin/
	$(GO) build -o $(BUILD_DIR)/$(BINARY) .

install: ## Install qp onto $GOBIN / $GOPATH/bin
	$(GO) install .

test: vet ## Run unit tests (race detector on)
	$(GO) test -race -count=1 ./...

vet: ## Run go vet
	$(GO) vet ./...

fmt: ## Format Go code
	$(GO) fmt ./...

fmt-check: ## Fail if any file is not gofmt-clean
	@out="$$(gofmt -l .)"; if [ -n "$$out" ]; then echo "gofmt needed:"; echo "$$out"; exit 1; fi

clean: ## Remove build artefacts
	rm -rf $(BUILD_DIR)
